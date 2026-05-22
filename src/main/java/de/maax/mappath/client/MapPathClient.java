package de.maax.mappath.client;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.blaze3d.platform.InputConstants;
import de.maax.mappath.MapPath;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import net.neoforged.neoforge.event.level.ChunkEvent;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = MapPath.MODID, value = Dist.CLIENT)
public final class MapPathClient {
    private static final int INVENTORY_MAP_BUTTON_OFFSET_X = 126;
    private static final int INVENTORY_BUTTON_Y = -22;
    private static final int INVENTORY_BUTTON_WIDTH = 20;
    private static final int INVENTORY_BUTTON_HEIGHT = 18;
    private static final WidgetSprites MAP_BUTTON_SPRITES = new WidgetSprites(
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "map_button"),
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "map_button_highlighted")
    );
    private static final WorldMapManager WORLD_MAP_MANAGER = new WorldMapManager();
    private static final KeyMapping OPEN_WORLD_MAP = new KeyMapping(
        "key.mappath.open_world_map",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_M,
        "key.categories.mappath"
    );
    private static final KeyMapping CANCEL_ROUTE = new KeyMapping(
        "key.mappath.cancel_route",
        KeyConflictContext.IN_GAME,
        KeyModifier.SHIFT,
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_C,
        "key.categories.mappath"
    );
    private static final KeyMapping CREATE_WAYPOINT = new KeyMapping(
        "key.mappath.create_waypoint",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_N,
        "key.categories.mappath"
    );

    private MapPathClient() {
    }

    @SubscribeEvent
    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(OPEN_WORLD_MAP);
        event.register(CANCEL_ROUTE);
        event.register(CREATE_WAYPOINT);
    }

    @SubscribeEvent
    public static void registerClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("mappath")
            .then(Commands.literal("trail")
                .then(Commands.literal("spawn")
                    .executes(context -> spawnRouteTrail(context, 8, 3.0D))
                    .then(Commands.argument("length", IntegerArgumentType.integer(1, 64))
                        .executes(context -> spawnRouteTrail(context, IntegerArgumentType.getInteger(context, "length"), 3.0D))
                        .then(Commands.argument("height", DoubleArgumentType.doubleArg(0.0D, 32.0D))
                            .executes(context -> spawnRouteTrail(
                                context,
                                IntegerArgumentType.getInteger(context, "length"),
                                DoubleArgumentType.getDouble(context, "height")
                            ))
                        )
                    )
                )
                .then(Commands.literal("clear")
                    .executes(context -> clearRouteTrail(context))
                )
            )
        );
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        WORLD_MAP_MANAGER.recordAroundPlayer(minecraft);

        while (OPEN_WORLD_MAP.consumeClick()) {
            openWorldMap(minecraft);
        }
        while (CANCEL_ROUTE.consumeClick()) {
            WORLD_MAP_MANAGER.cancelRoute(minecraft);
        }
        while (CREATE_WAYPOINT.consumeClick()) {
            openWaypointCreateScreenAtPlayer(minecraft);
        }
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ClientLevel level) {
            ChunkPos chunkPos = event.getChunk().getPos();
            WORLD_MAP_MANAGER.refreshChunkOnLoad(level.dimension().location(), chunkPos.x, chunkPos.z);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        HighlightedTargetRenderer.render(event, WORLD_MAP_MANAGER);
        RouteVisualizerRenderer.render(event, WORLD_MAP_MANAGER);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        HighlightedTargetRenderer.renderGui(event);
        RouteVisualizerRenderer.renderGui(event, WORLD_MAP_MANAGER);
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen inventoryScreen) {
            event.addListener(new InventoryMapButton(inventoryScreen));
        }
    }

    static void openWorldMap(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null) {
            return;
        }

        if (minecraft.screen instanceof WorldMapScreen) {
            minecraft.setScreen(null);
        } else {
            minecraft.setScreen(new WorldMapScreen(WORLD_MAP_MANAGER));
        }
    }

    static WorldMapManager worldMapManager() {
        return WORLD_MAP_MANAGER;
    }

    private static void openWaypointCreateScreenAtPlayer(Minecraft minecraft) {
        if (minecraft.level == null || minecraft.player == null || minecraft.screen != null) {
            return;
        }

        minecraft.setScreen(new WaypointCreateScreen(
            null,
            WORLD_MAP_MANAGER,
            Mth.floor(minecraft.player.getX()),
            Mth.floor(minecraft.player.getY()),
            Mth.floor(minecraft.player.getZ()),
            true
        ));
    }

    private static int spawnRouteTrail(CommandContext<CommandSourceStack> context, int length, double height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            context.getSource().sendFailure(Component.literal("Route trail can only be spawned in a world"));
            return 0;
        }

        WORLD_MAP_MANAGER.spawnStraightRouteTrail(minecraft, length, height);
        context.getSource().sendSuccess(() -> Component.literal("Spawned route trail"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int clearRouteTrail(CommandContext<CommandSourceStack> context) {
        WORLD_MAP_MANAGER.cancelRoute(Minecraft.getInstance());
        context.getSource().sendSuccess(() -> Component.literal("Route trail cleared"), false);
        return Command.SINGLE_SUCCESS;
    }

    private static final class InventoryMapButton extends ImageButton {
        private final InventoryScreen inventoryScreen;

        private InventoryMapButton(InventoryScreen inventoryScreen) {
            super(
                INVENTORY_BUTTON_WIDTH,
                INVENTORY_BUTTON_HEIGHT,
                MAP_BUTTON_SPRITES,
                ignored -> openWorldMap(Minecraft.getInstance()),
                Component.translatable("gui.mappath.open_map")
            );
            this.inventoryScreen = inventoryScreen;
            this.updatePosition();
        }

        @Override
        public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
            this.updatePosition();
            super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);
        }

        private void updatePosition() {
            this.setPosition(
                this.inventoryScreen.getGuiLeft() + INVENTORY_MAP_BUTTON_OFFSET_X,
                this.inventoryScreen.height / 2 + INVENTORY_BUTTON_Y
            );
        }
    }
}
