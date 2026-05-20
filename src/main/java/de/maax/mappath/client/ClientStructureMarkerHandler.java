package de.maax.mappath.client;

import de.maax.mappath.MapPathNetworking;
import net.minecraft.client.Minecraft;

public final class ClientStructureMarkerHandler {
    private ClientStructureMarkerHandler() {
    }

    public static void handle(MapPathNetworking.StructureMarkersPayload payload) {
        Minecraft minecraft = Minecraft.getInstance();
        WorldMapManager worldMapManager = MapPathClient.worldMapManager();
        worldMapManager.addStructureMarkers(minecraft, payload.dimensionId(), payload.markers());
    }
}
