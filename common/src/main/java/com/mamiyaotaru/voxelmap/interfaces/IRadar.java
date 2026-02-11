package com.mamiyaotaru.voxelmap.interfaces;

import com.mamiyaotaru.voxelmap.util.LayoutVariables;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.packs.resources.ResourceManager;

public interface IRadar {
    void onResourceManagerReload(ResourceManager resourceManager);

    void onTickInGame(GuiGraphics guiGraphics, LayoutVariables layoutVariables, float scaleProj);

    void renderBelowFrame(GuiGraphics guiGraphics, int x, int y, float scaleProj);

    void renderAboveFrame(GuiGraphics guiGraphics, int x, int y, float scaleProj);
}
