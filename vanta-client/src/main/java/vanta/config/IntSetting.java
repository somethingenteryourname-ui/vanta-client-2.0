package vanta.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** A whole number clamped to [min, max] and snapped to step. Shown as a slider. */
public final class IntSetting extends Setting<Integer> {
	private final int min;
	private final int max;
	private final int step;
	private final String unit;

	public IntSetting(String id, String name, String description, int defaultValue, int min, int max, int step, String unit) {
		super(id, name, description, defaultValue);
		this.min = min;
		this.max = max;
		this.step = Math.max(1, step);
		this.unit = unit == null ? "" : unit;
	}

	public int min() { return min; }
	public int max() { return max; }
	public int step() { return step; }
	public int value() { return get(); }

	/** Sets the value from a 0..1 slider position. */
	public void setFraction(double fraction) {
		double f = Math.max(0, Math.min(1, fraction));
		set((int) Math.round(min + f * (max - min)));
	}

	public double fraction() {
		return max == min ? 0 : (get() - min) / (double) (max - min);
	}

	@Override public Kind kind() { return Kind.INT; }

	@Override
	protected Integer validate(Integer candidate) {
		int v = Math.max(min, Math.min(max, candidate));
		v = min + Math.round((v - min) / (float) step) * step;
		return Math.max(min, Math.min(max, v));
	}

	@Override public JsonElement toJson() { return new JsonPrimitive(get()); }

	@Override
	protected Integer parse(JsonElement element) {
		if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) return null;
		return (int) Math.round(element.getAsDouble());
	}

	@Override public String displayValue() { return get() + unit; }
}
