package vanta.modules.impl;

import vanta.config.Labeled;

/** Whether a key-activated feature works while the key is held or toggles on each press. */
public enum ActivationMode implements Labeled {
	HOLD("Hold key"), TOGGLE("Toggle");
	private final String label;
	ActivationMode(String label) { this.label = label; }
	@Override public String label() { return label; }
}
