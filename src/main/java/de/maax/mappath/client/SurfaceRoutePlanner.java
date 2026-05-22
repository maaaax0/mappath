package de.maax.mappath.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

final class SurfaceRoutePlanner {
    private static final int MAX_STEP_UP = 1;
    private static final int MAX_SAFE_DROP = 2;
    private static final int MAX_BUILD_UP = 6;
    private static final int MAX_VERTICAL_BUILD_UP = 8;
    private static final int MAX_BRIDGE_DROP = 24;
    private static final int GOAL_VERTICAL_TOLERANCE = 2;
    private static final int FLOATING_TARGET_AIR_GAP = 5;
    private static final int MAX_VISUAL_SEGMENT_LENGTH = 6;
    private static final double HEIGHT_CHANGE_COST = 4.0D;
    private static final double DROP_COST = 3.0D;
    private static final double DIAGONAL_HEIGHT_CHANGE_COST = 2.0D;
    private static final double ROUGH_NEIGHBOR_COST = 0.35D;
    private static final double UNKNOWN_NEIGHBOR_COST = 0.08D;
    private static final double MAP_FALLBACK_COST = 6.0D;
    private static final double WATER_COST = 1.25D;
    private static final double FLOATING_TARGET_WATER_COST = 0.35D;
    private static final double WATER_CLIMB_COST = 1.5D;
    private static final double BRIDGE_BLOCK_COST = 4.0D;
    private static final double STAIR_BLOCK_COST = 5.0D;
    private static final double PILLAR_BLOCK_COST = 6.0D;
    private static final double FLOATING_TARGET_PILLAR_BLOCK_COST = 2.0D;
    private static final double PILLAR_UNSUSTAINABLE_COST = 10.0D;
    private static final double BLOCK_COUNT_TIE_BREAK_COST_WINDOW = 2.0D;
    private static final long NO_PREVIOUS_KEY = Long.MIN_VALUE;
    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    private SurfaceRoutePlanner() {
    }

    static Result plan(
        ClientLevel level,
        LocalPlayer player,
        MapTileStore store,
        int startX,
        int startZ,
        RouteTarget target,
        boolean allowBuilding,
        int planningDistance
    ) {
        int goalX = target.worldX();
        int goalZ = target.worldZ();
        int maxPlanningDistance = Math.max(32, planningDistance);
        double startGoalDistance = heuristic(startX, startZ, goalX, goalZ);
        boolean partialSearch = startGoalDistance > maxPlanningDistance;
        if (!store.hasKnownHeight(startX, startZ)
            || (!partialSearch && !store.hasKnownHeight(goalX, goalZ) && !level.hasChunk(goalX >> 4, goalZ >> 4))) {
            return Result.failure("gui.mappath.route_missing_data");
        }

        SearchContext context = new SearchContext(level, store, allowBuilding, isFloatingTarget(level, store, target));
        int startY = resolveStartY(context, player, startX, startZ);
        int margin = 32;
        int minX = partialSearch ? startX - maxPlanningDistance : Math.min(startX, goalX) - margin;
        int maxX = partialSearch ? startX + maxPlanningDistance : Math.max(startX, goalX) + margin;
        int minZ = partialSearch ? startZ - maxPlanningDistance : Math.min(startZ, goalZ) - margin;
        int maxZ = partialSearch ? startZ + maxPlanningDistance : Math.max(startZ, goalZ) + margin;

        long startKey = key(startX, startY, startZ);
        PriorityQueue<Node> open = new PriorityQueue<>();
        Map<Long, SearchNode> nodes = new HashMap<>();
        SearchNode start = new SearchNode(startX, startY, startZ, 0.0D, 0, NO_PREVIOUS_KEY, RoutePoint.SegmentType.WALK, List.of(), Set.of());
        nodes.put(startKey, start);
        open.add(new Node(startKey, heuristic(startX, startZ, goalX, goalZ)));

        while (!open.isEmpty()) {
            Node currentEntry = open.poll();
            SearchNode current = nodes.get(currentEntry.key());
            if (current == null || current.closed()) {
                continue;
            }
            if (isGoalReached(current, target)) {
                return Result.success(simplifyForRendering(buildPath(nodes, currentEntry.key())));
            }
            if (partialSearch && isPartialHandoff(current, startX, startZ, goalX, goalZ, maxPlanningDistance, startGoalDistance)) {
                return Result.partial(simplifyForRendering(buildPath(nodes, currentEntry.key())));
            }

            current.setClosed();
            for (Transition transition : transitions(context, current, minX, maxX, minZ, maxZ)) {
                long nextKey = key(transition.x(), transition.y(), transition.z());
                SearchNode next = nodes.get(nextKey);
                if (next == null || isBetterTransition(transition, next)) {
                    SearchNode updated = new SearchNode(
                        transition.x(),
                        transition.y(),
                        transition.z(),
                        transition.cost(),
                        transition.usedBlocks(),
                        currentEntry.key(),
                        transition.segmentType(),
                        transition.placementBlocks(),
                        withPlacedBlocks(current, transition.placementBlocks())
                    );
                    nodes.put(nextKey, updated);
                    open.add(new Node(nextKey, transition.cost() + heuristic(transition.x(), transition.z(), goalX, goalZ)));
                }
            }
        }

        return Result.failure("gui.mappath.route_unavailable");
    }

    private static List<Transition> transitions(SearchContext context, SearchNode current, int minX, int maxX, int minZ, int maxZ) {
        List<Transition> transitions = new ArrayList<>();
        addWaterClimbTransitions(context, current, transitions);
        if (context.allowBuilding()) {
            addVerticalBuildTransition(context, current, transitions);
        }
        for (int[] direction : DIRECTIONS) {
            int nextX = current.x() + direction[0];
            int nextZ = current.z() + direction[1];
            if (nextX < minX || nextX > maxX || nextZ < minZ || nextZ > maxZ) {
                continue;
            }

            boolean diagonal = direction[0] != 0 && direction[1] != 0;
            WalkTarget walkTarget = resolveWalkTarget(context, current, current.y(), nextX, nextZ);
            if (walkTarget != null && (!diagonal || canMoveDiagonally(context, current, direction, walkTarget.y()))) {
                int heightDelta = walkTarget.y() - current.y();
                double stepCost = diagonal ? 1.41421356237D : 1.0D;
                double comfortCost = Math.abs(heightDelta) * HEIGHT_CHANGE_COST
                    + (heightDelta < 0 ? Math.abs(heightDelta) * DROP_COST : 0.0D)
                    + (diagonal && heightDelta > 0 ? DIAGONAL_HEIGHT_CHANGE_COST : 0.0D)
                    + (walkTarget.water() ? waterCost(context) : 0.0D)
                    + (walkTarget.live() ? 0.0D : MAP_FALLBACK_COST + localRoughnessCost(context.store(), nextX, nextZ, walkTarget.y() - 1));
                transitions.add(new Transition(
                    nextX,
                    walkTarget.y(),
                    nextZ,
                    current.cost() + stepCost + comfortCost,
                    current.usedBlocks(),
                    RoutePoint.SegmentType.WALK,
                    List.of()
                ));
                continue;
            }

            if (!diagonal && context.allowBuilding()) {
                addBuildTransitions(context, current, direction, nextX, nextZ, transitions);
            }
        }
        return transitions;
    }

    private static boolean isBetterTransition(Transition transition, SearchNode existing) {
        if (transition.cost() < existing.cost()) {
            return true;
        }
        return transition.usedBlocks() < existing.usedBlocks()
            && transition.cost() <= existing.cost() + BLOCK_COUNT_TIE_BREAK_COST_WINDOW;
    }

    private static boolean isGoalReached(SearchNode current, RouteTarget target) {
        return current.x() == target.worldX()
            && current.z() == target.worldZ()
            && Math.abs(current.y() - target.worldY()) <= GOAL_VERTICAL_TOLERANCE;
    }

    private static boolean isPartialHandoff(
        SearchNode current,
        int startX,
        int startZ,
        int goalX,
        int goalZ,
        int maxPlanningDistance,
        double startGoalDistance
    ) {
        int distanceFromStart = Math.max(Math.abs(current.x() - startX), Math.abs(current.z() - startZ));
        if (distanceFromStart < maxPlanningDistance - 4) {
            return false;
        }

        double remainingDistance = heuristic(current.x(), current.z(), goalX, goalZ);
        return remainingDistance < startGoalDistance - 8.0D;
    }

    private static void addWaterClimbTransitions(SearchContext context, SearchNode current, List<Transition> transitions) {
        if (!isWaterColumnAt(context, current.x(), current.y(), current.z())) {
            return;
        }

        int upY = current.y() + 1;
        if (canOccupyWater(context, current.x(), upY, current.z())) {
            transitions.add(new Transition(
                current.x(),
                upY,
                current.z(),
                current.cost() + WATER_CLIMB_COST,
                current.usedBlocks(),
                RoutePoint.SegmentType.WALK,
                List.of()
            ));
        }

        int downY = current.y() - 1;
        if (canOccupyWater(context, current.x(), downY, current.z())) {
            transitions.add(new Transition(
                current.x(),
                downY,
                current.z(),
                current.cost() + 1.0D,
                current.usedBlocks(),
                RoutePoint.SegmentType.WALK,
                List.of()
            ));
        }

        int floorY = waterFloorY(context, current.x(), current.y(), current.z());
        if (floorY != Integer.MIN_VALUE && floorY < current.y()) {
            transitions.add(new Transition(
                current.x(),
                floorY,
                current.z(),
                current.cost() + Math.max(1.0D, (current.y() - floorY) * 0.15D),
                current.usedBlocks(),
                RoutePoint.SegmentType.WALK,
                List.of()
            ));
        }
    }

    private static void addBuildTransitions(SearchContext context, SearchNode current, int[] direction, int nextX, int nextZ, List<Transition> transitions) {
        if (isWaterRouteColumn(context, nextX, current.y(), nextZ)) {
            return;
        }

        BlockPos bridgeBlock = new BlockPos(nextX, current.y() - 1, nextZ);
        if (canPlaceRouteBlock(context, current, bridgeBlock, List.of()) && canStandAfterPlacement(context, current, nextX, current.y(), nextZ, List.of(bridgeBlock))) {
            RoutePoint.SegmentType type = hasDeepDrop(context, nextX, current.y(), nextZ) ? RoutePoint.SegmentType.BRIDGE : RoutePoint.SegmentType.STAIR_BUILD;
            double blockCost = type == RoutePoint.SegmentType.BRIDGE ? BRIDGE_BLOCK_COST : STAIR_BLOCK_COST;
            transitions.add(new Transition(
                nextX,
                current.y(),
                nextZ,
                current.cost() + 1.0D + blockCost,
                current.usedBlocks() + 1,
                type,
                List.of(bridgeBlock)
            ));
        }

        for (int climb = 1; climb <= MAX_BUILD_UP; climb++) {
            int targetY = current.y() + climb;
            List<BlockPos> blocks = stairBlocks(nextX, nextZ, current.y(), climb);
            if (canPlaceAllRouteBlocks(context, current, blocks) && canStandAfterPlacement(context, current, nextX, targetY, nextZ, blocks)) {
                transitions.add(new Transition(
                    nextX,
                    targetY,
                    nextZ,
                    current.cost() + 1.0D + blocks.size() * STAIR_BLOCK_COST + climb * HEIGHT_CHANGE_COST,
                    current.usedBlocks() + blocks.size(),
                    RoutePoint.SegmentType.STAIR_BUILD,
                    blocks
                ));
                break;
            }
        }

    }

    private static void addVerticalBuildTransition(SearchContext context, SearchNode current, List<Transition> transitions) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int climb = 1; climb <= MAX_VERTICAL_BUILD_UP; climb++) {
            BlockPos block = new BlockPos(current.x(), current.y() + climb - 1, current.z());
            if (!canPlaceRouteBlock(context, current, block, blocks)) {
                return;
            }
            blocks.add(block);

            int targetY = current.y() + climb;
            if (!canStandAfterPlacement(context, current, current.x(), targetY, current.z(), blocks)) {
                continue;
            }

            transitions.add(new Transition(
                current.x(),
                targetY,
                current.z(),
                current.cost() + blocks.size() * pillarBlockCost(context) + climb * HEIGHT_CHANGE_COST,
                current.usedBlocks() + blocks.size(),
                RoutePoint.SegmentType.PILLAR_BUILD,
                List.copyOf(blocks)
            ));
            return;
        }
    }

    private static List<BlockPos> stairBlocks(int x, int z, int currentY, int climb) {
        List<BlockPos> blocks = new ArrayList<>();
        for (int offset = 0; offset < climb; offset++) {
            blocks.add(new BlockPos(x, currentY + offset, z));
        }
        return blocks;
    }

    private static double pillarBlockCost(SearchContext context) {
        return context.floatingTarget()
            ? FLOATING_TARGET_PILLAR_BLOCK_COST
            : PILLAR_BLOCK_COST + PILLAR_UNSUSTAINABLE_COST;
    }

    private static double waterCost(SearchContext context) {
        return context.floatingTarget() ? FLOATING_TARGET_WATER_COST : WATER_COST;
    }

    private static boolean canMoveDiagonally(SearchContext context, SearchNode current, int[] direction, int targetY) {
        return resolveWalkTarget(context, current, current.y(), current.x() + direction[0], current.z()) != null
            && resolveWalkTarget(context, current, current.y(), current.x(), current.z() + direction[1]) != null
            && canStandAt(context, current, current.x() + direction[0], targetY, current.z())
            && canStandAt(context, current, current.x(), targetY, current.z() + direction[1]);
    }

    private static WalkTarget resolveWalkTarget(SearchContext context, SearchNode current, int currentY, int x, int z) {
        if (isLiveColumn(context.level(), x, z)) {
            for (int y = currentY + MAX_STEP_UP; y >= currentY - MAX_SAFE_DROP; y--) {
                if (canOccupyWater(context, x, y, z)) {
                    return new WalkTarget(y, true, true);
                }
            }
            for (int y = currentY + MAX_STEP_UP; y >= currentY - MAX_SAFE_DROP; y--) {
                if (canStandAt(context, current, x, y, z)) {
                    return new WalkTarget(y, isWaterAt(context, x, y, z), true);
                }
            }
            return null;
        }

        if (!context.store().hasKnownHeight(x, z)) {
            return null;
        }
        int footY = context.store().getHeight(x, z) + 1;
        int delta = footY - currentY;
        if (delta > MAX_STEP_UP || delta < -MAX_SAFE_DROP) {
            return null;
        }
        return new WalkTarget(footY, false, false);
    }

    private static boolean canStandAt(SearchContext context, SearchNode current, int x, int y, int z) {
        if (!isLiveColumn(context.level(), x, z)) {
            return context.store().hasKnownHeight(x, z) && context.store().getHeight(x, z) + 1 == y;
        }

        BlockPos foot = new BlockPos(x, y, z);
        BlockPos head = foot.above();
        BlockPos below = foot.below();
        if (current.hasPlacedBlock(foot) || current.hasPlacedBlock(head)) {
            return false;
        }
        return isPassable(context, foot)
            && isPassable(context, head)
            && (current.hasPlacedBlock(below) || isStableSupport(context, below));
    }

    private static boolean isPassable(SearchContext context, BlockPos pos) {
        BlockState state = context.level().getBlockState(pos);
        return state.getFluidState().isEmpty() || state.getFluidState().is(FluidTags.WATER)
            ? state.getCollisionShape(context.level(), pos).isEmpty() && !state.getFluidState().is(FluidTags.LAVA)
            : false;
    }

    private static boolean isStableSupport(SearchContext context, BlockPos pos) {
        BlockState state = context.level().getBlockState(pos);
        if (state.getFluidState().is(FluidTags.LAVA) || state.getFluidState().is(FluidTags.WATER)) {
            return false;
        }
        VoxelShape shape = state.getCollisionShape(context.level(), pos);
        return !shape.isEmpty();
    }

    private static boolean isWaterAt(SearchContext context, int x, int y, int z) {
        BlockPos foot = new BlockPos(x, y, z);
        return context.level().getBlockState(foot).getFluidState().is(FluidTags.WATER)
            || context.level().getBlockState(foot.below()).getFluidState().is(FluidTags.WATER);
    }

    private static boolean isWaterRouteColumn(SearchContext context, int x, int y, int z) {
        if (!isLiveColumn(context.level(), x, z)) {
            return false;
        }

        BlockPos foot = new BlockPos(x, y, z);
        return context.level().getBlockState(foot).getFluidState().is(FluidTags.WATER)
            || context.level().getBlockState(foot.above()).getFluidState().is(FluidTags.WATER)
            || context.level().getBlockState(foot.below()).getFluidState().is(FluidTags.WATER);
    }

    private static boolean canOccupyWater(SearchContext context, int x, int y, int z) {
        if (!isLiveColumn(context.level(), x, z)) {
            return false;
        }

        BlockPos foot = new BlockPos(x, y, z);
        BlockPos head = foot.above();
        return isWaterColumnAt(context, x, y, z)
            && context.level().getBlockState(foot).getCollisionShape(context.level(), foot).isEmpty()
            && context.level().getBlockState(head).getCollisionShape(context.level(), head).isEmpty();
    }

    private static boolean isWaterColumnAt(SearchContext context, int x, int y, int z) {
        BlockPos foot = new BlockPos(x, y, z);
        BlockPos head = foot.above();
        return context.level().getBlockState(foot).getFluidState().is(FluidTags.WATER)
            || context.level().getBlockState(head).getFluidState().is(FluidTags.WATER);
    }

    private static int waterFloorY(SearchContext context, int x, int y, int z) {
        if (!isLiveColumn(context.level(), x, z)) {
            return Integer.MIN_VALUE;
        }

        for (int floorY = y; floorY >= context.level().getMinBuildHeight(); floorY--) {
            BlockPos foot = new BlockPos(x, floorY, z);
            if (!context.level().getBlockState(foot).getFluidState().is(FluidTags.WATER)) {
                continue;
            }
            BlockPos support = foot.below();
            if (isStableSupport(context, support)) {
                return floorY;
            }
        }
        return Integer.MIN_VALUE;
    }

    private static boolean hasDeepDrop(SearchContext context, int x, int y, int z) {
        if (!isLiveColumn(context.level(), x, z)) {
            return false;
        }
        for (int drop = 1; drop <= MAX_BRIDGE_DROP; drop++) {
            if (isStableSupport(context, new BlockPos(x, y - 1 - drop, z))) {
                return drop > MAX_SAFE_DROP;
            }
        }
        return true;
    }

    private static boolean canStandAfterPlacement(SearchContext context, SearchNode current, int x, int y, int z, List<BlockPos> plannedBlocks) {
        if (!isLiveColumn(context.level(), x, z)) {
            return false;
        }

        BlockPos foot = new BlockPos(x, y, z);
        BlockPos head = foot.above();
        BlockPos below = foot.below();
        if (plannedBlocks.contains(foot) || plannedBlocks.contains(head) || current.hasPlacedBlock(foot) || current.hasPlacedBlock(head)) {
            return false;
        }
        return isPassable(context, foot)
            && isPassable(context, head)
            && (plannedBlocks.contains(below) || current.hasPlacedBlock(below) || isStableSupport(context, below));
    }

    private static boolean canPlaceAllRouteBlocks(SearchContext context, SearchNode current, List<BlockPos> blocks) {
        List<BlockPos> placed = new ArrayList<>();
        for (BlockPos block : blocks) {
            if (!canPlaceRouteBlock(context, current, block, placed)) {
                return false;
            }
            placed.add(block);
        }
        return true;
    }

    private static boolean canPlaceRouteBlock(SearchContext context, SearchNode current, BlockPos pos, List<BlockPos> plannedNeighbors) {
        if (!isLiveColumn(context.level(), pos.getX(), pos.getZ())) {
            return false;
        }
        if (current.hasPlacedBlock(pos) || plannedNeighbors.contains(pos)) {
            return false;
        }

        BlockState state = context.level().getBlockState(pos);
        if (!state.getCollisionShape(context.level(), pos).isEmpty() || state.getFluidState().is(FluidTags.LAVA)) {
            return false;
        }
        return hasPlacementNeighbor(context, current, pos, plannedNeighbors);
    }

    private static boolean hasPlacementNeighbor(SearchContext context, SearchNode current, BlockPos pos, Collection<BlockPos> plannedNeighbors) {
        return isStableSupport(context, pos.below())
            || isStableSupport(context, pos.north())
            || isStableSupport(context, pos.south())
            || isStableSupport(context, pos.west())
            || isStableSupport(context, pos.east())
            || current.hasPlacedBlock(pos.below())
            || current.hasPlacedBlock(pos.north())
            || current.hasPlacedBlock(pos.south())
            || current.hasPlacedBlock(pos.west())
            || current.hasPlacedBlock(pos.east())
            || plannedNeighbors.contains(pos.below())
            || plannedNeighbors.contains(pos.north())
            || plannedNeighbors.contains(pos.south())
            || plannedNeighbors.contains(pos.west())
            || plannedNeighbors.contains(pos.east());
    }

    private static int resolveStartY(SearchContext context, LocalPlayer player, int startX, int startZ) {
        int playerY = Mth.floor(player.getY());
        SearchNode startProbe = new SearchNode(startX, playerY, startZ, 0.0D, 0, NO_PREVIOUS_KEY, RoutePoint.SegmentType.WALK, List.of(), Set.of());
        WalkTarget target = resolveWalkTarget(context, startProbe, playerY, startX, startZ);
        if (target != null) {
            return target.y();
        }
        int storedHeight = context.store().getHeight(startX, startZ);
        return storedHeight == Integer.MIN_VALUE ? playerY : storedHeight + 1;
    }

    private static List<RoutePoint> buildPath(Map<Long, SearchNode> nodes, long goalKey) {
        List<RoutePoint> reversed = new ArrayList<>();
        long currentKey = goalKey;
        while (currentKey != NO_PREVIOUS_KEY) {
            SearchNode node = nodes.get(currentKey);
            if (node == null) {
                break;
            }
            reversed.add(new RoutePoint(node.x(), node.y(), node.z(), node.segmentType(), node.placementBlocks()));
            currentKey = node.previousKey();
        }
        Collections.reverse(reversed);
        return reversed;
    }

    private static Set<BlockPos> withPlacedBlocks(SearchNode current, List<BlockPos> placementBlocks) {
        if (placementBlocks.isEmpty()) {
            return current.placedBlocks();
        }

        Set<BlockPos> placedBlocks = new LinkedHashSet<>(current.placedBlocks());
        placedBlocks.addAll(placementBlocks);
        return placedBlocks;
    }

    private static List<RoutePoint> simplifyForRendering(List<RoutePoint> rawPath) {
        if (rawPath.isEmpty()) {
            return rawPath;
        }

        List<RoutePoint> simplified = new ArrayList<>();
        simplified.add(rawPath.getFirst());
        int previousDirectionX = 0;
        int previousDirectionZ = 0;
        int segmentLength = 0;
        for (int index = 1; index < rawPath.size(); index++) {
            RoutePoint previous = rawPath.get(index - 1);
            RoutePoint current = rawPath.get(index);
            int directionX = Integer.compare(current.worldX(), previous.worldX());
            int directionZ = Integer.compare(current.worldZ(), previous.worldZ());
            boolean changedDirection = directionX != previousDirectionX || directionZ != previousDirectionZ;
            boolean changedHeight = previous.worldY() != current.worldY();
            boolean changedType = current.segmentType() != previous.segmentType();
            boolean segmentTooLong = segmentLength >= MAX_VISUAL_SEGMENT_LENGTH;
            if (index > 1 && (changedDirection || changedHeight || changedType || previous.requiresPlacement() || segmentTooLong)) {
                simplified.add(previous);
                segmentLength = 0;
            }
            if (current.requiresPlacement()) {
                simplified.add(current);
                segmentLength = 0;
            }
            previousDirectionX = directionX;
            previousDirectionZ = directionZ;
            segmentLength++;
        }
        RoutePoint last = rawPath.getLast();
        if (!simplified.getLast().equals(last)) {
            simplified.add(last);
        }
        return mergePlacementBlocks(simplified);
    }

    private static List<RoutePoint> mergePlacementBlocks(List<RoutePoint> points) {
        Set<BlockPos> seen = new HashSet<>();
        List<RoutePoint> merged = new ArrayList<>(points.size());
        for (RoutePoint point : points) {
            if (point.placementBlocks().isEmpty()) {
                merged.add(point);
                continue;
            }
            List<BlockPos> placements = new ArrayList<>();
            for (BlockPos block : point.placementBlocks()) {
                if (seen.add(block)) {
                    placements.add(block);
                }
            }
            merged.add(new RoutePoint(point.worldX(), point.worldY(), point.worldZ(), point.segmentType(), placements));
        }
        return merged;
    }

    private static double localRoughnessCost(MapTileStore store, int worldX, int worldZ, int height) {
        double cost = 0.0D;
        for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                if (offsetX == 0 && offsetZ == 0) {
                    continue;
                }
                int neighborX = worldX + offsetX;
                int neighborZ = worldZ + offsetZ;
                if (!store.hasKnownHeight(neighborX, neighborZ)) {
                    cost += UNKNOWN_NEIGHBOR_COST;
                    continue;
                }
                if (Math.abs(store.getHeight(neighborX, neighborZ) - height) > MAX_STEP_UP) {
                    cost += ROUGH_NEIGHBOR_COST;
                }
            }
        }

        return cost;
    }

    private static boolean isLiveColumn(ClientLevel level, int x, int z) {
        return level.hasChunk(x >> 4, z >> 4);
    }

    private static boolean isFloatingTarget(ClientLevel level, MapTileStore store, RouteTarget target) {
        if (store.hasKnownHeight(target.worldX(), target.worldZ())
            && target.worldY() - (store.getHeight(target.worldX(), target.worldZ()) + 1) >= FLOATING_TARGET_AIR_GAP) {
            return true;
        }

        if (!isLiveColumn(level, target.worldX(), target.worldZ())) {
            return false;
        }

        int airGap = 0;
        for (int y = target.worldY() - 2; y >= level.getMinBuildHeight(); y--) {
            BlockPos pos = new BlockPos(target.worldX(), y, target.worldZ());
            BlockState state = level.getBlockState(pos);
            if (!state.getCollisionShape(level, pos).isEmpty() || !state.getFluidState().isEmpty()) {
                return airGap >= FLOATING_TARGET_AIR_GAP;
            }
            airGap++;
            if (airGap >= FLOATING_TARGET_AIR_GAP) {
                return true;
            }
        }
        return airGap >= FLOATING_TARGET_AIR_GAP;
    }

    private static double heuristic(int x, int z, int goalX, int goalZ) {
        int dx = Math.abs(goalX - x);
        int dz = Math.abs(goalZ - z);
        int diagonal = Math.min(dx, dz);
        int straight = Math.max(dx, dz) - diagonal;
        return diagonal * 1.41421356237D + straight;
    }

    private static long key(int x, int y, int z) {
        long xPart = ((long)x & 0x3FFFFFFL) << 38;
        long zPart = ((long)z & 0x3FFFFFFL) << 12;
        long yPart = y & 0xFFFL;
        return xPart | zPart | yPart;
    }

    record Result(boolean success, List<RoutePoint> points, String messageKey, boolean partial) {
        static Result success(List<RoutePoint> points) {
            return new Result(true, points, null, false);
        }

        static Result partial(List<RoutePoint> points) {
            return new Result(true, points, null, true);
        }

        static Result failure(String messageKey) {
            return new Result(false, List.of(), messageKey, false);
        }
    }

    private record SearchContext(ClientLevel level, MapTileStore store, boolean allowBuilding, boolean floatingTarget) {
    }

    private record WalkTarget(int y, boolean water, boolean live) {
    }

    private record Transition(
        int x,
        int y,
        int z,
        double cost,
        int usedBlocks,
        RoutePoint.SegmentType segmentType,
        List<BlockPos> placementBlocks
    ) {
    }

    private record Node(long key, double priority) implements Comparable<Node> {
        @Override
        public int compareTo(Node other) {
            return Double.compare(this.priority, other.priority);
        }
    }

    private static final class SearchNode {
        private final int x;
        private final int y;
        private final int z;
        private final double cost;
        private final int usedBlocks;
        private final long previousKey;
        private final RoutePoint.SegmentType segmentType;
        private final List<BlockPos> placementBlocks;
        private final Set<BlockPos> placedBlocks;
        private boolean closed;

        private SearchNode(
            int x,
            int y,
            int z,
            double cost,
            int usedBlocks,
            long previousKey,
            RoutePoint.SegmentType segmentType,
            List<BlockPos> placementBlocks,
            Set<BlockPos> placedBlocks
        ) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.cost = cost;
            this.usedBlocks = usedBlocks;
            this.previousKey = previousKey;
            this.segmentType = segmentType;
            this.placementBlocks = List.copyOf(placementBlocks);
            this.placedBlocks = Set.copyOf(placedBlocks);
        }

        private int x() {
            return this.x;
        }

        private int y() {
            return this.y;
        }

        private int z() {
            return this.z;
        }

        private double cost() {
            return this.cost;
        }

        private int usedBlocks() {
            return this.usedBlocks;
        }

        private long previousKey() {
            return this.previousKey;
        }

        private RoutePoint.SegmentType segmentType() {
            return this.segmentType;
        }

        private List<BlockPos> placementBlocks() {
            return this.placementBlocks;
        }

        private Set<BlockPos> placedBlocks() {
            return this.placedBlocks;
        }

        private boolean hasPlacedBlock(BlockPos pos) {
            return this.placedBlocks.contains(pos);
        }

        private boolean closed() {
            return this.closed;
        }

        private void setClosed() {
            this.closed = true;
        }
    }
}
