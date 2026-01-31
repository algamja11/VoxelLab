package com.mamiyaotaru.voxelmap.util;

import com.mamiyaotaru.voxelmap.VoxelConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.HashMap;
import java.util.HashSet;

public enum MobPreset {
    DANGEROUS_MOBS,
    NON_DANGEROUS_MOBS,
    HOSTILE_MOBS,
    NEUTRAL_MOBS,
    FRIENDLY_MOBS;

    private static final HashSet<Identifier> ALL_ENTITIES = new HashSet<>();
    private static final HashMap<MobPreset, HashSet<Identifier>> MATCHING_ENTITIES = new HashMap<>();
    private static final HashMap<MobPreset, HashSet<Identifier>> MISMATCHING_ENTITIES = new HashMap<>();

    private static void init() {
        if (!ALL_ENTITIES.isEmpty()) {
            return;
        }

        for (MobPreset preset : MobPreset.values()) {
            MATCHING_ENTITIES.put(preset, new HashSet<>());
        }

        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(entry -> {
            Identifier identifier = entry.getKey().identifier();
            Entity tempEntity = entry.getValue().create(VoxelConstants.getMinecraft().level, EntitySpawnReason.LOAD);

            if (tempEntity instanceof LivingEntity) {
                ALL_ENTITIES.add(identifier);

                for (MobPreset preset : MobPreset.values()) {
                    if (matchesPreset(tempEntity, preset)) {
                        MATCHING_ENTITIES.get(preset).add(identifier);
                    }
                }
            }
        });

        for (MobPreset preset : MobPreset.values()) {
            HashSet<Identifier> mismatch = new HashSet<>(ALL_ENTITIES);
            mismatch.removeAll(MATCHING_ENTITIES.get(preset));

            MISMATCHING_ENTITIES.put(preset, mismatch);
        }
    }

    public static HashSet<Identifier> getMatchingEntities(MobPreset preset) {
        init();
        return new HashSet<>(MATCHING_ENTITIES.get(preset));
    }

    public static HashSet<Identifier> getMismatchingEntities(MobPreset preset) {
        init();
        return new HashSet<>(MISMATCHING_ENTITIES.get(preset));
    }


    private static boolean matchesPreset(Entity entity, MobPreset preset) {
        return switch (preset) {
            case DANGEROUS_MOBS -> entity instanceof Enemy || MobCategory.isHostile(entity);
            case NON_DANGEROUS_MOBS -> (!(entity instanceof Enemy) && !MobCategory.isHostile(entity));
            case HOSTILE_MOBS -> MobCategory.isHostile(entity);
            case NEUTRAL_MOBS -> MobCategory.isNeutral(entity);
            case FRIENDLY_MOBS -> MobCategory.isFriendly(entity);
//            default -> false;
        };
    }
}
