package de.maax.mappath;

import de.maax.mappath.client.MapPathConfigScreen;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = MapPath.MODID, dist = Dist.CLIENT)
public class MapPath {
    public static final String MODID = "mappath";

    public MapPath(ModContainer modContainer) {
        modContainer.registerConfig(ModConfig.Type.CLIENT, MapPathConfig.CLIENT_SPEC);
        modContainer.registerExtensionPoint(
            IConfigScreenFactory.class,
            (container, parent) -> new ConfigurationScreen(container, parent, MapPathConfigScreen::new)
        );
    }
}
