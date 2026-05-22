package de.maax.mappath.client;

import de.maax.mappath.MapPathConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

final class RouteNavigationManager {
    private static final double ARRIVAL_RADIUS = 3.0D;
    private static final int REPLAN_COOLDOWN_TICKS = 30;

    private ActiveRoute activeRoute;
    private Component lastStatusMessage;
    private int statusMessageTicks;
    private int replanCooldownTicks;

    void tick(Minecraft minecraft, WorldMapManager worldMapManager) {
        if (minecraft.level == null || minecraft.player == null) {
            this.cancelRoute();
            return;
        }

        if (this.statusMessageTicks > 0) {
            this.statusMessageTicks--;
        }
        if (this.replanCooldownTicks > 0) {
            this.replanCooldownTicks--;
        }

        if (this.activeRoute == null) {
            return;
        }

        if (!this.activeRoute.target().dimensionId().equals(minecraft.level.dimension().location())) {
            this.cancelRoute();
            return;
        }

        if (this.activeRoute.hasArrived(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ())) {
            this.activeRoute = null;
            this.showStatus(minecraft, Component.translatable("gui.mappath.route_arrived"));
            return;
        }

        this.activeRoute.updateProgress(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        boolean needsContinuation = this.activeRoute.needsContinuation(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ());
        boolean needsDistanceRecalculation = !this.activeRoute.partial()
            && this.activeRoute.needsRecalculation(minecraft.player.getX(), minecraft.player.getY(), minecraft.player.getZ(), MapPathConfig.CLIENT.routeRecalculateDistance());
        if (this.activeRoute.recalculates() && this.replanCooldownTicks <= 0 && (needsContinuation || needsDistanceRecalculation)) {
            this.replanCooldownTicks = REPLAN_COOLDOWN_TICKS;
            ActiveRoute recalculated = this.calculateRoute(minecraft, worldMapManager, this.activeRoute.target(), false, true);
            if (recalculated != null) {
                this.activeRoute = recalculated;
            }
        }
    }

    void startRouteToPosition(Minecraft minecraft, WorldMapManager worldMapManager, String label, int worldX, int worldY, int worldZ, RouteTarget.Type type) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        ResourceLocation dimensionId = minecraft.level.dimension().location();
        RouteTarget target = RouteTarget.point(type, label, dimensionId, worldX, worldY, worldZ, ARRIVAL_RADIUS);
        this.startRoute(minecraft, worldMapManager, target);
    }

    void startRouteToStructure(
        Minecraft minecraft,
        WorldMapManager worldMapManager,
        String label,
        int worldX,
        int worldY,
        int worldZ,
        int minWorldX,
        int minWorldY,
        int minWorldZ,
        int maxWorldX,
        int maxWorldY,
        int maxWorldZ
    ) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null) {
            return;
        }

        ResourceLocation dimensionId = minecraft.level.dimension().location();
        RouteTarget target = RouteTarget.structure(label, dimensionId, worldX, worldY, worldZ, ARRIVAL_RADIUS, minWorldX, minWorldY, minWorldZ, maxWorldX, maxWorldY, maxWorldZ);
        this.startRoute(minecraft, worldMapManager, target);
    }

    private void startRoute(Minecraft minecraft, WorldMapManager worldMapManager, RouteTarget target) {
        ActiveRoute route = this.calculateRoute(minecraft, worldMapManager, target, true, true);
        if (route != null) {
            this.activeRoute = route;
            this.replanCooldownTicks = 0;
            this.showStatus(minecraft, Component.translatable("gui.mappath.route_started", target.label()));
        }
    }

    void cancelRoute() {
        this.activeRoute = null;
        this.replanCooldownTicks = 0;
    }

    void cancelRoute(Minecraft minecraft) {
        this.cancelRoute();
        this.showStatus(minecraft, Component.translatable("gui.mappath.route_cancelled"));
    }

    void setManualRoute(Minecraft minecraft, ActiveRoute route, Component message) {
        this.activeRoute = route;
        this.replanCooldownTicks = 0;
        this.showStatus(minecraft, message);
    }

    boolean hasActiveRoute() {
        return this.activeRoute != null;
    }

    ActiveRoute activeRoute() {
        return this.activeRoute;
    }

    Component statusMessage() {
        return this.statusMessageTicks <= 0 ? null : this.lastStatusMessage;
    }

    private ActiveRoute calculateRoute(Minecraft minecraft, WorldMapManager worldMapManager, RouteTarget target, boolean showFailure, boolean allowBuilding) {
        MapTileStore store = worldMapManager.getTileStore(minecraft);
        if (store == null || minecraft.player == null) {
            if (showFailure) {
                this.showStatus(minecraft, Component.translatable("gui.mappath.route_unavailable"));
            }
            return null;
        }

        int startX = Mth.floor(minecraft.player.getX());
        int startZ = Mth.floor(minecraft.player.getZ());
        RouteTarget destination = this.closestDestination(minecraft.level, store, startX, startZ, target);
        SurfaceRoutePlanner.Result result = SurfaceRoutePlanner.plan(
            minecraft.level,
            minecraft.player,
            store,
            startX,
            startZ,
            destination,
            allowBuilding,
            MapPathConfig.CLIENT.routePlanningDistance()
        );
        if (!result.success()) {
            if (showFailure) {
                this.activeRoute = null;
                this.showStatus(minecraft, Component.translatable(result.messageKey()));
            }
            return null;
        }
        if (result.points().isEmpty()) {
            if (showFailure) {
                this.activeRoute = null;
                this.showStatus(minecraft, Component.translatable("gui.mappath.route_unavailable"));
            }
            return null;
        }

        return new ActiveRoute(target, result.points(), startX, startZ, result.partial());
    }

    private RouteTarget closestDestination(ClientLevel level, MapTileStore store, int startX, int startZ, RouteTarget target) {
        if (!target.hasBounds()) {
            return target;
        }

        int closestX = Mth.clamp(startX, target.minWorldX(), target.maxWorldX());
        int closestZ = Mth.clamp(startZ, target.minWorldZ(), target.maxWorldZ());
        int closestY = routeFootY(level, store, closestX, closestZ);
        if (closestY == Integer.MIN_VALUE) {
            closestY = target.worldY();
        }
        return target.withPoint(closestX, closestY, closestZ);
    }

    private static int routeFootY(ClientLevel level, MapTileStore store, int worldX, int worldZ) {
        if (store.hasKnownHeight(worldX, worldZ)) {
            return store.getHeight(worldX, worldZ) + 1;
        }

        if (!level.hasChunk(worldX >> 4, worldZ >> 4)) {
            return Integer.MIN_VALUE;
        }

        return level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, worldX, worldZ);
    }

    private void showStatus(Minecraft minecraft, Component message) {
        this.lastStatusMessage = message;
        this.statusMessageTicks = 80;
        if (minecraft != null && minecraft.gui != null) {
            minecraft.gui.setOverlayMessage(message, false);
        }
    }
}
