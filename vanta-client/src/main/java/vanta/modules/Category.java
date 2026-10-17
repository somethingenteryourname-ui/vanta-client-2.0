package vanta.modules;

public enum Category {
	PERFORMANCE("Performance"),
	VISUALS("Visuals"),
	PVP("PvP"),
	HUD("HUD"),
	INTERFACE("Interface"),
	QOL("Quality of life"),
	EXPERIMENTAL("Experimental");

	public final String label;

	Category(String label) {
		this.label = label;
	}
}
