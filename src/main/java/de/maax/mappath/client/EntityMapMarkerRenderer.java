package de.maax.mappath.client;

import de.maax.mappath.EntityMarkerTarget;
import de.maax.mappath.EntityMarkerType;
import de.maax.mappath.MapPath;
import de.maax.mappath.MapPathConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

final class EntityMapMarkerRenderer {
    private static final int TEXTURE_SIZE = 64;
    private static final int FALLBACK_OUTLINE_COLOR = 0xFF000000;
    private static final int HOSTILE_FALLBACK_COLOR = 0xFFFF3030;
    private static final int NEUTRAL_FALLBACK_COLOR = 0xFFFFA23D;
    private static final int FRIENDLY_FALLBACK_COLOR = 0xFFFFD83D;
    private static final int PLAYER_FALLBACK_COLOR = 0xFF55B7FF;
    private static final int ITEM_FALLBACK_COLOR = 0xFF7DFF67;
    private static final int BOSS_FALLBACK_COLOR = 0xFFD35DFF;
    private static final ResourceLocation BOSS_FALLBACK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/mob_icons/fallback/boss.png");
    private static final ResourceLocation HOSTILE_FALLBACK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/mob_icons/fallback/hostile.png");
    private static final ResourceLocation NEUTRAL_FALLBACK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/mob_icons/fallback/neutral.png");
    private static final ResourceLocation FRIENDLY_FALLBACK_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/mob_icons/fallback/friendly.png");
    private static final Set<EntityType<?>> NEUTRAL_MOB_TYPES = Set.of(
        EntityType.DOLPHIN,
        EntityType.GOAT,
        EntityType.LLAMA,
        EntityType.PANDA,
        EntityType.PIGLIN,
        EntityType.SPIDER,
        EntityType.TRADER_LLAMA
    );
    private static final Map<EntityType<?>, ResourceLocation> MOB_MARKER_TEXTURES = createMobMarkerTextures();

    private EntityMapMarkerRenderer() {
    }

    static boolean shouldRenderEntityMarker(Entity entity, Minecraft minecraft, EntityMarkerTarget target) {
        if (minecraft.player == null || !entity.isAlive()) {
            return false;
        }
        EntityMarkerType markerType = markerType(entity);
        if (markerType == null
            || !MapPathConfig.CLIENT.showEntityMarkers(target, markerType)
            || MapPathConfig.CLIENT.entityMarkersPlayerListOnly(target, markerType) && !isPlayerListOpen(minecraft)) {
            return false;
        }

        if (entity instanceof Player player) {
            return player != minecraft.player && !player.isInvisibleTo(minecraft.player);
        }
        if (entity instanceof Mob mob) {
            return !mob.isInvisibleTo(minecraft.player);
        }

        return markerItem(entity) != null;
    }

    static boolean isWithinVerticalRange(Entity entity, Minecraft minecraft, float partialTick) {
        if (minecraft.player == null) {
            return false;
        }
        if (hasExtendedRenderRange(entity)) {
            return true;
        }

        double playerY = Mth.lerp(partialTick, minecraft.player.yo, minecraft.player.getY());
        double entityY = Mth.lerp(partialTick, entity.yo, entity.getY());
        return Math.abs(entityY - playerY) <= MapPathConfig.CLIENT.entityMarkerVerticalRange();
    }

    static boolean hasExtendedRenderRange(Entity entity) {
        return entity instanceof Player || entity instanceof Mob mob && isBoss(mob);
    }

    static void render(GuiGraphics guiGraphics, Minecraft minecraft, Entity entity, EntityMarkerTarget target, float centerX, float centerY, int markerSize) {
        EntityMarkerType markerType = markerType(entity);
        boolean useIcons = markerType != null && MapPathConfig.CLIENT.useEntityMarkerIcons(target, markerType);

        if (entity instanceof Player player) {
            if (useIcons) {
                renderPlayer(guiGraphics, minecraft, player, centerX, centerY, markerSize);
            } else {
                renderFallbackDot(guiGraphics, centerX, centerY, markerSize, PLAYER_FALLBACK_COLOR);
            }
            return;
        }

        ItemStack markerItem = markerItem(entity);
        if (markerItem != null) {
            if (useIcons) {
                renderItem(guiGraphics, markerItem, centerX, centerY, markerSize);
            } else {
                renderFallbackDot(guiGraphics, centerX, centerY, markerSize, ITEM_FALLBACK_COLOR);
            }
            return;
        }

        if (entity instanceof Mob mob) {
            if (useIcons) {
                ResourceLocation texture = mobMarkerTexture(minecraft, mob);
                if (texture != null) {
                    renderMobTexture(guiGraphics, texture, centerX, centerY, markerSize);
                    return;
                }

                ResourceLocation fallbackTexture = fallbackTexture(mob);
                if (minecraft.getResourceManager().getResource(fallbackTexture).isPresent()) {
                    renderTexture(guiGraphics, fallbackTexture, centerX, centerY, fallbackMarkerSize(mob, markerSize));
                    return;
                }
            }

            renderFallbackDot(guiGraphics, centerX, centerY, markerSize, fallbackColor(mob));
        }
    }

    private static EntityMarkerType markerType(Entity entity) {
        if (entity instanceof Player) {
            return EntityMarkerType.PLAYER;
        }
        if (entity instanceof Mob mob) {
            return isBoss(mob) ? EntityMarkerType.BOSS : EntityMarkerType.MOB;
        }
        return markerItem(entity) != null ? EntityMarkerType.ITEM : null;
    }

    private static boolean isPlayerListOpen(Minecraft minecraft) {
        return minecraft.options.keyPlayerList.isDown();
    }

    private static void renderPlayer(GuiGraphics guiGraphics, Minecraft minecraft, Player player, float centerX, float centerY, int markerSize) {
        PlayerSkin skin = player instanceof AbstractClientPlayer clientPlayer
            ? clientPlayer.getSkin()
            : minecraft.getSkinManager().getInsecureSkin(player.getGameProfile());
        boolean showHat = player.isModelPartShown(PlayerModelPart.HAT);
        boolean upsideDown = LivingEntityRenderer.isEntityUpsideDown(player);
        int size = playerMarkerSize(markerSize);

        PlayerFaceRenderer.draw(
            guiGraphics,
            skin.texture(),
            Mth.floor(centerX - size / 2.0F),
            Mth.floor(centerY - size / 2.0F),
            size,
            showHat,
            upsideDown
        );
    }

    private static ResourceLocation mobMarkerTexture(Minecraft minecraft, Mob mob) {
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (entityId == null) {
            return null;
        }

        ResourceLocation entityNamespaceTexture = ResourceLocation.fromNamespaceAndPath(
            entityId.getNamespace(),
            "textures/gui/mob_icons/" + entityId.getPath() + ".png"
        );
        if (minecraft.getResourceManager().getResource(entityNamespaceTexture).isPresent()) {
            return entityNamespaceTexture;
        }

        ResourceLocation mapPathNamespacedTexture = ResourceLocation.fromNamespaceAndPath(
            MapPath.MODID,
            "textures/gui/mob_icons/" + entityId.getNamespace() + "/" + entityId.getPath() + ".png"
        );
        if (minecraft.getResourceManager().getResource(mapPathNamespacedTexture).isPresent()) {
            return mapPathNamespacedTexture;
        }

        ResourceLocation mappedTexture = MOB_MARKER_TEXTURES.get(mob.getType());
        if (mappedTexture != null && minecraft.getResourceManager().getResource(mappedTexture).isPresent()) {
            return mappedTexture;
        }

        return null;
    }

    private static ItemStack markerItem(Entity entity) {
        if (entity instanceof ItemEntity itemEntity) {
            ItemStack itemStack = itemEntity.getItem();
            return itemStack.isEmpty() ? null : itemStack;
        }
        if (entity instanceof AbstractMinecart || entity instanceof Boat) {
            ItemStack itemStack = entity.getPickResult();
            return itemStack == null || itemStack.isEmpty() ? null : itemStack;
        }

        return null;
    }

    private static void renderTexture(GuiGraphics guiGraphics, ResourceLocation texture, float centerX, float centerY, int markerSize) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX - markerSize / 2.0F, centerY - markerSize / 2.0F, 0.0F);
        guiGraphics.blit(
            texture,
            0,
            0,
            markerSize,
            markerSize,
            0.0F,
            0.0F,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
            TEXTURE_SIZE,
            TEXTURE_SIZE
        );
        guiGraphics.pose().popPose();
    }

    private static void renderMobTexture(GuiGraphics guiGraphics, ResourceLocation texture, float centerX, float centerY, int markerSize) {
        renderTexture(guiGraphics, texture, centerX, centerY, mobTextureMarkerSize(markerSize));
    }

    private static void renderItem(GuiGraphics guiGraphics, ItemStack itemStack, float centerX, float centerY, int markerSize) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(centerX - markerSize / 2.0F, centerY - markerSize / 2.0F, 0.0F);
        float scale = markerSize / 16.0F;
        guiGraphics.pose().scale(scale, scale, 1.0F);
        guiGraphics.renderItem(itemStack, 0, 0);
        guiGraphics.pose().popPose();
    }

    private static void renderFallbackDot(GuiGraphics guiGraphics, float centerX, float centerY, int markerSize, int color) {
        int radius = Math.max(1, Math.round(markerSize / 4.0F));
        int centerPixelX = Mth.floor(centerX);
        int centerPixelY = Mth.floor(centerY);
        renderFilledCircle(guiGraphics, centerPixelX, centerPixelY, radius + 1, FALLBACK_OUTLINE_COLOR);
        renderFilledCircle(guiGraphics, centerPixelX, centerPixelY, radius, color);
    }

    private static int fallbackMarkerSize(Mob mob, int markerSize) {
        int fallbackSize = Math.max(4, Math.round(markerSize * 0.56F));
        if (isBoss(mob)) {
            return Math.max(fallbackSize + 1, Math.round(markerSize * 0.68F));
        }

        return fallbackSize;
    }

    private static int mobTextureMarkerSize(int markerSize) {
        return Math.max(4, Math.round(markerSize * 0.75F));
    }

    private static int playerMarkerSize(int markerSize) {
        return Math.max(mobTextureMarkerSize(markerSize) + 1, Math.round(markerSize * 0.9F));
    }

    private static ResourceLocation fallbackTexture(Mob mob) {
        if (isBoss(mob)) {
            return BOSS_FALLBACK_TEXTURE;
        }

        if (isAggressiveNeutral(mob)) {
            return HOSTILE_FALLBACK_TEXTURE;
        }

        if (isNeutral(mob)) {
            return NEUTRAL_FALLBACK_TEXTURE;
        }

        if (mob instanceof Enemy) {
            return HOSTILE_FALLBACK_TEXTURE;
        }

        return mob.getType().getCategory().isFriendly() ? FRIENDLY_FALLBACK_TEXTURE : NEUTRAL_FALLBACK_TEXTURE;
    }

    private static boolean isBoss(Mob mob) {
        EntityType<?> type = mob.getType();
        return type == EntityType.ENDER_DRAGON || type == EntityType.WITHER;
    }

    private static boolean isNeutral(Mob mob) {
        return mob instanceof NeutralMob || NEUTRAL_MOB_TYPES.contains(mob.getType());
    }

    private static boolean isAggressiveNeutral(Mob mob) {
        if (!isNeutral(mob)) {
            return false;
        }
        if (mob instanceof NeutralMob neutralMob && neutralMob.isAngry()) {
            return true;
        }

        return mob.getTarget() != null && mob.getTarget().isAlive();
    }

    private static void renderFilledCircle(GuiGraphics guiGraphics, int centerPixelX, int centerPixelY, int radius, int color) {
        for (int y = -radius; y <= radius; y++) {
            int halfWidth = Mth.floor(Math.sqrt(radius * radius - y * y));
            guiGraphics.fill(centerPixelX - halfWidth, centerPixelY + y, centerPixelX + halfWidth + 1, centerPixelY + y + 1, color);
        }
    }

    private static int fallbackColor(Mob mob) {
        if (isBoss(mob)) {
            return BOSS_FALLBACK_COLOR;
        }

        if (isAggressiveNeutral(mob)) {
            return HOSTILE_FALLBACK_COLOR;
        }

        if (isNeutral(mob)) {
            return NEUTRAL_FALLBACK_COLOR;
        }

        if (mob instanceof Enemy) {
            return HOSTILE_FALLBACK_COLOR;
        }

        return FRIENDLY_FALLBACK_COLOR;
    }

    private static Map<EntityType<?>, ResourceLocation> createMobMarkerTextures() {
        Map<EntityType<?>, ResourceLocation> textures = new HashMap<>();
        textures.put(EntityType.CREEPER, ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/mob_icons/creeper.png"));
        textures.put(EntityType.ENDERMAN, ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/mob_icons/enderman.png"));
        return Map.copyOf(textures);
    }
}
