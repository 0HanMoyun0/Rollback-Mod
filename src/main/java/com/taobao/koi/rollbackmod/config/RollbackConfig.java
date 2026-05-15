package com.taobao.koi.rollbackmod.config;

import net.minecraftforge.common.ForgeConfigSpec;

public final class RollbackConfig {
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLE_DEATH_ROLLBACK;
    public static final ForgeConfigSpec.BooleanValue DESTROY_ALL_CORES_ON_ROLLBACK;
    public static final ForgeConfigSpec.BooleanValue SAVE_ON_FIRST_WORLD_LOAD;

    public static final ForgeConfigSpec.BooleanValue ROLLBACK_ALL_PLAYERS_ON_DEATH;
    public static final ForgeConfigSpec.BooleanValue SYNC_INVENTORY_AND_HEALTH;

    public static final ForgeConfigSpec.IntValue CHRONOS_DURATION_TICKS;
    public static final ForgeConfigSpec.ConfigValue<String> CHRONOS_KEY_MODE;
    public static final ForgeConfigSpec.DoubleValue CHRONOS_SLOW_TIME_FACTOR;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_TIME_CLOCK_MOD;
    public static final ForgeConfigSpec.ConfigValue<String> TIME_CLOCK_MOD_ID;

    public static final ForgeConfigSpec.IntValue STASIS_DURATION_TICKS;

    public static final ForgeConfigSpec.BooleanValue ONLY_ONE_MARK_PER_PLAYER;
    public static final ForgeConfigSpec.BooleanValue MARK_LOST_WHEN_TARGET_DEAD;

    public static final ForgeConfigSpec.BooleanValue MYRIAD_REQUIRES_WEAPON_TO_PREVENT_DEATH;

    public static final ForgeConfigSpec.BooleanValue SHOW_DAY_HUD;
    public static final ForgeConfigSpec.BooleanValue SHOW_DAY_TRANSITION;
    public static final ForgeConfigSpec.BooleanValue COUNTDOWN_MODE;
    public static final ForgeConfigSpec.IntValue COUNTDOWN_DAYS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("rollback");
        ENABLE_DEATH_ROLLBACK = builder.define("enable_death_rollback", true);
        DESTROY_ALL_CORES_ON_ROLLBACK = builder.define("destroy_all_cores_on_rollback", true);
        SAVE_ON_FIRST_WORLD_LOAD = builder.define("save_on_first_world_load", true);
        builder.pop();

        builder.push("multiplayer");
        ROLLBACK_ALL_PLAYERS_ON_DEATH = builder.define("rollback_all_players_on_death", true);
        SYNC_INVENTORY_AND_HEALTH = builder.define("sync_inventory_and_health", true);
        builder.pop();

        builder.push("chronos");
        CHRONOS_DURATION_TICKS = builder.defineInRange("chronos_duration_ticks", 1200, 1, 20 * 60 * 60);
        CHRONOS_KEY_MODE = builder.define("chronos_key_mode", "hold", RollbackConfig::isValidChronosKeyMode);
        CHRONOS_SLOW_TIME_FACTOR = builder.defineInRange("chronos_slow_time_factor", 0.5D, 0.01D, 1.0D);
        REQUIRE_TIME_CLOCK_MOD = builder.define("require_time_clock_mod", true);
        TIME_CLOCK_MOD_ID = builder.comment("Configured mod id for the external Time Clock mod integration.")
                .define("time_clock_mod_id", "timeclock");
        builder.pop();

        builder.push("stasis");
        STASIS_DURATION_TICKS = builder.defineInRange("stasis_duration_ticks", 100, 1, 20 * 60 * 60);
        builder.pop();

        builder.push("mark");
        ONLY_ONE_MARK_PER_PLAYER = builder.define("only_one_mark_per_player", true);
        MARK_LOST_WHEN_TARGET_DEAD = builder.define("mark_lost_when_target_dead", true);
        builder.pop();

        builder.push("myriad");
        MYRIAD_REQUIRES_WEAPON_TO_PREVENT_DEATH = builder.define("myriad_requires_weapon_to_prevent_death", false);
        builder.pop();

        builder.push("day_display");
        SHOW_DAY_HUD = builder.comment("Show a small persistent day label in the top-left corner.")
                .define("show_day_hud", false);
        SHOW_DAY_TRANSITION = builder.comment("Show a fullscreen black day reminder when the displayed day changes.")
                .define("show_day_transition", true);
        COUNTDOWN_MODE = builder.comment("false: show current world day. true: show days remaining and trigger tower-like fatal rollback at zero.")
                .define("countdown_mode", false);
        COUNTDOWN_DAYS = builder.defineInRange("countdown_days", 7, 1, 100000);
        builder.pop();

        SPEC = builder.build();
    }

    private RollbackConfig() {
    }

    public static boolean isChronosToggleMode() {
        return "toggle".equalsIgnoreCase(CHRONOS_KEY_MODE.get());
    }

    private static boolean isValidChronosKeyMode(Object value) {
        if (!(value instanceof String mode)) {
            return false;
        }
        return "hold".equalsIgnoreCase(mode) || "toggle".equalsIgnoreCase(mode);
    }
}
