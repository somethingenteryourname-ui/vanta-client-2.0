package vanta.packs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;

/**
 * Quick resource pack switching for the Visuals page. Turn packs on and off, move them
 * up, then press Apply once: Minecraft reloads resources a single time for all changes.
 */
public final class PackToggle {
	public record Row(String id, String title, String description, boolean on, boolean pending) {
	}

	private static final Set<String> CHANGED = new LinkedHashSet<>();
	private static List<String> order; // wanted selection, bottom to top (top wins)

	private PackToggle() {
	}

	private static PackRepository repo() {
		Minecraft mc = Minecraft.getInstance();
		return mc == null ? null : mc.getResourcePackRepository();
	}

	private static List<String> current() {
		PackRepository r = repo();
		if (order == null) order = r == null ? new ArrayList<>() : new ArrayList<>(r.getSelectedIds());
		return order;
	}

	/** Packs you can switch: everything except the built-in required ones. Top of the list = highest priority. */
	public static List<Row> rows() {
		PackRepository r = repo();
		List<Row> out = new ArrayList<>();
		if (r == null) return out;
		List<String> sel = current();
		List<Pack> available = new ArrayList<>(r.getAvailablePacks());
		// selected packs first, highest priority on top
		for (int i = sel.size() - 1; i >= 0; i--) {
			Pack p = r.getPack(sel.get(i));
			if (p != null && !p.isRequired() && !p.isFixedPosition()) out.add(row(p, true));
		}
		for (Pack p : available) {
			if (p.isRequired() || p.isFixedPosition() || sel.contains(p.getId())) continue;
			out.add(row(p, false));
		}
		return out;
	}

	private static Row row(Pack p, boolean on) {
		String title = p.getTitle().getString();
		String desc = p.getDescription().getString().replace('\n', ' ');
		return new Row(p.getId(), title, desc, on, CHANGED.contains(p.getId()));
	}

	public static void toggle(String id) {
		List<String> sel = current();
		if (!sel.remove(id)) sel.add(id);
		if (!CHANGED.remove(id)) CHANGED.add(id);
	}

	/** Moves a selected pack one step up (higher priority). */
	public static void raise(String id) {
		List<String> sel = current();
		int i = sel.indexOf(id);
		if (i < 0 || i >= sel.size() - 1) return;
		sel.remove(i);
		sel.add(i + 1, id);
		CHANGED.add(id);
	}

	public static boolean hasChanges() { return !CHANGED.isEmpty(); }

	/** Applies the changes and reloads resources once. */
	public static void apply() {
		PackRepository r = repo();
		Minecraft mc = Minecraft.getInstance();
		if (r == null || mc == null || CHANGED.isEmpty()) return;
		r.setSelected(new ArrayList<>(current()));
		mc.options.updateResourcePacks(r);
		CHANGED.clear();
		order = null;
	}

	public static void discard() {
		CHANGED.clear();
		order = null;
	}

	/** Re-reads the resource packs folder (after you add a pack). */
	public static void rescan() {
		PackRepository r = repo();
		if (r == null) return;
		Collection<String> keep = new ArrayList<>(r.getSelectedIds());
		r.reload();
		r.setSelected(keep);
		order = null;
		CHANGED.clear();
	}

	public static java.nio.file.Path folder() {
		return net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir().resolve("resourcepacks");
	}
}
