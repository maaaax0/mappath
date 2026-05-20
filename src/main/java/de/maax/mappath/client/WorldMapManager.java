package de.maax.mappath.client;

import de.maax.mappath.MapPathConfig;
import de.maax.mappath.MapPathNetworking;
import de.maax.mappath.BannerIconType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.util.Collection;
import java.util.List;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

final class WorldMapManager {
    private static final int RECORD_RADIUS = 256;
    private static final int CHUNK_SIZE = 16;
    private static final int MIN_INITIAL_LOAD_CHUNKS_PER_TICK = 625;
    private static final int MIN_CHUNK_REFRESHS_PER_TICK = 12;
    private static final int MOVEMENT_REPAIR_RADIUS_CHUNKS = 4;

    private ResourceLocation dimensionId;
    private Path storeDirectory;
    private MapTileStore tileStore;
    private SurfaceMapAtlas mapAtlas;
    private final StructureMarkerStore structureMarkerStore = new StructureMarkerStore();
    private final WaypointStore waypointStore = new WaypointStore();
    private final Queue<PendingChunkRefresh> chunksPendingRefresh = new ArrayDeque<>();
    private final Set<String> queuedChunkRefreshes = new HashSet<>();
    private int liveRefreshStripOffset;
    private int tickCounter;
    private int missingChunkSearchTickCounter;

    MapTileStore getTileStore(Minecraft minecraft) {
        if (minecraft.level == null) {
            return null;
        }

        ResourceLocation currentDimension = minecraft.level.dimension().location();
        Path currentStoreDirectory = MapTileStore.directoryFor(minecraft, currentDimension);
        if (currentStoreDirectory == null) {
            this.closeCurrentStore();
            return null;
        }

        if (!currentDimension.equals(this.dimensionId) || !currentStoreDirectory.equals(this.storeDirectory)) {
            this.closeCurrentStore();
            this.dimensionId = currentDimension;
            this.storeDirectory = currentStoreDirectory;
            this.tileStore = new MapTileStore(minecraft, currentDimension);
            this.mapAtlas = new SurfaceMapAtlas();
            this.liveRefreshStripOffset = 0;
            this.tickCounter = 0;
            this.missingChunkSearchTickCounter = 0;
        }

        return this.tileStore;
    }

    void refreshChunkOnLoad(ResourceLocation dimensionId, int chunkX, int chunkZ) {
        this.queueChunkRefresh(dimensionId, chunkX, chunkZ);
    }

    SurfaceMapAtlas getMapAtlas(Minecraft minecraft) {
        this.getTileStore(minecraft);
        if (this.mapAtlas == null) {
            this.mapAtlas = new SurfaceMapAtlas();
        }

        return this.mapAtlas;
    }

    void recordAroundPlayer(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            this.close();
            return;
        }

        MapTileStore store = this.getTileStore(minecraft);
        if (store == null) {
            return;
        }

        int initialLoadBudget = Math.max(MapPathConfig.CLIENT.initialMapLoadPerTick(), MIN_INITIAL_LOAD_CHUNKS_PER_TICK);
        store.tickInitialLoad(initialLoadBudget);
        if (this.mapAtlas != null) {
            this.mapAtlas.tick(minecraft, store, initialLoadBudget);
        }
        store.flushDirtyTiles(MapPathConfig.CLIENT.tileWritesPerTick());

        int recordIntervalTicks = Math.min(MapPathConfig.CLIENT.recordIntervalTicks(), MapPathConfig.DEFAULT_RECORD_INTERVAL_TICKS);
        if (++this.tickCounter < recordIntervalTicks) {
            return;
        }
        this.tickCounter = 0;

        ClientLevel level = minecraft.level;
        int centerX = Mth.floor(minecraft.player.getX());
        int centerZ = Mth.floor(minecraft.player.getZ());
        this.queueIncompleteChunksNearPlayer(level, store, Math.floorDiv(centerX, CHUNK_SIZE), Math.floorDiv(centerZ, CHUNK_SIZE), MOVEMENT_REPAIR_RADIUS_CHUNKS);
        this.refreshLoadedChunks(minecraft, level, store);

        this.refreshLiveAroundPlayer(minecraft, level, store, centerX, centerZ);
        if (++this.missingChunkSearchTickCounter >= MapPathConfig.CLIENT.missingChunkSearchIntervalTicks()) {
            this.missingChunkSearchTickCounter = 0;
            this.queueMissingChunksAroundPlayer(level, store, centerX, centerZ);
        }
    }

    void close() {
        this.closeCurrentStore();
        this.liveRefreshStripOffset = 0;
        this.tickCounter = 0;
        this.missingChunkSearchTickCounter = 0;
        this.chunksPendingRefresh.clear();
        this.queuedChunkRefreshes.clear();
        this.structureMarkerStore.close();
        this.waypointStore.close();
    }

    long storeRevision(Minecraft minecraft) {
        MapTileStore store = this.getTileStore(minecraft);
        return store == null ? 0L : store.revision();
    }

    boolean initialLoadComplete(Minecraft minecraft) {
        MapTileStore store = this.getTileStore(minecraft);
        return store == null || store.initialLoadComplete();
    }

    int loadedTileCount(Minecraft minecraft) {
        MapTileStore store = this.getTileStore(minecraft);
        return store == null ? 0 : store.loadedTileCount();
    }

    int totalTileCount(Minecraft minecraft) {
        MapTileStore store = this.getTileStore(minecraft);
        return store == null ? 0 : store.totalTileCount();
    }

    void teleportToTopBlock(Minecraft minecraft, int worldX, int worldZ) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }

        MapPathNetworking.sendTeleportTop(worldX, worldZ);
    }

    void teleportToPosition(Minecraft minecraft, int worldX, int worldY, int worldZ) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null || minecraft.player.connection == null) {
            return;
        }

        MapPathNetworking.sendTeleportPosition(worldX, worldY, worldZ);
    }

    Collection<StructureMarkerStore.Marker> structureMarkers(Minecraft minecraft) {
        return this.structureMarkerStore.markers(minecraft);
    }

    void addStructureMarkers(Minecraft minecraft, ResourceLocation dimensionId, List<MapPathNetworking.StructureMarker> markers) {
        this.structureMarkerStore.addMarkers(minecraft, dimensionId, markers);
    }

    StructureMarkerStore.Marker structureMarker(Minecraft minecraft, String key) {
        return this.structureMarkerStore.marker(minecraft, key);
    }

    void updateStructureMarkerName(Minecraft minecraft, String key, String name) {
        this.structureMarkerStore.updateMarkerName(minecraft, key, name);
    }

    void setStructureMarkerHighlighted(Minecraft minecraft, String key, boolean highlighted) {
        this.structureMarkerStore.setMarkerHighlighted(minecraft, key, highlighted);
    }

    void deleteStructureMarker(Minecraft minecraft, String key) {
        this.structureMarkerStore.deleteMarker(minecraft, key);
    }

    Collection<WaypointStore.Waypoint> waypoints(Minecraft minecraft) {
        return this.waypointStore.waypoints(minecraft);
    }

    WaypointStore.Waypoint waypoint(Minecraft minecraft, UUID id) {
        return this.waypointStore.waypoint(minecraft, id);
    }

    void addWaypoint(Minecraft minecraft, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        this.waypointStore.addWaypoint(minecraft, icon, name, worldX, worldY, worldZ);
    }

    void updateWaypoint(Minecraft minecraft, UUID id, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        this.waypointStore.updateWaypoint(minecraft, id, icon, name, worldX, worldY, worldZ);
    }

    void setWaypointHighlighted(Minecraft minecraft, UUID id, boolean highlighted) {
        this.waypointStore.setWaypointHighlighted(minecraft, id, highlighted);
    }

    void deleteWaypoint(Minecraft minecraft, UUID id) {
        this.waypointStore.deleteWaypoint(minecraft, id);
    }

    int defaultWaypointY(Minecraft minecraft, int worldX, int worldZ) {
        MapTileStore store = this.getTileStore(minecraft);
        if (store != null) {
            int storedHeight = store.getHeight(worldX, worldZ);
            if (storedHeight != Integer.MIN_VALUE) {
                return storedHeight + 1;
            }
        }

        if (minecraft != null && minecraft.player != null) {
            return Mth.floor(minecraft.player.getY()) + 1;
        }

        return 0;
    }

    private void closeCurrentStore() {
        if (this.tileStore != null) {
            this.tileStore.close();
            this.tileStore = null;
        }
        if (this.mapAtlas != null) {
            this.mapAtlas.close();
            this.mapAtlas = null;
        }
        this.dimensionId = null;
        this.storeDirectory = null;
    }

    private void refreshLiveAroundPlayer(Minecraft minecraft, ClientLevel level, MapTileStore store, int centerX, int centerZ) {
        int liveRefreshRadius = MapPathConfig.CLIENT.liveRefreshRadius();
        if (liveRefreshRadius <= 0) {
            return;
        }

        int stripWidth = Math.max(MapPathConfig.CLIENT.liveRefreshColumnsPerTick(), MapPathConfig.DEFAULT_LIVE_REFRESH_COLUMNS_PER_TICK);
        int startX = centerX - liveRefreshRadius + this.liveRefreshStripOffset;
        int endX = Math.min(startX + stripWidth, centerX + liveRefreshRadius + 1);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos heightPos = new BlockPos.MutableBlockPos();

        for (int worldZ = centerZ - liveRefreshRadius; worldZ <= centerZ + liveRefreshRadius; worldZ++) {
            for (int worldX = startX; worldX < endX; worldX++) {
                SurfaceSampler.Sample sample = SurfaceSampler.sampleLive(minecraft, level, worldX, worldZ, pos, fluidPos, heightPos);
                if (sample != null) {
                    store.put(worldX, worldZ, sample.color(), sample.height());
                }
            }
        }

        this.liveRefreshStripOffset += stripWidth;
        if (this.liveRefreshStripOffset > liveRefreshRadius * 2) {
            this.liveRefreshStripOffset = 0;
        }
    }

    private void queueMissingChunksAroundPlayer(ClientLevel level, MapTileStore store, int centerX, int centerZ) {
        int minChunkX = Math.floorDiv(centerX - RECORD_RADIUS, CHUNK_SIZE);
        int maxChunkX = Math.floorDiv(centerX + RECORD_RADIUS, CHUNK_SIZE);
        int minChunkZ = Math.floorDiv(centerZ - RECORD_RADIUS, CHUNK_SIZE);
        int maxChunkZ = Math.floorDiv(centerZ + RECORD_RADIUS, CHUNK_SIZE);

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                if (level.hasChunk(chunkX, chunkZ) && !store.isChunkComplete(chunkX, chunkZ)) {
                    this.queueChunkRefresh(this.dimensionId, chunkX, chunkZ);
                }
            }
        }
    }

    private void queueIncompleteChunksNearPlayer(ClientLevel level, MapTileStore store, int centerChunkX, int centerChunkZ, int radiusChunks) {
        for (int offsetZ = -radiusChunks; offsetZ <= radiusChunks; offsetZ++) {
            for (int offsetX = -radiusChunks; offsetX <= radiusChunks; offsetX++) {
                int chunkX = centerChunkX + offsetX;
                int chunkZ = centerChunkZ + offsetZ;
                if (level.hasChunk(chunkX, chunkZ) && !store.isChunkComplete(chunkX, chunkZ)) {
                    this.queueChunkRefresh(this.dimensionId, chunkX, chunkZ);
                }
            }
        }
    }

    private void queueChunkRefresh(ResourceLocation dimensionId, int chunkX, int chunkZ) {
        long chunkKey = asLong(chunkX, chunkZ);
        String queueKey = dimensionId + ":" + chunkKey;
        if (this.queuedChunkRefreshes.add(queueKey)) {
            this.chunksPendingRefresh.add(new PendingChunkRefresh(dimensionId, chunkKey));
        }
    }

    private void refreshLoadedChunks(Minecraft minecraft, ClientLevel level, MapTileStore store) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos fluidPos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos heightPos = new BlockPos.MutableBlockPos();
        int refreshedChunks = 0;
        int chunkRefreshesPerTick = Math.max(MapPathConfig.CLIENT.chunkRefreshesPerTick(), MIN_CHUNK_REFRESHS_PER_TICK);

        while (refreshedChunks < chunkRefreshesPerTick && !this.chunksPendingRefresh.isEmpty()) {
            PendingChunkRefresh pendingChunk = this.chunksPendingRefresh.remove();
            this.queuedChunkRefreshes.remove(pendingChunk.queueKey());
            if (!pendingChunk.dimensionId().equals(this.dimensionId)) {
                continue;
            }

            long chunkKey = pendingChunk.chunkKey();
            int chunkX = unpackX(chunkKey);
            int chunkZ = unpackZ(chunkKey);
            if (!level.hasChunk(chunkX, chunkZ)) {
                continue;
            }

            int startX = chunkX * CHUNK_SIZE;
            int startZ = chunkZ * CHUNK_SIZE;
            for (int localZ = 0; localZ < CHUNK_SIZE; localZ++) {
                for (int localX = 0; localX < CHUNK_SIZE; localX++) {
                    int worldX = startX + localX;
                    int worldZ = startZ + localZ;
                    SurfaceSampler.Sample sample = SurfaceSampler.sampleLive(minecraft, level, worldX, worldZ, pos, fluidPos, heightPos);
                    if (sample != null) {
                        store.put(worldX, worldZ, sample.color(), sample.height());
                    }
                }
            }

            refreshedChunks++;
        }
    }

    private static long asLong(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static int unpackX(long chunkKey) {
        return (int)(chunkKey >> 32);
    }

    private static int unpackZ(long chunkKey) {
        return (int) chunkKey;
    }

    private record PendingChunkRefresh(ResourceLocation dimensionId, long chunkKey) {
        private String queueKey() {
            return this.dimensionId + ":" + this.chunkKey;
        }
    }
}
