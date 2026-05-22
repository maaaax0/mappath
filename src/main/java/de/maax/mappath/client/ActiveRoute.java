package de.maax.mappath.client;

import java.util.List;

import net.minecraft.world.phys.Vec3;

final class ActiveRoute {
    private static final double POINT_PROGRESS_DISTANCE_SQR = 9.0D;
    private static final double CONTINUATION_DISTANCE_SQR = 1024.0D;
    private static final int PROGRESS_LOOKAHEAD_POINTS = 8;
    private static final int MAX_OFF_ROUTE_REPLAN_DISTANCE = 12;

    private final RouteTarget target;
    private final List<RoutePoint> points;
    private final boolean partial;
    private final boolean recalculates;
    private final Vec3 fixedPulseStart;
    private int nextPointIndex;

    ActiveRoute(RouteTarget target, List<RoutePoint> points, int startX, int startZ, boolean partial) {
        this(target, points, partial, true, null);
    }

    ActiveRoute(RouteTarget target, List<RoutePoint> points, boolean partial, boolean recalculates, Vec3 fixedPulseStart) {
        this.target = target;
        this.points = List.copyOf(points);
        this.partial = partial;
        this.recalculates = recalculates;
        this.fixedPulseStart = fixedPulseStart;
        this.nextPointIndex = this.points.size() > 1 ? 1 : 0;
    }

    RouteTarget target() {
        return this.target;
    }

    List<RoutePoint> points() {
        return this.points;
    }

    int nextPointIndex() {
        return this.nextPointIndex;
    }

    boolean isEmpty() {
        return this.points.isEmpty();
    }

    void updateProgress(double playerX, double playerY, double playerZ) {
        while (this.nextPointIndex < this.points.size() - 1
            && this.points.get(this.nextPointIndex).distanceToSqr(playerX, playerY, playerZ) <= POINT_PROGRESS_DISTANCE_SQR) {
            this.nextPointIndex++;
        }

        int bestIndex = this.nextPointIndex;
        double bestDistance = Double.MAX_VALUE;
        int lastIndex = Math.min(this.points.size() - 1, this.nextPointIndex + PROGRESS_LOOKAHEAD_POINTS);
        for (int index = this.nextPointIndex; index <= lastIndex; index++) {
            double distance = this.points.get(index).distanceToSqr(playerX, playerY, playerZ);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        if (bestIndex > this.nextPointIndex && bestDistance <= POINT_PROGRESS_DISTANCE_SQR) {
            this.nextPointIndex = Math.min(bestIndex + 1, this.points.size() - 1);
        }
    }

    boolean hasArrived(double playerX, double playerY, double playerZ) {
        if (this.target.hasBounds() && this.target.contains(playerX, playerY, playerZ)) {
            return true;
        }

        double dx = this.target.worldX() + 0.5D - playerX;
        double dy = this.target.worldY() - playerY;
        double dz = this.target.worldZ() + 0.5D - playerZ;
        double radius = this.target.arrivalRadius();
        return dx * dx + dy * dy + dz * dz <= radius * radius;
    }

    boolean needsRecalculation(double playerX, double playerY, double playerZ, int maxDistance) {
        if (this.points.isEmpty()) {
            return false;
        }

        int effectiveMaxDistance = Math.min(maxDistance, MAX_OFF_ROUTE_REPLAN_DISTANCE);
        double maxDistanceSqr = effectiveMaxDistance * (double)effectiveMaxDistance;
        return this.distanceToRemainingRouteSqr(playerX, playerY, playerZ) >= maxDistanceSqr;
    }

    boolean partial() {
        return this.partial;
    }

    boolean recalculates() {
        return this.recalculates;
    }

    Vec3 pulseStartPosition(Vec3 fallback) {
        return this.fixedPulseStart == null ? fallback : this.fixedPulseStart;
    }

    boolean needsContinuation(double playerX, double playerY, double playerZ) {
        return this.partial
            && !this.points.isEmpty()
            && (this.nextPointIndex >= this.points.size() - 2 || this.points.getLast().distanceToSqr(playerX, playerY, playerZ) <= CONTINUATION_DISTANCE_SQR);
    }

    private double distanceToRemainingRouteSqr(double playerX, double playerY, double playerZ) {
        int startIndex = Math.max(0, this.nextPointIndex - 1);
        double bestDistance = this.points.get(startIndex).distanceToSqr(playerX, playerY, playerZ);
        for (int index = startIndex + 1; index < this.points.size(); index++) {
            bestDistance = Math.min(bestDistance, distanceToSegmentSqr(this.points.get(index - 1), this.points.get(index), playerX, playerY, playerZ));
        }
        return bestDistance;
    }

    private static double distanceToSegmentSqr(RoutePoint start, RoutePoint end, double playerX, double playerY, double playerZ) {
        double startX = start.worldX() + 0.5D;
        double startY = start.worldY();
        double startZ = start.worldZ() + 0.5D;
        double endX = end.worldX() + 0.5D;
        double endY = end.worldY();
        double endZ = end.worldZ() + 0.5D;
        double segmentX = endX - startX;
        double segmentY = endY - startY;
        double segmentZ = endZ - startZ;
        double segmentLengthSqr = segmentX * segmentX + segmentY * segmentY + segmentZ * segmentZ;
        if (segmentLengthSqr <= 0.0D) {
            return start.distanceToSqr(playerX, playerY, playerZ);
        }

        double t = ((playerX - startX) * segmentX + (playerY - startY) * segmentY + (playerZ - startZ) * segmentZ) / segmentLengthSqr;
        t = Math.max(0.0D, Math.min(1.0D, t));
        double closestX = startX + segmentX * t;
        double closestY = startY + segmentY * t;
        double closestZ = startZ + segmentZ * t;
        double dx = closestX - playerX;
        double dy = closestY - playerY;
        double dz = closestZ - playerZ;
        return dx * dx + dy * dy + dz * dz;
    }
}
