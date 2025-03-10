package io.github.mortuusars.wares.block.entity;

import io.github.mortuusars.wares.Wares;
import io.github.mortuusars.wares.data.Package;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

public class PackageBlockEntity extends BlockEntity {
    private Package pack;
    private boolean unpacksWhenBroken = true;

    public PackageBlockEntity(BlockPos pos, BlockState blockState) {
        super(Wares.BlockEntities.PACKAGE.get(), pos, blockState);
    }

    public Package getPackage() {
        return pack;
    }

    public void setPackage(Package pack) {
        this.pack = pack;
        setChanged();
    }

    public boolean unpacksWhenBroken() {
        return unpacksWhenBroken;
    }

    public void setUnpacksWhenBroken(boolean unpackWhenBroken) {
        unpacksWhenBroken = unpackWhenBroken;
        setChanged();
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        pack = Package.fromTag(tag).orElse(Package.DEFAULT);
        if (tag.contains("unpacksWhenBroken"))
            unpacksWhenBroken = tag.getBoolean("unpacksWhenBroken");
        else
            unpacksWhenBroken = true; // Unpacks by default (and for backwards compatibility)
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (pack != null)
            pack.toTag(tag);
        else
            Wares.LOGGER.error("Failed to save package. null.");

        tag.putBoolean("unpacksWhenBroken", unpacksWhenBroken);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        handleUpdateTag(pkt.getTag());
    }

    @Override
    public @NotNull Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public @NotNull CompoundTag getUpdateTag() {
        return serializeNBT();
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        super.handleUpdateTag(tag);
        load(tag);
    }
}
