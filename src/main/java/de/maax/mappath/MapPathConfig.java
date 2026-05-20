package de.maax.mappath;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

public final class MapPathConfig {
    public static final int DEFAULT_LIVE_REFRESH_RADIUS = 64;
    public static final int MAX_LIVE_REFRESH_RADIUS = 512;
    public static final int DEFAULT_RECORD_INTERVAL_TICKS = 1;
    public static final int DEFAULT_LIVE_REFRESH_COLUMNS_PER_TICK = 8;
    public static final int DEFAULT_CHUNK_REFRESHES_PER_TICK = 12;
    public static final int DEFAULT_MISSING_CHUNK_SEARCH_INTERVAL_TICKS = 10;
    public static final int DEFAULT_TILE_WRITES_PER_TICK = 2;
    public static final int DEFAULT_INITIAL_MAP_LOAD_PER_TICK = 625;
    public static final boolean DEFAULT_SHOW_MAP_LOAD_STATUS = true;
    public static final boolean DEFAULT_SHOW_STRUCTURE_MARKERS = true;
    public static final boolean DEFAULT_SHOW_WAYPOINTS = true;

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    static {
        Pair<Client, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = specPair.getLeft();
        CLIENT_SPEC = specPair.getRight();
    }

    private MapPathConfig() {
    }

    public static final class Client {
        private final ModConfigSpec.IntValue liveRefreshRadius;
        private final ModConfigSpec.IntValue recordIntervalTicks;
        private final ModConfigSpec.IntValue liveRefreshColumnsPerTick;
        private final ModConfigSpec.IntValue chunkRefreshesPerTick;
        private final ModConfigSpec.IntValue missingChunkSearchIntervalTicks;
        private final ModConfigSpec.IntValue tileWritesPerTick;
        private final ModConfigSpec.IntValue initialMapLoadPerTick;
        private final ModConfigSpec.BooleanValue showMapLoadStatus;
        private final ModConfigSpec.BooleanValue showStructureMarkers;
        private final ModConfigSpec.BooleanValue showWaypoints;

        private Client(ModConfigSpec.Builder builder) {
            this.liveRefreshRadius = builder
                .comment(
                    "Radius in blocks around the player that is regularly re-sampled for map updates.",
                    "0 disables regular live updates. Maximum: " + MAX_LIVE_REFRESH_RADIUS + "."
                )
                .translation("mappath.configuration.liveRefreshRadius")
                .defineInRange("liveRefreshRadius", DEFAULT_LIVE_REFRESH_RADIUS, 0, MAX_LIVE_REFRESH_RADIUS);
            this.recordIntervalTicks = builder
                .comment("How often the background map recorder runs. Higher values reduce CPU usage.")
                .translation("mappath.configuration.recordIntervalTicks")
                .defineInRange("recordIntervalTicks", DEFAULT_RECORD_INTERVAL_TICKS, DEFAULT_RECORD_INTERVAL_TICKS, 40);
            this.liveRefreshColumnsPerTick = builder
                .comment("How many block columns in the live refresh radius are sampled per recorder run.")
                .translation("mappath.configuration.liveRefreshColumnsPerTick")
                .defineInRange("liveRefreshColumnsPerTick", DEFAULT_LIVE_REFRESH_COLUMNS_PER_TICK, DEFAULT_LIVE_REFRESH_COLUMNS_PER_TICK, 64);
            this.chunkRefreshesPerTick = builder
                .comment("How many newly loaded or incomplete chunks are fully sampled per recorder run.")
                .translation("mappath.configuration.chunkRefreshesPerTick")
                .defineInRange("chunkRefreshesPerTick", DEFAULT_CHUNK_REFRESHES_PER_TICK, DEFAULT_CHUNK_REFRESHES_PER_TICK, 128);
            this.missingChunkSearchIntervalTicks = builder
                .comment("How often loaded chunks around the player are scanned for missing map data.")
                .translation("mappath.configuration.missingChunkSearchIntervalTicks")
                .defineInRange("missingChunkSearchIntervalTicks", DEFAULT_MISSING_CHUNK_SEARCH_INTERVAL_TICKS, 1, 200);
            this.tileWritesPerTick = builder
                .comment("Maximum dirty map tile files written per client tick. 0 writes only when the map store is closed.")
                .translation("mappath.configuration.tileWritesPerTick")
                .defineInRange("tileWritesPerTick", DEFAULT_TILE_WRITES_PER_TICK, 0, 64);
            this.initialMapLoadPerTick = builder
                .comment("Existing map tile files loaded into the world map atlas per client tick after joining a world. Default 625 equals a 25x25 chunk area.")
                .translation("mappath.configuration.initialMapLoadPerTick")
                .defineInRange("initialMapLoadPerTick", DEFAULT_INITIAL_MAP_LOAD_PER_TICK, DEFAULT_INITIAL_MAP_LOAD_PER_TICK, 4096);
            this.showMapLoadStatus = builder
                .comment("Shows a small status line while existing map data is loaded into the world map atlas.")
                .translation("mappath.configuration.showMapLoadStatus")
                .define("showMapLoadStatus", DEFAULT_SHOW_MAP_LOAD_STATUS);
            this.showStructureMarkers = builder
                .comment("Shows discovered structure icons on the world map.")
                .translation("mappath.configuration.showStructureMarkers")
                .define("showStructureMarkers", DEFAULT_SHOW_STRUCTURE_MARKERS);
            this.showWaypoints = builder
                .comment("Shows waypoint icons on the world map.")
                .translation("mappath.configuration.showWaypoints")
                .define("showWaypoints", DEFAULT_SHOW_WAYPOINTS);
        }

        public int liveRefreshRadius() {
            return this.liveRefreshRadius.get();
        }

        public int recordIntervalTicks() {
            return this.recordIntervalTicks.get();
        }

        public int liveRefreshColumnsPerTick() {
            return this.liveRefreshColumnsPerTick.get();
        }

        public int chunkRefreshesPerTick() {
            return this.chunkRefreshesPerTick.get();
        }

        public int missingChunkSearchIntervalTicks() {
            return this.missingChunkSearchIntervalTicks.get();
        }

        public int tileWritesPerTick() {
            return this.tileWritesPerTick.get();
        }

        public int initialMapLoadPerTick() {
            return this.initialMapLoadPerTick.get();
        }

        public boolean showMapLoadStatus() {
            return this.showMapLoadStatus.get();
        }

        public boolean showStructureMarkers() {
            return this.showStructureMarkers.get();
        }

        public boolean showWaypoints() {
            return this.showWaypoints.get();
        }

        public void setShowStructureMarkers(boolean showStructureMarkers) {
            this.showStructureMarkers.set(showStructureMarkers);
            this.showStructureMarkers.save();
        }

        public void setShowWaypoints(boolean showWaypoints) {
            this.showWaypoints.set(showWaypoints);
            this.showWaypoints.save();
        }
    }
}
