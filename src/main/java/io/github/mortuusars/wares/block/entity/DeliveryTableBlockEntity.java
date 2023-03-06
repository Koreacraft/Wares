package io.github.mortuusars.wares.block.entity;

import io.github.mortuusars.wares.Wares;
import io.github.mortuusars.wares.data.agreement.DeliveryAgreement;
import io.github.mortuusars.wares.menu.DeliveryTableMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class DeliveryTableBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int SLOTS = 13;
    public static final int AGREEMENT_SLOT = 0;
    public static final int[] INPUT_PLUS_AGREEMENT_SLOTS = new int[] {0,1,2,3,4,5,6};
    public static final int[] INPUT_SLOTS = new int[] {1,2,3,4,5,6};
    public static final int[] OUTPUT_SLOTS = new int[] {7,8,9,10,11,12};

    public static final int CONTAINER_DATA_DELIVERY_PROGRESS = 0;
    public static final int CONTAINER_DATA_DELIVERY_DURATION = 1;

    protected final ContainerData containerData = new ContainerData() {
        public int get(int id) {
            switch(id) {
                case CONTAINER_DATA_DELIVERY_PROGRESS:
                    return DeliveryTableBlockEntity.this.progress;
                case CONTAINER_DATA_DELIVERY_DURATION:
                    return DeliveryTableBlockEntity.this.getAgreement().getDeliveryTimeOrDefault();
                default:
                    return 0;
            }
        }

        public void set(int id, int value) {
            switch(id) {
                case 0:
                    DeliveryTableBlockEntity.this.progress = value;
                    break;
            }

        }

        public int getCount() {
            return 2;
        }
    };

    protected final ItemStackHandler inventory;
    protected LazyOptional<IItemHandlerModifiable>[] inventoryHandlers;

    protected DeliveryAgreement agreement = DeliveryAgreement.EMPTY;

    protected int progress = 0;
    protected boolean canDeliverCached = false; // Store value until inventory changed

    public DeliveryTableBlockEntity(BlockPos pos, BlockState blockState) {
        super(Wares.BlockEntities.DELIVERY_TABLE.get(), pos, blockState);
        inventory = createInventory(SLOTS);
        inventoryHandlers = SidedInvWrapper.create(this, Direction.DOWN, Direction.UP, Direction.NORTH);
    }

    public int getBatchSize() {
        return 1;
    }

    public DeliveryAgreement getAgreement() {
        return agreement;
    }

    public void refreshAgreement() {
        agreement = DeliveryAgreement.fromItemStack(getItem(AGREEMENT_SLOT)).orElse(DeliveryAgreement.EMPTY);
        resetProgress();
    }

    public void updateAgreementItemStack() {
        getAgreement().toItemStack(inventory.getStackInSlot(AGREEMENT_SLOT));
        setChanged();
    }

    public void resetProgress() {
        progress = 0;
        canDeliverCached = false;
    }

    public static <T extends BlockEntity> void serverTick(Level level, BlockPos pos, BlockState blockState, T blockEntity) {
        if ( !(blockEntity instanceof DeliveryTableBlockEntity deliveryTableEntity))
            return;

        DeliveryAgreement agreement = deliveryTableEntity.getAgreement();

        // TODO: validate

        if (!deliveryTableEntity.canDeliverCached) {
            if (agreement == DeliveryAgreement.EMPTY || !deliveryTableEntity.canDeliver()) {
                deliveryTableEntity.resetProgress();
                return;
            }
        }

        deliveryTableEntity.canDeliverCached = true;

        if (deliveryTableEntity.progress >= agreement.getDeliveryTimeOrDefault()) {
            boolean success = true;

            for (int i = 0; i < deliveryTableEntity.getBatchSize(); i++) {
                if (!deliveryTableEntity.tryDeliverBatch()) {
                    success = false;
                    break;
                }
            }

            if (success)
                deliveryTableEntity.resetProgress();
        }
        else {
            deliveryTableEntity.progress++;
        }

        deliveryTableEntity.setChanged();
    }

    private boolean tryDeliverBatch() {
        if (!canDeliver())
            return false;

        consumeFromInputSlots(getAgreement().getRequestedItems());
        insertCopiesToOutputSlots(getAgreement().getPaymentItems());

        int quantity = getAgreement().getRemaining();
        if (quantity > 0) {
            getAgreement().setRemaining(--quantity);

            if (quantity <= 0)
                onAgreementCompleted();

            updateAgreementItemStack();
        }

        return true;
    }

    private void onAgreementCompleted() {
        int experience = getAgreement().getExperience();
        if (experience > 0 && level instanceof ServerLevel serverLevel)
            ExperienceOrb.award(serverLevel, Vec3.atCenterOf(getBlockPos()), experience);

        ItemStack agreementStack = inventory.getStackInSlot(AGREEMENT_SLOT);
        ItemStack noteStack = new ItemStack(Wares.Items.DELIVERY_NOTE.get());
        noteStack.setTag(agreementStack.getTag());

        getAgreement().toItemStack(noteStack);

        inventory.setStackInSlot(AGREEMENT_SLOT, noteStack);

        setChanged();
    }

    private boolean canDeliver() {
        return !getAgreement().isExpired(level.getGameTime())
                && (getAgreement().isInfinite() || getAgreement().getRemaining() > 0)
                && hasRequestedItems()
                && hasSpaceForPayment();
    }

    private void consumeFromInputSlots(List<ItemStack> requestedItems) {
        for (ItemStack requestedItem : requestedItems) {
            int requiredCount = requestedItem.getCount();

            for (int slotIndex : INPUT_SLOTS) {
                if (ItemStack.isSameItemSameTags(requestedItem, inventory.getStackInSlot(slotIndex))) {
                    ItemStack extractedStack = inventory.extractItem(slotIndex, requiredCount, false);
                    requiredCount -= extractedStack.getCount();

                    if (requiredCount <= 0)
                        break;
                }
            }
        }
    }

    private void insertCopiesToOutputSlots(List<ItemStack> paymentItems) {
        for (ItemStack stack : paymentItems) {
            ItemStack insertedStack = stack.copy();
            for (int slotIndex : OUTPUT_SLOTS) {
                insertedStack = inventory.insertItem(slotIndex, insertedStack, false);
                if (insertedStack.isEmpty())
                    break;
            }
        }
    }

    private boolean hasRequestedItems() {
        List<ItemStack> requestedItems = agreement.getRequestedItems();

        List<ItemStack> inputStacks = new ArrayList<>();
        for (int slotIndex : INPUT_SLOTS) {
            ItemStack stackInSlot = inventory.getStackInSlot(slotIndex);
            if (!stackInSlot.isEmpty())
                inputStacks.add(stackInSlot.copy());
        }

        for (ItemStack requestedItem : requestedItems) {
            int requiredCount = requestedItem.getCount();

            for (ItemStack stack : inputStacks) {
                if (!stack.isEmpty() && ItemStack.isSameItemSameTags(requestedItem, stack)) {
                    ItemStack split = stack.split(requiredCount);
                    requiredCount -= split.getCount();
                }

                if (requiredCount <= 0)
                    break;
            }

            if (requiredCount > 0)
                return false;
        }

        return true;
    }

    private boolean hasSpaceForPayment() {
        for (ItemStack stack : getAgreement().getPaymentItems()) {
            for (int slotIndex : OUTPUT_SLOTS) {
                stack = inventory.insertItem(slotIndex, stack, true);
                if (stack.isEmpty())
                    break;
            }

            if (!stack.isEmpty())
                return false;
        }

        return true;
    }


    // <Container>

    protected @NotNull ItemStackHandler createInventory(int slots) {
        return new ItemStackHandler(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                if (slot == AGREEMENT_SLOT)
                    refreshAgreement();
                setChanged();
            }
        };
    }

    public IItemHandlerModifiable getInventory() {
        return inventory;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (!this.remove && cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            if (side == Direction.DOWN) return inventoryHandlers[0].cast();
            if (side == Direction.UP) return inventoryHandlers[1].cast();
            if (side != null) return inventoryHandlers[2].cast();
        }

        return super.getCapability(cap, side);
    }

    public int getContainerSize(){
        return inventory.getSlots();
    }

    @Override
    public boolean isEmpty() {
        for (int i = 0; i < getContainerSize(); i++) {
            if (!getItem(i).isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        return inventory.extractItem(slot, amount, false);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        ItemStack stack = inventory.getStackInSlot(slot);
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        inventory.setStackInSlot(slot, stack);
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return switch (side) {
            case DOWN -> OUTPUT_SLOTS;
            case UP -> INPUT_PLUS_AGREEMENT_SLOTS;
            case NORTH, SOUTH, WEST, EAST -> INPUT_SLOTS;
        };
    }

    @Override
    public boolean canPlaceItemThroughFace(int index, ItemStack itemStack, @Nullable Direction direction) {
        return canPlaceItem(index, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int index, ItemStack pStack, Direction direction) {
        return index >= 7;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    public boolean canPlaceItem(int slotIndex, ItemStack stack) {
        if (slotIndex >= 7)
            return false;
        if (slotIndex == AGREEMENT_SLOT && !stack.is(Wares.Tags.Items.AGREEMENTS))
            return false;

        return true;
    }

    @Override
    public boolean stillValid(Player pPlayer) {
        if (this.level.getBlockEntity(this.worldPosition) != this)
            return false;
        else
            return pPlayer.distanceToSqr(this.worldPosition.getX() + 0.5D,
                    this.worldPosition.getY() + 0.5D,
                    this.worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    protected Component getDefaultName() {
        return Wares.translate("container.delivery_table");
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DeliveryTableMenu(containerId, inventory, this, containerData);
    }


    // <Load/Save>

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        this.inventory.deserializeNBT(tag.getCompound("Inventory"));
        this.progress = tag.getInt("DeliveryTime");

        refreshAgreement();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", this.inventory.serializeNBT());
        tag.putInt("DeliveryTime", progress);
    }

    @Override
    public void setChanged() {
        super.setChanged();
        canDeliverCached = false;
    }

    // <Updating>

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        super.onDataPacket(net, pkt);
        handleUpdateTag(pkt.getTag());
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
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

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (int i = 0; i < inventoryHandlers.length; i++) {
            inventoryHandlers[i].invalidate();
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        inventoryHandlers = net.minecraftforge.items.wrapper.SidedInvWrapper.create(this, Direction.DOWN, Direction.UP, Direction.NORTH);
    }
}
