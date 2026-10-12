package vanta.menu;

import java.util.ArrayList;
import java.util.List;

/**
 * Small pure-Java GIF decoder for animated menu backgrounds. Frames are composited
 * (disposal methods 0-3, transparency, interlacing) and downscaled while decoding, so
 * a large GIF never needs more memory than the limits passed in.
 */
public final class GifDecoder {
	/** Decoded animation. Frames are ARGB, all {@code width x height}. */
	public record Result(int width, int height, List<int[]> frames, int[] delaysMs) {
	}

	private GifDecoder() {
	}

	public static Result decode(byte[] data, int maxWidth, int maxFrames, long maxTotalPixels) {
		Reader in = new Reader(data);
		String sig = in.ascii(6);
		if (!sig.equals("GIF87a") && !sig.equals("GIF89a")) throw new IllegalArgumentException("Not a GIF file");
		int w = in.u16();
		int h = in.u16();
		if (w <= 0 || h <= 0 || (long) w * h > 4096L * 4096L) throw new IllegalArgumentException("GIF size not supported: " + w + "x" + h);
		int packed = in.u8();
		in.u8(); // background color index: modern viewers treat the background as transparent
		in.u8(); // aspect ratio
		int[] global = (packed & 0x80) != 0 ? in.colorTable(2 << (packed & 7)) : null;

		float s = Math.min(1f, maxWidth / (float) w);
		int tw = Math.max(1, Math.round(w * s));
		int th = Math.max(1, Math.round(h * s));
		long perFrame = (long) tw * th;
		int frameLimit = (int) Math.max(1, Math.min(maxFrames, maxTotalPixels / Math.max(1, perFrame)));

		int[] canvas = new int[w * h];
		int[] previous = null;
		List<int[]> frames = new ArrayList<>();
		List<Integer> delays = new ArrayList<>();
		int disposal = 0, delay = 0, transparent = -1;

		while (in.hasMore() && frames.size() < frameLimit) {
			int block = in.u8();
			if (block == 0x3B) break; // trailer
			if (block == 0x21) {
				int label = in.u8();
				if (label == 0xF9) {
					int size = in.u8();
					int flags = in.u8();
					delay = in.u16() * 10;
					int ti = in.u8();
					in.skip(size - 4);
					in.skipSubBlocks();
					disposal = (flags >> 2) & 7;
					transparent = (flags & 1) != 0 ? ti : -1;
				} else {
					in.skipSubBlocks();
				}
				continue;
			}
			if (block != 0x2C) break; // unknown block: stop with what we have

			int fx = in.u16(), fy = in.u16(), fw = in.u16(), fh = in.u16();
			int fp = in.u8();
			int[] table = (fp & 0x80) != 0 ? in.colorTable(2 << (fp & 7)) : global;
			boolean interlaced = (fp & 0x40) != 0;
			int minCode = in.u8();
			byte[] lzw = in.subBlocks();
			if (table == null || fw <= 0 || fh <= 0) continue;

			if (disposal == 3) previous = canvas.clone();
			byte[] indices = new byte[fw * fh];
			decodeLzw(lzw, minCode, indices);

			for (int row = 0; row < fh; row++) {
				int srcRow = interlaced ? interlacedRow(row, fh) : row;
				int cy = fy + srcRow;
				if (cy < 0 || cy >= h) continue;
				for (int col = 0; col < fw; col++) {
					int cx = fx + col;
					if (cx < 0 || cx >= w) continue;
					int idx = indices[row * fw + col] & 0xFF;
					if (idx == transparent || idx >= table.length) continue;
					canvas[cy * w + cx] = table[idx];
				}
			}

			frames.add(scale(canvas, w, h, tw, th));
			delays.add(delay < 20 ? 100 : delay); // browsers treat 0-10 ms as 100 ms

			if (disposal == 2) {
				for (int row = Math.max(0, fy); row < Math.min(h, fy + fh); row++) {
					for (int col = Math.max(0, fx); col < Math.min(w, fx + fw); col++) canvas[row * w + col] = 0;
				}
			} else if (disposal == 3 && previous != null) {
				canvas = previous;
			}
			disposal = 0;
			delay = 0;
			transparent = -1;
		}
		if (frames.isEmpty()) throw new IllegalArgumentException("GIF has no frames");
		int[] d = new int[delays.size()];
		for (int i = 0; i < d.length; i++) d[i] = delays.get(i);
		return new Result(tw, th, frames, d);
	}

	/** Maps the n-th stored row of an interlaced image to its real row. */
	static int interlacedRow(int n, int height) {
		int[] starts = {0, 4, 2, 1};
		int[] steps = {8, 8, 4, 2};
		for (int pass = 0; pass < 4; pass++) {
			int count = height > starts[pass] ? (height - starts[pass] + steps[pass] - 1) / steps[pass] : 0;
			if (n < count) return starts[pass] + n * steps[pass];
			n -= count;
		}
		return height - 1;
	}

	static void decodeLzw(byte[] data, int minCodeSize, byte[] out) {
		if (minCodeSize < 1 || minCodeSize > 11) return;
		int clear = 1 << minCodeSize;
		int eoi = clear + 1;
		int codeSize = minCodeSize + 1;
		int next = clear + 2;
		short[] prefix = new short[4096];
		byte[] suffix = new byte[4096];
		byte[] stack = new byte[4097];
		for (int i = 0; i < clear; i++) suffix[i] = (byte) i;

		int datum = 0, bits = 0, outPos = 0, old = -1, first = 0;
		for (int pos = 0; pos < data.length && outPos < out.length; pos++) {
			datum |= (data[pos] & 0xFF) << bits;
			bits += 8;
			while (bits >= codeSize && outPos < out.length) {
				int code = datum & ((1 << codeSize) - 1);
				datum >>>= codeSize;
				bits -= codeSize;
				if (code == clear) {
					codeSize = minCodeSize + 1;
					next = clear + 2;
					old = -1;
					continue;
				}
				if (code == eoi) return;
				if (old == -1) {
					if (code >= clear) return; // corrupt
					out[outPos++] = suffix[code];
					old = code;
					first = code;
					continue;
				}
				int in = code;
				int sp = 0;
				if (code >= next) {
					if (code > next) return; // corrupt
					stack[sp++] = (byte) first;
					code = old;
				}
				while (code >= clear) {
					if (sp >= stack.length - 1) return;
					stack[sp++] = suffix[code];
					code = prefix[code];
				}
				first = suffix[code] & 0xFF;
				stack[sp++] = (byte) first;
				if (next < 4096) {
					prefix[next] = (short) old;
					suffix[next] = (byte) first;
					next++;
					if ((next & ((1 << codeSize) - 1)) == 0 && next < 4096) codeSize++;
				}
				old = in;
				while (sp > 0 && outPos < out.length) out[outPos++] = stack[--sp];
			}
		}
	}

	/** Box-filter downscale (or copy when the size is unchanged). ARGB in, ARGB out. */
	static int[] scale(int[] src, int w, int h, int tw, int th) {
		if (tw == w && th == h) return src.clone();
		int[] out = new int[tw * th];
		for (int y = 0; y < th; y++) {
			int y0 = y * h / th, y1 = Math.max(y0 + 1, (y + 1) * h / th);
			for (int x = 0; x < tw; x++) {
				int x0 = x * w / tw, x1 = Math.max(x0 + 1, (x + 1) * w / tw);
				long a = 0, r = 0, g = 0, b = 0;
				int n = 0;
				for (int yy = y0; yy < y1; yy++) {
					for (int xx = x0; xx < x1; xx++) {
						int c = src[yy * w + xx];
						a += c >>> 24;
						r += (c >> 16) & 0xFF;
						g += (c >> 8) & 0xFF;
						b += c & 0xFF;
						n++;
					}
				}
				out[y * tw + x] = (int) (a / n) << 24 | (int) (r / n) << 16 | (int) (g / n) << 8 | (int) (b / n);
			}
		}
		return out;
	}

	private static final class Reader {
		private final byte[] d;
		private int p;

		Reader(byte[] d) {
			this.d = d;
		}

		boolean hasMore() { return p < d.length; }

		int u8() {
			if (p >= d.length) throw new IllegalArgumentException("GIF file is truncated");
			return d[p++] & 0xFF;
		}

		int u16() { return u8() | (u8() << 8); }

		void skip(int n) { p = Math.min(d.length, p + Math.max(0, n)); }

		String ascii(int n) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < n; i++) sb.append((char) u8());
			return sb.toString();
		}

		int[] colorTable(int size) {
			int[] t = new int[size];
			for (int i = 0; i < size; i++) t[i] = 0xFF000000 | u8() << 16 | u8() << 8 | u8();
			return t;
		}

		void skipSubBlocks() {
			int n;
			while (p < d.length && (n = u8()) != 0) skip(n);
		}

		byte[] subBlocks() {
			java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
			int n;
			while (p < d.length && (n = u8()) != 0) {
				int len = Math.min(n, d.length - p);
				out.write(d, p, len);
				p += len;
			}
			return out.toByteArray();
		}
	}
}
