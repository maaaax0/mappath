package de.maax.mappath.client;

import com.mojang.blaze3d.systems.RenderSystem;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class WaypointListScreen extends Screen {
    private static final int ROOT_MARGIN_X = 74;
    private static final int ROOT_MARGIN_Y = 32;
    private static final int GAP = 6;
    private static final int FILTER_PADDING = 20;
    private static final int ROW_HEIGHT = 26;
    private static final int ROW_GAP = 4;
    private static final int TITLE_HEIGHT = 24;
    private static final int FIELD_HEIGHT = 20;
    private static final int ICON_SIZE = 16;
    private static final int ICON_TEXTURE_SIZE = 64;
    private static final int BUTTON_HEIGHT = 18;
    private static final int BUTTON_GAP = 4;
    private static final int ADD_BUTTON_WIDTH = 70;
    private static final int EDIT_BUTTON_WIDTH = 70;
    private static final int TELEPORT_BUTTON_WIDTH = 90;
    private static final int DELETE_BUTTON_WIDTH = 80;
    private static final int RESTORE_BUTTON_WIDTH = 90;
    private static final int DELETE_PERMANENTLY_BUTTON_WIDTH = 112;
    private static final int ACTION_BAR_HEIGHT = 24;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int SEARCH_TEXT_PADDING_X = 8;
    private static final int SEARCH_TEXT_OFFSET_Y = 2;
    private static final int CHECKBOX_SIZE = 16;
    private static final int CHECKBOX_TEXTURE_SIZE = 16;
    private static final int CHECKBOX_LABEL_GAP = 8;
    private static final int TEXT_COLOR = 0xFFE8EAED;
    private static final int WHITE_TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_TEXT_COLOR = 0xFFBFC7D5;
    private static final int PENDING_TEXT_COLOR = 0xFF90949C;
    private static final int PANEL_BACKGROUND = 0x40000000;
    private static final int PANEL_BORDER = 0xFF000000;
    private static final int ROW_BACKGROUND = 0x70000000;
    private static final int ROW_HOVER_BORDER = 0xFF80FFFF;
    private static final int ROW_SELECTED_BORDER = 0xFFFFFFFF;
    private static final ResourceLocation BUTTON_SPRITE = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation SCROLLER_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller");
    private static final ResourceLocation SCROLLER_BACKGROUND_SPRITE = ResourceLocation.withDefaultNamespace("widget/scroller_background");
    private static final ResourceLocation CHECKBOX_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/checkbox/checkbox.png");
    private static final ResourceLocation CHECKBOX_HIGHLIGHTED_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/checkbox/checkbox_highlighted.png");
    private static final ResourceLocation CHECKBOX_SELECTED_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/checkbox/checkbox_selected.png");
    private static final ResourceLocation CHECKBOX_SELECTED_HIGHLIGHTED_TEXTURE =
        ResourceLocation.fromNamespaceAndPath(MapPath.MODID, "textures/gui/checkbox/checkbox_selected_highlighted.png");
    private static final Map<BannerIconType, ResourceLocation> BANNER_ICON_TEXTURES = createBannerIconTextures();

    private final Screen parent;
    private final WorldMapManager worldMapManager;
    private final Set<String> pendingDeletedWaypoints = new HashSet<>();
    private final Dropdown<DimensionOption> dimensionDropdown = new Dropdown<>();
    private final Dropdown<SortCategory> sortCategoryDropdown = new Dropdown<>();
    private final Dropdown<SortOrder> sortOrderDropdown = new Dropdown<>();
    private List<WaypointStore.DimensionWaypoint> allWaypoints = List.of();
    private List<WaypointStore.DimensionWaypoint> visibleWaypoints = List.of();
    private List<DimensionOption> dimensionOptions = List.of();
    private String searchText = "";
    private String selectedDimensionKey = "";
    private SortCategory sortCategory = SortCategory.NAME;
    private SortOrder sortOrder = SortOrder.ASCENDING;
    private String selectedWaypointKey = "";
    private int scrollOffset;
    private boolean keepSearchFocused;
    private boolean draggingScrollbar;
    private EditBox searchBox;

    WaypointListScreen(Screen parent, WorldMapManager worldMapManager) {
        super(Component.translatable("screen.mappath.waypoints"));
        this.parent = parent;
        this.worldMapManager = worldMapManager;
    }

    @Override
    protected void init() {
        this.refreshWaypoints();
        this.rebuildWidgets();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        this.renderPanels(guiGraphics);
        this.renderFilterPanel(guiGraphics, mouseX, mouseY);
        this.renderWaypointPanel(guiGraphics, mouseX, mouseY);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.dimensionDropdown.renderMenu(guiGraphics, this.font, mouseX, mouseY);
        this.sortCategoryDropdown.renderMenu(guiGraphics, this.font, mouseX, mouseY);
        this.sortOrderDropdown.renderMenu(guiGraphics, this.font, mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.dimensionDropdown.mouseClicked(mouseX, mouseY, button)) {
            this.sortCategoryDropdown.close();
            this.sortOrderDropdown.close();
            return true;
        }
        if (this.sortCategoryDropdown.mouseClicked(mouseX, mouseY, button)) {
            this.dimensionDropdown.close();
            this.sortOrderDropdown.close();
            return true;
        }
        if (this.sortOrderDropdown.mouseClicked(mouseX, mouseY, button)) {
            this.dimensionDropdown.close();
            this.sortCategoryDropdown.close();
            return true;
        }
        this.dimensionDropdown.close();
        this.sortCategoryDropdown.close();
        this.sortOrderDropdown.close();

        if (button == 0 && this.isMouseOverSearchBox(mouseX, mouseY)) {
            this.searchBox.setFocused(true);
            this.setFocused(this.searchBox);
            return true;
        }

        if (button == 0 && this.isMouseOverDeathWaypointsToggle(mouseX, mouseY)) {
            MapPathConfig.CLIENT.setShowDeathWaypointsInWaypointList(!MapPathConfig.CLIENT.showDeathWaypointsInWaypointList());
            this.applyFilters(true);
            this.rebuildWidgets();
            return true;
        }

        if (button == 0 && this.isMouseOverScrollbar(mouseX, mouseY) && this.maxScrollOffset() > 0) {
            this.draggingScrollbar = true;
            this.scrollToMouse(mouseY);
            return true;
        }

        WaypointStore.DimensionWaypoint clickedWaypoint = this.waypointAt(mouseX, mouseY);
        if (button == 0 && clickedWaypoint != null) {
            this.selectedWaypointKey = this.key(clickedWaypoint);
            this.rebuildWidgets();
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.draggingScrollbar) {
            this.draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.draggingScrollbar) {
            this.scrollToMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.dimensionDropdown.mouseScrolled(mouseX, mouseY, scrollY) || this.sortCategoryDropdown.mouseScrolled(mouseX, mouseY, scrollY) || this.sortOrderDropdown.mouseScrolled(mouseX, mouseY, scrollY)) {
            return true;
        }

        int newScrollOffset = Mth.clamp(this.scrollOffset - (int)Math.signum(scrollY), 0, this.maxScrollOffset());
        if (newScrollOffset != this.scrollOffset) {
            this.scrollOffset = newScrollOffset;
            this.rebuildWidgets();
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

    private void refreshWaypoints() {
        this.allWaypoints = this.worldMapManager.allWaypoints(this.minecraft);
        this.dimensionOptions = this.createDimensionOptions();
        if (this.dimensionOptions.stream().noneMatch(option -> option.key().equals(this.selectedDimensionKey))) {
            this.selectedDimensionKey = this.currentDimensionKey();
        }
        this.applyFilters(false);
    }

    private void applyFilters(boolean resetScroll) {
        String query = this.searchText.trim().toLowerCase(Locale.ROOT);
        List<WaypointStore.DimensionWaypoint> filtered = new ArrayList<>();
        for (WaypointStore.DimensionWaypoint entry : this.allWaypoints) {
            if (!this.dimensionKey(entry).equals(this.selectedDimensionKey)) {
                continue;
            }
            if (!MapPathConfig.CLIENT.showDeathWaypointsInWaypointList() && entry.waypoint().icon() == BannerIconType.DEATH) {
                continue;
            }
            if (!query.isEmpty() && !this.matchesSearch(entry, query)) {
                continue;
            }
            filtered.add(entry);
        }

        filtered.sort(this.comparatorForSortMode());
        this.visibleWaypoints = filtered;
        if (resetScroll) {
            this.scrollOffset = 0;
        } else {
            this.scrollOffset = Mth.clamp(this.scrollOffset, 0, this.maxScrollOffset());
        }
        if (this.selectedWaypoint() == null) {
            this.selectedWaypointKey = "";
        }
    }

    @Override
    protected void rebuildWidgets() {
        this.clearWidgets();

        int filterPadding = this.filterPadding();
        int filterX = this.filterPanelLeft() + filterPadding;
        int controlWidth = Math.max(24, this.filterPanelWidth() - filterPadding * 2);
        int y = this.compactLayout() ? this.filterPanelTop() + 4 : this.filterPanelTop() + 74;

        this.searchBox = new EditBox(
            this.font,
            filterX + SEARCH_TEXT_PADDING_X,
            y + (FIELD_HEIGHT - this.font.lineHeight) / 2 + SEARCH_TEXT_OFFSET_Y,
            controlWidth - SEARCH_TEXT_PADDING_X * 2,
            this.font.lineHeight,
            Component.translatable("gui.mappath.waypoints.search")
        );
        this.searchBox.setHint(Component.translatable("gui.mappath.waypoints.search_hint"));
        this.searchBox.setBordered(false);
        this.searchBox.setMaxLength(64);
        this.searchBox.setValue(this.searchText);
        this.searchBox.setResponder(value -> {
            if (!this.searchText.equals(value)) {
                this.searchText = value;
                this.keepSearchFocused = true;
                this.applyFilters(true);
                this.rebuildWidgets();
            }
        });
        this.addRenderableWidget(this.searchBox);
        if (this.keepSearchFocused) {
            this.searchBox.setFocused(true);
            this.searchBox.moveCursorToEnd(false);
            this.keepSearchFocused = false;
        }

        y = this.deathWaypointsToggleY() + CHECKBOX_SIZE + (this.compactLayout() ? 6 : 14);
        int dropdownWidth = controlWidth;
        int dimensionX = filterX;
        int sortCategoryX = filterX;
        int sortOrderX = filterX;
        int dimensionY = y;
        int sortY = y + this.filterControlStep();
        int orderY = y + this.filterControlStep() * 2;
        if (this.compactLayout()) {
            dropdownWidth = Math.max(44, (controlWidth - GAP * 2) / 3);
            dimensionX = filterX;
            sortCategoryX = filterX + dropdownWidth + GAP;
            sortOrderX = filterX + (dropdownWidth + GAP) * 2;
            dimensionY = y;
            sortY = y;
            orderY = y;
        }

        this.dimensionDropdown.set(
            dimensionX,
            dimensionY,
            dropdownWidth,
            FIELD_HEIGHT,
            this.dimensionOptions,
            this.selectedDimension(),
            option -> {
                this.selectedDimensionKey = option.key();
                this.applyFilters(true);
                this.rebuildWidgets();
            }
        );

        this.sortCategoryDropdown.set(
            sortCategoryX,
            sortY,
            dropdownWidth,
            FIELD_HEIGHT,
            List.of(SortCategory.values()),
            this.sortCategory,
            category -> {
                this.sortCategory = category;
                this.applyFilters(true);
                this.rebuildWidgets();
            }
        );

        this.sortOrderDropdown.set(
            sortOrderX,
            orderY,
            dropdownWidth,
            FIELD_HEIGHT,
            List.of(SortOrder.values()),
            this.sortOrder,
            order -> {
                this.sortOrder = order;
                this.applyFilters(true);
                this.rebuildWidgets();
            }
        );

        this.addActionButtons();
    }

    private void addActionButtons() {
        WaypointStore.DimensionWaypoint selectedWaypoint = this.selectedWaypoint();
        boolean hasSelection = selectedWaypoint != null;
        boolean pendingDelete = hasSelection && this.isPendingDelete(selectedWaypoint);
        int y = this.actionButtonY();
        int x = this.actionButtonsLeft(pendingDelete);
        int addWidth = this.addButtonWidth();
        int editWidth = this.editButtonWidth();
        int teleportWidth = this.teleportButtonWidth();
        int deleteWidth = this.deleteButtonWidth();
        int restoreWidth = this.restoreButtonWidth();
        int deletePermanentlyWidth = this.deletePermanentlyButtonWidth();

        if (pendingDelete) {
            Button restoreButton = Button.builder(Component.translatable("gui.mappath.waypoints.restore_short"), button -> {
                WaypointStore.DimensionWaypoint waypoint = this.selectedWaypoint();
                if (waypoint != null) {
                    this.pendingDeletedWaypoints.remove(this.key(waypoint));
                    this.rebuildWidgets();
                }
            }).bounds(x, y, restoreWidth, BUTTON_HEIGHT).build();
            restoreButton.active = hasSelection;
            this.addRenderableWidget(restoreButton);

            Button deleteFinalButton = Button.builder(Component.translatable("gui.mappath.waypoints.delete_final_short"), button -> {
                WaypointStore.DimensionWaypoint waypoint = this.selectedWaypoint();
                if (waypoint != null) {
                    this.pendingDeletedWaypoints.remove(this.key(waypoint));
                    this.worldMapManager.deleteWaypoint(waypoint);
                    this.selectedWaypointKey = "";
                    this.refreshWaypoints();
                    this.rebuildWidgets();
                }
            }).bounds(x + restoreWidth + BUTTON_GAP, y, deletePermanentlyWidth, BUTTON_HEIGHT).build();
            deleteFinalButton.active = hasSelection;
            this.addRenderableWidget(deleteFinalButton);
            return;
        }

        Button addButton = Button.builder(Component.translatable("gui.mappath.waypoints.add_short"), button -> this.openAddWaypointScreen())
            .bounds(x, y, addWidth, BUTTON_HEIGHT)
            .build();
        addButton.active = this.selectedDimension().path() != null;
        this.addRenderableWidget(addButton);

        Button editButton = Button.builder(Component.translatable("gui.mappath.waypoints.edit_short"), button -> {
            WaypointStore.DimensionWaypoint waypoint = this.selectedWaypoint();
            if (waypoint != null && waypoint.waypoint().icon() != BannerIconType.DEATH && this.minecraft != null) {
                this.minecraft.setScreen(new WaypointCreateScreen(this, this.worldMapManager, waypoint));
            }
        }).bounds(x + addWidth + BUTTON_GAP, y, editWidth, BUTTON_HEIGHT).build();
        editButton.active = hasSelection && selectedWaypoint.waypoint().icon() != BannerIconType.DEATH;
        this.addRenderableWidget(editButton);

        Button teleportButton = Button.builder(Component.translatable("gui.mappath.waypoints.teleport_short"), button -> {
            WaypointStore.DimensionWaypoint waypoint = this.selectedWaypoint();
            if (waypoint != null) {
                this.worldMapManager.teleportToPosition(this.minecraft, waypoint.waypoint().worldX(), waypoint.waypoint().worldY(), waypoint.waypoint().worldZ());
                this.onClose();
            }
        }).bounds(x + addWidth + editWidth + BUTTON_GAP * 2, y, teleportWidth, BUTTON_HEIGHT).build();
        teleportButton.active = hasSelection && selectedWaypoint.currentDimension();
        this.addRenderableWidget(teleportButton);

        Button deleteButton = Button.builder(Component.translatable("gui.mappath.waypoints.delete_short"), button -> {
            WaypointStore.DimensionWaypoint waypoint = this.selectedWaypoint();
            if (waypoint != null) {
                this.pendingDeletedWaypoints.add(this.key(waypoint));
                this.rebuildWidgets();
            }
        }).bounds(x + addWidth + editWidth + teleportWidth + BUTTON_GAP * 3, y, deleteWidth, BUTTON_HEIGHT).build();
        deleteButton.active = hasSelection;
        this.addRenderableWidget(deleteButton);
    }

    private void openAddWaypointScreen() {
        if (this.minecraft == null) {
            return;
        }

        DimensionOption dimension = this.selectedDimension();
        int defaultX = this.minecraft.player == null ? 0 : Mth.floor(this.minecraft.player.getX());
        int defaultZ = this.minecraft.player == null ? 0 : Mth.floor(this.minecraft.player.getZ());
        int defaultY = dimension.currentDimension()
            ? this.worldMapManager.defaultWaypointY(this.minecraft, defaultX, defaultZ)
            : this.minecraft.player == null ? 64 : Mth.floor(this.minecraft.player.getY());
        this.minecraft.setScreen(new WaypointCreateScreen(this, this.worldMapManager, dimension.path(), dimension.currentDimension(), defaultX, defaultY, defaultZ));
    }

    private void renderPanels(GuiGraphics guiGraphics) {
        this.renderPanel(guiGraphics, this.filterPanelLeft(), this.filterPanelTop(), this.filterPanelRight(), this.filterPanelBottom());
        this.renderPanel(guiGraphics, this.contentPanelLeft(), this.contentPanelTop(), this.contentPanelRight(), this.rootBottom());
    }

    private void renderFilterPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = this.filterPanelLeft();
        int top = this.filterPanelTop();
        int labelX = left + this.filterPadding();
        this.renderSearchBoxBackground(guiGraphics);
        this.renderDeathWaypointsToggle(guiGraphics, mouseX, mouseY);
        if (this.compactLayout()) {
            this.dimensionDropdown.renderButton(guiGraphics, this.font);
            this.sortCategoryDropdown.renderButton(guiGraphics, this.font);
            this.sortOrderDropdown.renderButton(guiGraphics, this.font);
            return;
        }

        guiGraphics.drawCenteredString(this.font, Component.translatable("gui.mappath.waypoints.filters"), (left + this.filterPanelRight()) / 2, top + 28, WHITE_TEXT_COLOR);
        int labelY = top + 60;
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.search"), labelX, labelY, WHITE_TEXT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.dimension"), labelX, this.dimensionDropdown.y() + 6, WHITE_TEXT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.sort_category"), labelX, this.sortCategoryDropdown.y() + 6, WHITE_TEXT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.sort_order"), labelX, this.sortOrderDropdown.y() + 6, WHITE_TEXT_COLOR, false);

        this.dimensionDropdown.renderButton(guiGraphics, this.font);
        this.sortCategoryDropdown.renderButton(guiGraphics, this.font);
        this.sortOrderDropdown.renderButton(guiGraphics, this.font);
    }

    private void renderSearchBoxBackground(GuiGraphics guiGraphics) {
        if (this.searchBox == null) {
            return;
        }

        int left = this.searchBox.getX() - SEARCH_TEXT_PADDING_X;
        int top = this.searchBox.getY() - (FIELD_HEIGHT - this.font.lineHeight) / 2 - SEARCH_TEXT_OFFSET_Y;
        int right = this.searchBox.getX() + this.searchBox.getWidth() + SEARCH_TEXT_PADDING_X;
        int bottom = top + FIELD_HEIGHT;
        int border = this.searchBox.isFocused() ? 0xFFFFFFFF : 0xFF505050;
        int textColor = this.searchBox.getValue().isEmpty() ? 0xFF505050 : WHITE_TEXT_COLOR;
        guiGraphics.fill(left, top, right, bottom, 0xFF000000);
        this.drawBorder(guiGraphics, left, top, right, bottom, border);
        this.searchBox.setTextColor(textColor);
        this.searchBox.setTextColorUneditable(textColor);
    }

    private void renderDeathWaypointsToggle(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = this.deathWaypointsToggleX();
        int top = this.deathWaypointsToggleY();
        int right = left + CHECKBOX_SIZE;
        ResourceLocation texture = this.deathWaypointsToggleTexture(mouseX, mouseY);
        guiGraphics.blit(
            texture,
            left,
            top,
            CHECKBOX_SIZE,
            CHECKBOX_SIZE,
            0.0F,
            0.0F,
            CHECKBOX_TEXTURE_SIZE,
            CHECKBOX_TEXTURE_SIZE,
            CHECKBOX_TEXTURE_SIZE,
            CHECKBOX_TEXTURE_SIZE
        );

        guiGraphics.drawString(
            this.font,
            Component.translatable("gui.mappath.waypoints.show_death_waypoints"),
            right + CHECKBOX_LABEL_GAP,
            top + (CHECKBOX_SIZE - this.font.lineHeight) / 2,
            WHITE_TEXT_COLOR,
            false
        );
    }

    private ResourceLocation deathWaypointsToggleTexture(int mouseX, int mouseY) {
        boolean selected = MapPathConfig.CLIENT.showDeathWaypointsInWaypointList();
        boolean highlighted = this.isMouseOverDeathWaypointsToggle(mouseX, mouseY);
        if (selected) {
            return highlighted ? CHECKBOX_SELECTED_HIGHLIGHTED_TEXTURE : CHECKBOX_SELECTED_TEXTURE;
        }
        return highlighted ? CHECKBOX_HIGHLIGHTED_TEXTURE : CHECKBOX_TEXTURE;
    }

    private void renderWaypointPanel(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int left = this.contentPanelLeft();
        int top = this.contentPanelTop();
        int right = this.contentPanelRight();
        int titleY = this.compactLayout() ? top + 3 : top + 28;
        int headerY = this.compactLayout() ? top + 18 : top + 58;
        guiGraphics.drawCenteredString(this.font, this.title, left + this.contentPanelWidth() / 2, titleY, WHITE_TEXT_COLOR);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.icon"), this.iconColumnX(), headerY, WHITE_TEXT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.name"), this.nameColumnX(), headerY, WHITE_TEXT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.dimension"), this.dimensionColumnX(), headerY, WHITE_TEXT_COLOR, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.mappath.waypoints.distance"), this.distanceColumnX(), headerY, WHITE_TEXT_COLOR, false);

        if (this.visibleWaypoints.isEmpty()) {
            guiGraphics.drawCenteredString(this.font, Component.translatable("gui.mappath.waypoints.empty"), left + this.contentPanelWidth() / 2, this.rowsTop() + 24, MUTED_TEXT_COLOR);
        }

        int rowsEnd = Math.min(this.visibleWaypoints.size(), this.scrollOffset + this.visibleRows());
        for (int index = this.scrollOffset; index < rowsEnd; index++) {
            WaypointStore.DimensionWaypoint entry = this.visibleWaypoints.get(index);
            int y = this.rowsTop() + (index - this.scrollOffset) * ROW_HEIGHT;
            this.renderWaypointRow(guiGraphics, entry, y, mouseX, mouseY);
        }

        this.renderScrollbar(guiGraphics, mouseX, mouseY);
    }

    private void renderWaypointRow(GuiGraphics guiGraphics, WaypointStore.DimensionWaypoint entry, int y, int mouseX, int mouseY) {
        int left = this.contentPanelLeft();
        int right = this.rowsRight();
        int rowLeft = left + 8;
        int rowRight = right;
        int rowTop = y;
        int rowBottom = Math.min(y + ROW_HEIGHT - ROW_GAP, this.rowsBottom());
        guiGraphics.fill(rowLeft, rowTop, rowRight, rowBottom, ROW_BACKGROUND);
        if (this.isPendingDelete(entry)) {
            guiGraphics.fill(rowLeft, rowTop, rowRight, rowBottom, 0x333A1B1B);
        }
        if (this.key(entry).equals(this.selectedWaypointKey)) {
            this.drawBorder(guiGraphics, rowLeft, rowTop, rowRight, rowBottom, ROW_SELECTED_BORDER);
        }
        if (mouseY >= rowTop && mouseY < rowBottom && mouseX >= left && mouseX < right) {
            this.drawBorder(guiGraphics, rowLeft, rowTop, rowRight, rowBottom, ROW_HOVER_BORDER);
        }

        ResourceLocation texture = BANNER_ICON_TEXTURES.get(entry.waypoint().icon());
        if (texture != null) {
            guiGraphics.blit(texture, this.iconColumnX() + 8, rowTop + Math.max(1, (rowBottom - rowTop - ICON_SIZE) / 2), ICON_SIZE, ICON_SIZE, 0.0F, 0.0F, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
        }

        int textColor = this.isPendingDelete(entry) ? PENDING_TEXT_COLOR : TEXT_COLOR;
        int textY = rowTop + Math.max(1, (rowBottom - rowTop - this.font.lineHeight) / 2);
        guiGraphics.drawString(this.font, this.trim(entry.waypoint().name(), this.nameColumnWidth()), this.nameColumnX(), textY, textColor, false);
        guiGraphics.drawString(this.font, this.trim(entry.dimensionName(), this.dimensionColumnWidth()), this.dimensionColumnX(), textY, textColor, false);
        guiGraphics.drawString(this.font, this.distanceText(entry), this.distanceColumnX(), textY, textColor, false);
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int x = this.scrollbarX();
        int top = this.rowsTop();
        int bottom = this.rowsBottom();
        int trackHeight = bottom - top;
        int contentHeight = this.visibleWaypoints.size() * ROW_HEIGHT;
        int knobHeight = this.maxScrollOffset() <= 0 || contentHeight <= 0
            ? trackHeight
            : Mth.clamp((int)((float)(trackHeight * trackHeight) / (float)contentHeight), 32, trackHeight - 8);
        int maxKnobY = bottom - knobHeight;
        int knobY = this.maxScrollOffset() <= 0 ? top : top + (int)Math.round((trackHeight - knobHeight) * (this.scrollOffset / (double)this.maxScrollOffset()));
        knobY = Mth.clamp(knobY, top, maxKnobY);
        RenderSystem.enableBlend();
        guiGraphics.blitSprite(SCROLLER_BACKGROUND_SPRITE, x, top, SCROLLBAR_WIDTH, trackHeight);
        guiGraphics.blitSprite(SCROLLER_SPRITE, x, knobY, SCROLLBAR_WIDTH, knobHeight);
        RenderSystem.disableBlend();
    }

    private void renderPanel(GuiGraphics guiGraphics, int left, int top, int right, int bottom) {
        guiGraphics.fill(left, top, right, bottom, PANEL_BACKGROUND);
        guiGraphics.fill(left, top, right, top + 1, PANEL_BORDER);
        guiGraphics.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
        guiGraphics.fill(left, top, left + 1, bottom, PANEL_BORDER);
        guiGraphics.fill(right - 1, top, right, bottom, PANEL_BORDER);
    }

    private void drawBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(left, top, right, top + 1, color);
        guiGraphics.fill(left, bottom - 1, right, bottom, color);
        guiGraphics.fill(left, top, left + 1, bottom, color);
        guiGraphics.fill(right - 1, top, right, bottom, color);
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
        for (WaypointStore.DimensionWaypoint entry : this.allWaypoints) {
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

    private java.nio.file.Path waypointPathFor(ResourceLocation dimensionId) {
        java.nio.file.Path directory = MapTileStore.directoryFor(this.minecraft, dimensionId);
        return directory == null ? null : directory.resolve(WaypointStore.FILE_NAME);
    }

    private DimensionOption selectedDimension() {
        for (DimensionOption option : this.dimensionOptions) {
            if (option.key().equals(this.selectedDimensionKey)) {
                return option;
            }
        }
        return this.dimensionOptions.isEmpty() ? new DimensionOption("", Component.literal("-"), null, false) : this.dimensionOptions.get(0);
    }

    private Comparator<WaypointStore.DimensionWaypoint> comparatorForSortMode() {
        Comparator<WaypointStore.DimensionWaypoint> byName = Comparator
            .comparing((WaypointStore.DimensionWaypoint entry) -> entry.waypoint().name(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(WaypointStore.DimensionWaypoint::dimensionName, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.waypoint().id().toString());
        Comparator<WaypointStore.DimensionWaypoint> byDistance = Comparator
            .comparingLong(this::distanceSquaredToPlayer)
            .thenComparing(byName);

        Comparator<WaypointStore.DimensionWaypoint> comparator = switch (this.sortCategory) {
            case NAME -> byName;
            case DISTANCE -> byDistance;
        };
        return this.sortOrder == SortOrder.ASCENDING ? comparator : comparator.reversed();
    }

    private long distanceSquaredToPlayer(WaypointStore.DimensionWaypoint entry) {
        if (!entry.currentDimension() || this.minecraft == null || this.minecraft.player == null) {
            return Long.MAX_VALUE;
        }

        long dx = entry.waypoint().worldX() - Mth.floor(this.minecraft.player.getX());
        long dy = entry.waypoint().worldY() - Mth.floor(this.minecraft.player.getY());
        long dz = entry.waypoint().worldZ() - Mth.floor(this.minecraft.player.getZ());
        return dx * dx + dy * dy + dz * dz;
    }

    private String distanceText(WaypointStore.DimensionWaypoint entry) {
        if (!entry.currentDimension() || this.minecraft == null || this.minecraft.player == null) {
            return "-";
        }
        return Integer.toString(Mth.floor(Math.sqrt(this.distanceSquaredToPlayer(entry)))) + " m";
    }

    private String currentDimensionKey() {
        return this.minecraft != null && this.minecraft.level != null ? this.minecraft.level.dimension().location().toString() : "";
    }

    private boolean matchesSearch(WaypointStore.DimensionWaypoint entry, String query) {
        WaypointStore.Waypoint waypoint = entry.waypoint();
        return waypoint.name().toLowerCase(Locale.ROOT).contains(query)
            || entry.dimensionName().toLowerCase(Locale.ROOT).contains(query)
            || Integer.toString(waypoint.worldX()).contains(query)
            || Integer.toString(waypoint.worldY()).contains(query)
            || Integer.toString(waypoint.worldZ()).contains(query)
            || (waypoint.worldX() + " " + waypoint.worldY() + " " + waypoint.worldZ()).contains(query);
    }

    private String dimensionKey(WaypointStore.DimensionWaypoint entry) {
        return entry.dimensionId() != null ? entry.dimensionId().toString() : "path:" + entry.dimensionDirectoryName();
    }

    private boolean isPendingDelete(WaypointStore.DimensionWaypoint entry) {
        return this.pendingDeletedWaypoints.contains(this.key(entry));
    }

    private String key(WaypointStore.DimensionWaypoint entry) {
        return entry.path() + ":" + entry.waypoint().id();
    }

    private WaypointStore.DimensionWaypoint selectedWaypoint() {
        if (this.selectedWaypointKey.isEmpty()) {
            return null;
        }
        for (WaypointStore.DimensionWaypoint entry : this.visibleWaypoints) {
            if (this.key(entry).equals(this.selectedWaypointKey)) {
                return entry;
            }
        }
        return null;
    }

    private WaypointStore.DimensionWaypoint waypointAt(double mouseX, double mouseY) {
        if (mouseX < this.contentPanelLeft()
            || mouseX >= this.rowsRight()
            || mouseY < this.rowsTop()
            || mouseY >= this.rowsBottom()) {
            return null;
        }

        int visibleIndex = Mth.floor((mouseY - this.rowsTop()) / ROW_HEIGHT);
        int index = this.scrollOffset + visibleIndex;
        return index >= 0 && index < this.visibleWaypoints.size() ? this.visibleWaypoints.get(index) : null;
    }

    private void scrollToMouse(double mouseY) {
        int trackTop = this.rowsTop();
        int trackHeight = this.rowsBottom() - trackTop;
        double ratio = (mouseY - trackTop) / Math.max(1.0D, trackHeight);
        this.scrollOffset = Mth.clamp((int)Math.round(ratio * this.maxScrollOffset()), 0, this.maxScrollOffset());
        this.rebuildWidgets();
    }

    private boolean isMouseOverScrollbar(double mouseX, double mouseY) {
        return mouseX >= this.scrollbarX()
            && mouseX < this.scrollbarX() + SCROLLBAR_WIDTH
            && mouseY >= this.rowsTop()
            && mouseY < this.rowsBottom();
    }

    private boolean isMouseOverSearchBox(double mouseX, double mouseY) {
        if (this.searchBox == null) {
            return false;
        }

        int left = this.searchBox.getX() - SEARCH_TEXT_PADDING_X;
        int top = this.searchBox.getY() - (FIELD_HEIGHT - this.font.lineHeight) / 2 - SEARCH_TEXT_OFFSET_Y;
        return mouseX >= left
            && mouseX < this.searchBox.getX() + this.searchBox.getWidth() + SEARCH_TEXT_PADDING_X
            && mouseY >= top
            && mouseY < top + FIELD_HEIGHT;
    }

    private boolean isMouseOverDeathWaypointsToggle(double mouseX, double mouseY) {
        int left = this.deathWaypointsToggleX();
        int top = this.deathWaypointsToggleY();
        int labelWidth = this.font.width(Component.translatable("gui.mappath.waypoints.show_death_waypoints"));
        int right = left + CHECKBOX_SIZE + CHECKBOX_LABEL_GAP + labelWidth;
        return mouseX >= left
            && mouseX < right
            && mouseY >= top
            && mouseY < top + CHECKBOX_SIZE;
    }

    private int deathWaypointsToggleX() {
        return this.filterPanelLeft() + this.filterPadding();
    }

    private int deathWaypointsToggleY() {
        if (this.searchBox == null) {
            return this.filterPanelTop() + (this.compactLayout() ? 28 : 100);
        }

        int searchTop = this.searchBox.getY() - (FIELD_HEIGHT - this.font.lineHeight) / 2 - SEARCH_TEXT_OFFSET_Y;
        return searchTop + FIELD_HEIGHT + (this.compactLayout() ? 4 : 8);
    }

    private int rootLeft() {
        return this.horizontalMargin();
    }

    private int rootRight() {
        return this.width - this.horizontalMargin();
    }

    private int rootBottom() {
        return Math.max(this.contentPanelTop() + 44, this.height - this.verticalMargin() - ACTION_BAR_HEIGHT);
    }

    private int filterPanelLeft() {
        return this.rootLeft();
    }

    private int filterPanelTop() {
        return this.verticalMargin();
    }

    private int filterPanelRight() {
        return this.compactLayout() ? this.rootRight() : this.filterPanelLeft() + this.filterPanelWidth();
    }

    private int filterPanelBottom() {
        return this.compactLayout() ? this.filterPanelTop() + this.compactFilterPanelHeight() : this.rootBottom();
    }

    private int contentPanelLeft() {
        return this.compactLayout() ? this.rootLeft() : this.filterPanelRight() + GAP;
    }

    private int contentPanelTop() {
        return this.compactLayout() ? this.filterPanelBottom() + GAP : this.filterPanelTop();
    }

    private int contentPanelRight() {
        return this.rootRight();
    }

    private int contentPanelWidth() {
        return this.contentPanelRight() - this.contentPanelLeft();
    }

    private int filterPanelWidth() {
        if (this.compactLayout()) {
            return this.rootRight() - this.rootLeft();
        }

        return Mth.clamp((this.rootRight() - this.rootLeft() - GAP) / 3, 170, 220);
    }

    private int rowsTop() {
        return this.compactLayout() ? this.contentPanelTop() + 30 : this.contentPanelTop() + TITLE_HEIGHT + 50;
    }

    private int rowsBottom() {
        return this.rootBottom() - this.filterPadding();
    }

    private int visibleRows() {
        return Math.max(1, (this.rowsBottom() - this.rowsTop()) / ROW_HEIGHT);
    }

    private int maxScrollOffset() {
        return Math.max(0, this.visibleWaypoints.size() - this.visibleRows());
    }

    private int scrollbarX() {
        return this.contentPanelRight() - this.filterPadding() - SCROLLBAR_WIDTH;
    }

    private int rowsRight() {
        return this.scrollbarX() - GAP;
    }

    private int iconColumnX() {
        return this.contentPanelLeft() + (this.compactLayout() ? 8 : 16);
    }

    private int nameColumnX() {
        return this.contentPanelLeft() + (this.compactLayout() ? 38 : 56);
    }

    private int dimensionColumnX() {
        if (this.compactLayout()) {
            return this.contentPanelLeft() + Math.max(92, this.contentPanelWidth() * 45 / 100);
        }

        return this.contentPanelLeft() + Math.max(190, this.contentPanelWidth() / 3 + 30);
    }

    private int distanceColumnX() {
        return this.scrollbarX() - (this.compactLayout() ? 46 : 78);
    }

    private int nameColumnWidth() {
        return Math.max(70, this.dimensionColumnX() - this.nameColumnX() - 18);
    }

    private int dimensionColumnWidth() {
        return Math.max(70, this.distanceColumnX() - this.dimensionColumnX() - 18);
    }

    private int actionButtonY() {
        return this.rootBottom() + 6;
    }

    private int actionButtonsLeft(boolean pendingDelete) {
        int width = pendingDelete
            ? this.restoreButtonWidth() + this.deletePermanentlyButtonWidth() + BUTTON_GAP
            : this.addButtonWidth() + this.editButtonWidth() + this.teleportButtonWidth() + this.deleteButtonWidth() + BUTTON_GAP * 3;
        return (this.width - width) / 2;
    }

    private boolean compactLayout() {
        return this.width < 640 || this.height < 360;
    }

    private int horizontalMargin() {
        return this.compactLayout() ? 4 : Math.min(ROOT_MARGIN_X, Math.max(16, this.width / 14));
    }

    private int verticalMargin() {
        return this.compactLayout() ? 4 : Math.min(ROOT_MARGIN_Y, Math.max(12, this.height / 18));
    }

    private int filterPadding() {
        return this.compactLayout() ? 6 : FILTER_PADDING;
    }

    private int compactFilterPanelHeight() {
        return 78;
    }

    private int filterControlStep() {
        int available = this.rootBottom() - this.filterPanelTop() - 112;
        return Mth.clamp(available / 3, 34, 46);
    }

    private int compactActionWidth(int preferred, int slotCount) {
        if (!this.compactLayout()) {
            return preferred;
        }

        int available = Math.max(64, this.rootRight() - this.rootLeft() - BUTTON_GAP * (slotCount - 1));
        return Math.max(34, available / slotCount);
    }

    private int addButtonWidth() {
        return this.compactActionWidth(ADD_BUTTON_WIDTH, 4);
    }

    private int editButtonWidth() {
        return this.compactActionWidth(EDIT_BUTTON_WIDTH, 4);
    }

    private int teleportButtonWidth() {
        return this.compactActionWidth(TELEPORT_BUTTON_WIDTH, 4);
    }

    private int deleteButtonWidth() {
        return this.compactActionWidth(DELETE_BUTTON_WIDTH, 4);
    }

    private int restoreButtonWidth() {
        return this.compactActionWidth(RESTORE_BUTTON_WIDTH, 2);
    }

    private int deletePermanentlyButtonWidth() {
        return this.compactActionWidth(DELETE_PERMANENTLY_BUTTON_WIDTH, 2);
    }

    private String trim(String text, int maxWidth) {
        if (this.font.width(text) <= maxWidth) {
            return text;
        }

        return this.font.plainSubstrByWidth(text, Math.max(0, maxWidth - this.font.width("..."))) + "...";
    }

    private static Map<BannerIconType, ResourceLocation> createBannerIconTextures() {
        Map<BannerIconType, ResourceLocation> textures = new EnumMap<>(BannerIconType.class);
        for (BannerIconType type : BannerIconType.values()) {
            textures.put(type, ResourceLocation.fromNamespaceAndPath(MapPath.MODID, type.texturePath()));
        }

        return textures;
    }

    private record DimensionOption(String key, Component label, java.nio.file.Path path, boolean currentDimension) implements DropdownEntry {
        private static final String ALL_KEY = "";

        @Override
        public Component displayName() {
            return this.label;
        }
    }

    private enum SortCategory implements DropdownEntry {
        NAME(Component.translatable("gui.mappath.waypoints.sort_name")),
        DISTANCE(Component.translatable("gui.mappath.waypoints.sort_distance"));

        private final Component label;

        SortCategory(Component label) {
            this.label = label;
        }

        @Override
        public Component displayName() {
            return this.label;
        }
    }

    private enum SortOrder implements DropdownEntry {
        ASCENDING(Component.translatable("gui.mappath.waypoints.sort_ascending")),
        DESCENDING(Component.translatable("gui.mappath.waypoints.sort_descending"));

        private final Component label;

        SortOrder(Component label) {
            this.label = label;
        }

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
            guiGraphics.drawString(font, text, this.x + 6, this.y + 6, 0xFFFFFFFF, true);
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
            int menuBottom = menuTop + visibleOptions * optionHeight;
            int innerMenuTop = menuTop + 4;
            int innerMenuBottom = menuBottom - 4;
            boolean scrollable = this.maxScrollOffset() > 0;
            int textRight = this.x + this.width - (scrollable ? MENU_SCROLLBAR_WIDTH + 6 : 6);

            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0.0F, 0.0F, MENU_Z_OFFSET);
            guiGraphics.fill(this.x, menuTop, this.x + this.width, menuBottom, MENU_BACKGROUND_COLOR);
            this.drawMenuBorder(guiGraphics, this.x, menuTop, this.x + this.width, menuBottom);

            for (int visibleIndex = 0; visibleIndex < visibleOptions; visibleIndex++) {
                int index = this.scrollOffset + visibleIndex;
                T option = this.options.get(index);
                int optionTop = menuTop + visibleIndex * optionHeight;
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

                int color = selectedOption ? 0xFFFFFFFF : 0xFFE8E8E8;
                guiGraphics.drawString(font, font.plainSubstrByWidth(option.displayName().getString(), textRight - this.x - 12), this.x + 8, optionTop + (optionHeight - font.lineHeight) / 2, color, false);
            }

            if (scrollable) {
                int scrollbarX = this.x + this.width - MENU_SCROLLBAR_WIDTH - 4;
                int trackTop = menuTop + 4;
                int trackBottom = menuBottom - 4;
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
            if (mouseX >= this.x && mouseX < this.x + this.width && mouseY >= menuTop && mouseY < menuTop + visibleOptions * optionHeight) {
                int index = this.scrollOffset + (int)((mouseY - menuTop) / optionHeight);
                this.open = false;
                T option = this.options.get(index);
                if (!Objects.equals(option, this.selected)) {
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
            if (mouseX < this.x || mouseX >= this.x + this.width || mouseY < menuTop || mouseY >= menuTop + visibleOptions * this.menuOptionHeight()) {
                return false;
            }

            this.scrollOffset = Mth.clamp(this.scrollOffset - (int)Math.signum(scrollY), 0, this.maxScrollOffset());
            return true;
        }

        private void close() {
            this.open = false;
        }

        private int y() {
            return this.y;
        }

        private int maxScrollOffset() {
            return Math.max(0, this.options.size() - MAX_VISIBLE_OPTIONS);
        }

        private int menuOptionHeight() {
            return Math.min(this.height, MENU_OPTION_HEIGHT);
        }
    }
}
