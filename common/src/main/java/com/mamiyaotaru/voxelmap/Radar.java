package com.mamiyaotaru.voxelmap;

import com.mamiyaotaru.voxelmap.entityrender.EntityMapImageManager;
import com.mamiyaotaru.voxelmap.interfaces.IRadar;
import com.mamiyaotaru.voxelmap.textures.Sprite;
import com.mamiyaotaru.voxelmap.util.Contact;
import com.mamiyaotaru.voxelmap.util.GameVariableAccessShim;
import com.mamiyaotaru.voxelmap.util.LayoutVariables;
import com.mamiyaotaru.voxelmap.util.TextUtils;
import com.mamiyaotaru.voxelmap.util.VoxelMapRenderer;
import com.mamiyaotaru.voxelmap.util.VoxelMapMobCategory;
import com.mamiyaotaru.voxelmap.util.VoxelMapPipelines;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;

import java.util.ArrayList;

public class Radar implements IRadar {
    private final MapSettingsManager minimapOptions;
    private final RadarSettingsManager options;
    private final ArrayList<Contact> contacts = new ArrayList<>(40);
    private final EntityMapImageManager entityMapImageManager;
    private final Minecraft minecraft = Minecraft.getInstance();

    private LayoutVariables layoutVariables;
    private double lastX;
    private double lastY;
    private double lastZ;

    private int timer = 500;
    private float direction;
    private boolean lastOutlines = true;
    private int calculateMobsPart;

    public Radar() {
        entityMapImageManager = new EntityMapImageManager();
        this.minimapOptions = VoxelConstants.getVoxelMapInstance().getMapOptions();
        this.options = VoxelConstants.getVoxelMapInstance().getRadarOptions();
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        entityMapImageManager.reset();
    }

    @Override
    public void onTickInGame(GuiGraphics drawContext, LayoutVariables layoutVariables, float scaleProj) {
        entityMapImageManager.onRenderTick(drawContext);
        if (this.options.radarAllowed || this.options.radarMobsAllowed || this.options.radarPlayersAllowed) {
            this.layoutVariables = layoutVariables;
            if (this.options.isChanged()) {
                this.timer = 500;
                if (this.options.outlines != this.lastOutlines) {
                    this.lastOutlines = this.options.outlines;
//                    entityMapImageManager.reset();
                }
            }

            this.direction = GameVariableAccessShim.rotationYaw() + 180.0F;

            while (this.direction >= 360.0F) {
                this.direction -= 360.0F;
            }

            while (this.direction < 0.0F) {
                this.direction += 360.0F;
            }

            if (this.timer > 15) {
                // long t0 = System.nanoTime();
                this.calculateMobs();
                // long t1 = System.nanoTime();
                // VoxelConstants.getLogger().info("Calculate Mobs " + calculateMobsPart + " took " + ((t1 - t0) / 1000) + " micros");
                this.timer = 0;
            }

            this.lastX = GameVariableAccessShim.xCoordDouble();
            this.lastY = GameVariableAccessShim.yCoordDouble();
            this.lastZ = GameVariableAccessShim.zCoordDouble();

            ++this.timer;
            this.updateMobs();
        }
    }

    private boolean isEntityShown(Entity entity) {
        if (entity.isInvisibleTo(VoxelConstants.getPlayer()) || entity.equals(VoxelConstants.getPlayer()) || !(entity instanceof LivingEntity)) {
            return false;
        }

        boolean playersAllowed = this.options.radarAllowed || this.options.radarPlayersAllowed;
        boolean mobsAllowed = this.options.radarAllowed || this.options.radarMobsAllowed;

        return switch (VoxelMapMobCategory.forEntity(entity)) {
            case PLAYER -> playersAllowed;
            case HOSTILE -> mobsAllowed && this.options.showHostiles;
            case NEUTRAL -> mobsAllowed && this.options.showNeutrals;
        };
    }

    private float getEntityMaxHeight(Entity entity) {
        if (entity.getType() == EntityType.PHANTOM) {
            return 64.0F;
        }

        return 32.0F;
    }

    private float getIconVerticalOffset(Entity entity) {
        if (entity.getVehicle() != null && isEntityShown(entity.getVehicle())) {
            return -4.0F;
        }

        return 0.0F;
    }

    private boolean canRenderAboveFrame(Entity entity) {
        return entity.getType() == EntityType.PLAYER;
    }

    private boolean isInRange(Entity entity, double dx, double dy, double dz, double cullDist) {
        double scale = layoutVariables.zoomScaleAdjusted;
        dx /= scale;
        dy /= scale;
        dz /= scale;

        if (Math.abs(dy) > getEntityMaxHeight(entity) + cullDist) {
            return false;
        }

        double maxDist = 32.0 + cullDist;
        if (!minimapOptions.squareMap) {
            return (dx * dx + dz * dz) <= (maxDist * maxDist);
        } else {
            return Math.abs(dx) <= maxDist && Math.abs(dz) <= maxDist;
        }
    }

    public void calculateMobs() {
        calculateMobsPart = (calculateMobsPart + 1) & 7;
        this.contacts.removeIf(e -> (e.uuid.getLeastSignificantBits() & 7) == calculateMobsPart);
        // this.contacts.clear();

        Iterable<Entity> entities = VoxelConstants.getClientWorld().entitiesForRendering();

        for (Entity entity : entities) {
            if ((entity.getUUID().getLeastSignificantBits() & 7) != calculateMobsPart) {
                continue;
            }
            try {
                if (this.isEntityShown(entity)) {
                    int wayX = GameVariableAccessShim.xCoord() - (int) entity.position().x();
                    int wayZ = GameVariableAccessShim.zCoord() - (int) entity.position().z();
                    int wayY = GameVariableAccessShim.yCoord() - (int) entity.position().y();

                    if (this.canRenderAboveFrame(entity) || this.isInRange(entity, wayX, wayY, wayZ, 5.0)) {
                        Contact contact = new Contact((LivingEntity) entity, VoxelMapMobCategory.forEntity(entity));
                        if (contact.entity.getVehicle() != null && this.isEntityShown(contact.entity.getVehicle())) {
                            contact.yFudge = 1;
                        }
                        if (VoxelMap.radarOptions.isMobEnabled(contact.entity)) {
                            if (contact.icon == null) {
                                contact.icon = entityMapImageManager.requestImageForMob(contact.entity, 32, options.outlines);
                            }

                            String scrubbedName = TextUtils.scrubCodes(contact.entity.getName().getString());
                            if ((scrubbedName.equals("Dinnerbone") || scrubbedName.equals("Grumm")) && (!(contact.entity instanceof Player) || ((Player) contact.entity).isModelPartShown(PlayerModelPart.CAPE))) {
                                contact.setRotationFactor(contact.rotationFactor + 180);
                            }

                            if (this.options.showHelmetsPlayers && contact.category == VoxelMapMobCategory.PLAYER || this.options.showHelmetsMobs && contact.category != VoxelMapMobCategory.PLAYER) {
                                contact.armorIcon = entityMapImageManager.requestImageForArmor(contact.entity, 32, options.outlines);
                            }

                            this.contacts.add(contact);
                        }
                    }
                }
            } catch (Exception var16) {
                VoxelConstants.getLogger().error(var16.getLocalizedMessage(), var16);
            }
        }

        this.contacts.sort((c1, c2) -> {
            double dy = c1.y - c2.y;
            if (dy != 0) {
                return dy > 0 ? 1 : -1;
            }
            double dx = c1.x - c2.x;
            if (dx != 0) {
                return dx > 0 ? 1 : -1;
            }
            double dz = c1.z - c2.z;
            if (dz != 0) {
                return dz > 0 ? 1 : -1;
            }
            return 0;
        });
    }

    private void updateMobs() {
        for (Contact contact : this.contacts) {
            if (contact.icon == null) {
                contact.enabled = false;
                continue;
            }

            contact.updateLocation();

            double wayX = lastX - contact.x;
            double wayY = lastY - contact.y;
            double wayZ = lastZ - contact.z;

            if (!isInRange(contact.entity, wayX, wayY, wayZ, 0.0)) {
                contact.enabled = false;
                continue;
            }

            contact.enabled = true;

            double maxHeight = getEntityMaxHeight(contact.entity) * layoutVariables.zoomScaleAdjusted;
            double heightDiff = maxHeight - Math.max(0.0, Math.abs(wayY));
            float brightness = (float) Math.max(0.0, heightDiff / maxHeight);
            brightness *= brightness;

            contact.distance = Math.sqrt(wayX * wayX + wayZ * wayZ);
            contact.angle = (float) (Math.toDegrees(Math.atan2(wayX, wayZ)));
            if (this.minimapOptions.rotates) {
                contact.angle += this.direction;
            } else if (this.minimapOptions.oldNorth) {
                contact.angle -= 90.0F;
            }
            if (wayY < 0) {
                contact.color = ARGB.colorFromFloat(brightness, 1.0F, 1.0f, 1.0F);
            } else {
                float brightness2 = Math.max(0.3F, brightness);
                contact.color = ARGB.colorFromFloat(1.0F, brightness2, brightness2, brightness2);
            }
        }
    }

    @Override
    public void renderBelowFrame(GuiGraphics guiGraphics, int x, int y, float scaleProj) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scaleProj, scaleProj);

        VoxelMapRenderer.beginBatch(VertexFormat.Mode.QUADS, VoxelMapPipelines.GUI_TEXTURED_MASKED_NO_DEPTH_TEST);
        VoxelMapRenderer.bindTexture(EntityMapImageManager.resourceTextureAtlasMarker);

        for (Contact contact : this.contacts) {
            if (contact.enabled) {
                try {
                    guiGraphics.pose().pushMatrix();
                    double scaledDistance = contact.distance / layoutVariables.zoomScaleAdjusted;
                    float iconX = (float) (Math.sin(Math.toRadians(contact.angle)) * scaledDistance);
                    float iconY = (float) (Math.cos(Math.toRadians(contact.angle)) * scaledDistance);
                    iconY += getIconVerticalOffset(contact.entity);

                    drawMobIcon(guiGraphics, contact, x, y, iconX, iconY, scaleProj, false);
                } catch (Exception e) {
                    VoxelConstants.getLogger().error("Error rendering mob icon! " + e.getLocalizedMessage() + " contact type " + BuiltInRegistries.ENTITY_TYPE.getKey(contact.entity.getType()), e);
                } finally {
                    guiGraphics.pose().popMatrix();
                }
            }
        }
        VoxelMapRenderer.endBatch();

        guiGraphics.pose().popMatrix();
    }

    @Override
    public void renderAboveFrame(GuiGraphics guiGraphics, int x, int y, float scaleProj) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(scaleProj, scaleProj);

        for (Contact contact : this.contacts) {
            if (contact.enabled) {
                try {
                    guiGraphics.pose().pushMatrix();
                    double scaledDistance = contact.distance / layoutVariables.zoomScaleAdjusted;
                    float iconX = (float) (Math.sin(Math.toRadians(contact.angle)) * scaledDistance);
                    float iconY = (float) (Math.cos(Math.toRadians(contact.angle)) * scaledDistance);
                    iconY += getIconVerticalOffset(contact.entity);
//
//                    drawMobIcon(guiGraphics, contact, x, y, iconX, iconY, scaleProj, true);

                    if (scaledDistance <= 28.5 && contact.name != null && ((this.options.showPlayerNames && contact.category == VoxelMapMobCategory.PLAYER) || (this.options.showMobNames && contact.category != VoxelMapMobCategory.PLAYER))) {
                        float scale = this.options.fontScale / 4.0F;
                        guiGraphics.pose().pushMatrix();
                        guiGraphics.pose().scale(scale, scale);

                        int m = minecraft.font.width(contact.name) / 2;
                        guiGraphics.drawString(minecraft.font, contact.name, (int) ((x - iconX) / scale - m), (int) ((y - iconY + 3) / scale), 0xFFFFFFFF, false);

                        guiGraphics.pose().popMatrix();
                    }
                } catch (Exception e) {
                    VoxelConstants.getLogger().error("Error rendering mob icon! " + e.getLocalizedMessage() + " contact type " + BuiltInRegistries.ENTITY_TYPE.getKey(contact.entity.getType()), e);
                } finally {
                    guiGraphics.pose().popMatrix();
                }
            }
        }

        guiGraphics.pose().popMatrix();
    }

    private void drawMobIcon(GuiGraphics guiGraphics, Contact contact, int mapX, int mapY, float iconX, float iconY, float scaleProj, boolean aboveFrame) {
        int color = contact.color;

        float imageWidth = contact.icon.getIconWidth() / 8.0F;
        float imageHeight = contact.icon.getIconHeight() / 8.0F;
        if (!aboveFrame) {
            drawSpriteQuad(guiGraphics, contact.icon, -iconX - (imageWidth / 2), iconY - (imageHeight / 2), -2500, imageWidth, imageHeight, color, scaleProj);
        } else {
            contact.icon.blit(guiGraphics, RenderPipelines.GUI_TEXTURED, mapX - iconX - (imageWidth / 2), mapY - iconY - (imageHeight / 2), imageWidth, imageHeight, color);
        }

        if (contact.armorIcon != null) {
            float helmetWidth = contact.armorIcon.getIconWidth() / 8.0F;
            float helmetHeight = contact.armorIcon.getIconHeight() / 8.0F;
            float helmetOffset = Float.parseFloat(this.entityMapImageManager.getMobProperties(contact.entity).getProperty("helmetOffset", "0.0"));
            if (!aboveFrame) {
                drawSpriteQuad(guiGraphics, contact.armorIcon, -iconX - (helmetWidth / 2), iconY + helmetOffset - (helmetHeight / 2), -2500, helmetWidth, helmetHeight, color, scaleProj);
            } else {
                contact.armorIcon.blit(guiGraphics, RenderPipelines.GUI_TEXTURED, mapX - iconX - (helmetWidth / 2), mapY - iconY + helmetOffset - (helmetHeight / 2), helmetWidth, helmetWidth, color);
            }
        }
    }

    private void drawSpriteQuad(GuiGraphics guiGraphics, Sprite sprite, float x, float y, float z, float width, float height, int color, float scaleProj) {
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().scale(512.0F / 64.0F / scaleProj, 512.0F / 64.0F / scaleProj);

        float uMin = sprite.getMinU();
        float uMax = sprite.getMaxU();
        float vMin = sprite.getMinV();
        float vMax = sprite.getMaxV();

        VoxelMapRenderer.addVertex(guiGraphics.pose(), x, y + height, z).setUv(uMin, vMin).setColor(color);
        VoxelMapRenderer.addVertex(guiGraphics.pose(), x + width, y + height, z).setUv(uMax, vMin).setColor(color);
        VoxelMapRenderer.addVertex(guiGraphics.pose(), x + width, y, z).setUv(uMax, vMax).setColor(color);
        VoxelMapRenderer.addVertex(guiGraphics.pose(), x, y, z).setUv(uMin, vMax).setColor(color);

        guiGraphics.pose().popMatrix();
    }

    public void onJoinServer() {
        entityMapImageManager.reset();
    }

    public EntityMapImageManager getEntityMapImageManager() {
        return entityMapImageManager;
    }
}
