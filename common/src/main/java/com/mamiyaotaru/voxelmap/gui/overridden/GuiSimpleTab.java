package com.mamiyaotaru.voxelmap.gui.overridden;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

public record GuiSimpleTab(Component title, int tabIndex) implements Tab {
    @Override
    public Component getTabTitle() {
        return title;
    }

    @Override
    public Component getTabExtraNarration() {
        return Component.empty();
    }

    @Override
    public void visitChildren(Consumer<AbstractWidget> consumer) {

    }

    @Override
    public void doLayout(ScreenRectangle screenRectangle) {

    }
}
