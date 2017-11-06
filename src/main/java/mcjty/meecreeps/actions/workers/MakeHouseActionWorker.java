package mcjty.meecreeps.actions.workers;

import mcjty.meecreeps.api.IWorkerHelper;
import mcjty.meecreeps.varia.SoundTools;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemDoor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.function.Predicate;

public class MakeHouseActionWorker extends AbstractActionWorker {

    private AxisAlignedBB actionBox = null;
    private int stage = -1;     // -1 means flattening. Otherwise it is the level we are currently working on
    private int size = 0;
    private BlockPos relativePos = null;     // Relative position!

    public MakeHouseActionWorker(IWorkerHelper helper) {
        super(helper);
    }

    @Nullable
    @Override
    public AxisAlignedBB getActionBox() {
        if (actionBox == null) {
            // @todo config
            actionBox = new AxisAlignedBB(options.getTargetPos().add(-12, -5, -12), options.getTargetPos().add(12, 5, 12));
        }
        return actionBox;
    }

    private void placeBuildingBlock(BlockPos pos, Predicate<ItemStack> isBuilderBlock) {
        World world = entity.getWorld();
        ItemStack block = entity.consumeItem(isBuilderBlock, 1);
        if (!block.isEmpty()) {
            entity.getWorld().setBlockState(pos, Blocks.COBBLESTONE.getDefaultState(), 3);
            SoundTools.playSound(world, Blocks.COBBLESTONE.getSoundType().getPlaceSound(), pos.getX(), pos.getY(), pos.getZ(), 1.0f, 1.0f);
        }
    }

    private boolean isCobble(ItemStack stack) {
        return stack.getItem() == Item.getItemFromBlock(Blocks.COBBLESTONE);
    }

    private boolean isDoor(ItemStack stack) {
        return stack.getItem() instanceof ItemDoor;
    }

    private boolean isTorch(ItemStack stack) {
        return stack.getItem() == Item.getItemFromBlock(Blocks.TORCH);
    }

    private boolean isGlass(ItemStack stack) {
        return stack.getItem() == Item.getItemFromBlock(Blocks.GLASS);
    }

    private int getSize() {
        if (size == 0) {
            String id = options.getFurtherQuestionId();
            if ("9x9".equals(id)) {
                size = 9;
            } else if ("11x11".equals(id)) {
                size = 11;
            } else {
                size = 13;
            }
        }
        return size;
    }

    private boolean isBorderPos(int hs) {
        return relativePos.getX() <= -hs || relativePos.getX() >= hs || relativePos.getZ() <= -hs || relativePos.getZ() >= hs;
    }

    private boolean isDoorPos(int hs) {
        return relativePos.getZ() == 0 && relativePos.getX() == hs;
    }

    private boolean isGlassPos(int hs) {
        return relativePos.getX() != 0 && relativePos.getZ() != 0 && Math.abs(relativePos.getX()) < hs-4 && Math.abs(relativePos.getZ()) < hs-4;
    }

    private boolean isTorchPos(int hs) {
        if (relativePos.getZ() == 0 && (relativePos.getX() == hs-1 || relativePos.getX() == hs+1)) {
            return true;
        }
        return relativePos.getX() == 0 && (relativePos.getZ() == hs-1 || relativePos.getZ() == hs+1);
    }


    private Pair<BlockPos,Predicate<ItemStack>> findSpotToBuild() {
        while (true) {
            BlockPos tpos = options.getTargetPos();
            int hs = (getSize() - 1) / 2;

            if (relativePos == null) {
                relativePos = new BlockPos(-hs, stage, -hs);
            }
            if (entity.getWorld().isAirBlock(relativePos.add(tpos))) {
                if (stage == 0) {
                } else if (stage == 1) {
                    if (isDoorPos(hs)) {
                        return Pair.of(relativePos.add(tpos), this::isDoor);
                    } else if (isBorderPos(hs)) {
                        return Pair.of(relativePos.add(tpos), this::isCobble);
                    }
                } else if (stage == 2) {
                    if (isBorderPos(hs) && !isDoorPos(hs)) {
                        return Pair.of(relativePos.add(tpos), this::isCobble);
                    }
                } else if (stage == 3) {
                    if (isBorderPos(hs)) {
                        return Pair.of(relativePos.add(tpos), this::isCobble);
                    } else if (isTorchPos(hs)) {
                        return Pair.of(relativePos.add(tpos), this::isTorch);
                    }
                } else if (stage == 4) {
                    if (isBorderPos(hs)) {
                        return Pair.of(relativePos.add(tpos), this::isCobble);
                    }
                } else {
                    if (isGlassPos(hs)) {
                        return Pair.of(relativePos.add(tpos), this::isGlass);
                    } else {
                        return Pair.of(relativePos.add(tpos), this::isCobble);
                    }
                }
            }

            if (relativePos.getX() < hs) {
                relativePos = new BlockPos(relativePos.getX() + 1, stage, relativePos.getZ());
            } else if (relativePos.getZ() < hs) {
                relativePos = new BlockPos(hs, stage, relativePos.getZ() + 1);
            } else if (stage < 5) {
                stage++;
            } else {
                return null;
            }
        }
    }

    private BlockPos findSpotToFlatten() {
        while (true) {
            BlockPos tpos = options.getTargetPos();
            int hs = (getSize() - 1) / 2;

            if (relativePos == null) {
                relativePos = new BlockPos(-hs, 0, -hs);
            }
            for (int i = 5; i >= 0; i--) {
                if (!entity.getWorld().isAirBlock(relativePos.add(tpos).up(i))) {
                    return relativePos.add(tpos).up(i);
                }
            }

            if (relativePos.getX() < hs) {
                relativePos = new BlockPos(relativePos.getX() + 1, 0, relativePos.getZ());
            } else if (relativePos.getZ() < hs) {
                relativePos = new BlockPos(-hs, 0, relativePos.getZ() + 1);
            } else {
                return null;
            }
        }
    }

    @Override
    public void tick(boolean timeToWrapUp) {
        if (timeToWrapUp) {
            helper.done();
        } if (stage == -1) {
            BlockPos flatSpot = findSpotToFlatten();
            if (flatSpot == null) {
                stage = 0;
            } else {
                helper.navigateTo(flatSpot, helper::harvestAndDrop);
            }
        } else {
            Pair<BlockPos,Predicate<ItemStack>> buildSpot = findSpotToBuild();
            if (buildSpot != null) {
                if (!entity.hasItem(buildSpot.getValue())) {
                    helper.findItemOnGroundOrInChest(buildSpot.getValue(), "I cannot find any @@@cobblestone");
                } else {
                    helper.navigateTo(buildSpot.getKey(), p -> placeBuildingBlock(p, buildSpot.getValue()));
                }
            } else {
                helper.taskIsDone();
            }
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        stage = tag.getInteger("stage");
        if (tag.hasKey("workPos")) {
            relativePos = BlockPos.fromLong(tag.getLong("workPos"));
        } else {
            relativePos = null;
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        tag.setInteger("stage", stage);
        if (relativePos != null) {
            tag.setLong("workPos", relativePos.toLong());
        }
    }
}