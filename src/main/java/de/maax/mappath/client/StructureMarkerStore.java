package de.maax.mappath.client;

import de.maax.mappath.MapPathNetworking;
import de.maax.mappath.StructureMarkerType;
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

final class StructureMarkerStore {
    private static final int FILE_VERSION = 3;
    private static final String FILE_NAME = "structure_markers.bin";

    private ResourceLocation dimensionId;
    private Path markerPath;
    private final Map<String, Marker> markers = new HashMap<>();
    private boolean dirty;

    Collection<Marker> markers(Minecraft minecraft) {
        this.ensureLoaded(minecraft);
        return this.markers.values();
    }

    void addMarkers(Minecraft minecraft, ResourceLocation dimensionId, List<MapPathNetworking.StructureMarker> incomingMarkers) {
        this.ensureLoaded(minecraft, dimensionId);
        boolean changed = false;
        for (MapPathNetworking.StructureMarker incomingMarker : incomingMarkers) {
            StructureMarkerType type = StructureMarkerType.byId(incomingMarker.typeId());
            if (type == null) {
                continue;
            }

            Marker marker = new Marker(type, incomingMarker.worldX(), incomingMarker.worldY(), incomingMarker.worldZ(), "", false);
            Marker existingMarker = this.markers.get(marker.key());
            if (existingMarker == null) {
                this.markers.put(marker.key(), marker);
                changed = true;
            } else if (existingMarker.worldY() != marker.worldY()) {
                this.markers.put(marker.key(), new Marker(existingMarker.type(), existingMarker.worldX(), marker.worldY(), existingMarker.worldZ(), existingMarker.name(), existingMarker.highlighted()));
                changed = true;
            }
        }

        if (changed) {
            this.dirty = true;
            this.flush();
        }
    }

    Marker marker(Minecraft minecraft, String key) {
        this.ensureLoaded(minecraft);
        return this.markers.get(key);
    }

    void updateMarkerName(Minecraft minecraft, String key, String name) {
        this.ensureLoaded(minecraft);
        Marker marker = this.markers.get(key);
        if (marker == null) {
            return;
        }

        this.markers.put(key, new Marker(marker.type(), marker.worldX(), marker.worldY(), marker.worldZ(), name, marker.highlighted()));
        this.dirty = true;
        this.flush();
    }

    void setMarkerHighlighted(Minecraft minecraft, String key, boolean highlighted) {
        this.ensureLoaded(minecraft);
        Marker marker = this.markers.get(key);
        if (marker == null || marker.highlighted() == highlighted) {
            return;
        }

        this.markers.put(key, new Marker(marker.type(), marker.worldX(), marker.worldY(), marker.worldZ(), marker.name(), highlighted));
        this.dirty = true;
        this.flush();
    }

    void deleteMarker(Minecraft minecraft, String key) {
        this.ensureLoaded(minecraft);
        if (this.markers.remove(key) != null) {
            this.dirty = true;
            this.flush();
        }
    }

    void close() {
        this.flush();
        this.markers.clear();
        this.dimensionId = null;
        this.markerPath = null;
        this.dirty = false;
    }

    private void ensureLoaded(Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) {
            this.close();
            return;
        }

        this.ensureLoaded(minecraft, minecraft.level.dimension().location());
    }

    private void ensureLoaded(Minecraft minecraft, ResourceLocation requestedDimensionId) {
        Path directory = MapTileStore.directoryFor(minecraft, requestedDimensionId);
        if (directory == null) {
            this.close();
            return;
        }

        Path requestedMarkerPath = directory.resolve(FILE_NAME);
        if (requestedDimensionId.equals(this.dimensionId) && requestedMarkerPath.equals(this.markerPath)) {
            return;
        }

        this.flush();
        this.dimensionId = requestedDimensionId;
        this.markerPath = requestedMarkerPath;
        this.markers.clear();
        this.dirty = false;
        this.load();
    }

    private void load() {
        if (this.markerPath == null || !Files.isRegularFile(this.markerPath)) {
            return;
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(this.markerPath)))) {
            int fileVersion = input.readInt();
            if (fileVersion < 1 || fileVersion > FILE_VERSION) {
                return;
            }

            int markerCount = input.readInt();
            for (int index = 0; index < markerCount; index++) {
                StructureMarkerType type = StructureMarkerType.byId(input.readUTF());
                int worldX = input.readInt();
                int worldY = fileVersion >= 3 ? input.readInt() : Integer.MIN_VALUE;
                int worldZ = input.readInt();
                String name = fileVersion >= 2 ? input.readUTF() : "";
                boolean highlighted = fileVersion >= 3 && input.readBoolean();
                if (type != null) {
                    Marker marker = new Marker(type, worldX, worldY, worldZ, name, highlighted);
                    this.markers.put(marker.key(), marker);
                }
            }
        } catch (IOException ignored) {
            this.markers.clear();
        }
    }

    private void flush() {
        if (!this.dirty || this.markerPath == null) {
            return;
        }

        try {
            Files.createDirectories(this.markerPath.getParent());
            List<Marker> sortedMarkers = new ArrayList<>(this.markers.values());
            sortedMarkers.sort(Comparator.comparing(Marker::key));
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(this.markerPath)))) {
                output.writeInt(FILE_VERSION);
                output.writeInt(sortedMarkers.size());
                for (Marker marker : sortedMarkers) {
                    output.writeUTF(marker.type().id());
                    output.writeInt(marker.worldX());
                    output.writeInt(marker.worldY());
                    output.writeInt(marker.worldZ());
                    output.writeUTF(marker.name());
                    output.writeBoolean(marker.highlighted());
                }
            }
            this.dirty = false;
        } catch (IOException ignored) {
        }
    }

    record Marker(StructureMarkerType type, int worldX, int worldY, int worldZ, String name, boolean highlighted) {
        String key() {
            return this.type.id() + ":" + this.worldX + ":" + this.worldZ;
        }
    }
}
