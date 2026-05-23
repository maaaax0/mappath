package de.maax.mappath.client;

import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

public final class MapPathClientSetup {
    private MapPathClientSetup() {
    }

    public static void registerExtensions(ModContainer modContainer) {
        modContainer.registerExtensionPoint(
            IConfigScreenFactory.class,
            (container, parent) -> new MapPathConfigScreen(parent)
        );
    }
}
