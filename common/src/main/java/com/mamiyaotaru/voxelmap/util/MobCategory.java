package com.mamiyaotaru.voxelmap.util;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.polarbear.PolarBear;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.Enemy;

public enum MobCategory {
    PLAYER,
    HOSTILE,
    NEUTRAL,
    FRIENDLY,
//  Tameable mobs can be friendly, neutral or hostile depending on their current state.
//    TAMEABLE,
    UNKNOWN;

    public static MobCategory forEntity(Entity entity) {
        if (!(entity instanceof LivingEntity)) {
            return UNKNOWN;
        }

        if (isPlayer(entity)) {
            return PLAYER;
        } else if (isHostile(entity)) {
            return HOSTILE;
        } else if (isNeutral(entity)) {
            return NEUTRAL;
        } else if (isFriendly(entity)) {
            return FRIENDLY;
        }

        return UNKNOWN;
    }

    public static MobCategory forEntityType(EntityType<?> entityType) {
        if (VoxelConstants.getMinecraft().level == null) {
            return UNKNOWN;
        }

        Entity entity = entityType.create(VoxelConstants.getMinecraft().level, EntitySpawnReason.LOAD);

        return forEntity(entity);
    }

    public static boolean isPlayer(Entity entity) {
        return entity instanceof RemotePlayer;
    }

    public static boolean isHostile(Entity entity) {
        switch (entity) {
            case PolarBear polarBear -> {
                for (PolarBear object : polarBear.level().getEntitiesOfClass(PolarBear.class, polarBear.getBoundingBox().inflate(8.0, 4.0, 8.0))) {
                    if (object.isBaby()) {
                        return true;
                    }
                }

                return false;
            }
            case Rabbit rabbit -> {
                return rabbit.getVariant() == Rabbit.Variant.EVIL;
            }
            case NeutralMob neutralMob -> {
                return neutralMob.getPersistentAngerTarget() != null && neutralMob.getPersistentAngerTarget().getUUID().equals(VoxelConstants.getPlayer().getUUID());
            }
            case Enemy enemy -> {
                return true;
            }
            default -> {}
        }

        return false;
    }

    public static boolean isNeutral(Entity entity) {
        return !isHostile(entity) && (entity instanceof NeutralMob);
    }

    public static boolean isFriendly(Entity entity) {
        return !isPlayer(entity) && !isNeutral(entity) && !isHostile(entity);
    }

    public static boolean isOwnable(Entity entity) {
        return entity instanceof OwnableEntity;
    }
}
