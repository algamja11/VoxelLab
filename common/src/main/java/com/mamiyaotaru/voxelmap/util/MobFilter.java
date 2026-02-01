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

public enum MobFilter {
    ALL_MOBS,
    DANGEROUS_MOBS,
    NON_DANGEROUS_MOBS,
    HOSTILE_MOBS,
    NEUTRAL_MOBS,
    FRIENDLY_MOBS;

    private static final HashSet<Identifier> ALL_ENTITIES = new HashSet<>();
    private static final HashMap<MobFilter, HashSet<Identifier>> MATCHING_ENTITIES = new HashMap<>();
    private static final HashMap<MobFilter, HashSet<Identifier>> MISMATCHING_ENTITIES = new HashMap<>();

    private static void init() {
        if (!ALL_ENTITIES.isEmpty()) {
            return;
        }

        for (MobFilter filter : MobFilter.values()) {
            MATCHING_ENTITIES.put(filter, new HashSet<>());
        }

        BuiltInRegistries.ENTITY_TYPE.entrySet().forEach(entry -> {
            Identifier identifier = entry.getKey().identifier();
            Entity tempEntity = entry.getValue().create(VoxelConstants.getMinecraft().level, EntitySpawnReason.LOAD);

            if (tempEntity instanceof LivingEntity) {
                ALL_ENTITIES.add(identifier);

                for (MobFilter filter : MobFilter.values()) {
                    if (matchesFilter(tempEntity, filter)) {
                        MATCHING_ENTITIES.get(filter).add(identifier);
                    }
                }
            }
        });

        for (MobFilter filter : MobFilter.values()) {
            HashSet<Identifier> mismatch = new HashSet<>(ALL_ENTITIES);
            mismatch.removeAll(MATCHING_ENTITIES.get(filter));

            MISMATCHING_ENTITIES.put(filter, mismatch);
        }
    }

    public static HashSet<Identifier> getMatchingEntities(MobFilter filter) {
        init();
        return new HashSet<>(MATCHING_ENTITIES.get(filter));
    }

    public static HashSet<Identifier> getMismatchingEntities(MobFilter filter) {
        init();
        return new HashSet<>(MISMATCHING_ENTITIES.get(filter));
    }

    public static boolean matchesFilter(Identifier identifier, MobFilter filter) {
        init();
        return MATCHING_ENTITIES.get(filter).contains(identifier);
    }

    public static boolean matchesFilter(Entity entity, MobFilter filter) {
        return switch (filter) {
            case ALL_MOBS -> true;
            case DANGEROUS_MOBS -> entity instanceof Enemy || MobCategory.isHostile(entity);
            case NON_DANGEROUS_MOBS -> (!(entity instanceof Enemy) && !MobCategory.isHostile(entity));
            case HOSTILE_MOBS -> MobCategory.isHostile(entity);
            case NEUTRAL_MOBS -> MobCategory.isNeutral(entity);
            case FRIENDLY_MOBS -> MobCategory.isFriendly(entity);
//            default -> false;
        };
    }
}
