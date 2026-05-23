package de.maax.mappath.client;

import de.maax.mappath.BannerIconType;
import de.maax.mappath.MapPath;
import de.maax.mappath.MapPathConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

final class WaypointCreateScreen extends Screen {
    private static final int PANEL_WIDTH = 320;
    private static final int FIELD_HEIGHT = 20;
    private static final int BUTTON_HEIGHT = 18;
    private static final int PANEL_PADDING = 20;
    private static final int LABEL_WIDTH = 78;
    private static final int ROW_GAP = 28;
    private static final int SEARCH_TEXT_PADDING_X = 8;
    private static final int SEARCH_TEXT_OFFSET_Y = 2;
    private static final int BANNER_TEXTURE_SIZE = 64;
    private static final int BANNER_PREVIEW_SIZE = 18;
    private static final int WHITE_TEXT_COLOR = 0xFFFFFFFF;
    private static final int PANEL_BACKGROUND = 0x40000000;
    private static final int PANEL_BORDER = 0xFF000000;
    private static final ResourceLocation BUTTON_SPRITE = ResourceLocation.withDefaultNamespace("widget/button");
    private static final List<BannerIconType> SELECTABLE_ICONS = Arrays.stream(BannerIconType.values())
        .filter(icon -> icon != BannerIconType.DEATH)
        .toList();

    private final Screen parent;
    private final WorldMapManager worldMapManager;
    private final WaypointStore.Waypoint editingWaypoint;
    private final WaypointStore.DimensionWaypoint editingDimensionWaypoint;
    private final int defaultX;
    private final int defaultY;
    private final int defaultZ;
    private final boolean prefillCoordinates;
    private final Path targetWaypointPath;
    private final boolean targetCurrentDimension;
    private final Dropdown<IconOption> iconDropdown = new Dropdown<>();
    private final Dropdown<DimensionOption> dimensionDropdown = new Dropdown<>();
    private List<DimensionOption> dimensionOptions = List.of();
    private String selectedDimensionKey = "";
    private BannerIconType selectedIcon;
    private EditBox nameBox;
    private EditBox xBox;
    private EditBox yBox;
    private EditBox zBox;
    private Button confirmButton;

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, int defaultX, int defaultY, int defaultZ) {
        this(parent, worldMapManager, defaultX, defaultY, defaultZ, false);
    }

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, int defaultX, int defaultY, int defaultZ, boolean prefillCoordinates) {
        super(Component.translatable("screen.mappath.create_waypoint"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
        this.editingWaypoint = null;
        this.editingDimensionWaypoint = null;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.defaultZ = defaultZ;
        this.prefillCoordinates = prefillCoordinates;
        this.targetWaypointPath = null;
        this.targetCurrentDimension = true;
        this.selectedIcon = randomSelectableIcon();
    }

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, Path targetWaypointPath, boolean targetCurrentDimension, int defaultX, int defaultY, int defaultZ) {
        super(Component.translatable("screen.mappath.create_waypoint"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
        this.editingWaypoint = null;
        this.editingDimensionWaypoint = null;
        this.defaultX = defaultX;
        this.defaultY = defaultY;
        this.defaultZ = defaultZ;
        this.prefillCoordinates = true;
        this.targetWaypointPath = targetWaypointPath;
        this.targetCurrentDimension = targetCurrentDimension;
        this.selectedIcon = randomSelectableIcon();
    }

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, WaypointStore.Waypoint editingWaypoint) {
        super(Component.translatable("screen.mappath.edit_waypoint"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
        this.editingWaypoint = editingWaypoint;
        this.editingDimensionWaypoint = null;
        this.defaultX = editingWaypoint.worldX();
        this.defaultY = editingWaypoint.worldY();
        this.defaultZ = editingWaypoint.worldZ();
        this.prefillCoordinates = true;
        this.targetWaypointPath = null;
        this.targetCurrentDimension = true;
        this.selectedIcon = editingWaypoint.icon() == BannerIconType.DEATH ? randomSelectableIcon() : editingWaypoint.icon();
    }

    WaypointCreateScreen(Screen parent, WorldMapManager worldMapManager, WaypointStore.DimensionWaypoint editingDimensionWaypoint) {
        super(Component.translatable("screen.mappath.edit_waypoint"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
        this.editingWaypoint = editingDimensionWaypoint.waypoint();
        this.editingDimensionWaypoint = editingDimensionWaypoint;
        this.defaultX = editingDimensionWaypoint.waypoint().worldX();
        this.defaultY = editingDimensionWaypoint.waypoint().worldY();
        this.defaultZ = editingDimensionWaypoint.waypoint().worldZ();
        this.prefillCoordinates = true;
        this.targetWaypointPath = null;
        this.targetCurrentDimension = editingDimensionWaypoint.currentDimension();
        this.selectedIcon = editingDimensionWaypoint.waypoint().icon() == BannerIconType.DEATH ? randomSelectableIcon() : editingDimensionWaypoint.waypoint().icon();
    }

    @Override
    protected void init() {
        this.dimensionOptions = this.createDimensionOptions();
        this.selectedDimensionKey = this.initialDimensionKey();
        int left = this.panelLeft();
        int top = this.panelTop();
        int fieldLeft = left + PANEL_PADDING + LABEL_WIDTH;
        int fieldWidth = PANEL_WIDTH - PANEL_PADDING * 2 - LABEL_WIDTH;
        int rowTop = top + 48;

        this.iconDropdown.set(
            fieldLeft,
            rowTop,
            fieldWidth,
            FIELD_HEIGHT,
            SELECTABLE_ICONS.stream().map(IconOption::new).toList(),
            new IconOption(this.selectedIcon),
            option -> this.selectedIcon = option.icon()
        );

        this.dimensionDropdown.set(
            fieldLeft,
            rowTop + ROW_GAP,
            fieldWidth,
            FIELD_HEIGHT,
            this.dimensionOptions,
            this.selectedDimension(),
            option -> {
                this.selectedDimensionKey = option.key();
                this.updateConfirmState();
            }
        );

        this.nameBox = this.textBox(fieldLeft, rowTop + ROW_GAP * 2, fieldWidth, Component.translatable("gui.mappath.waypoint_name"));
        this.nameBox.setMaxLength(64);
        if (this.editingWaypoint != null) {
            this.nameBox.setValue(this.editingWaypoint.name());
        }
        this.addRenderableWidget(this.nameBox);

        this.xBox = this.coordinateBox(fieldLeft, rowTop + ROW_GAP * 3, fieldWidth, this.defaultX);
        this.yBox = this.coordinateBox(fieldLeft, rowTop + ROW_GAP * 4, fieldWidth, this.defaultY);
        this.zBox = this.coordinateBox(fieldLeft, rowTop + ROW_GAP * 5, fieldWidth, this.defaultZ);
        if (this.prefillCoordinates) {
            this.xBox.setValue(Integer.toString(this.defaultX));
            this.yBox.setValue(Integer.toString(this.defaultY));
            this.zBox.setValue(Integer.toString(this.defaultZ));
        }
        this.addRenderableWidget(this.xBox);
        this.addRenderableWidget(this.yBox);
        this.addRenderableWidget(this.zBox);

        int buttonTop = rowTop + ROW_GAP * 6 + 4;
        this.confirmButton = this.addRenderableWidget(
            Button.builder(Component.translatable("gui.mappath.confirm"), button -> this.confirm())
                .bounds(left + PANEL_PADDING, buttonTop, 136, BUTTON_HEIGHT)
                .build()
        );
        this.addRenderableWidget(
            Button.builder(Component.translatable("gui.mappath.cancel"), button -> this.onClose())
                .bounds(left + PANEL_WIDTH - PANEL_PADDING - 136, buttonTop, 136, BUTTON_HEIGHT)
                .build()
        );
        this.updateConfirmState();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = this.panelLeft();
        int top = this.panelTop();
        int labelX = left + PANEL_PADDING;
        int fieldLeft = left + PANEL_PADDING + LABEL_WIDTH;
        int rowTop = top + 48;

        this.renderPanel(guiGraphics, left, top, left + PANEL_WIDTH, this.panelBottom());
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 18, WHITE_TEXT_COLOR);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_icon", labelX, rowTop + 6);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_dimension", labelX, rowTop + ROW_GAP + 6);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_name", labelX, rowTop + ROW_GAP * 2 + 6);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_x", labelX, rowTop + ROW_GAP * 3 + 6);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_y", labelX, rowTop + ROW_GAP * 4 + 6);
        this.drawLabel(guiGraphics, "gui.mappath.waypoint_z", labelX, rowTop + ROW_GAP * 5 + 6);
        this.renderEditBoxBackground(guiGraphics, this.nameBox);
        this.renderEditBoxBackground(guiGraphics, this.xBox);
        this.renderEditBoxBackground(guiGraphics, this.yBox);
        this.renderEditBoxBackground(guiGraphics, this.zBox);

        this.iconDropdown.renderButton(guiGraphics, this.font);
        this.dimensionDropdown.renderButton(guiGraphics, this.font);
        ResourceLocation texture = ResourceLocation.fromNamespaceAndPath(MapPath.MODID, this.selectedIcon.texturePath());
        guiGraphics.blit(texture, fieldLeft - BANNER_PREVIEW_SIZE - 6, rowTop + 1, BANNER_PREVIEW_SIZE, BANNER_PREVIEW_SIZE, 0.0F, 0.0F, BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE, BANNER_TEXTURE_SIZE);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.iconDropdown.renderMenu(guiGraphics, this.font, mouseX, mouseY);
        this.dimensionDropdown.renderMenu(guiGraphics, this.font, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.iconDropdown.mouseClicked(mouseX, mouseY, button)) {
            this.dimensionDropdown.close();
            return true;
        }
        if (this.dimensionDropdown.mouseClicked(mouseX, mouseY, button)) {
            this.iconDropdown.close();
            return true;
        }
        this.iconDropdown.close();
        this.dimensionDropdown.close();
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.iconDropdown.mouseScrolled(mouseX, mouseY, scrollY) || this.dimensionDropdown.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private EditBox textBox(int x, int y, int width, Component message) {
        EditBox box = new EditBox(this.font, x + SEARCH_TEXT_PADDING_X, y + (FIELD_HEIGHT - this.font.lineHeight) / 2 + SEARCH_TEXT_OFFSET_Y, width - SEARCH_TEXT_PADDING_X * 2, this.font.lineHeight, message);
        box.setBordered(false);
        return box;
    }

    private EditBox coordinateBox(int x, int y, int width, int defaultValue) {
        EditBox box = this.textBox(x, y, width, Component.empty());
        box.setMaxLength(12);
        box.setFilter(value -> value.isEmpty() || value.matches("-?\\d*"));
        box.setHint(Component.literal(Integer.toString(defaultValue)));
        box.setResponder(value -> this.updateConfirmState());
        return box;
    }

    private void drawLabel(GuiGraphics guiGraphics, String translationKey, int x, int y) {
        guiGraphics.drawString(this.font, Component.translatable(translationKey), x, y, WHITE_TEXT_COLOR, false);
    }

    private void renderEditBoxBackground(GuiGraphics guiGraphics, EditBox box) {
        if (box == null) {
            return;
        }

        int left = box.getX() - SEARCH_TEXT_PADDING_X;
        int top = box.getY() - (FIELD_HEIGHT - this.font.lineHeight) / 2 - SEARCH_TEXT_OFFSET_Y;
        int right = box.getX() + box.getWidth() + SEARCH_TEXT_PADDING_X;
        int bottom = top + FIELD_HEIGHT;
        int border = box.isFocused() ? 0xFFFFFFFF : 0xFF505050;
        int textColor = box.getValue().isEmpty() ? 0xFF808080 : WHITE_TEXT_COLOR;
        guiGraphics.fill(left, top, right, bottom, 0xFF000000);
        this.drawBorder(guiGraphics, left, top, right, bottom, border);
        box.setTextColor(textColor);
        box.setTextColorUneditable(textColor);
    }

    private void renderPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        guiGraphics.fill(left, top, right, bottom, PANEL_BACKGROUND);
        this.drawBorder(guiGraphics, left, top, right, bottom, PANEL_BORDER);
    }

    private void drawBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(left, top, right, top + 1, color);
        guiGraphics.fill(left, bottom - 1, right, bottom, color);
        guiGraphics.fill(left, top, left + 1, bottom, color);
        guiGraphics.fill(right - 1, top, right, bottom, color);
    }

    private void updateConfirmState() {
        if (this.confirmButton != null) {
            this.confirmButton.active = this.selectedDimension().path() != null
                && this.parseCoordinate(this.xBox, this.defaultX) != null
                && this.parseCoordinate(this.yBox, this.defaultY) != null
                && this.parseCoordinate(this.zBox, this.defaultZ) != null;
        }
    }

    private void confirm() {
        Integer worldX = this.parseCoordinate(this.xBox, this.defaultX);
        Integer worldY = this.parseCoordinate(this.yBox, this.defaultY);
        Integer worldZ = this.parseCoordinate(this.zBox, this.defaultZ);
        DimensionOption dimension = this.selectedDimension();
        if (worldX == null || worldY == null || worldZ == null || dimension.path() == null || this.minecraft == null) {
            return;
        }

        String name = this.nameBox.getValue().trim();
        if (name.isEmpty()) {
            name = "X: " + worldX + " Y: " + worldY + " Z: " + worldZ;
        }

        if (this.editingWaypoint == null && !MapPathConfig.CLIENT.showWaypoints()) {
            this.minecraft.setScreen(this.parent);
            return;
        }

        if (this.editingWaypoint == null) {
            this.worldMapManager.addWaypoint(dimension.path(), dimension.currentDimension(), this.selectedIcon, name, worldX, worldY, worldZ);
        } else if (this.editingDimensionWaypoint != null) {
            this.worldMapManager.moveWaypoint(this.editingDimensionWaypoint, dimension.path(), dimension.currentDimension(), this.selectedIcon, name, worldX, worldY, worldZ);
        } else {
            this.worldMapManager.moveWaypoint(this.minecraft, this.editingWaypoint.id(), dimension.path(), dimension.currentDimension(), this.selectedIcon, name, worldX, worldY, worldZ);
        }
        this.minecraft.setScreen(this.parent);
    }

    private Integer parseCoordinate(EditBox box, int defaultValue) {
        String value = box.getValue().trim();
        if (value.isEmpty()) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private List<DimensionOption> createDimensionOptions() {
        List<DimensionOption> options = new ArrayList<>();
        ResourceLocation currentDimensionId = this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.dimension().location() : null;
        String currentKey = currentDimensionId == null ? null : currentDimensionId.toString();
        if (currentDimensionId != null) {
            options.add(new DimensionOption(
                currentKey,
                Component.literal(WaypointStore.displayName(currentDimensionId, MapTileStore.sanitizePathPart(currentDimensionId.toString()))),
                this.waypointPathFor(currentDimensionId),
                true
            ));
        }

        Map<String, DimensionOption> byKey = new HashMap<>();
        for (ResourceLocation dimensionId : WaypointStore.dimensionIdsByDirectory(this.minecraft).values()) {
            String key = dimensionId.toString();
            if (Objects.equals(key, currentKey)) {
                continue;
            }
            byKey.putIfAbsent(key, new DimensionOption(
                key,
                Component.literal(WaypointStore.displayName(dimensionId, MapTileStore.sanitizePathPart(key))),
                this.waypointPathFor(dimensionId),
                false
            ));
        }
        for (WaypointStore.DimensionWaypoint entry : this.worldMapManager.allWaypoints(this.minecraft)) {
            String key = this.dimensionKey(entry);
            if (Objects.equals(key, currentKey)) {
                continue;
            }
            byKey.putIfAbsent(key, new DimensionOption(key, Component.literal(entry.dimensionName()), entry.path(), entry.currentDimension()));
        }

        List<DimensionOption> remaining = new ArrayList<>(byKey.values());
        remaining.sort(Comparator.comparing(option -> option.label().getString(), String.CASE_INSENSITIVE_ORDER));
        options.addAll(remaining);
        return options;
    }

    private Path waypointPathFor(ResourceLocation dimensionId) {
        Path directory = MapTileStore.directoryFor(this.minecraft, dimensionId);
        return directory == null ? null : directory.resolve(WaypointStore.FILE_NAME);
    }

    private String initialDimensionKey() {
        if (this.editingDimensionWaypoint != null) {
            return this.dimensionKey(this.editingDimensionWaypoint);
        }

        if (this.targetWaypointPath != null) {
            for (DimensionOption option : this.dimensionOptions) {
                if (this.targetWaypointPath.equals(option.path()) && this.targetCurrentDimension == option.currentDimension()) {
                    return option.key();
                }
            }
        }

        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.dimension().location().toString() : "";
    }

    private DimensionOption selectedDimension() {
        for (DimensionOption option : this.dimensionOptions) {
            if (option.key().equals(this.selectedDimensionKey)) {
                return option;
            }
        }
        return this.dimensionOptions.isEmpty() ? new DimensionOption("", Component.literal("-"), null, false) : this.dimensionOptions.get(0);
    }

    private String dimensionKey(WaypointStore.DimensionWaypoint entry) {
        return entry.dimensionId() != null ? entry.dimensionId().toString() : "path:" + entry.dimensionDirectoryName();
    }

    private int panelLeft() {
        return (this.width - PANEL_WIDTH) / 2;
    }

    private int panelTop() {
        return Math.max(12, this.height / 2 - 128);
    }

    private int panelBottom() {
        return this.panelTop() + 258;
    }

    private static BannerIconType randomSelectableIcon() {
        return SELECTABLE_ICONS.get(ThreadLocalRandom.current().nextInt(SELECTABLE_ICONS.size()));
    }

    private record IconOption(BannerIconType icon) implements DropdownEntry {
        @Override
        public Component displayName() {
            return this.icon.displayName();
        }
    }

    private record DimensionOption(String key, Component label, Path path, boolean currentDimension) implements DropdownEntry {
        @Override
        public Component displayName() {
            return this.label;
        }
    }

    private interface DropdownEntry {
        Component displayName();
    }

    private static final class Dropdown<T extends DropdownEntry> {
        private static final int MAX_VISIBLE_OPTIONS = 6;
        private static final int MENU_SCROLLBAR_WIDTH = 8;
        private static final int MENU_OPTION_HEIGHT = 18;
        private static final int MENU_PADDING = 4;
        private static final int MENU_Z_OFFSET = 200;
        private static final int MENU_BACKGROUND_COLOR = 0xFF1B1B1B;
        private static final int MENU_BORDER_COLOR = 0xFF8E8E8E;
        private static final int MENU_INNER_BORDER_COLOR = 0xFF303030;
        private static final int MENU_HOVER_COLOR = 0x66808080;
        private static final int MENU_SELECTED_COLOR = 0xFF3A3A3A;
        private static final int MENU_SCROLLBAR_TRACK_COLOR = 0xFF4C4C4C;
        private static final int MENU_SCROLLBAR_KNOB_COLOR = 0xFF686868;

        private int x;
        private int y;
        private int width;
        private int height;
        private List<T> options = List.of();
        private T selected;
        private java.util.function.Consumer<T> onSelected = ignored -> {
        };
        private boolean open;
        private int scrollOffset;

        private void set(int x, int y, int width, int height, List<T> options, T selected, java.util.function.Consumer<T> onSelected) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.options = List.copyOf(options);
            this.selected = selected;
            this.onSelected = onSelected;
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScrollOffset());
        }

        private void renderButton(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font) {
            guiGraphics.blitSprite(BUTTON_SPRITE, this.x, this.y, this.width, this.height);
            Component label = this.selected == null ? Component.literal("-") : this.selected.displayName();
            String text = font.plainSubstrByWidth(label.getString(), this.width - 28);
            guiGraphics.drawString(font, text, this.x + 6, this.y + 6, WHITE_TEXT_COLOR, true);
            int arrowLeft = this.x + this.width - 25;
            int arrowTop = this.y + 6;
            guiGraphics.fill(arrowLeft, arrowTop, arrowLeft + 17, arrowTop + 1, 0xFF000000);
            guiGraphics.fill(arrowLeft + 2, arrowTop + 1, arrowLeft + 15, arrowTop + 2, 0xFF000000);
            guiGraphics.fill(arrowLeft + 4, arrowTop + 2, arrowLeft + 13, arrowTop + 3, 0xFF000000);
            guiGraphics.fill(arrowLeft + 6, arrowTop + 3, arrowLeft + 11, arrowTop + 4, 0xFF000000);
            guiGraphics.fill(arrowLeft + 8, arrowTop + 4, arrowLeft + 9, arrowTop + 5, 0xFF000000);
        }

        private void renderMenu(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font, int mouseX, int mouseY) {
            if (!this.open || this.options.isEmpty()) {
                return;
            }

            int visibleOptions = Math.min(MAX_VISIBLE_OPTIONS, this.options.size());
            int menuTop = this.y + this.height + 1;
            int optionHeight = this.menuOptionHeight();
            int menuBottom = menuTop + MENU_PADDING * 2 + visibleOptions * optionHeight;
            int innerMenuTop = menuTop + MENU_PADDING;
            int innerMenuBottom = menuBottom - MENU_PADDING;
            boolean scrollable = this.maxScrollOffset() > 0;
            int textRight = this.x + this.width - (scrollable ? MENU_SCROLLBAR_WIDTH + 6 : 6);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, MENU_Z_OFFSET);
            guiGraphics.fill(this.x, menuTop, this.x + this.width, menuBottom, MENU_BACKGROUND_COLOR);
            this.drawMenuBorder(guiGraphics, this.x, menuTop, this.x + this.width, menuBottom);

            for (int visibleIndex = 0; visibleIndex < visibleOptions; visibleIndex++) {
                int index = this.scrollOffset + visibleIndex;
                T option = this.options.get(index);
                int optionTop = innerMenuTop + visibleIndex * optionHeight;
                boolean selectedOption = Objects.equals(option, this.selected);
                boolean hoveredOption = mouseX >= this.x
                    && mouseX < textRight
                    && mouseY >= optionTop
                    && mouseY < optionTop + optionHeight;
                if (hoveredOption) {
                    int hoverTop = Math.max(optionTop, innerMenuTop);
                    int hoverBottom = Math.min(optionTop + optionHeight, innerMenuBottom);
                    if (hoverTop < hoverBottom) {
                        guiGraphics.fill(this.x + 4, hoverTop, this.x + this.width - 4, hoverBottom, MENU_HOVER_COLOR);
                    }
                }
                if (selectedOption) {
                    int selectedTop = Math.max(optionTop, innerMenuTop);
                    int selectedBottom = Math.min(optionTop + optionHeight, innerMenuBottom);
                    if (selectedTop < selectedBottom) {
                        guiGraphics.fill(this.x + 4, selectedTop, this.x + this.width - 4, selectedBottom, MENU_SELECTED_COLOR);
                    }
                }

                int color = selectedOption ? WHITE_TEXT_COLOR : 0xFFE8E8E8;
                guiGraphics.drawString(font, font.plainSubstrByWidth(option.displayName().getString(), textRight - this.x - 12), this.x + 8, optionTop + (optionHeight - font.lineHeight) / 2, color, false);
            }

            if (scrollable) {
                int scrollbarX = this.x + this.width - MENU_SCROLLBAR_WIDTH - 4;
                int trackTop = innerMenuTop;
                int trackBottom = innerMenuBottom;
                int trackHeight = trackBottom - trackTop;
                int knobHeight = Mth.clamp(trackHeight * visibleOptions / this.options.size(), 10, trackHeight);
                int knobY = trackTop + (int)Math.round((trackHeight - knobHeight) * (this.scrollOffset / (double)this.maxScrollOffset()));
                guiGraphics.fill(scrollbarX, trackTop, scrollbarX + MENU_SCROLLBAR_WIDTH, trackBottom, MENU_SCROLLBAR_TRACK_COLOR);
                guiGraphics.fill(scrollbarX + 1, knobY, scrollbarX + MENU_SCROLLBAR_WIDTH - 1, knobY + knobHeight, MENU_SCROLLBAR_KNOB_COLOR);
            }

            guiGraphics.pose().popPose();
        }

        private void drawMenuBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
            guiGraphics.fill(left, top, right, top + 2, MENU_BORDER_COLOR);
            guiGraphics.fill(left, bottom - 2, right, bottom, MENU_BORDER_COLOR);
            guiGraphics.fill(left, top, left + 2, bottom, MENU_BORDER_COLOR);
            guiGraphics.fill(right - 2, top, right, bottom, MENU_BORDER_COLOR);
            guiGraphics.fill(left + 2, top + 2, right - 2, top + 4, MENU_INNER_BORDER_COLOR);
            guiGraphics.fill(left + 2, bottom - 4, right - 2, bottom - 2, MENU_INNER_BORDER_COLOR);
            guiGraphics.fill(left + 2, top + 2, left + 4, bottom - 2, MENU_INNER_BORDER_COLOR);
            guiGraphics.fill(right - 4, top + 2, right - 2, bottom - 2, MENU_INNER_BORDER_COLOR);
        }

        private boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button != 0) {
                return false;
            }

            if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= this.y && mouseY < this.y + this.height) {
                this.open = !this.open;
                return true;
            }

            if (!this.open) {
                return false;
            }

            int visibleOptions = Math.min(MAX_VISIBLE_OPTIONS, this.options.size());
            int menuTop = this.y + this.height + 1;
            int optionHeight = this.menuOptionHeight();
            int innerMenuTop = menuTop + MENU_PADDING;
            if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= innerMenuTop && mouseY < innerMenuTop + visibleOptions * optionHeight) {
                int index = this.scrollOffset + (int)((mouseY - innerMenuTop) / optionHeight);
                this.open = false;
                T option = this.options.get(index);
                if (!Objects.equals(option, this.selected)) {
                    this.selected = option;
                    this.onSelected.accept(option);
                }
                return true;
            }

            this.open = false;
            return false;
        }

        private boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
            if (!this.open || this.maxScrollOffset() <= 0) {
                return false;
            }

            int visibleOptions = Math.min(MAX_VISIBLE_OPTIONS, this.options.size());
            int menuTop = this.y + this.height + 1;
            int innerMenuTop = menuTop + MENU_PADDING;
            if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < innerMenuTop || mouseY >= innerMenuTop + visibleOptions * this.menuOptionHeight()) {
                return false;
            }

            this.scrollOffset = Mth.clamp(this.scrollOffset - (int)Math.signum(scrollY), 0, this.maxScrollOffset());
            return true;
        }

        private void close() {
            this.open = false;
        }

        private int maxScrollOffset() {
            return Math.max(0, this.options.size() - MAX_VISIBLE_OPTIONS);
        }

        private int menuOptionHeight() {
            return Math.min(this.height, MENU_OPTION_HEIGHT);
        }
    }
}
