package com.example.beaconhardcore;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BeaconOwnershipManager {
    private static final Map<String, UUID> OWNERS = new ConcurrentHashMap<>();

    private BeaconOwnershipManager() {
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + ":" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static void setOwner(ServerLevel level, BlockPos pos, UUID owner) {
        OWNERS.put(key(level, pos), owner);
    }

    public static UUID getOwner(ServerLevel level, BlockPos pos) {
        return OWNERS.get(key(level, pos));
    }

    public static void removeOwner(ServerLevel level, BlockPos pos) {
        OWNERS.remove(key(level, pos));
    }
}