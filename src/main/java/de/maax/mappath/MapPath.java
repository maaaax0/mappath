package de.maax.mappath;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.api.distmarker.Dist;

import java.lang.reflect.InvocationTargetException;

@Mod(MapPath.MODID)
public class MapPath {
    public static final String MODID = "mappath";

    public MapPath(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(MapPathNetworking::register);
        NeoForge.EVENT_BUS.addListener(StructureMarkerServerTracker::onServerTick);
        modContainer.registerConfig(ModConfig.Type.CLIENT, MapPathConfig.CLIENT_SPEC);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            registerClientExtensions(modContainer);
        }
    }

    private static void registerClientExtensions(ModContainer modContainer) {
        try {
            Class.forName("de.maax.mappath.client.MapPathClientSetup")
                .getMethod("registerExtensions", ModContainer.class)
                .invoke(null, modContainer);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to register MapPath client extensions", exception);
        }
    }
}
