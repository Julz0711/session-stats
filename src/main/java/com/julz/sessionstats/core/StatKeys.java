package com.julz.sessionstats.core;

/**
 * Stats are stored as {@code "<stat type id>/<value id>"}, for example
 * {@code "minecraft:mined/minecraft:diamond_ore"} or {@code "minecraft:custom/minecraft:walk_one_cm"}.
 * Plain strings keep the core free of Minecraft classes and the session files readable.
 */
public final class StatKeys {
    public static final String MINED = "minecraft:mined";
    public static final String CUSTOM = "minecraft:custom";
    public static final String KILLED = "minecraft:killed";
    public static final String USED = "minecraft:used";
    /** Not a vanilla stat type: uses of block items, which vanilla counts when a block is placed. */
    public static final String PLACED = "session-stats:placed";

    public static final String MOB_KILLS = custom("minecraft:mob_kills");
    public static final String DEATHS = custom("minecraft:deaths");
    public static final String WALK = custom("minecraft:walk_one_cm");
    public static final String FLY = custom("minecraft:fly_one_cm");
    /** Riding has one stat per vehicle; the client counts all of them as this one. */
    public static final String RIDE = custom("minecraft:minecart_one_cm");

    private StatKeys() {
    }

    public static String of(String type, String value) {
        return type + "/" + value;
    }

    public static String custom(String value) {
        return of(CUSTOM, value);
    }

    public static String type(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? key : key.substring(0, slash);
    }

    public static String value(String key) {
        int slash = key.indexOf('/');
        return slash < 0 ? "" : key.substring(slash + 1);
    }

    /** The part of an id after the namespace, e.g. {@code diamond_ore} for {@code minecraft:diamond_ore}. */
    public static String path(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }

    public static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    public enum Movement { WALK, FLY, RIDE, NONE }

    /** Sorts the vanilla "..._one_cm" stats into walking, flying and riding. Falling counts as none. */
    public static Movement movement(String key) {
        if (!CUSTOM.equals(type(key))) {
            return Movement.NONE;
        }
        String path = path(value(key));
        if (!path.endsWith("_one_cm")) {
            return Movement.NONE;
        }
        return switch (path) {
            case "walk_one_cm", "sprint_one_cm", "crouch_one_cm", "swim_one_cm", "walk_on_water_one_cm",
                 "walk_under_water_one_cm", "climb_one_cm" -> Movement.WALK;
            case "fly_one_cm", "aviate_one_cm" -> Movement.FLY;
            case "fall_one_cm" -> Movement.NONE;
            default -> Movement.RIDE;
        };
    }

    /**
     * The ore a mined block counts as, so stone and deepslate variants add up: {@code minecraft:diamond}
     * for both diamond ores. Returns null for blocks that are not ores.
     */
    public static String oreType(String blockId) {
        String namespace = namespace(blockId);
        String path = path(blockId);
        if (path.equals("ancient_debris")) {
            return blockId;
        }
        if (!path.endsWith("_ore")) {
            return null;
        }
        String type = path.substring(0, path.length() - "_ore".length());
        for (String prefix : new String[]{"deepslate_", "nether_", "end_"}) {
            if (type.startsWith(prefix) && type.length() > prefix.length()) {
                type = type.substring(prefix.length());
                break;
            }
        }
        return namespace + ":" + type;
    }

    /** A block to show for an ore type, e.g. {@code minecraft:diamond_ore}. */
    public static String oreBlock(String oreType) {
        if (oreType.equals("minecraft:quartz")) {
            return "minecraft:nether_quartz_ore";
        }
        if (path(oreType).equals("ancient_debris")) {
            return oreType;
        }
        return oreType + "_ore";
    }
}
