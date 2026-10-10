package com.miaokatze.gtsr.common.dimension.prosperity.altar;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;

import com.miaokatze.gtsr.common.blocks.TileRunawaySingularity;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeTravel;

import thaumcraft.api.aspects.Aspect;
import thaumcraft.common.items.wands.ItemWandCasting;

/** Persisted altar state; never executes the parent expiry, destruction or damage logic. */
public final class TileSpacetimeAltar extends TileRunawaySingularity {

    public static final int ACTIVATION_TICKS = 200;
    private String instance = "";
    private boolean paid;
    private int activationTicks;

    public TileSpacetimeAltar() {
        setParams(12, 0, 0, -1, ATTRIBUTE_NULL_PLUS, "light_blue", 12);
        setDestroyBlocks(false);
    }

    public String instanceId() {
        return instance;
    }

    public void bind(String id) {
        instance = id;
        sync();
    }

    public boolean isPaid() {
        return paid;
    }

    public boolean isActive() {
        return paid && activationTicks >= ACTIVATION_TICKS;
    }

    @Override
    public double getActiveFactor() {
        return paid ? activationTicks / (double) ACTIVATION_TICKS : 0;
    }

    public void activate(EntityPlayer player) {
        if (worldObj == null || worldObj.isRemote
            || worldObj.provider.dimensionId != 0
            || instance.isEmpty()
            || paid
            || !(player instanceof EntityPlayerMP)
            || player.worldObj != worldObj
            || player.getDistanceSq(xCoord + .5, yCoord + .5, zCoord + .5) > 144) return;
        SpacetimeAltarIndex.Entry entry = SpacetimeAltarIndex.get(worldObj)
            .find(instance);
        if (entry == null || !entry.valid
            || entry.x != xCoord
            || entry.y != yCoord
            || entry.z != zCoord
            || worldObj.getBlock(xCoord, yCoord, zCoord) != SpacetimeAltarBlocks.core) return;
        ItemStack held = player.getHeldItem();
        if (held == null || !(held.getItem() instanceof ItemWandCasting)) {
            player.addChatMessage(new ChatComponentTranslation("gtsr.altar.need_wand"));
            return;
        }
        if (!payVis(held)) {
            player.addChatMessage(new ChatComponentTranslation("gtsr.altar.insufficient_vis"));
            return;
        }
        paid = true;
        activationTicks = 0;
        sync();
        SpacetimeAltarStory.activated((EntityPlayerMP) player, instance);
    }

    /** TC stores centivis. One NBT swap commits exactly 50 of every primal, without discounts. */
    public static boolean payVis(ItemStack stack) {
        if (stack == null || !(stack.getItem() instanceof ItemWandCasting)) return false;
        ItemWandCasting wand = (ItemWandCasting) stack.getItem();
        Aspect[] primals = { Aspect.AIR, Aspect.FIRE, Aspect.WATER, Aspect.EARTH, Aspect.ORDER, Aspect.ENTROPY };
        for (Aspect aspect : primals) if (wand.getVis(stack, aspect) < 5000) return false;
        ItemStack paidCopy = stack.copy();
        for (Aspect aspect : primals) wand.storeVis(paidCopy, aspect, wand.getVis(paidCopy, aspect) - 5000);
        stack.setTagCompound(paidCopy.getTagCompound());
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote || instance.isEmpty()) return;
        if (paid && activationTicks < ACTIVATION_TICKS) {
            activationTicks++;
            markDirty();
            if (activationTicks % 5 == 0) sync();
        }
        if (worldObj.getTotalWorldTime() % 20 == 0) {
            for (Object object : worldObj.playerEntities) {
                EntityPlayerMP player = (EntityPlayerMP) object;
                if (Math.abs(player.posX - (xCoord + .5)) <= 7 && Math.abs(player.posZ - (zCoord + .5)) <= 7
                    && player.posY >= yCoord - 8
                    && player.posY <= yCoord + 7) SpacetimeAltarStory.enter(player, instance);
            }
        }
        if (isActive()) attract();
    }

    private void attract() {
        double cx = xCoord + .5, cy = yCoord + .5, cz = zCoord + .9;
        List<?> nearby = worldObj.getEntitiesWithinAABB(
            EntityPlayerMP.class,
            AxisAlignedBB.getBoundingBox(cx - 4, cy - 8, cz - 6, cx + 4, cy + 5, cz + 6));
        long now = worldObj.getTotalWorldTime();
        int handled = 0;
        for (Object object : nearby) {
            EntityPlayerMP player = (EntityPlayerMP) object;
            if (++handled > 64) break;
            if (!SpacetimeTravel.eligible(player) || player.isSneaking()
                || player.ridingEntity != null
                || player.riddenByEntity != null
                || player.capabilities.isCreativeMode && player.capabilities.isFlying
                || player.getEntityData()
                    .getLong("gtsrSpacetimeCooldownUntil") > now)
                continue;
            // Lift in the open front corridor first, then pass through the ring centre.
            double tx = cx, ty = cy - .8, tz = cz;
            if (player.posY < cy - 1) tz = cz - 3;
            double dx = tx - player.posX, dz = tz - player.posZ, length = Math.hypot(dx, dz);
            double scale = length > .12 ? .12 / length : 1;
            dx *= scale;
            dz *= scale;
            double dy = Math.max(-.16, Math.min(.16, ty - player.posY));
            AxisAlignedBB swept = player.boundingBox.addCoord(dx, dy, dz);
            if (!worldObj.getCollidingBoundingBoxes(player, swept)
                .isEmpty()) continue;
            player.motionX = dx;
            player.motionY = dy;
            player.motionZ = dz;
            player.fallDistance = 0;
            player.velocityChanged = true;
            AxisAlignedBB core = AxisAlignedBB
                .getBoundingBox(cx - .55, cy - .55, cz - .55, cx + .55, cy + .55, cz + .55);
            if (player.boundingBox.intersectsWith(core)) {
                long retry = instance.equals(
                    player.getEntityData()
                        .getString("gtsrAltarRetryInstance")) ? player.getEntityData()
                            .getLong("gtsrAltarRetryUntil") : 0;
                player.motionX = player.motionY = player.motionZ = 0;
                if (retry <= now) {
                    player.getEntityData()
                        .setString("gtsrAltarRetryInstance", instance);
                    player.getEntityData()
                        .setLong("gtsrAltarRetryUntil", now + 20);
                    if (SpacetimeTravel.enterProsperity(player, true)) player.getEntityData()
                        .setLong("gtsrSpacetimeCooldownUntil", player.worldObj.getTotalWorldTime() + 60);
                }
            }
        }
    }

    private void sync() {
        markDirty();
        if (worldObj != null && !worldObj.isRemote) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        tag.setString("altarInstance", instance);
        tag.setBoolean("altarPaid", paid);
        tag.setInteger("altarTicks", activationTicks);
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        instance = tag.getString("altarInstance");
        paid = tag.getBoolean("altarPaid");
        activationTicks = Math.max(0, Math.min(ACTIVATION_TICKS, tag.getInteger("altarTicks")));
        setParams(12, 0, 0, -1, ATTRIBUTE_NULL_PLUS, "light_blue", 12);
        setDestroyBlocks(false);
    }
}
