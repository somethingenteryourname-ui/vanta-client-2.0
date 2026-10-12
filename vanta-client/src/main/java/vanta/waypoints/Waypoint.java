package vanta.waypoints;

/** A saved location. Coordinates are block coordinates; dimension is a dimension id like "minecraft:the_nether". */
public final class Waypoint {
	public String name;
	public int x, y, z;
	public String dimension;
	public int color;
	public boolean visible = true;
	public boolean death;
	public long created;

	public Waypoint(String name, int x, int y, int z, String dimension, int color) {
		this.name = name;
		this.x = x;
		this.y = y;
		this.z = z;
		this.dimension = dimension;
		this.color = color;
		this.created = System.currentTimeMillis();
	}

	public String coords() { return x + ", " + y + ", " + z; }

	public double distanceTo(double px, double py, double pz) {
		double dx = x + 0.5 - px, dy = y + 0.5 - py, dz = z + 0.5 - pz;
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}

	/** Short dimension label for lists. */
	public String dimensionLabel() {
		return switch (dimension) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "End";
			default -> dimension.contains(":") ? dimension.substring(dimension.indexOf(':') + 1) : dimension;
		};
	}
}
