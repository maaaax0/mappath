package de.maax.mappath;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class StructureMarkerServerTracker {
    private static final int SCAN_INTERVAL_TICKS = 20;
    private static final int SCAN_RADIUS_CHUNKS = 3;
    private static int tickCounter;

    private StructureMarkerServerTracker() {
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (++tickCounter < SCAN_INTERVAL_TICKS) {
            return;
        }
        tickCounter = 0;

        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            sendKnownStructuresNear(player);
        }
    }

    private static void sendKnownStructuresNear(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Registry<Structure> structureRegistry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        ChunkPos centerChunk = player.chunkPosition();
        List<MapPathNetworking.StructureMarker> markers = new ArrayList<>();
        Set<String> markerKeys = new HashSet<>();

        for (int chunkZ = centerChunk.z - SCAN_RADIUS_CHUNKS; chunkZ <= centerChunk.z + SCAN_RADIUS_CHUNKS; chunkZ++) {
            for (int chunkX = centerChunk.x - SCAN_RADIUS_CHUNKS; chunkX <= centerChunk.x + SCAN_RADIUS_CHUNKS; chunkX++) {
                ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);
                for (StructureStart start : level.structureManager().startsForStructure(chunkPos, structure -> isTrackedStructure(structureRegistry, structure))) {
                    addMarker(player, structureRegistry, start, markers, markerKeys);
                }
            }
        }

        if (!markers.isEmpty()) {
            MapPathNetworking.sendStructureMarkers(player, level.dimension().location(), markers);
        }
    }

    private static boolean isTrackedStructure(Registry<Structure> structureRegistry, Structure structure) {
        return structureRegistry.getResourceKey(structure)
            .map(StructureMarkerType::byStructureKey)
            .isPresent();
    }

    private static void addMarker(
        ServerPlayer player,
        Registry<Structure> structureRegistry,
        StructureStart start,
        List<MapPathNetworking.StructureMarker> markers,
        Set<String> markerKeys
    ) {
        if (!start.isValid()) {
            return;
        }

        Optional<ResourceKey<Structure>> structureKey = structureRegistry.getResourceKey(start.getStructure());
        if (structureKey.isEmpty()) {
            return;
        }

        StructureMarkerType markerType = StructureMarkerType.byStructureKey(structureKey.get());
        if (markerType == null) {
            return;
        }

        BoundingBox bounds = start.getBoundingBox();
        if (!bounds.isInside(player.blockPosition())) {
            return;
        }

        int centerX = bounds.getCenter().getX();
        int centerY = bounds.getCenter().getY();
        int centerZ = bounds.getCenter().getZ();
        String markerKey = markerType.id() + ":" + centerX + ":" + centerZ;
        if (markerKeys.add(markerKey)) {
            markers.add(new MapPathNetworking.StructureMarker(
                markerType.id(),
                centerX,
                centerY,
                centerZ,
                bounds.minX(),
                bounds.minY(),
                bounds.minZ(),
                bounds.maxX(),
                bounds.maxY(),
                bounds.maxZ()
            ));
        }
    }

}
