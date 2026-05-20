package de.maax.mappath.client;

import de.maax.mappath.MapPathConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

final class WorldMapManager {
    private static final int RECORD_RADIUS = 256;
    private static final int RECORD_INTERVAL_TICKS = 1;
    private static final int CHUNK_REFRESHES_PER_TICK = 1;
    private static final int CHUNK_SIZE = 16;
    private static final int LIVE_REFRESH_STRIP_WIDTH = 2;

    private ResourceLocation dimensionId;
    private Path storeDirectory;
    private MapTileStore tileStore;
    private final Queue<PendingChunkRefresh> chunksPendingRefresh = new ArrayDeque<>();
    private final Set<String> queuedChunkRefreshes = new HashSet<>();
    private int liveRefreshStripOffset;
    private int tickCounter;

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
            this.liveRefreshStripOffset = 0;
        }

        return this.tileStore;
    }

    void refreshChunkOnLoad(ResourceLocation dimensionId, int chunkX, int chunkZ) {
        this.queueChunkRefresh(dimensionId, chunkX, chunkZ);
    }

    void recordAroundPlayer(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }
        if (++this.tickCounter < RECORD_INTERVAL_TICKS) {
            return;
        }
        this.tickCounter = 0;

        MapTileStore store = this.getTileStore(minecraft);
        if (store == null) {
            return;
        }

        ClientLevel level = minecraft.level;
        this.refreshLoadedChunks(minecraft, level, store);

        int centerX = Mth.floor(minecraft.player.getX());
        int centerZ = Mth.floor(minecraft.player.getZ());
        this.refreshLiveAroundPlayer(minecraft, level, store, centerX, centerZ);
        this.queueMissingChunksAroundPlayer(level, store, centerX, centerZ);
        this.refreshLoadedChunks(minecraft, level, store);
    }

    void close() {
        this.closeCurrentStore();
        this.liveRefreshStripOffset = 0;
        this.tickCounter = 0;
        this.chunksPendingRefresh.clear();
        this.queuedChunkRefreshes.clear();
    }

    long storeRevision(Minecraft minecraft) {
        MapTileStore store = this.getTileStore(minecraft);
        return store == null ? 0L : store.revision();
    }

    private void closeCurrentStore() {
        if (this.tileStore != null) {
            this.tileStore.close();
            this.tileStore = null;
        }
        this.dimensionId = null;
        this.storeDirectory = null;
    }

    private void refreshLiveAroundPlayer(Minecraft minecraft, ClientLevel level, MapTileStore store, int centerX, int centerZ) {
        int liveRefreshRadius = MapPathConfig.CLIENT.liveRefreshRadius();
        if (liveRefreshRadius <= 0) {
            return;
        }

        int startX = centerX - liveRefreshRadius + this.liveRefreshStripOffset;
        int endX = Math.min(startX + LIVE_REFRESH_STRIP_WIDTH, centerX + liveRefreshRadius + 1);
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

        this.liveRefreshStripOffset += LIVE_REFRESH_STRIP_WIDTH;
        if (this.liveRefreshStripOffset > liveRefreshRadius * 2) {
            this.liveRefreshStripOffset = 0;
            store.flush();
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

        while (refreshedChunks < CHUNK_REFRESHES_PER_TICK && !this.chunksPendingRefresh.isEmpty()) {
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
