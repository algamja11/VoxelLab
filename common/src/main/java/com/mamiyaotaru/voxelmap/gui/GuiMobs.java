package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.RadarSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiSimpleTab;
import com.mamiyaotaru.voxelmap.util.MobFilter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;

public class GuiMobs extends GuiScreenMinimap {
    protected final RadarSettingsManager options;
    protected Component screenTitle;
    private Component tooltip;
    protected Identifier selectedMobId;

    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget);
    private TabNavigationBar tabNavigationBar;

    private int tabIndex = 0;
    private int lastTabIndex = 0;

    private AbstractSelectionList<?> currentList;
    private GuiSlotMobs mobsList;
    private GuiSlotMobFilters presetsList;
    protected EditBox filter;
    private Button buttonEnable;
    private Button buttonDisable;
    private final ArrayList<AbstractWidget> tabWidgets = new ArrayList<>();

    public GuiMobs(Screen parentScreen, RadarSettingsManager options) {
        this.lastScreen = parentScreen;
        this.options = options;
    }

    @Override
    public void tick() {
    }

    @Override
    public void init() {
        this.screenTitle = Component.translatable("options.minimap.mobs.title");

        this.tabNavigationBar = TabNavigationBar.builder(this.tabManager, this.width).addTabs(new Tab[] {
                new GuiSimpleTab(Component.translatable("options.minimap.mobs.tab.toggleMobs"), 0),
                new GuiSimpleTab(Component.translatable("options.minimap.mobs.tab.filters"), 1)}).build();

        this.tabNavigationBar.setFocused(true);
        this.tabNavigationBar.selectTab(this.tabIndex, false);
        this.tabNavigationBar.arrangeElements();
        this.addRenderableWidget(this.tabNavigationBar);

        int tabBottom = this.tabNavigationBar.getRectangle().bottom();

        this.mobsList = new GuiSlotMobs(this);
        this.presetsList = new GuiSlotMobFilters(this);
        this.currentList = this.mobsList;

        ScreenRectangle tabAreaRect = new ScreenRectangle(0, tabBottom, this.width, this.currentList.getY() + this.currentList.getHeight());
        this.tabManager.setTabArea(tabAreaRect);

        int filterStringWidth = this.getFont().width(I18n.get("minimap.waypoints.filter") + ":");
        this.filter = new EditBox(this.getFont(), this.getWidth() / 2 - 153 + filterStringWidth + 5, this.getHeight() - 56, 305 - filterStringWidth - 5, 20, Component.empty());
        this.filter.setMaxLength(35);
        this.setFocused(this.filter);
        this.addRenderableWidget(this.filter);

        boolean isSomethingSelected = this.selectedMobId != null;

        this.buttonEnable = new Button.Builder(Component.translatable("options.minimap.mobs.enable"), button -> this.setMobEnabled(this.selectedMobId, true)).bounds(this.getWidth() / 2 - 154, this.getHeight() - 28, 100, 20).build();
        this.buttonEnable.active = isSomethingSelected;
        this.addRenderableWidget(this.buttonEnable);

        this.buttonDisable = new Button.Builder(Component.translatable("options.minimap.mobs.disable"), button -> this.setMobEnabled(this.selectedMobId, false)).bounds(this.getWidth() / 2 - 50, this.getHeight() - 28, 100, 20).build();
        this.buttonDisable.active = isSomethingSelected;
        this.addRenderableWidget(this.buttonDisable);

        Button doneButton = new Button.Builder(Component.translatable("gui.done"), button -> this.onClose()).bounds(this.getWidth() / 2 + 4 + 50, this.getHeight() - 28, 100, 20).build();
        this.addRenderableWidget(doneButton);

        this.lastTabIndex = -1;
        this.replaceElements();

    }

    private void replaceElements() {
        for (AbstractWidget widget : this.tabWidgets) {
            this.removeWidget(widget);
        }
        this.tabWidgets.clear();

        if (this.tabIndex == this.lastTabIndex) {
            return;
        }
        this.lastTabIndex = this.tabIndex;

        boolean isMobsTab = this.tabIndex == 0;

        this.selectedMobId = null;
        this.currentList = isMobsTab ? this.mobsList : this.presetsList;
        this.addTabWidget(this.currentList);

        this.filter.setValue("");
        this.filter.active = isMobsTab;
        this.updateListFilter("");
    }

    private void addTabWidget(AbstractWidget widget) {
        this.tabWidgets.add(widget);
        this.addRenderableWidget(widget);
    }

    private void checkTabSwitch() {
        if (this.tabManager.getCurrentTab() instanceof GuiSimpleTab tab) {
            if (tab.tabIndex() != this.tabIndex) {
                this.tabIndex = tab.tabIndex();
                this.replaceElements();
            }
        }
    }

    private void updateListFilter(String filter) {
        if (this.currentList == this.mobsList) {
            this.mobsList.updateFilter(filter);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        boolean OK = super.keyPressed(keyEvent);
        this.checkTabSwitch();
        if (this.filter.isFocused()) {
            this.updateListFilter(this.filter.getValue().toLowerCase());
        }

        return OK;
    }

    @Override
    public boolean charTyped(CharacterEvent characterEvent) {
        boolean OK = super.charTyped(characterEvent);
        if (this.filter.isFocused()) {
            this.updateListFilter(this.filter.getValue().toLowerCase());
        }

        return OK;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
        boolean clicked = super.mouseClicked(mouseButtonEvent, doubleClick);
        this.checkTabSwitch();

        return clicked;
    }

    protected void setSelectedMob(Identifier id) {
        this.selectedMobId = id;
    }

    private boolean isMobEnabled(Identifier mobId) {
        return !VoxelMap.radarOptions.hiddenMobs.contains(mobId);
    }

    private void setMobEnabled(Identifier mobId, boolean enabled) {
        if (enabled) {
            VoxelMap.radarOptions.hiddenMobs.remove(mobId);
        } else {
            VoxelMap.radarOptions.hiddenMobs.add(mobId);
        }
    }

    protected void toggleMobVisibility() {
        setMobEnabled(selectedMobId, !isMobEnabled(selectedMobId));
    }

    protected void setMobFilter(MobFilter filter) {
        VoxelMap.radarOptions.mobFilter = filter;
    }

    @Override
    public void renderMenuBackground(GuiGraphics drawContext) {
        drawContext.blit(RenderPipelines.GUI_TEXTURED, CreateWorldScreen.TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, this.width, this.currentList.getY(), 16, 16);
        this.renderMenuBackground(drawContext, 0, this.currentList.getY(), this.width, this.height);
    }

    @Override
    public void render(GuiGraphics drawContext, int mouseX, int mouseY, float delta) {
        this.tooltip = null;

        super.render(drawContext, mouseX, mouseY, delta);

        drawContext.blit(RenderPipelines.GUI_TEXTURED, Screen.FOOTER_SEPARATOR, 0, this.currentList.getY() + this.currentList.getHeight(), 0.0F, 0.0F, this.width, 2, 32, 2);

        boolean isSomethingSelected = this.selectedMobId != null;
        this.buttonEnable.active = isSomethingSelected && !this.isMobEnabled(this.selectedMobId);
        this.buttonDisable.active = isSomethingSelected && this.isMobEnabled(this.selectedMobId);

        drawContext.drawString(this.getFont(), I18n.get("minimap.waypoints.filter") + ":", this.getWidth() / 2 - 153, this.getHeight() - 51, 0xFFA0A0A0);

        if (this.tooltip != null) {
            this.renderTooltip(drawContext, this.tooltip, mouseX, mouseY);
        }

    }

    static void setTooltip(GuiMobs par0GuiWaypoints, Component par1Str) {
        par0GuiWaypoints.tooltip = par1Str;
    }
}
