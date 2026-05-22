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
    public static final boolean DEFAULT_SHOW_BETA_FEATURES = false;
    public static final boolean DEFAULT_SHOW_MINIMAP = true;
    public static final int DEFAULT_MINIMAP_SIZE = 128;
    public static final int DEFAULT_MINIMAP_BLOCKS_PER_PIXEL = 2;
    public static final boolean DEFAULT_SHOW_ROUTE_VISUALIZER = true;
    public static final boolean DEFAULT_SHOW_ROUTE_TARGET_MARKER = false;
    public static final int DEFAULT_ROUTE_TRAIL_MAX_SPEED = 6;
    public static final int DEFAULT_ROUTE_PLANNING_DISTANCE = 64;
    public static final int DEFAULT_ROUTE_RECALCULATE_DISTANCE = 12;

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
        private final ModConfigSpec.BooleanValue showBetaFeatures;
        private final ModConfigSpec.BooleanValue showMinimap;
        private final ModConfigSpec.IntValue minimapSize;
        private final ModConfigSpec.IntValue minimapBlocksPerPixel;
        private final ModConfigSpec.BooleanValue showRouteVisualizer;
        private final ModConfigSpec.BooleanValue showRouteTargetMarker;
        private final ModConfigSpec.IntValue routeTrailMaxSpeed;
        private final ModConfigSpec.IntValue routePlanningDistance;
        private final ModConfigSpec.IntValue routeRecalculateDistance;

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
            this.showBetaFeatures = builder
                .comment("Enables beta features on the world map.")
                .translation("mappath.configuration.showBetaFeatures")
                .define("showBetaFeatures", DEFAULT_SHOW_BETA_FEATURES);
            this.showMinimap = builder
                .comment("Shows a small minimap in the top-right HUD.")
                .translation("mappath.configuration.showMinimap")
                .define("showMinimap", DEFAULT_SHOW_MINIMAP);
            this.minimapSize = builder
                .comment("Minimap size in HUD pixels.")
                .translation("mappath.configuration.minimapSize")
                .defineInRange("minimapSize", DEFAULT_MINIMAP_SIZE, 64, 256);
            this.minimapBlocksPerPixel = builder
                .comment("Minimap zoom level. Higher values show more blocks with less detail.")
                .translation("mappath.configuration.minimapBlocksPerPixel")
                .defineInRange("minimapBlocksPerPixel", DEFAULT_MINIMAP_BLOCKS_PER_PIXEL, 1, 8);
            this.showRouteVisualizer = builder
                .comment("Shows the active client-side route visualizer in the world and HUD.")
                .translation("mappath.configuration.showRouteVisualizer")
                .define("showRouteVisualizer", DEFAULT_SHOW_ROUTE_VISUALIZER);
            this.showRouteTargetMarker = builder
                .comment("Shows a marker at the active route target in the world.")
                .translation("mappath.configuration.showRouteTargetMarker")
                .define("showRouteTargetMarker", DEFAULT_SHOW_ROUTE_TARGET_MARKER);
            this.routeTrailMaxSpeed = builder
                .comment("Maximum speed of the animated route trail in blocks per second.")
                .translation("mappath.configuration.routeTrailMaxSpeed")
                .defineInRange("routeTrailMaxSpeed", DEFAULT_ROUTE_TRAIL_MAX_SPEED, 1, 64);
            this.routePlanningDistance = builder
                .comment("Maximum horizontal distance planned at once for long routes. Longer routes are recalculated while travelling.")
                .translation("mappath.configuration.routePlanningDistance")
                .defineInRange("routePlanningDistance", DEFAULT_ROUTE_PLANNING_DISTANCE, 32, 512);
            this.routeRecalculateDistance = builder
                .comment("How far the player may move away from the active route before it is recalculated.")
                .translation("mappath.configuration.routeRecalculateDistance")
                .defineInRange("routeRecalculateDistance", DEFAULT_ROUTE_RECALCULATE_DISTANCE, 3, 128);
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

        public boolean showBetaFeatures() {
            return this.showBetaFeatures.get();
        }

        public boolean showMinimap() {
            return this.showMinimap.get();
        }

        public int minimapSize() {
            return this.minimapSize.get();
        }

        public int minimapBlocksPerPixel() {
            return this.minimapBlocksPerPixel.get();
        }

        public boolean showRouteVisualizer() {
            return this.showRouteVisualizer.get();
        }

        public boolean showRouteTargetMarker() {
            return this.showRouteTargetMarker.get();
        }

        public int routeTrailMaxSpeed() {
            return this.routeTrailMaxSpeed.get();
        }

        public int routePlanningDistance() {
            return this.routePlanningDistance.get();
        }

        public int routeRecalculateDistance() {
            return this.routeRecalculateDistance.get();
        }

        public void setShowStructureMarkers(boolean showStructureMarkers) {
            this.showStructureMarkers.set(showStructureMarkers);
            this.showStructureMarkers.save();
        }

        public void setShowWaypoints(boolean showWaypoints) {
            this.showWaypoints.set(showWaypoints);
            this.showWaypoints.save();
        }

        public void setShowBetaFeatures(boolean showBetaFeatures) {
            this.showBetaFeatures.set(showBetaFeatures);
            this.showBetaFeatures.save();
        }

        public void setShowMinimap(boolean showMinimap) {
            this.showMinimap.set(showMinimap);
            this.showMinimap.save();
        }
    }
}
