package de.maax.mappath.client;

import de.maax.mappath.BannerIconType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class WaypointStore {
    private static final int FILE_VERSION = 2;
    private static final String FILE_NAME = "waypoints.bin";

    private ResourceLocation dimensionId;
    private Path waypointPath;
    private final Map<UUID, Waypoint> waypoints = new HashMap<>();
    private boolean dirty;

    Collection<Waypoint> waypoints(Minecraft minecraft) {
        this.ensureLoaded(minecraft);
        return this.waypoints.values();
    }

    Waypoint waypoint(Minecraft minecraft, UUID id) {
        this.ensureLoaded(minecraft);
        return this.waypoints.get(id);
    }

    void addWaypoint(Minecraft minecraft, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        this.ensureLoaded(minecraft);
        if (this.waypointPath == null) {
            return;
        }

        Waypoint waypoint = new Waypoint(UUID.randomUUID(), icon, name, worldX, worldY, worldZ, true);
        this.waypoints.put(waypoint.id(), waypoint);
        this.dirty = true;
        this.flush();
    }

    void updateWaypoint(Minecraft minecraft, UUID id, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        this.ensureLoaded(minecraft);
        if (this.waypointPath == null || !this.waypoints.containsKey(id)) {
            return;
        }

        Waypoint existingWaypoint = this.waypoints.get(id);
        this.waypoints.put(id, new Waypoint(id, icon, name, worldX, worldY, worldZ, existingWaypoint.highlighted()));
        this.dirty = true;
        this.flush();
    }

    void setWaypointHighlighted(Minecraft minecraft, UUID id, boolean highlighted) {
        this.ensureLoaded(minecraft);
        Waypoint waypoint = this.waypoints.get(id);
        if (waypoint == null || waypoint.highlighted() == highlighted) {
            return;
        }

        this.waypoints.put(id, new Waypoint(waypoint.id(), waypoint.icon(), waypoint.name(), waypoint.worldX(), waypoint.worldY(), waypoint.worldZ(), highlighted));
        this.dirty = true;
        this.flush();
    }

    void deleteWaypoint(Minecraft minecraft, UUID id) {
        this.ensureLoaded(minecraft);
        if (this.waypoints.remove(id) != null) {
            this.dirty = true;
            this.flush();
        }
    }

    void close() {
        this.flush();
        this.waypoints.clear();
        this.dimensionId = null;
        this.waypointPath = null;
        this.dirty = false;
    }

    private void ensureLoaded(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            this.close();
            return;
        }

        ResourceLocation requestedDimensionId = minecraft.level.dimension().location();
        Path directory = MapTileStore.directoryFor(minecraft, requestedDimensionId);
        if (directory == null) {
            this.close();
            return;
        }

        Path requestedWaypointPath = directory.resolve(FILE_NAME);
        if (requestedDimensionId.equals(this.dimensionId) && requestedWaypointPath.equals(this.waypointPath)) {
            return;
        }

        this.flush();
        this.dimensionId = requestedDimensionId;
        this.waypointPath = requestedWaypointPath;
        this.waypoints.clear();
        this.dirty = false;
        this.load();
    }

    private void load() {
        if (this.waypointPath == null || !Files.isRegularFile(this.waypointPath)) {
            return;
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(this.waypointPath)))) {
            int fileVersion = input.readInt();
            if (fileVersion < 1 || fileVersion > FILE_VERSION) {
                return;
            }

            int waypointCount = input.readInt();
            for (int index = 0; index < waypointCount; index++) {
                UUID id = new UUID(input.readLong(), input.readLong());
                BannerIconType icon = BannerIconType.byId(input.readUTF());
                String name = input.readUTF();
                int worldX = input.readInt();
                int worldY = input.readInt();
                int worldZ = input.readInt();
                boolean highlighted = fileVersion >= 2 ? input.readBoolean() : true;
                if (icon != null) {
                    Waypoint waypoint = new Waypoint(id, icon, name, worldX, worldY, worldZ, highlighted);
                    this.waypoints.put(waypoint.id(), waypoint);
                }
            }
        } catch (IOException ignored) {
            this.waypoints.clear();
        }
    }

    private void flush() {
        if (!this.dirty || this.waypointPath == null) {
            return;
        }

        try {
            Files.createDirectories(this.waypointPath.getParent());
            List<Waypoint> sortedWaypoints = new ArrayList<>(this.waypoints.values());
            sortedWaypoints.sort(Comparator.comparing(waypoint -> waypoint.id().toString()));
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(this.waypointPath)))) {
                output.writeInt(FILE_VERSION);
                output.writeInt(sortedWaypoints.size());
                for (Waypoint waypoint : sortedWaypoints) {
                    output.writeLong(waypoint.id().getMostSignificantBits());
                    output.writeLong(waypoint.id().getLeastSignificantBits());
                    output.writeUTF(waypoint.icon().id());
                    output.writeUTF(waypoint.name());
                    output.writeInt(waypoint.worldX());
                    output.writeInt(waypoint.worldY());
                    output.writeInt(waypoint.worldZ());
                    output.writeBoolean(waypoint.highlighted());
                }
            }
            this.dirty = false;
        } catch (IOException ignored) {
        }
    }

    record Waypoint(UUID id, BannerIconType icon, String name, int worldX, int worldY, int worldZ, boolean highlighted) {
    }
}
