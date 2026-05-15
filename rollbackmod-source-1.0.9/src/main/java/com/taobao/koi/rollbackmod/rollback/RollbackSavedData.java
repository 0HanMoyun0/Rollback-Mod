package com.taobao.koi.rollbackmod.rollback;

import com.taobao.koi.rollbackmod.core.CoreType;
import java.util.HashSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RollbackSavedData extends SavedData {
    public static final String DATA_NAME = "rollbackmod_state";
    private static final Logger LOGGER = LoggerFactory.getLogger(RollbackSavedData.class);

    private Checkpoint checkpoint;
    private boolean initialCheckpointCreated;
    private long countdownStartDay;
    private boolean countdownExpired;
    private long lastAnnouncedDay;
    private final Map<UUID, MarkRecord> marks = new HashMap<>();
    private final Map<String, BlockSnapshotRecord> changedBlocks = new LinkedHashMap<>();
    private final Set<UUID> selfDestructedPlayers = new HashSet<>();

    public static RollbackSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(RollbackSavedData::load, RollbackSavedData::new, DATA_NAME);
    }

    public static RollbackSavedData load(CompoundTag tag) {
        RollbackSavedData data = new RollbackSavedData();
        data.initialCheckpointCreated = tag.getBoolean("initialCheckpointCreated");
        if (tag.contains("countdownStartDay", Tag.TAG_LONG)) {
            data.countdownStartDay = Math.max(1L, tag.getLong("countdownStartDay"));
        }
        data.countdownExpired = tag.getBoolean("countdownExpired");
        if (tag.contains("lastAnnouncedDay", Tag.TAG_LONG)) {
            data.lastAnnouncedDay = Math.max(1L, tag.getLong("lastAnnouncedDay"));
        }
        if (tag.contains("checkpoint", Tag.TAG_COMPOUND)) {
            try {
                data.checkpoint = Checkpoint.load(tag.getCompound("checkpoint"));
            } catch (RuntimeException exception) {
                LOGGER.warn("Discarding invalid rollback checkpoint data", exception);
            }
        }
        ListTag markList = tag.getList("marks", Tag.TAG_COMPOUND);
        for (Tag rawMark : markList) {
            try {
                MarkRecord record = MarkRecord.load((CompoundTag) rawMark);
                data.marks.put(record.playerId(), record);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid rollback mark record", exception);
            }
        }
        int[] selfDestructedPlayerIds = tag.getIntArray("selfDestructedPlayers");
        for (int i = 0; i + 3 < selfDestructedPlayerIds.length; i += 4) {
            long most = ((long) selfDestructedPlayerIds[i] << 32) | (selfDestructedPlayerIds[i + 1] & 0xFFFFFFFFL);
            long least = ((long) selfDestructedPlayerIds[i + 2] << 32) | (selfDestructedPlayerIds[i + 3] & 0xFFFFFFFFL);
            data.selfDestructedPlayers.add(new UUID(most, least));
        }
        ListTag blockList = tag.getList("changedBlocks", Tag.TAG_COMPOUND);
        for (Tag rawBlock : blockList) {
            try {
                BlockSnapshotRecord record = BlockSnapshotRecord.load((CompoundTag) rawBlock);
                data.changedBlocks.put(blockKey(record.dimension(), record.pos()), record);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid rollback block snapshot", exception);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("initialCheckpointCreated", initialCheckpointCreated);
        tag.putLong("countdownStartDay", countdownStartDay);
        tag.putBoolean("countdownExpired", countdownExpired);
        tag.putLong("lastAnnouncedDay", lastAnnouncedDay);
        if (checkpoint != null) {
            tag.put("checkpoint", checkpoint.save());
        }
        ListTag markList = new ListTag();
        for (MarkRecord record : marks.values()) {
            markList.add(record.save());
        }
        tag.put("marks", markList);
        int[] selfDestructedPlayerIds = new int[selfDestructedPlayers.size() * 4];
        int selfDestructedPlayerIndex = 0;
        for (UUID playerId : selfDestructedPlayers) {
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) (playerId.getMostSignificantBits() >> 32);
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) playerId.getMostSignificantBits();
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) (playerId.getLeastSignificantBits() >> 32);
            selfDestructedPlayerIds[selfDestructedPlayerIndex++] = (int) playerId.getLeastSignificantBits();
        }
        tag.putIntArray("selfDestructedPlayers", selfDestructedPlayerIds);
        ListTag blockList = new ListTag();
        for (BlockSnapshotRecord record : changedBlocks.values()) {
            blockList.add(record.save());
        }
        tag.put("changedBlocks", blockList);
        return tag;
    }

    public boolean hasCheckpoint() {
        return checkpoint != null;
    }

    public Checkpoint getCheckpoint() {
        return checkpoint;
    }

    public void setCheckpoint(Checkpoint checkpoint) {
        this.checkpoint = checkpoint;
        setDirty();
    }

    public void clearCheckpoint() {
        this.checkpoint = null;
        setDirty();
    }

    public boolean isInitialCheckpointCreated() {
        return initialCheckpointCreated;
    }

    public void setInitialCheckpointCreated(boolean initialCheckpointCreated) {
        this.initialCheckpointCreated = initialCheckpointCreated;
        setDirty();
    }

    public Optional<MarkRecord> getMark(UUID playerId) {
        return Optional.ofNullable(marks.get(playerId));
    }

    public Collection<MarkRecord> getMarks() {
        return marks.values();
    }

    public void putMark(MarkRecord record) {
        marks.put(record.playerId(), record);
        setDirty();
    }

    public void removeMark(UUID playerId) {
        marks.remove(playerId);
        setDirty();
    }

    public void clearMarks() {
        marks.clear();
        setDirty();
    }

    public boolean isSelfDestructed(UUID playerId) {
        return selfDestructedPlayers.contains(playerId);
    }

    public void markSelfDestructed(UUID playerId) {
        if (selfDestructedPlayers.add(playerId)) {
            setDirty();
        }
    }

    public Set<UUID> getSelfDestructedPlayers() {
        return Set.copyOf(selfDestructedPlayers);
    }

    public void clearSelfDestructedPlayers() {
        if (!selfDestructedPlayers.isEmpty()) {
            selfDestructedPlayers.clear();
            setDirty();
        }
    }

    public Collection<BlockSnapshotRecord> getChangedBlocks() {
        return changedBlocks.values();
    }

    public void rememberChangedBlock(BlockSnapshotRecord record) {
        String key = blockKey(record.dimension(), record.pos());
        if (!changedBlocks.containsKey(key)) {
            changedBlocks.put(key, record);
            setDirty();
        }
    }

    public void clearChangedBlocks() {
        changedBlocks.clear();
        setDirty();
    }

    public long getCountdownStartDay() {
        return countdownStartDay;
    }

    public void setCountdownStartDay(long countdownStartDay) {
        this.countdownStartDay = Math.max(1L, countdownStartDay);
        setDirty();
    }

    public boolean isCountdownExpired() {
        return countdownExpired;
    }

    public void setCountdownExpired(boolean countdownExpired) {
        this.countdownExpired = countdownExpired;
        setDirty();
    }

    public long getLastAnnouncedDay() {
        return lastAnnouncedDay;
    }

    public void setLastAnnouncedDay(long lastAnnouncedDay) {
        this.lastAnnouncedDay = Math.max(1L, lastAnnouncedDay);
        setDirty();
    }

    public record Checkpoint(long gameTime, long dayTime, Map<UUID, PlayerSnapshot> players) {
        public static Checkpoint capture(MinecraftServer server) {
            Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                snapshots.put(player.getUUID(), PlayerSnapshot.capture(player));
            }
            ServerLevel overworld = server.overworld();
            return new Checkpoint(overworld.getGameTime(), overworld.getDayTime(), snapshots);
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putLong("gameTime", gameTime);
            tag.putLong("dayTime", dayTime);
            CompoundTag playerTag = new CompoundTag();
            for (Map.Entry<UUID, PlayerSnapshot> entry : players.entrySet()) {
                playerTag.put(entry.getKey().toString(), entry.getValue().save());
            }
            tag.put("players", playerTag);
            return tag;
        }

        public static Checkpoint load(CompoundTag tag) {
            Map<UUID, PlayerSnapshot> snapshots = new HashMap<>();
            CompoundTag playerTag = tag.getCompound("players");
            for (String key : playerTag.getAllKeys()) {
                try {
                    snapshots.put(UUID.fromString(key), PlayerSnapshot.load(playerTag.getCompound(key)));
                } catch (RuntimeException exception) {
                    LOGGER.warn("Skipping invalid rollback player snapshot {}", key, exception);
                }
            }
            return new Checkpoint(tag.getLong("gameTime"), tag.getLong("dayTime"), snapshots);
        }

        public Optional<PlayerSnapshot> getSnapshot(UUID playerId) {
            return Optional.ofNullable(players.get(playerId));
        }
    }

    public record PlayerSnapshot(
            UUID playerId,
            String dimension,
            double x,
            double y,
            double z,
            float yRot,
            float xRot,
            float health,
            float absorption,
            int foodLevel,
            float saturation,
            int experienceLevel,
            int totalExperience,
            float experienceProgress,
            int selectedSlot,
            int remainingFireTicks,
            int airSupply,
            float fallDistance,
            ListTag inventory,
            ListTag effects
    ) {
        public static PlayerSnapshot capture(ServerPlayer player) {
            ListTag inventoryTag = new ListTag();
            player.getInventory().save(inventoryTag);

            ListTag effectTag = new ListTag();
            for (MobEffectInstance effect : player.getActiveEffects()) {
                effectTag.add(effect.save(new CompoundTag()));
            }

            return new PlayerSnapshot(
                    player.getUUID(),
                    player.serverLevel().dimension().location().toString(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    player.getYRot(),
                    player.getXRot(),
                    player.getHealth(),
                    player.getAbsorptionAmount(),
                    player.getFoodData().getFoodLevel(),
                    player.getFoodData().getSaturationLevel(),
                    player.experienceLevel,
                    player.totalExperience,
                    player.experienceProgress,
                    player.getInventory().selected,
                    player.getRemainingFireTicks(),
                    player.getAirSupply(),
                    player.fallDistance,
                    inventoryTag,
                    effectTag
            );
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("playerId", playerId);
            tag.putString("dimension", dimension);
            tag.putDouble("x", x);
            tag.putDouble("y", y);
            tag.putDouble("z", z);
            tag.putFloat("yRot", yRot);
            tag.putFloat("xRot", xRot);
            tag.putFloat("health", health);
            tag.putFloat("absorption", absorption);
            tag.putInt("foodLevel", foodLevel);
            tag.putFloat("saturation", saturation);
            tag.putInt("experienceLevel", experienceLevel);
            tag.putInt("totalExperience", totalExperience);
            tag.putFloat("experienceProgress", experienceProgress);
            tag.putInt("selectedSlot", selectedSlot);
            tag.putInt("remainingFireTicks", remainingFireTicks);
            tag.putInt("airSupply", airSupply);
            tag.putFloat("fallDistance", fallDistance);
            tag.put("inventory", inventory.copy());
            tag.put("effects", effects.copy());
            return tag;
        }

        public static PlayerSnapshot load(CompoundTag tag) {
            return new PlayerSnapshot(
                    tag.getUUID("playerId"),
                    tag.getString("dimension"),
                    tag.getDouble("x"),
                    tag.getDouble("y"),
                    tag.getDouble("z"),
                    tag.getFloat("yRot"),
                    tag.getFloat("xRot"),
                    tag.getFloat("health"),
                    tag.getFloat("absorption"),
                    tag.getInt("foodLevel"),
                    tag.getFloat("saturation"),
                    tag.getInt("experienceLevel"),
                    tag.getInt("totalExperience"),
                    tag.getFloat("experienceProgress"),
                    tag.getInt("selectedSlot"),
                    tag.getInt("remainingFireTicks"),
                    tag.getInt("airSupply"),
                    tag.getFloat("fallDistance"),
                    tag.getList("inventory", Tag.TAG_COMPOUND),
                    tag.getList("effects", Tag.TAG_COMPOUND)
            );
        }

        public void restore(ServerPlayer player, MinecraftServer server) {
            ServerLevel targetLevel = resolveLevel(server, dimension).orElse(player.serverLevel());
            player.teleportTo(targetLevel, x, y, z, yRot, xRot);

            player.getInventory().clearContent();
            player.getInventory().load(inventory.copy());
            player.getInventory().selected = Mth.clamp(selectedSlot, 0, 8);

            player.removeAllEffects();
            for (Tag rawEffect : effects) {
                MobEffectInstance effect = MobEffectInstance.load((CompoundTag) rawEffect);
                if (effect != null) {
                    player.addEffect(effect);
                }
            }

            player.setAbsorptionAmount(absorption);
            player.setHealth(Mth.clamp(health, 1.0F, player.getMaxHealth()));
            player.getFoodData().setFoodLevel(foodLevel);
            player.getFoodData().setSaturation(saturation);
            player.experienceLevel = experienceLevel;
            player.totalExperience = totalExperience;
            player.experienceProgress = experienceProgress;
            player.setRemainingFireTicks(remainingFireTicks);
            player.setAirSupply(airSupply);
            player.fallDistance = fallDistance;
            player.deathTime = 0;
            player.hurtTime = 0;
            player.hurtDuration = 0;
            player.invulnerableTime = 20;
            player.setPose(Pose.STANDING);
            player.setDeltaMovement(0.0D, 0.0D, 0.0D);
            player.hurtMarked = true;

            player.inventoryMenu.broadcastChanges();
            player.containerMenu.broadcastChanges();
            player.connection.send(new ClientboundSetExperiencePacket(experienceProgress, totalExperience, experienceLevel));
        }
    }

    public record MarkRecord(
            UUID playerId,
            CoreType coreType,
            UUID targetId,
            String targetDimension,
            long expiresAtGameTime,
            boolean originalNoAi,
            boolean originalGlowing
    ) {
        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("playerId", playerId);
            tag.putString("coreType", coreType.name());
            tag.putUUID("targetId", targetId);
            tag.putString("targetDimension", targetDimension);
            tag.putLong("expiresAtGameTime", expiresAtGameTime);
            tag.putBoolean("originalNoAi", originalNoAi);
            tag.putBoolean("originalGlowing", originalGlowing);
            return tag;
        }

        public static MarkRecord load(CompoundTag tag) {
            return new MarkRecord(
                    tag.getUUID("playerId"),
                    CoreType.valueOf(tag.getString("coreType")),
                    tag.getUUID("targetId"),
                    tag.getString("targetDimension"),
                    tag.getLong("expiresAtGameTime"),
                    tag.getBoolean("originalNoAi"),
                    tag.getBoolean("originalGlowing")
            );
        }

        public MarkRecord withTarget(UUID targetId, String targetDimension) {
            return new MarkRecord(
                    playerId,
                    coreType,
                    targetId,
                    targetDimension,
                    expiresAtGameTime,
                    originalNoAi,
                    originalGlowing
            );
        }
    }

    public static Optional<ServerLevel> resolveLevel(MinecraftServer server, String dimensionId) {
        ResourceLocation location = ResourceLocation.tryParse(dimensionId);
        if (location == null) {
            return Optional.empty();
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, location);
        return Optional.ofNullable(server.getLevel(key));
    }

    private static String blockKey(String dimension, BlockPos pos) {
        return dimension + "|" + pos.asLong();
    }

    public record BlockSnapshotRecord(
            String dimension,
            BlockPos pos,
            BlockState state,
            CompoundTag blockEntityTag
    ) {
        public static BlockSnapshotRecord capture(ServerLevel level, BlockPos pos) {
            return capture(level, pos, level.getBlockState(pos));
        }

        public static BlockSnapshotRecord capture(ServerLevel level, BlockPos pos, BlockState state) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            CompoundTag tag = blockEntity == null ? null : blockEntity.saveWithFullMetadata();
            return new BlockSnapshotRecord(
                    level.dimension().location().toString(),
                    pos.immutable(),
                    state,
                    tag
            );
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("dimension", dimension);
            tag.put("pos", NbtUtils.writeBlockPos(pos));
            tag.put("state", NbtUtils.writeBlockState(state));
            if (blockEntityTag != null) {
                tag.put("blockEntity", blockEntityTag.copy());
            }
            return tag;
        }

        public static BlockSnapshotRecord load(CompoundTag tag) {
            CompoundTag blockEntityTag = tag.contains("blockEntity", Tag.TAG_COMPOUND)
                    ? tag.getCompound("blockEntity")
                    : null;
            return new BlockSnapshotRecord(
                    tag.getString("dimension"),
                    NbtUtils.readBlockPos(tag.getCompound("pos")),
                    NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("state")),
                    blockEntityTag
            );
        }

        public void restore(MinecraftServer server) {
            resolveLevel(server, dimension).ifPresent(level -> {
                BlockState oldState = level.getBlockState(pos);
                level.setBlock(pos, state, 3);
                level.removeBlockEntity(pos);
                if (blockEntityTag != null) {
                    BlockEntity blockEntity = BlockEntity.loadStatic(pos, state, blockEntityTag.copy());
                    if (blockEntity != null) {
                        level.setBlockEntity(blockEntity);
                    }
                }
                level.sendBlockUpdated(pos, oldState, state, 3);
            });
        }
    }
}
