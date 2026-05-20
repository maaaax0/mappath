package de.maax.mappath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

final class MapTileStore implements AutoCloseable {
    private static final int TILE_SIZE = 16;
    private static final int TILE_AREA = TILE_SIZE * TILE_SIZE;
    private static final int FILE_VERSION = 13;
    private static final int UNKNOWN_COLOR = 0;
    private static final short UNKNOWN_HEIGHT = Short.MIN_VALUE;

    private final Path tileDirectory;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private final Queue<Long> dirtyTileQueue = new ArrayDeque<>();
    private final Set<Long> queuedDirtyTiles = new HashSet<>();
    private final Queue<ChunkFile> initialLoadQueue = new ArrayDeque<>();
    private final Queue<Long> atlasUpdateQueue = new ArrayDeque<>();
    private final Set<Long> queuedAtlasUpdates = new HashSet<>();
    private long revision;
    private boolean tileDirectoryCreated;
    private boolean initialLoadStarted;
    private boolean initialLoadComplete;
    private int totalTileCount;
    private int loadedTileCount;

    MapTileStore(Minecraft minecraft, ResourceLocation dimensionId) {
        this.tileDirectory = directoryFor(minecraft, dimensionId);
        int centerChunkX = minecraft.player == null ? 0 : minecraft.player.chunkPosition().x;
        int centerChunkZ = minecraft.player == null ? 0 : minecraft.player.chunkPosition().z;
        this.startInitialLoad(centerChunkX, centerChunkZ);
    }

    static Path directoryFor(Minecraft minecraft, ResourceLocation dimensionId) {
        Path worldDirectory = worldDirectory(minecraft);
        if (worldDirectory == null) {
            return null;
        }

        String safeDimension = sanitizePathPart(dimensionId.toString());
        return minecraft.gameDirectory
            .toPath()
            .resolve("mappath")
            .resolve("maps")
            .resolve(worldDirectory)
            .resolve(safeDimension);
    }

    int getColor(int worldX, int worldZ) {
        Tile tile = this.getTile(worldX >> 4, worldZ >> 4);
        return tile.colors[index(worldX, worldZ)];
    }

    int getHeight(int worldX, int worldZ) {
        Tile tile = this.getTile(worldX >> 4, worldZ >> 4);
        short height = tile.heights[index(worldX, worldZ)];
        return height == UNKNOWN_HEIGHT ? Integer.MIN_VALUE : height;
    }

    boolean isChunkComplete(int chunkX, int chunkZ) {
        Tile tile = this.getTile(chunkX, chunkZ);
        for (int color : tile.colors) {
            if (color == UNKNOWN_COLOR) {
                return false;
            }
        }

        return true;
    }

    boolean put(int worldX, int worldZ, int color, int height) {
        int chunkX = worldX >> 4;
        int chunkZ = worldZ >> 4;
        long tileKey = asLong(chunkX, chunkZ);
        Tile tile = this.getTile(chunkX, chunkZ);
        int index = index(worldX, worldZ);
        short storedHeight = (short) Mth.clamp(height, Short.MIN_VALUE + 1, Short.MAX_VALUE);
        if (tile.colors[index] == color && tile.heights[index] == storedHeight) {
            return false;
        }

        tile.colors[index] = color;
        tile.heights[index] = storedHeight;
        tile.dirty = true;
        this.queueDirtyTile(tileKey);
        this.revision++;
        return true;
    }

    long revision() {
        return this.revision;
    }

    void startInitialLoad() {
        this.startInitialLoad(0, 0);
    }

    void startInitialLoad(int centerChunkX, int centerChunkZ) {
        this.initialLoadQueue.clear();
        this.initialLoadStarted = true;
        this.initialLoadComplete = false;
        this.totalTileCount = 0;
        this.loadedTileCount = 0;

        if (this.tileDirectory == null || !Files.isDirectory(this.tileDirectory)) {
            this.initialLoadComplete = true;
            return;
        }

        List<ChunkFile> chunkFiles = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(this.tileDirectory, "*.bin")) {
            for (Path path : stream) {
                ChunkFile chunkFile = parseChunkFile(path);
                if (chunkFile != null) {
                    chunkFiles.add(chunkFile);
                }
            }
        } catch (IOException ignored) {
            this.initialLoadQueue.clear();
            this.totalTileCount = 0;
        }

        chunkFiles.sort(Comparator.comparingLong(chunkFile -> distanceSquared(chunkFile.chunkX(), chunkFile.chunkZ(), centerChunkX, centerChunkZ)));
        this.initialLoadQueue.addAll(chunkFiles);
        this.totalTileCount = chunkFiles.size();

        this.initialLoadComplete = this.initialLoadQueue.isEmpty();
    }

    int tickInitialLoad(int budget) {
        if (!this.initialLoadStarted) {
            this.startInitialLoad();
        }

        int loaded = 0;
        while (loaded < budget && !this.initialLoadQueue.isEmpty()) {
            ChunkFile chunkFile = this.initialLoadQueue.remove();
            long key = asLong(chunkFile.chunkX(), chunkFile.chunkZ());
            if (!this.tiles.containsKey(key)) {
                Tile tile = this.load(chunkFile.chunkX(), chunkFile.chunkZ());
                this.tiles.put(key, tile);
            }
            this.queueAtlasUpdate(key);
            this.loadedTileCount++;
            loaded++;
        }

        if (this.initialLoadQueue.isEmpty()) {
            this.initialLoadComplete = true;
        }

        return loaded;
    }

    boolean initialLoadComplete() {
        return this.initialLoadComplete;
    }

    int loadedTileCount() {
        return this.loadedTileCount;
    }

    int totalTileCount() {
        return this.totalTileCount;
    }

    Long pollAtlasUpdateChunk() {
        Long key = this.atlasUpdateQueue.poll();
        if (key != null) {
            this.queuedAtlasUpdates.remove(key);
        }
        return key;
    }

    void copyColors(int chunkX, int chunkZ, int[] target) {
        Tile tile = this.getTile(chunkX, chunkZ);
        System.arraycopy(tile.colors, 0, target, 0, TILE_AREA);
    }

    void flush() {
        for (Tile tile : this.tiles.values()) {
            if (tile.dirty) {
                this.save(tile);
            }
        }
        this.dirtyTileQueue.clear();
        this.queuedDirtyTiles.clear();
    }

    int flushDirtyTiles(int maxTiles) {
        int savedTiles = 0;
        while (savedTiles < maxTiles && !this.dirtyTileQueue.isEmpty()) {
            long key = this.dirtyTileQueue.remove();
            this.queuedDirtyTiles.remove(key);
            Tile tile = this.tiles.get(key);
            if (tile != null && tile.dirty && this.save(tile)) {
                savedTiles++;
            } else if (tile != null && tile.dirty) {
                this.queueDirtyTile(key);
                break;
            }
        }

        return savedTiles;
    }

    @Override
    public void close() {
        this.flush();
    }

    private Tile getTile(int chunkX, int chunkZ) {
        long key = asLong(chunkX, chunkZ);
        Tile tile = this.tiles.get(key);
        if (tile != null) {
            return tile;
        }

        boolean persistedTile = Files.isRegularFile(this.pathFor(chunkX, chunkZ));
        tile = this.load(chunkX, chunkZ);
        this.tiles.put(key, tile);
        if (persistedTile) {
            this.queueAtlasUpdate(key);
        }
        return tile;
    }

    private Tile load(int chunkX, int chunkZ) {
        Tile tile = new Tile(chunkX, chunkZ);
        Path path = this.pathFor(chunkX, chunkZ);
        if (!Files.isRegularFile(path)) {
            return tile;
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(path)))) {
            if (input.readInt() != FILE_VERSION) {
                return tile;
            }

            for (int i = 0; i < TILE_AREA; i++) {
                tile.colors[i] = input.readInt();
                tile.heights[i] = input.readShort();
            }
        } catch (IOException ignored) {
            return new Tile(chunkX, chunkZ);
        }

        return tile;
    }

    private boolean save(Tile tile) {
        try {
            if (!this.tileDirectoryCreated) {
                Files.createDirectories(this.tileDirectory);
                this.tileDirectoryCreated = true;
            }
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(this.pathFor(tile.chunkX, tile.chunkZ))))) {
                output.writeInt(FILE_VERSION);
                for (int i = 0; i < TILE_AREA; i++) {
                    output.writeInt(tile.colors[i]);
                    output.writeShort(tile.heights[i]);
                }
            }
            tile.dirty = false;
            return true;
        } catch (IOException ignored) {
            return false;
        }
    }

    private void queueDirtyTile(long key) {
        if (this.queuedDirtyTiles.add(key)) {
            this.dirtyTileQueue.add(key);
        }
        this.queueAtlasUpdate(key);
    }

    private void queueAtlasUpdate(long key) {
        if (this.queuedAtlasUpdates.add(key)) {
            this.atlasUpdateQueue.add(key);
        }
    }

    private Path pathFor(int chunkX, int chunkZ) {
        return this.tileDirectory.resolve(chunkX + "_" + chunkZ + ".bin");
    }

    private static Path worldDirectory(Minecraft minecraft) {
        if (minecraft.isLocalServer()) {
            if (!minecraft.hasSingleplayerServer()) {
                return null;
            }

            IntegratedServer server = minecraft.getSingleplayerServer();
            long seed = server.getWorldData().worldGenOptions().seed();
            String worldFolder = server.getWorldPath(LevelResource.ROOT).normalize().getFileName().toString();
            return Path.of("seed_" + seed, "world_" + sanitizePathPart(worldFolder));
        }

        ServerData serverData = minecraft.getCurrentServer();
        if (serverData != null && serverData.ip != null && !serverData.ip.isBlank()) {
            return Path.of("server_" + sanitizePathPart(serverData.ip));
        }

        return null;
    }

    private static String sanitizePathPart(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return sanitized.isBlank() ? "unknown" : sanitized;
    }

    private static int index(int worldX, int worldZ) {
        return (worldZ & 15) * TILE_SIZE + (worldX & 15);
    }

    private static long asLong(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xFFFFFFFFL);
    }

    private static long distanceSquared(int chunkX, int chunkZ, int centerChunkX, int centerChunkZ) {
        long dx = (long)chunkX - centerChunkX;
        long dz = (long)chunkZ - centerChunkZ;
        return dx * dx + dz * dz;
    }

    static int unpackX(long chunkKey) {
        return (int)(chunkKey >> 32);
    }

    static int unpackZ(long chunkKey) {
        return (int) chunkKey;
    }

    private static ChunkFile parseChunkFile(Path path) {
        String fileName = path.getFileName().toString();
        if (!fileName.endsWith(".bin")) {
            return null;
        }

        String stem = fileName.substring(0, fileName.length() - 4);
        int separator = stem.indexOf('_');
        if (separator <= 0 || separator >= stem.length() - 1) {
            return null;
        }

        try {
            int chunkX = Integer.parseInt(stem.substring(0, separator));
            int chunkZ = Integer.parseInt(stem.substring(separator + 1));
            return new ChunkFile(chunkX, chunkZ);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record ChunkFile(int chunkX, int chunkZ) {
    }

    private static final class Tile {
        private final int chunkX;
        private final int chunkZ;
        private final int[] colors = new int[TILE_AREA];
        private final short[] heights = new short[TILE_AREA];
        private boolean dirty;

        private Tile(int chunkX, int chunkZ) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            for (int i = 0; i < TILE_AREA; i++) {
                this.colors[i] = UNKNOWN_COLOR;
                this.heights[i] = UNKNOWN_HEIGHT;
            }
        }
    }
}
