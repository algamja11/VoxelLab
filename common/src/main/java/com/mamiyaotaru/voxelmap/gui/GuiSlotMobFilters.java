package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.VoxelMap;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiIconElement;
import com.mamiyaotaru.voxelmap.util.MobFilter;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;

import java.util.ArrayList;

class GuiSlotMobFilters extends AbstractSelectionList<GuiSlotMobFilters.BasicItem> {
    private final GuiMobs parentGui;
    private final ArrayList<BasicItem> filters;

    GuiSlotMobFilters(GuiMobs parent) {
        super(VoxelConstants.getMinecraft(), parent.getWidth(), parent.getHeight() - 86, 24, 18);

        this.parentGui = parent;
        this.filters = new ArrayList<>();

        this.filters.add(new FilterItem(this.parentGui, I18n.get("options.minimap.mobs.filters.allMobs"), MobFilter.ALL_MOBS));

        this.filters.add(new HeaderItem(this.parentGui, I18n.get("options.minimap.mobs.filters.header.voxelMap")));
        this.filters.add(new FilterItem(this.parentGui, I18n.get("options.minimap.mobs.filters.dangerousMobs"), MobFilter.DANGEROUS_MOBS));
        this.filters.add(new FilterItem(this.parentGui, I18n.get("options.minimap.mobs.filters.nonDangerousMobs"), MobFilter.NON_DANGEROUS_MOBS));

        this.filters.add(new HeaderItem(this.parentGui, I18n.get("options.minimap.mobs.filters.header.default")));
        this.filters.add(new FilterItem(this.parentGui, I18n.get("options.minimap.mobs.filters.hostileMobs"), MobFilter.HOSTILE_MOBS));
        this.filters.add(new FilterItem(this.parentGui, I18n.get("options.minimap.mobs.filters.neutralMobs"), MobFilter.NEUTRAL_MOBS));
        this.filters.add(new FilterItem(this.parentGui, I18n.get("options.minimap.mobs.filters.friendlyMobs"), MobFilter.FRIENDLY_MOBS));

        this.filters.forEach(this::addEntry);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    @Override
    protected void renderListSeparators(GuiGraphics guiGraphics) {
    }

    @Override
    protected void renderListBackground(GuiGraphics guiGraphics) {
    }

    public class BasicItem extends Entry<BasicItem> {
        @Override
        public void renderContent(GuiGraphics drawContext, int mouseX, int mouseY, boolean hovered, float tickDelta) {
        }
    }

    public class HeaderItem extends BasicItem {
        private final GuiMobs parentGui;
        private final String name;

        protected HeaderItem(GuiMobs parent, String name) {
            this.parentGui = parent;
            this.name = name;
        }

        @Override
        public void renderContent(GuiGraphics drawContext, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            int centerSpacing = this.name.isEmpty() ? 0 : this.parentGui.getFont().width(this.name) + 6;
            int left = this.getX() - 5;
            int right = this.getX() + this.getWidth() + 5;
            int textLeft = (GuiSlotMobFilters.this.getWidth() - centerSpacing) / 2;
            int textRight = (GuiSlotMobFilters.this.getWidth() + centerSpacing) / 2;

            drawContext.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, left, this.getY() + 8, 0.0F, 0.0F, textLeft - left, 2, 32, 2);
            drawContext.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, textRight, this.getY() + 8, 0.0F, 0.0F, right - textRight, 2, 32, 2);

            drawContext.drawCenteredString(this.parentGui.getFont(), this.name, this.parentGui.getWidth() / 2, this.getY() + 5, 0xFFFFFFFF);
        }
    }

    public class FilterItem extends BasicItem {
        private final GuiMobs parentGui;
        private final String name;
        private final MobFilter filter;
        private final GuiIconElement filterToggle;

        protected FilterItem(GuiMobs parent, String name, MobFilter filter) {
            this.parentGui = parent;
            this.name = name;
            this.filter = filter;
            this.filterToggle = new GuiIconElement(this.getX() + this.getWidth() - 20, this.getY(), 18, 18, true, element -> this.parentGui.setMobFilter(this.filter));
        }

        @Override
        public void renderContent(GuiGraphics drawContext, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            drawContext.drawString(this.parentGui.getFont(), this.name, this.getX() + 4, this.getY() + 5, 0xFFFFFFFF);

            boolean isEnabled = VoxelMap.radarOptions.mobFilter == this.filter;

            this.filterToggle.setPosition(this.getX() + this.getWidth() - 20, this.getY());
            this.filterToggle.setIconForRender(RenderPipelines.GUI_TEXTURED, isEnabled ? VoxelConstants.getCheckMarkerTexture() : VoxelConstants.getCrossMarkerTexture(), 0xFFFFFFFF);
            this.filterToggle.render(drawContext, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
            double mouseX = mouseButtonEvent.x();
            double mouseY = mouseButtonEvent.y();
            if (mouseY < GuiSlotMobFilters.this.getY() || mouseY > GuiSlotMobFilters.this.getBottom()) {
                return false;
            }

            GuiSlotMobFilters.this.setSelected(this);

            this.filterToggle.mouseClicked(mouseButtonEvent, doubleClick);

            return true;
        }
    }
}
