package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiIconElement;
import com.mamiyaotaru.voxelmap.util.MobPreset;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;

class GuiSlotMobPresets extends AbstractSelectionList<GuiSlotMobPresets.BasicItem> {
    private final GuiMobs parentGui;
    private final ArrayList<BasicItem> presets;

    GuiSlotMobPresets(GuiMobs parent) {
        super(VoxelConstants.getMinecraft(), parent.getWidth(), parent.getHeight() - 86, 24, 18);

        this.parentGui = parent;
        this.presets = new ArrayList<>();

        this.presets.add(new HeaderItem(this.parentGui, I18n.get("options.minimap.mobs.presets.header.voxelMapPresets")));
        this.presets.add(new PresetItem(this.parentGui, I18n.get("options.minimap.mobs.presets.dangerousMobs"), MobPreset.DANGEROUS_MOBS));
        this.presets.add(new PresetItem(this.parentGui, I18n.get("options.minimap.mobs.presets.nonDangerousMobs"), MobPreset.NON_DANGEROUS_MOBS));

        this.presets.add(new HeaderItem(this.parentGui, I18n.get("options.minimap.mobs.presets.header.defaultPresets")));
        this.presets.add(new PresetItem(this.parentGui, I18n.get("options.minimap.mobs.presets.hostileMobs"), MobPreset.HOSTILE_MOBS));
        this.presets.add(new PresetItem(this.parentGui, I18n.get("options.minimap.mobs.presets.neutralMobs"), MobPreset.NEUTRAL_MOBS));
        this.presets.add(new PresetItem(this.parentGui, I18n.get("options.minimap.mobs.presets.friendlyMobs"), MobPreset.FRIENDLY_MOBS));

        this.presets.forEach(this::addEntry);
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
            int textLeft = (GuiSlotMobPresets.this.getWidth() - centerSpacing) / 2;
            int textRight = (GuiSlotMobPresets.this.getWidth() + centerSpacing) / 2;

            drawContext.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, left, this.getY() + 8, 0.0F, 0.0F, textLeft - left, 2, 32, 2);
            drawContext.blit(RenderPipelines.GUI_TEXTURED, Screen.HEADER_SEPARATOR, textRight, this.getY() + 8, 0.0F, 0.0F, right - textRight, 2, 32, 2);

            drawContext.drawCenteredString(this.parentGui.getFont(), this.name, this.parentGui.getWidth() / 2, this.getY() + 5, 0xFFFFFFFF);
        }
    }

    public class PresetItem extends BasicItem {
        private final GuiMobs parentGui;
        private final String name;
        private final HashSet<Identifier> entities;
        private final GuiIconElement presetToggle;

        protected PresetItem(GuiMobs parent, String name, MobPreset preset) {
            this.parentGui = parent;
            this.name = name;
            this.entities = MobPreset.getMatchingEntities(preset);
            this.presetToggle = new GuiIconElement(this.getX() + this.getWidth() - 20, this.getY(), 18, 18, true, element -> {});
        }

        @Override
        public void renderContent(GuiGraphics drawContext, int mouseX, int mouseY, boolean hovered, float tickDelta) {
            drawContext.drawString(this.parentGui.getFont(), this.name, this.getX() + 4, this.getY() + 5, 0xFFFFFFFF);

            boolean isEnabled = Collections.disjoint(this.entities, this.parentGui.options.hiddenMobs);
            this.presetToggle.setPosition(this.getX() + this.getWidth() - 20, this.getY());
            this.presetToggle.setIconForRender(RenderPipelines.GUI_TEXTURED, isEnabled ? VoxelConstants.getCheckMarkerTexture() : VoxelConstants.getCrossMarkerTexture(), 0xFFFFFFFF);
            this.presetToggle.render(drawContext, mouseX, mouseY, tickDelta);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent mouseButtonEvent, boolean doubleClick) {
            double mouseX = mouseButtonEvent.x();
            double mouseY = mouseButtonEvent.y();
            if (mouseY < GuiSlotMobPresets.this.getY() || mouseY > GuiSlotMobPresets.this.getBottom()) {
                return false;
            }

            GuiSlotMobPresets.this.setSelected(this);

            this.presetToggle.mouseClicked(mouseButtonEvent, doubleClick);

            return true;
        }
    }
}
