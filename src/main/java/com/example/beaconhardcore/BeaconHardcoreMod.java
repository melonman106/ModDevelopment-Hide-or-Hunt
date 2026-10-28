package com.example.beaconhardcore;

import com.mojang.authlib.GameProfile;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.UUID;

public class BeaconHardcoreMod implements ModInitializer {
    public static final String BEACON_OWNER_NBT = "BeaconOwner";
    public static final String BEACON_USE_PERMISSION = "beaconhardcore.beacon";
    public static final String DOOMED_PERMISSION = "beaconhardcore.doomed";

    @Override
    public void onInitialize() {

        // /beacon
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("beacon").executes(context -> {
                ServerPlayer player = context.getSource().getPlayerOrException();

                if (!hasLuckPermsPermission(player, BEACON_USE_PERMISSION)) {
                    player.displayClientMessage(
                            Component.literal("You do not have permission to use /beacon.")
                                    .withStyle(ChatFormatting.RED),
                            false
                    );
                    return 0;
                }

                ItemStack beacon = new ItemStack(Items.BEACON);
                beacon.getOrCreateTag().putUUID(BEACON_OWNER_NBT, player.getUUID());

                if (!player.getInventory().add(beacon)) {
                    player.drop(beacon, false);
                }

                player.displayClientMessage(
                        Component.literal("You received your special beacon.")
                                .withStyle(ChatFormatting.GREEN),
                        false
                );

                return 1;
            }));
        });

        // If any player breaks a beacon, doom the owner
        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (!(level instanceof ServerLevel serverLevel)) return;
            if (!state.is(Blocks.BEACON)) return;

            UUID ownerUuid = BeaconOwnershipManager.getOwner(serverLevel, pos);
            if (ownerUuid == null) return;

            BeaconOwnershipManager.removeOwner(serverLevel, pos);

            MinecraftServer server = serverLevel.getServer();
            ServerPlayer ownerPlayer = server.getPlayerList().getPlayer(ownerUuid);

            String ownerName;
            if (ownerPlayer != null) {
                ownerName = ownerPlayer.getGameProfile().getName();
            } else {
                ownerName = getOfflineName(ownerUuid);
            }

            markDoomed(server, ownerUuid);

            // Big red title for the owner if they are online
            if (ownerPlayer != null) {
                showDoomedTitle(ownerPlayer, ownerName);
                ownerPlayer.displayClientMessage(
                        Component.literal("Your beacon was broken. You will become a spectator when you next die.")
                                .withStyle(ChatFormatting.RED),
                        false
                );
            }

            // Everyone knows
            server.getPlayerList().broadcastSystemMessage(
                    Component.literal(ownerName + "'s beacon was broken! " + ownerName + " is now doomed!")
                            .withStyle(ChatFormatting.RED),
                    false
            );
        });

        // After respawn, if doomed, switch to spectator
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (isDoomed(newPlayer.getServer(), newPlayer.getUUID())) {
                newPlayer.setGameMode(GameType.SPECTATOR);
                newPlayer.displayClientMessage(
                        Component.literal("You have died and are now in Spectator mode.")
                                .withStyle(ChatFormatting.RED),
                        false
                );
            }
        });
    }

    private boolean hasLuckPermsPermission(ServerPlayer player, String permission) {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            return false;
        }

        User user = getLuckPermsUser(player.getUUID());
        if (user == null) return false;

        return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
    }

    private void markDoomed(MinecraftServer server, UUID playerUuid) {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            return;
        }

        User user = getLuckPermsUser(playerUuid);
        if (user == null) return;

        user.data().add(Node.builder(DOOMED_PERMISSION).value(true).build());
        LuckPermsProvider.get().getUserManager().saveUser(user);
    }

    private boolean isDoomed(MinecraftServer server, UUID playerUuid) {
        if (!FabricLoader.getInstance().isModLoaded("luckperms")) {
            return false;
        }

        User user = getLuckPermsUser(playerUuid);
        if (user == null) return false;

        return user.getCachedData().getPermissionData().checkPermission(DOOMED_PERMISSION).asBoolean();
    }

    private User getLuckPermsUser(UUID playerUuid) {
        try {
            User user = LuckPermsProvider.get().getUserManager().getUser(playerUuid);
            if (user != null) return user;

            return LuckPermsProvider.get().getUserManager().loadUser(playerUuid).join();
        } catch (Exception e) {
            return null;
        }
    }

    private void showDoomedTitle(ServerPlayer player, String ownerName) {
        Component title = Component.literal(ownerName + " has be doomed!")
                .withStyle(ChatFormatting.RED, ChatFormatting.BOLD);

        Component subtitle = Component.literal("Your beacon was broken.")
                .withStyle(ChatFormatting.RED);

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 70, 20));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private String getOfflineName(UUID uuid) {
        GameProfile profile = new GameProfile(uuid, "A player");
        return profile.getName();
    }
}
