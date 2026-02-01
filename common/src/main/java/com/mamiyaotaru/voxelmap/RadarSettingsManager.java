package com.mamiyaotaru.voxelmap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mamiyaotaru.voxelmap.gui.overridden.EnumOptionsMinimap;
import com.mamiyaotaru.voxelmap.interfaces.ISubSettingsManager;
import com.mamiyaotaru.voxelmap.util.MobFilter;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RadarSettingsManager implements ISubSettingsManager {
    private boolean somethingChanged;
    public int radarMode = 2;
    public boolean showRadar = true;
    public boolean showPlayers = true;
    public boolean showPlayerNames = true;
    public boolean showMobs = true;
    public boolean showMobNames = true;
    public boolean outlines = true;
    public boolean filtering = true;
    public boolean showHelmetsPlayers = true;
    public boolean showHelmetsMobs = true;
    public boolean showFacing = true;
    public boolean radarAllowed = true;
    public boolean radarPlayersAllowed = true;
    public boolean radarMobsAllowed = true;
    public MobFilter mobFilter = MobFilter.DANGEROUS_MOBS;
    public final ConcurrentHashMap<Identifier, Boolean> overriddenMobs = new ConcurrentHashMap<>();

    float fontScale = 1.0F;
    private final Gson gson = new Gson();

    @Override
    public void loadSettings(File settingsFile) {
        try {
            BufferedReader in = new BufferedReader(new FileReader(settingsFile));

            String sCurrentLine;
            while ((sCurrentLine = in.readLine()) != null) {
                String[] curLine = sCurrentLine.split(":", 2);
                switch (curLine[0]) {
                    case "Radar Mode" -> this.radarMode = Math.max(1, Math.min(2, Integer.parseInt(curLine[1])));
                    case "Show Radar" -> this.showRadar = Boolean.parseBoolean(curLine[1]);
                    case "Show Mobs" -> this.showMobs = Boolean.parseBoolean(curLine[1]);
                    case "Show Mob Helmets" -> this.showHelmetsMobs = Boolean.parseBoolean(curLine[1]);
                    case "Show Mob Names" -> this.showMobNames = Boolean.parseBoolean(curLine[1]);
                    case "Show Players" -> this.showPlayers = Boolean.parseBoolean(curLine[1]);
                    case "Show Player Helmets" -> this.showHelmetsPlayers = Boolean.parseBoolean(curLine[1]);
                    case "Show Player Names" -> this.showPlayerNames = Boolean.parseBoolean(curLine[1]);
                    case "Filter Mob Icons" -> this.filtering = Boolean.parseBoolean(curLine[1]);
                    case "Outline Mob Icons" -> this.outlines = Boolean.parseBoolean(curLine[1]);
                    case "Font Scale" -> this.fontScale = Float.parseFloat(curLine[1]);
                    case "Show Facing" -> this.showFacing = Boolean.parseBoolean(curLine[1]);
                    case "Mob Filter" -> this.mobFilter = Enum.valueOf(MobFilter.class, curLine[1]);
                    case "Overridden Mobs" -> this.readOverriddenMobs(curLine[1]);
                }
            }

            in.close();
        } catch (IOException | ArrayIndexOutOfBoundsException ignored) {
        }

    }

    private void readOverriddenMobs(String list) {
        Type typeToken = new TypeToken<Map<String, Boolean>>(){}.getType();
        Map<String, Boolean> stringMap = this.gson.fromJson(list, typeToken);

        this.overriddenMobs.clear();
        stringMap.forEach((mobId, value) -> {
            Identifier.read(mobId).ifSuccess(identifier -> {
                this.overriddenMobs.put(identifier, value);
            });
        });
    }

    private String writeOverriddenMobs() {
        Map<String, Boolean> stringMap = new HashMap<>();
        overriddenMobs.forEach((mobId, value) -> {
            stringMap.put(mobId.toString(), value);
        });

        return this.gson.toJson(stringMap);
    }

    @Override
    public void saveAll(PrintWriter out) {
        out.println("Radar Mode:" + this.radarMode);
        out.println("Show Radar:" + this.showRadar);
        out.println("Show Mobs:" + this.showMobs);
        out.println("Show Mob Helmets:" + this.showHelmetsMobs);
        out.println("Show Mob Names:" + this.showMobNames);
        out.println("Show Players:" + this.showPlayers);
        out.println("Show Player Helmets:" + this.showHelmetsPlayers);
        out.println("Show Player Names:" + this.showPlayerNames);
        out.println("Filter Mob Icons:" + this.filtering);
        out.println("Outline Mob Icons:" + this.outlines);
        out.println("Font Scale:" + this.fontScale);
        out.println("Show Facing:" + this.showFacing);
        out.println("Mob Filter:" + this.mobFilter);
        out.println("Overridden Mobs:" + this.writeOverriddenMobs());
    }

    @Override
    public String getKeyText(EnumOptionsMinimap options) {
        String s = I18n.get(options.getName()) + ": ";
        if (options.isBoolean()) {
            return this.getOptionBooleanValue(options) ? s + I18n.get("options.on") : s + I18n.get("options.off");
        } else if (options.isList()) {
            String state = this.getOptionListValue(options);
            return s + state;
        } else {
            return s;
        }
    }

    public boolean getOptionBooleanValue(EnumOptionsMinimap par1EnumOptions) {
        return switch (par1EnumOptions) {
            case SHOW_RADAR -> this.showRadar;
            case SHOW_MOBS -> this.showMobs;
            case SHOW_MOB_HELMETS -> this.showHelmetsMobs;
            case SHOW_MOB_NAMES -> this.showMobNames;
            case SHOW_PLAYERS -> this.showPlayers;
            case SHOW_PLAYER_HELMETS -> this.showHelmetsPlayers;
            case SHOW_PLAYER_NAMES -> this.showPlayerNames;
            case RADAR_OUTLINES -> this.outlines;
            case RADAR_FILTERING -> this.filtering;
            case SHOW_FACING -> this.showFacing;
            default -> throw new IllegalArgumentException("Add code to handle EnumOptionMinimap: " + par1EnumOptions.getName() + ". (possibly not a boolean)");
        };
    }

    public String getOptionListValue(EnumOptionsMinimap par1EnumOptions) {
        switch (par1EnumOptions) {
            case RADAR_MODE -> {
                if (this.radarMode == 2) {
                    return I18n.get("options.minimap.radar.radarMode.full");
                }

                return I18n.get("options.minimap.radar.radarMode.simple");
            }
        }
        throw new IllegalArgumentException("Add code to handle EnumOptionMinimap: " + par1EnumOptions.getName() + ". (possibly not a list value applicable to minimap)");
    }

    @Override
    public void setOptionFloatValue(EnumOptionsMinimap options, float value) {
    }

    public void setOptionValue(EnumOptionsMinimap par1EnumOptions) {
        switch (par1EnumOptions) {
            case SHOW_RADAR -> this.showRadar = !this.showRadar;
            case SHOW_MOBS -> this.showMobs = !this.showMobs;
            case SHOW_MOB_HELMETS -> this.showHelmetsMobs = !this.showHelmetsMobs;
            case SHOW_MOB_NAMES -> this.showMobNames = !this.showMobNames;
            case SHOW_PLAYERS -> this.showPlayers = !this.showPlayers;
            case SHOW_PLAYER_HELMETS -> this.showHelmetsPlayers = !this.showHelmetsPlayers;
            case SHOW_PLAYER_NAMES -> this.showPlayerNames = !this.showPlayerNames;
            case RADAR_OUTLINES -> this.outlines = !this.outlines;
            case RADAR_FILTERING -> this.filtering = !this.filtering;
            case SHOW_FACING -> this.showFacing = !this.showFacing;
            case RADAR_MODE -> {
                if (this.radarMode == 2) {
                    this.radarMode = 1;
                } else {
                    this.radarMode = 2;
                }
            }
            default -> throw new IllegalArgumentException("Add code to handle EnumOptionMinimap: " + par1EnumOptions.getName());
        }

        this.somethingChanged = true;
    }

    public boolean isChanged() {
        if (this.somethingChanged) {
            this.somethingChanged = false;
            return true;
        } else {
            return false;
        }
    }

    @Override
    public float getOptionFloatValue(EnumOptionsMinimap options) {
        return 0.0F;
    }

    public boolean isMobEnabled(LivingEntity entity) {
        return isMobEnabled(entity.getType());
    }

    public boolean isMobEnabled(EntityType<?> type) {
        return isMobEnabled(BuiltInRegistries.ENTITY_TYPE.getKey(type));
    }

    public boolean isMobEnabled(Identifier identifier) {
        Boolean override = overriddenMobs.get(identifier);
        if (override != null) {
            return override;
        }

        return MobFilter.matchesFilter(identifier, mobFilter);
    }

    public void setMobFilter(MobFilter filter) {
        if (mobFilter != filter) {
            mobFilter = filter;
            overriddenMobs.clear();
        }
    }

    public void setMobEnabled(LivingEntity entity, boolean enabled) {
        setMobEnabled(entity.getType(), enabled);
    }

    public void setMobEnabled(EntityType<?> type, boolean enabled) {
        setMobEnabled(BuiltInRegistries.ENTITY_TYPE.getKey(type), enabled);
    }

    public void setMobEnabled(Identifier identifier, boolean enabled) {
        if (enabled == MobFilter.matchesFilter(identifier, mobFilter)) {
            overriddenMobs.remove(identifier);
            return;
        }

        overriddenMobs.put(identifier, enabled);
    }
}
