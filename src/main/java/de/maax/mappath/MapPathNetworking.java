package de.maax.mappath;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class MapPathNetworking {
    private static final int MAX_STRUCTURE_MARKERS_PER_PACKET = 512;

    private MapPathNetworking() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("3")
            .playToServer(TeleportTopPayload.TYPE, TeleportTopPayload.STREAM_CODEC, MapPathNetworking::handleTeleportTop)
            .playToServer(TeleportPositionPayload.TYPE, TeleportPositionPayload.STREAM_CODEC, MapPathNetworking::handleTeleportPosition)
            .playToClient(StructureMarkersPayload.TYPE, StructureMarkersPayload.STREAM_CODEC, MapPathNetworking::handleStructureMarkers);
    }

    public static void sendTeleportTop(int worldX, int worldZ) {
        PacketDistributor.sendToServer(new TeleportTopPayload(worldX, worldZ));
    }

    public static void sendTeleportPosition(int worldX, int worldY, int worldZ) {
        PacketDistributor.sendToServer(new TeleportPositionPayload(worldX, worldY, worldZ));
    }

    public static void sendStructureMarkers(ServerPlayer player, ResourceLocation dimensionId, List<StructureMarker> markers) {
        PacketDistributor.sendToPlayer(player, new StructureMarkersPayload(dimensionId, markers));
    }

    private static void handleTeleportTop(TeleportTopPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.createCommandSourceStack().hasPermission(2)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        int topY = findTopY(level, payload.worldX(), payload.worldZ());
        player.teleportTo(level, payload.worldX() + 0.5D, topY, payload.worldZ() + 0.5D, java.util.Set.of(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        player.setOnGround(true);
    }

    private static void handleTeleportPosition(TeleportPositionPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !player.createCommandSourceStack().hasPermission(2)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        player.teleportTo(level, payload.worldX() + 0.5D, payload.worldY(), payload.worldZ() + 0.5D, java.util.Set.of(), player.getYRot(), player.getXRot());
        player.setDeltaMovement(player.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        player.setOnGround(true);
    }

    private static int findTopY(ServerLevel level, int worldX, int worldZ) {
        LevelChunk chunk = level.getChunk(worldX >> 4, worldZ >> 4);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
            pos.set(worldX, y, worldZ);
            BlockState state = chunk.getBlockState(pos);
            if (!state.isAir()) {
                return y + 1;
            }
        }

        return level.getMinBuildHeight();
    }

    private static void handleStructureMarkers(StructureMarkersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> handlerClass = Class.forName("de.maax.mappath.client.ClientStructureMarkerHandler");
                Method handleMethod = handlerClass.getMethod("handle", StructureMarkersPayload.class);
                handleMethod.invoke(null, payload);
            } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            }
        });
    }

    public record TeleportTopPayload(int worldX, int worldZ) implements CustomPacketPayload {
        static final CustomPacketPayload.Type<TeleportTopPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "teleport_top")
        );
        static final StreamCodec<RegistryFriendlyByteBuf, TeleportTopPayload> STREAM_CODEC = CustomPacketPayload.codec(
            TeleportTopPayload::write,
            TeleportTopPayload::read
        );

        private static TeleportTopPayload read(RegistryFriendlyByteBuf buffer) {
            return new TeleportTopPayload(buffer.readInt(), buffer.readInt());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeInt(this.worldX);
            buffer.writeInt(this.worldZ);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record TeleportPositionPayload(int worldX, int worldY, int worldZ) implements CustomPacketPayload {
        static final CustomPacketPayload.Type<TeleportPositionPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "teleport_position")
        );
        static final StreamCodec<RegistryFriendlyByteBuf, TeleportPositionPayload> STREAM_CODEC = CustomPacketPayload.codec(
            TeleportPositionPayload::write,
            TeleportPositionPayload::read
        );

        private static TeleportPositionPayload read(RegistryFriendlyByteBuf buffer) {
            return new TeleportPositionPayload(buffer.readInt(), buffer.readInt(), buffer.readInt());
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeInt(this.worldX);
            buffer.writeInt(this.worldY);
            buffer.writeInt(this.worldZ);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record StructureMarker(
        String typeId,
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
        private static StructureMarker read(RegistryFriendlyByteBuf buffer) {
            return new StructureMarker(
                buffer.readUtf(64),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt(),
                buffer.readInt()
            );
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeUtf(this.typeId, 64);
            buffer.writeInt(this.worldX);
            buffer.writeInt(this.worldY);
            buffer.writeInt(this.worldZ);
            buffer.writeInt(this.minWorldX);
            buffer.writeInt(this.minWorldY);
            buffer.writeInt(this.minWorldZ);
            buffer.writeInt(this.maxWorldX);
            buffer.writeInt(this.maxWorldY);
            buffer.writeInt(this.maxWorldZ);
        }
    }

    public record StructureMarkersPayload(ResourceLocation dimensionId, List<StructureMarker> markers) implements CustomPacketPayload {
        public static final CustomPacketPayload.Type<StructureMarkersPayload> TYPE = new CustomPacketPayload.Type<>(
            ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "structure_markers")
        );
        static final StreamCodec<RegistryFriendlyByteBuf, StructureMarkersPayload> STREAM_CODEC = CustomPacketPayload.codec(
            StructureMarkersPayload::write,
            StructureMarkersPayload::read
        );

        private static StructureMarkersPayload read(RegistryFriendlyByteBuf buffer) {
            ResourceLocation dimensionId = buffer.readResourceLocation();
            int markerCount = Math.max(0, buffer.readVarInt());
            List<StructureMarker> markers = new ArrayList<>(Math.min(markerCount, MAX_STRUCTURE_MARKERS_PER_PACKET));
            for (int index = 0; index < markerCount; index++) {
                StructureMarker marker = StructureMarker.read(buffer);
                if (index < MAX_STRUCTURE_MARKERS_PER_PACKET) {
                    markers.add(marker);
                }
            }

            return new StructureMarkersPayload(dimensionId, markers);
        }

        private void write(RegistryFriendlyByteBuf buffer) {
            buffer.writeResourceLocation(this.dimensionId);
            int markerCount = Math.min(this.markers.size(), MAX_STRUCTURE_MARKERS_PER_PACKET);
            buffer.writeVarInt(markerCount);
            for (int index = 0; index < markerCount; index++) {
                this.markers.get(index).write(buffer);
            }
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
