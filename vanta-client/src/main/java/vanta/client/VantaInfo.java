package vanta.client;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/** Version strings, read from mod metadata (no network, no personal data). */
public final class VantaInfo {
	private static String vanta;
	private static String minecraft;
	private static String loader;
	private static String fabricApi;

	private VantaInfo() {
	}

	public static String vantaVersion() {
		if (vanta == null) vanta = version("vanta");
		return vanta;
	}

	public static String minecraftVersion() {
		if (minecraft == null) minecraft = version("minecraft");
		return minecraft;
	}

	public static String loaderVersion() {
		if (loader == null) loader = version("fabricloader");
		return loader;
	}

	public static String fabricApiVersion() {
		if (fabricApi == null) fabricApi = version("fabric-api");
		return fabricApi;
	}

	private static String version(String modId) {
		return FabricLoader.getInstance().getModContainer(modId)
				.map(ModContainer::getMetadata)
				.map(m -> m.getVersion().getFriendlyString())
				.orElse("unknown");
	}
}
