package com.mamiyaotaru.voxelmap.gui;

import com.mamiyaotaru.voxelmap.MapSettingsManager;
import com.mamiyaotaru.voxelmap.VoxelConstants;
import com.mamiyaotaru.voxelmap.gui.overridden.GuiScreenMinimap;
import com.mamiyaotaru.voxelmap.gui.settings.EntityTypeDialog;
import com.mamiyaotaru.voxelmap.gui.settings.SettingsCategory;
import com.mamiyaotaru.voxelmap.gui.settings.SettingsListWidget;
import com.mamiyaotaru.voxelmap.gui.settings.SettingsOption;
import com.mamiyaotaru.voxelmap.gui.settings.VoxelMapSettings;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.PreeditEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class GuiMinimapOptions extends GuiScreenMinimap {
    private static final int HEADER_HEIGHT = 24;
    private static final int FOOTER_HEIGHT = 32;
    private static final int MIN_TAB_WIDTH = 80;
    private static final int TAB_SIDE_OFFSET = 28;

    private final List<SettingsCategory> categories = VoxelMapSettings.create(this::openEntityTypeDialog);
    private final List<Button> categoryButtons = new ArrayList<>();
    private int selectedCategory;
    private int tabWidth;
    private int tabScroll;
    private int tabMinScroll;
    private int tabMaxScroll;
    private SettingsListWidget optionList;
    private EntityTypeDialog entityTypeDialog;

    public GuiMinimapOptions(Screen parent) {
        this(parent, "minimap");
    }

    public GuiMinimapOptions(Screen parent, String initialCategory) {
        super();
        this.lastScreen = parent;
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).id().equals(initialCategory)) {
                selectedCategory = i;
                break;
            }
        }
    }

    @Override
    protected void init() {
        boolean reopenEntityDialog = entityTypeDialog != null;
        entityTypeDialog = null;
        clearWidgets();
        categoryButtons.clear();

        tabWidth = Math.max(MIN_TAB_WIDTH, (getWidth() - TAB_SIDE_OFFSET * 2) / categories.size());
        tabMinScroll = -TAB_SIDE_OFFSET;
        tabMaxScroll = Math.max(tabMinScroll, tabWidth * categories.size() - getWidth() + TAB_SIDE_OFFSET);
        tabScroll = tabMinScroll;

        for (int i = 0; i < categories.size(); i++) {
            int index = i;
            Button button = new TabButton(categories.get(i).title(), 0 , 0, tabWidth, HEADER_HEIGHT, ignored -> selectCategory(index));
            categoryButtons.add(addRenderableWidget(button));
        }
        updateCategoryButtons();

        addRenderableWidget(Button.builder(Component.translatable("gui.done"), ignored -> onClose())
                .bounds(width / 2 - 100, height - 27, 200, 20).build());
        if (!VoxelConstants.isSinglePlayer()) {
            String currentServer = VoxelConstants.getVoxelMapInstance().getWaypointManager().getServerName();
            addRenderableWidget(Button.builder(Component.translatable("voxelmap.alias.editButton"),
                    ignored -> this.minecraft.gui.setScreen(new GuiServerAliases(this, currentServer)))
                    .bounds(10, height - 27, 150, 20).build());
        }
        rebuildContent();
        if (reopenEntityDialog)
            openEntityTypeDialog();
    }

    private void selectCategory(int index) {
        if (index == selectedCategory)
            return;
        selectedCategory = index;
        rebuildContent();
    }

    public void rebuildContent() {
        if (optionList != null) {
            optionList.commitPendingText();
            removeWidget(optionList);
        }
        optionList = new SettingsListWidget(this, 0, HEADER_HEIGHT, getWidth(), getHeight() - HEADER_HEIGHT - FOOTER_HEIGHT, categories.get(selectedCategory));
        addRenderableWidget(optionList);
        updateCategoryButtons();
        reorderCategoryButtons();
    }

    public void cycleChoice(SettingsOption<?> option) {
        if (option.choices().isEmpty())
            return;
        int currentIndex = 0;
        for (int i = 0; i < option.choices().size(); i++) {
            if (option.choices().get(i).value().equals(option.value())) {
                currentIndex = i;
                break;
            }
        }
        setChoice(option, (currentIndex + 1) % option.choices().size());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (entityTypeDialog != null) {
            if (event.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                closeEntityTypeDialog();
                return true;
            }
            return entityTypeDialog.keyPressed(event);
        }
        if (optionList != null && optionList.isEditingKey()) {
            return optionList.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        return entityTypeDialog != null ? entityTypeDialog.keyReleased(event) : super.keyReleased(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return entityTypeDialog != null ? entityTypeDialog.charTyped(event) : super.charTyped(event);
    }

    @Override
    public boolean preeditUpdated(PreeditEvent event) {
        return entityTypeDialog != null ? entityTypeDialog.preeditUpdated(event) : super.preeditUpdated(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (entityTypeDialog != null)
            entityTypeDialog.mouseMoved(mouseX, mouseY);
        else
            super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (entityTypeDialog != null) {
            entityTypeDialog.mouseClicked(event, doubleClick);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (entityTypeDialog != null) {
            entityTypeDialog.mouseReleased(event);
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (entityTypeDialog != null) {
            entityTypeDialog.mouseDragged(event, deltaX, deltaY);
            return true;
        }
        return super.mouseDragged(event, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (entityTypeDialog != null) {
            entityTypeDialog.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
            return true;
        }
        if (mouseY <= HEADER_HEIGHT) {
            tabScroll = Math.max(tabMinScroll, Math.min(tabMaxScroll, tabScroll + (int) Math.signum(verticalAmount) * 14));
            updateCategoryButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private void openEntityTypeDialog() {
        if (entityTypeDialog != null)
            return;
        entityTypeDialog = new EntityTypeDialog(this, this::closeEntityTypeDialog);
        addRenderableWidget(entityTypeDialog);
        setFocused(entityTypeDialog);
    }

    private void closeEntityTypeDialog() {
        if (entityTypeDialog == null)
            return;
        removeWidget(entityTypeDialog);
        entityTypeDialog = null;
        setFocused(optionList);
    }

    @SuppressWarnings("unchecked")
    private static <T> void setChoiceUnchecked(SettingsOption<T> option, int action) {
        option.set(option.choices().get(action).value());
    }

    private static void setChoice(SettingsOption<?> option, int action) {
        setChoiceUnchecked(option, action);
    }

    private void updateCategoryButtons() {
        for (int i = 0; i < categoryButtons.size(); i++) {
            Button button = categoryButtons.get(i);
            button.active = i != selectedCategory;
            button.setPosition(i * tabWidth - tabScroll, 0);
        }
    }

    private void reorderCategoryButtons() {
        // Make category tabs always on top
        for (Button button : categoryButtons) {
            removeWidget(button);
            addRenderableWidget(button);
        }
    }


    @Override
    public void onClose() {
        if (entityTypeDialog != null) {
            closeEntityTypeDialog();
            return;
        }
        if (optionList != null)
            optionList.commitPendingText();
        super.onClose();
    }

    @Override
    public void removed() {
        if (optionList != null)
            optionList.commitPendingText();
        MapSettingsManager.instance.saveAll();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        super.extractRenderState(graphics, mouseX, mouseY, delta);
    }

    class TabButton extends Button {
        private static final Identifier DEFAULT = Identifier.withDefaultNamespace("widget/tab");
        private static final Identifier DEFAULT_HOVER = Identifier.withDefaultNamespace("widget/tab_highlighted");
        private static final Identifier SELECTED = Identifier.withDefaultNamespace("widget/tab_selected");
        private static final Identifier SELECTED_HOVER = Identifier.withDefaultNamespace("widget/tab_selected_highlighted");

        public TabButton(Component title, int x, int y, int width, int height, OnPress onPress) {
            super(x, y, width, height, title, onPress, DEFAULT_NARRATION);
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            // inactive = selected
            Identifier sprite = active ? (isHoveredOrFocused() ? DEFAULT_HOVER : DEFAULT) : (isHoveredOrFocused() ? SELECTED_HOVER : SELECTED);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, getX(), getY(), getWidth(), getHeight(), 0XFFFFFFFF);

            graphics.centeredText(getFont(), getMessage().getString(), getX() + getWidth() / 2, getY() + getHeight() / 2 + (active ? -2 : -4), 0xFFFFFFFF);

            if (!active) {
                int textWidth = getFont().width(getMessage());
                graphics.fill(getX() + (getWidth() - textWidth) / 2, getY() + getHeight() - 2, getX() + (getWidth() + textWidth) / 2, getY() + getHeight() - 1, 0xFFFFFFFF);
            }
        }
    }

}
