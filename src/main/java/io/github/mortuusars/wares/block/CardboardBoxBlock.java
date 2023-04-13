package io.github.mortuusars.wares.block;

import io.github.mortuusars.wares.Wares;
import io.github.mortuusars.wares.util.VoxelShapeUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("deprecation")
public class CardboardBoxBlock extends Block {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    public static final IntegerProperty BOXES = IntegerProperty.create("boxes", 1, 4);

    private static final VoxelShape[] SHAPES = new VoxelShape[8];

    static {
        SHAPES[0] = Block.box(4, 0, 4, 10, 6, 13);
        SHAPES[1] = Shapes.or(Block.box(1, 0, 4, 7, 6, 13), Block.box(7, 0, 5, 15, 6, 12));
        SHAPES[2] = Shapes.or(Block.box(8, 0, 1, 14, 6, 8), Block.box(1, 0, 5, 8, 6, 14), Block.box(8, 0, 8, 15, 6, 15));
        SHAPES[3] = Shapes.or(SHAPES[2], Block.box(5, 6, 5, 11, 12, 12));
        SHAPES[4] = VoxelShapeUtils.rotate(SHAPES[0], Rotation.CLOCKWISE_90);
        SHAPES[5] = VoxelShapeUtils.rotate(SHAPES[1], Rotation.CLOCKWISE_90);
        SHAPES[6] = VoxelShapeUtils.rotate(SHAPES[2], Rotation.CLOCKWISE_90);
        SHAPES[7] = VoxelShapeUtils.rotate(SHAPES[3], Rotation.CLOCKWISE_90);
    }

    public CardboardBoxBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(getStateDefinition().any()
                .setValue(AXIS, Direction.Axis.X)
                .setValue(BOXES, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, BOXES);
    }

    @Override
    public @NotNull VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter pLevel, @NotNull BlockPos pPos, @NotNull CollisionContext pContext) {
        return SHAPES[state.getValue(BOXES) - 1 + (state.getValue(AXIS) == Direction.Axis.Z ? 4 : 0)];
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState blockstate = context.getLevel().getBlockState(context.getClickedPos());
        if (blockstate.is(this))
            return blockstate.cycle(BOXES);
        else
            return getStateDefinition().any().setValue(AXIS, context.getHorizontalDirection().getAxis());
    }

    @Override
    @SuppressWarnings("SimplifiableConditionalExpression")
    public boolean canBeReplaced(@NotNull BlockState state, BlockPlaceContext context) {
        return !context.isSecondaryUseActive()
                && context.getItemInHand().getItem() == this.asItem()
                && state.getValue(BOXES) < 4 ? true : super.canBeReplaced(state, context);
    }

    @Override
    public @NotNull InteractionResult use(@NotNull BlockState blockState, @NotNull Level level, @NotNull BlockPos pos, Player player, @NotNull InteractionHand hand, @NotNull BlockHitResult hitResult) {
        if (player.isSecondaryUseActive()) {

            if (level.isClientSide)
                return InteractionResult.SUCCESS;

            int packages = blockState.getValue(BOXES) - 1;

            if (packages <= 0)
                level.removeBlock(pos, false);
            else
                level.setBlock(pos, blockState.setValue(BOXES, packages), Block.UPDATE_ALL);


            level.playSound(null, pos, Wares.SoundEvents.CARDBOARD_HIT.get(), SoundSource.BLOCKS, 0.9f, level.getRandom().nextFloat() * 0.1f + 0.95f);

            player.addItem(new ItemStack(this.asItem()));
            return InteractionResult.SUCCESS;
        }

        return InteractionResult.PASS;
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.below(), Direction.UP);
    }
}
