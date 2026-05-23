package de.maax.mappath.client;

import de.maax.mappath.BannerIconType;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.DirectoryStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

final class WaypointStore {
    private static final int FILE_VERSION = 2;
    static final String FILE_NAME = "waypoints.bin";
    private static final String LAST_DEATH_NAME = "Last Death";
    private static final String ARCHIVED_DEATH_NAME = "Death";

    private ResourceLocation dimensionId;
    private Path waypointPath;
    private final Map<UUID, Waypoint> waypoints = new HashMap<>();
    private boolean dirty;

    Collection<Waypoint> waypoints(Minecraft minecraft) {
        this.ensureLoaded(minecraft);
        return this.waypoints.values();
    }

    List<DimensionWaypoint> allWaypoints(Minecraft minecraft) {
        this.ensureLoaded(minecraft);
        this.flush();
        Path currentDimensionDirectory = this.waypointPath == null ? null : this.waypointPath.getParent();
        Path worldDirectory = currentDimensionDirectory == null ? null : currentDimensionDirectory.getParent();
        if (worldDirectory == null || !Files.isDirectory(worldDirectory)) {
            return List.of();
        }

        Map<String, ResourceLocation> dimensionIdsByDirectory = dimensionIdsByDirectory(minecraft);
        List<DimensionWaypoint> allWaypoints = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(worldDirectory)) {
            for (Path dimensionDirectory : stream) {
                if (!Files.isDirectory(dimensionDirectory)) {
                    continue;
                }

                Path path = dimensionDirectory.resolve(FILE_NAME);
                List<Waypoint> waypoints = loadWaypoints(path);
                if (waypoints.isEmpty()) {
                    continue;
                }

                String dimensionDirectoryName = dimensionDirectory.getFileName().toString();
                ResourceLocation dimensionId = dimensionIdsByDirectory.get(dimensionDirectoryName);
                String dimensionName = displayName(dimensionId, dimensionDirectoryName);
                boolean currentDimension = currentDimensionDirectory != null && currentDimensionDirectory.equals(dimensionDirectory);
                for (Waypoint waypoint : waypoints) {
                    allWaypoints.add(new DimensionWaypoint(dimensionId, dimensionName, dimensionDirectoryName, path, waypoint, currentDimension));
                }
            }
        } catch (IOException ignored) {
            return List.of();
        }

        allWaypoints.sort(Comparator
            .comparing(DimensionWaypoint::dimensionName)
            .thenComparing(entry -> entry.waypoint().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.waypoint().id().toString()));
        return allWaypoints;
    }

    static String displayName(ResourceLocation dimensionId, String fallbackDirectoryName) {
        if (dimensionId != null) {
            if (Level.OVERWORLD.location().equals(dimensionId)) {
                return "Overworld";
            }
            if (Level.NETHER.location().equals(dimensionId)) {
                return "Nether";
            }
            if (Level.END.location().equals(dimensionId)) {
                return "The End";
            }

            String translated = net.minecraft.client.resources.language.I18n.get("dimension." + dimensionId.getNamespace() + "." + dimensionId.getPath());
            if (!translated.equals("dimension." + dimensionId.getNamespace() + "." + dimensionId.getPath())) {
                return translated;
            }
            return prettify(dimensionId.getPath());
        }

        return prettify(fallbackDirectoryName);
    }

    static Map<String, ResourceLocation> dimensionIdsByDirectory(Minecraft minecraft) {
        Map<String, ResourceLocation> dimensionIds = new HashMap<>();
        if (minecraft == null || minecraft.level == null || minecraft.getConnection() == null) {
            return dimensionIds;
        }

        Set<ResourceKey<Level>> levels = minecraft.getConnection().levels();
        for (ResourceKey<Level> levelKey : levels) {
            ResourceLocation id = levelKey.location();
            dimensionIds.put(MapTileStore.sanitizePathPart(id.toString()), id);
        }

        ResourceLocation currentDimensionId = minecraft.level.dimension().location();
        dimensionIds.put(MapTileStore.sanitizePathPart(currentDimensionId.toString()), currentDimensionId);
        return dimensionIds;
    }

    private static String prettify(String value) {
        String normalized = value;
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator >= 0 && namespaceSeparator < normalized.length() - 1) {
            normalized = normalized.substring(namespaceSeparator + 1);
        }
        if (normalized.startsWith("minecraft_")) {
            normalized = normalized.substring("minecraft_".length());
        }

        String[] parts = normalized.replace('-', '_').split("_+");
        StringBuilder result = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1) {
                result.append(part.substring(1));
            }
        }
        return result.isEmpty() ? "Unknown" : result.toString();
    }

    private static String nextArchivedDeathName(Set<String> usedDeathNames) {
        if (!usedDeathNames.contains(ARCHIVED_DEATH_NAME)) {
            return ARCHIVED_DEATH_NAME;
        }

        int index = 2;
        while (usedDeathNames.contains(ARCHIVED_DEATH_NAME + " " + index)) {
            index++;
        }
        return ARCHIVED_DEATH_NAME + " " + index;
    }

    Waypoint waypoint(Minecraft minecraft, UUID id) {
        this.ensureLoaded(minecraft);
        return this.waypoints.get(id);
    }

    void addWaypoint(Minecraft minecraft, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        this.ensureLoaded(minecraft);
        if (this.waypointPath == null || icon == BannerIconType.DEATH) {
            return;
        }

        Waypoint waypoint = new Waypoint(UUID.randomUUID(), icon, name, worldX, worldY, worldZ, true);
        this.waypoints.put(waypoint.id(), waypoint);
        this.dirty = true;
        this.flush();
    }

    void addDeathWaypoint(Minecraft minecraft, int worldX, int worldY, int worldZ) {
        this.ensureLoaded(minecraft);
        if (this.waypointPath == null) {
            return;
        }

        Set<String> usedDeathNames = new HashSet<>();
        for (Waypoint waypoint : this.waypoints.values()) {
            if (waypoint.icon() == BannerIconType.DEATH && !waypoint.name().equals(LAST_DEATH_NAME)) {
                usedDeathNames.add(waypoint.name());
            }
        }

        for (Map.Entry<UUID, Waypoint> entry : new ArrayList<>(this.waypoints.entrySet())) {
            Waypoint waypoint = entry.getValue();
            if (waypoint.icon() == BannerIconType.DEATH && waypoint.name().equals(LAST_DEATH_NAME)) {
                String archivedName = nextArchivedDeathName(usedDeathNames);
                usedDeathNames.add(archivedName);
                this.waypoints.put(
                    entry.getKey(),
                    new Waypoint(waypoint.id(), waypoint.icon(), archivedName, waypoint.worldX(), waypoint.worldY(), waypoint.worldZ(), false)
                );
            }
        }

        Waypoint waypoint = new Waypoint(UUID.randomUUID(), BannerIconType.DEATH, LAST_DEATH_NAME, worldX, worldY, worldZ, true);
        this.waypoints.put(waypoint.id(), waypoint);
        this.dirty = true;
        this.flush();
    }

    void addWaypoint(Path targetPath, boolean currentDimension, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        if (targetPath == null || icon == BannerIconType.DEATH) {
            return;
        }

        this.addWaypoint(targetPath, currentDimension, new Waypoint(UUID.randomUUID(), icon, name, worldX, worldY, worldZ, true));
    }

    private void addWaypoint(Path targetPath, boolean currentDimension, Waypoint waypoint) {
        if (currentDimension && targetPath.equals(this.waypointPath)) {
            this.waypoints.put(waypoint.id(), waypoint);
            this.dirty = true;
            this.flush();
            return;
        }

        List<Waypoint> waypoints = loadWaypoints(targetPath);
        waypoints.add(waypoint);
        saveWaypoints(targetPath, waypoints);
        if (currentDimension) {
            this.dimensionId = null;
        }
    }

    void updateWaypoint(Minecraft minecraft, UUID id, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        this.ensureLoaded(minecraft);
        if (this.waypointPath == null || icon == BannerIconType.DEATH || !this.waypoints.containsKey(id)) {
            return;
        }

        Waypoint existingWaypoint = this.waypoints.get(id);
        if (existingWaypoint.icon() == BannerIconType.DEATH) {
            return;
        }
        this.waypoints.put(id, new Waypoint(id, icon, name, worldX, worldY, worldZ, existingWaypoint.highlighted()));
        this.dirty = true;
        this.flush();
    }

    void updateWaypoint(DimensionWaypoint entry, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        if (entry.waypoint().icon() == BannerIconType.DEATH || icon == BannerIconType.DEATH) {
            return;
        }

        List<Waypoint> waypoints = loadWaypoints(entry.path());
        boolean updated = false;
        for (int index = 0; index < waypoints.size(); index++) {
            Waypoint waypoint = waypoints.get(index);
            if (waypoint.id().equals(entry.waypoint().id())) {
                waypoints.set(index, new Waypoint(waypoint.id(), icon, name, worldX, worldY, worldZ, waypoint.highlighted()));
                updated = true;
                break;
            }
        }

        if (!updated) {
            return;
        }

        saveWaypoints(entry.path(), waypoints);
        if (entry.currentDimension()) {
            this.dimensionId = null;
        }
    }

    void moveWaypoint(Minecraft minecraft, UUID id, Path targetPath, boolean targetCurrentDimension, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        this.ensureLoaded(minecraft);
        if (this.waypointPath == null || targetPath == null || icon == BannerIconType.DEATH || !this.waypoints.containsKey(id)) {
            return;
        }

        Waypoint existingWaypoint = this.waypoints.get(id);
        if (existingWaypoint.icon() == BannerIconType.DEATH) {
            return;
        }

        Waypoint updatedWaypoint = new Waypoint(id, icon, name, worldX, worldY, worldZ, existingWaypoint.highlighted());
        if (targetCurrentDimension && targetPath.equals(this.waypointPath)) {
            this.waypoints.put(id, updatedWaypoint);
            this.dirty = true;
            this.flush();
            return;
        }

        this.waypoints.remove(id);
        this.dirty = true;
        this.flush();
        this.addWaypoint(targetPath, targetCurrentDimension, updatedWaypoint);
    }

    void moveWaypoint(DimensionWaypoint entry, Path targetPath, boolean targetCurrentDimension, BannerIconType icon, String name, int worldX, int worldY, int worldZ) {
        if (entry.waypoint().icon() == BannerIconType.DEATH || targetPath == null || icon == BannerIconType.DEATH) {
            return;
        }

        if (entry.path().equals(targetPath)) {
            this.updateWaypoint(entry, icon, name, worldX, worldY, worldZ);
            return;
        }

        List<Waypoint> sourceWaypoints = loadWaypoints(entry.path());
        Waypoint existingWaypoint = null;
        for (Waypoint waypoint : sourceWaypoints) {
            if (waypoint.id().equals(entry.waypoint().id())) {
                existingWaypoint = waypoint;
                break;
            }
        }
        if (existingWaypoint == null) {
            return;
        }

        sourceWaypoints.removeIf(waypoint -> waypoint.id().equals(entry.waypoint().id()));
        saveWaypoints(entry.path(), sourceWaypoints);
        this.addWaypoint(targetPath, targetCurrentDimension, new Waypoint(existingWaypoint.id(), icon, name, worldX, worldY, worldZ, existingWaypoint.highlighted()));
        if (entry.currentDimension() || targetCurrentDimension) {
            this.dimensionId = null;
        }
    }

    void setWaypointHighlighted(Minecraft minecraft, UUID id, boolean highlighted) {
        this.ensureLoaded(minecraft);
        Waypoint waypoint = this.waypoints.get(id);
        if (waypoint == null || waypoint.icon() == BannerIconType.DEATH || waypoint.highlighted() == highlighted) {
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

    void deleteWaypoint(DimensionWaypoint entry) {
        List<Waypoint> waypoints = loadWaypoints(entry.path());
        if (!waypoints.removeIf(waypoint -> waypoint.id().equals(entry.waypoint().id()))) {
            return;
        }

        saveWaypoints(entry.path(), waypoints);
        if (entry.currentDimension()) {
            this.dimensionId = null;
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

        this.waypoints.clear();
        for (Waypoint waypoint : loadWaypoints(this.waypointPath)) {
            this.waypoints.put(waypoint.id(), waypoint);
        }
    }

    private static List<Waypoint> loadWaypoints(Path waypointPath) {
        List<Waypoint> waypoints = new ArrayList<>();
        if (waypointPath == null || !Files.isRegularFile(waypointPath)) {
            return waypoints;
        }

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(Files.newInputStream(waypointPath)))) {
            int fileVersion = input.readInt();
            if (fileVersion < 1 || fileVersion > FILE_VERSION) {
                return waypoints;
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
                    waypoints.add(new Waypoint(id, icon, name, worldX, worldY, worldZ, highlighted));
                }
            }
        } catch (IOException ignored) {
            waypoints.clear();
        }
        return waypoints;
    }

    private void flush() {
        if (!this.dirty || this.waypointPath == null) {
            return;
        }

        try {
            Files.createDirectories(this.waypointPath.getParent());
            saveWaypoints(this.waypointPath, this.waypoints.values());
            this.dirty = false;
        } catch (IOException ignored) {
        }
    }

    private static void saveWaypoints(Path waypointPath, Collection<Waypoint> waypoints) {
        try {
            Files.createDirectories(waypointPath.getParent());
            List<Waypoint> sortedWaypoints = new ArrayList<>(waypoints);
            sortedWaypoints.sort(Comparator.comparing(waypoint -> waypoint.id().toString()));
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(waypointPath)))) {
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
        } catch (IOException ignored) {
        }
    }

    record Waypoint(UUID id, BannerIconType icon, String name, int worldX, int worldY, int worldZ, boolean highlighted) {
    }

    record DimensionWaypoint(ResourceLocation dimensionId, String dimensionName, String dimensionDirectoryName, Path path, Waypoint waypoint, boolean currentDimension) {
    }
}
