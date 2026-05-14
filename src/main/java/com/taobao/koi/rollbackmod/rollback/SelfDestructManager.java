package com.taobao.koi.rollbackmod.rollback;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

public final class SelfDestructManager {
    public enum Cause {
        FATE_EXHAUSTED("death.rollbackmod.fate_exhausted"),
        CHOSE_DESTRUCTION("death.rollbackmod.chose_destruction");

        private final String translationKey;

        Cause(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    private SelfDestructManager() {
    }

    public static void destroy(ServerPlayer player, Cause cause) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        destroy(server, List.of(player), cause);
    }

    public static void destroyAll(MinecraftServer server, Cause cause) {
        destroy(server, List.copyOf(server.getPlayerList().getPlayers()), cause);
    }

    private static void destroy(MinecraftServer server, List<ServerPlayer> players, Cause cause) {
        if (players.isEmpty()) {
            return;
        }
        RollbackManager.deleteCheckpoint(server);
        RollbackSavedData data = RollbackSavedData.get(server);

        for (ServerPlayer player : players) {
            data.markSelfDestructed(player.getUUID());
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable(cause.translationKey, player.getDisplayName()),
                    false
            );
            enforce(player);
        }
        server.saveEverything(false, true, false);
    }

    public static boolean isLocked(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return false;
        }
        RollbackSavedData data = RollbackSavedData.get(server);
        return data.isCountdownExpired() || data.isSelfDestructed(player.getUUID());
    }

    public static void enforce(ServerPlayer player) {
        if (!isLocked(player)) {
            return;
        }
        forceSpectator(player);
    }

    private static void forceSpectator(ServerPlayer player) {
        player.setHealth(Math.max(1.0F, player.getHealth()));
        if (player.gameMode.getGameModeForPlayer() != GameType.SPECTATOR) {
            player.setGameMode(GameType.SPECTATOR);
        }
        player.onUpdateAbilities();
        player.inventoryMenu.broadcastChanges();
        player.containerMenu.broadcastChanges();
    }
}
