package de.maax.mappath.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

final class MapTileStore implements AutoCloseable {
    private static final int TILE_SIZE = 16;
    private static final int TILE_AREA = TILE_SIZE * TILE_SIZE;
    private static final int FILE_VERSION = 13;
    private static final int UNKNOWN_COLOR = 0;
    private static final short UNKNOWN_HEIGHT = Short.MIN_VALUE;

    private final Path tileDirectory;
    private final Map<Long, Tile> tiles = new HashMap<>();
    private long revision;

    MapTileStore(Minecraft minecraft, ResourceLocation dimensionId) {
        this.tileDirectory = directoryFor(minecraft, dimensionId);
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
        Tile tile = this.getTile(worldX >> 4, worldZ >> 4);
        int index = index(worldX, worldZ);
        short storedHeight = (short) Mth.clamp(height, Short.MIN_VALUE + 1, Short.MAX_VALUE);
        if (tile.colors[index] == color && tile.heights[index] == storedHeight) {
            return false;
        }

        tile.colors[index] = color;
        tile.heights[index] = storedHeight;
        tile.dirty = true;
        this.revision++;
        return true;
    }

    long revision() {
        return this.revision;
    }

    void flush() {
        for (Tile tile : this.tiles.values()) {
            if (tile.dirty) {
                this.save(tile);
            }
        }
    }

    @Override
    public void close() {
        this.flush();
    }

    private Tile getTile(int chunkX, int chunkZ) {
        long key = asLong(chunkX, chunkZ);
        return this.tiles.computeIfAbsent(key, ignored -> this.load(chunkX, chunkZ));
    }

    private Tile load(int chunkX, int chunkZ) {
        Tile tile = new Tile(chunkX, chunkZ);
        Path path = this.pathFor(chunkX, chunkZ);
        if (!Files.isRegularFile(path)) {
            return tile;
        }

        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
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

    private void save(Tile tile) {
        try {
            Files.createDirectories(this.tileDirectory);
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(this.pathFor(tile.chunkX, tile.chunkZ)))) {
                output.writeInt(FILE_VERSION);
                for (int i = 0; i < TILE_AREA; i++) {
                    output.writeInt(tile.colors[i]);
                    output.writeShort(tile.heights[i]);
                }
            }
            tile.dirty = false;
        } catch (IOException ignored) {
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
