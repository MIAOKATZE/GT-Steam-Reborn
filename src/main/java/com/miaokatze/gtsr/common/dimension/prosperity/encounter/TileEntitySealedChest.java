package com.miaokatze.gtsr.common.dimension.prosperity.encounter;

import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/** No inventory exists while sealed: automation cannot extract a deferred roll. */
public class TileEntitySealedChest extends TileEntity {

    private int tier = 1, platform = -1, openingTicks = -1;
    private String encounter = "";
    private boolean clickUnlock;
    private int storyEvent = -1;
    private String storyRelic = "";
    private int gateLayoutVersion, gateZone = -1;

    public void configureRuinGate(int layout, int zone) {
        gateLayoutVersion = layout;
        gateZone = zone;
        clickUnlock = false;
        markDirty();
    }

    public int getTier() {
        return tier;
    }

    public int getPlatformId() {
        return platform;
    }

    public String getEncounterId() {
        return encounter;
    }

    public int getOpeningTicks() {
        return openingTicks;
    }

    public void initialize(int t, String id, int p) {
        tier = Math.max(1, Math.min(5, t));
        encounter = id;
        platform = p;
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public void initializeClickUnlock(int t, String id) {
        clickUnlock = true;
        initialize(t, id, -1);
    }

    /** The story witness is deferred together with the base tier roll until the seal has opened. */
    public void initializeStoryCache(int t, String id, int event, String relic) {
        initializeClickUnlock(t, id);
        storyEvent = event >= 8 && event <= 10 ? event : -1;
        setStoryRelic(relic);
    }

    public void setStoryRelic(String relic) {
        storyRelic = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry.RELICS.containsKey(relic) ? relic
            : "";
        markDirty();
    }

    private boolean allowed() {
        if (gateLayoutVersion >= 2) return com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinObjectives
            .isChestReady(worldObj, encounter, gateZone);
        if (clickUnlock) return openingTicks >= 0;
        if (tier == 1) return openingTicks >= 0;
        if (encounter.isEmpty()) return false;
        if (encounter.startsWith("echo:"))
            return com.miaokatze.gtsr.common.dimension.prosperity.echo.RuinsEncounterData.get(worldObj)
                .dead(encounter, 0);
        ForgottenLakeEncounterData d = ForgottenLakeEncounterData.get(worldObj);
        return platform < 0 ? d.kingDead(encounter) : d.platformCleared(encounter, platform);
    }

    public void tryUnlockByClick() {
        if (gateLayoutVersion >= 2) {
            if (allowed() && openingTicks < 0) startOpening();
            return;
        }
        if ((tier == 1 || clickUnlock) && openingTicks < 0) startOpening();
    }

    private void startOpening() {
        openingTicks = 0;
        worldObj.playSoundEffect(xCoord + .5, yCoord + .5, zCoord + .5, "portal.trigger", .45F, 1.5F);
        markDirty();
        worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
    }

    public static ItemStack lootForTier(int tier) {
        switch (tier) {
            case 1:
                return new ItemStack(Items.iron_ingot);
            case 2:
                return new ItemStack(Items.gold_ingot);
            case 3:
                return new ItemStack(Items.diamond);
            case 4:
                return new ItemStack(Blocks.diamond_block);
            case 5:
                return new ItemStack(Blocks.emerald_block);
            default:
                throw new IllegalArgumentException("tier");
        }
    }

    public void updateEntity() {
        if (worldObj.isRemote) {
            if (openingTicks >= 0 && openingTicks < 60) {
                openingTicks++;
                double angle = openingTicks * .65;
                worldObj.spawnParticle(
                    "enchantmenttable",
                    xCoord + .5 + Math.cos(angle) * .6,
                    yCoord + .7,
                    zCoord + .5 + Math.sin(angle) * .6,
                    0,
                    .02,
                    0);
            }
            return;
        }
        if (openingTicks < 0) {
            if (allowed()) startOpening();
            return;
        }
        if (++openingTicks < 60) {
            markDirty();
            if (openingTicks % 5 == 0) worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
            return;
        }
        if (worldObj.getBlock(xCoord, yCoord, zCoord) != ForgottenLakeEncounterRegistry.sealedChest) return;
        worldObj.playSoundEffect(xCoord + .5, yCoord + .5, zCoord + .5, "random.levelup", .65F, .8F);
        if (worldObj
            .setBlock(xCoord, yCoord, zCoord, ForgottenLakeEncounterRegistry.unsealedChest, getBlockMetadata(), 3)) {
            TileEntity t = worldObj.getTileEntity(xCoord, yCoord, zCoord);
            if (t instanceof TileEntityUnsealedChest) {
                TileEntityUnsealedChest chest = (TileEntityUnsealedChest) t;
                chest.setInventorySlotContents(13, lootForTier(tier));
                chest.setStoryOrigin(encounter, storyEvent);
                net.minecraft.item.Item relic = com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreRegistry.RELICS
                    .get(storyRelic);
                if (relic != null) {
                    ItemStack witness = new ItemStack(relic);
                    if (storyEvent >= 8 && storyEvent <= 10) {
                        NBTTagCompound proof = new NBTTagCompound();
                        proof.setInteger("gtsr.storyCache", storyEvent);
                        proof.setString("gtsr.storyOrigin", encounter);
                        witness.setTagCompound(proof);
                    }
                    chest.setInventorySlotContents(12, witness);
                }
                chest.markDirty();
            }
        }
    }

    public void readFromNBT(NBTTagCompound n) {
        super.readFromNBT(n);
        tier = n.hasKey("tier") ? n.getInteger("tier") : 1;
        platform = n.hasKey("platform") ? n.getInteger("platform") : -1;
        encounter = n.getString("encounter");
        clickUnlock = n.getBoolean("clickUnlock");
        storyEvent = n.hasKey("storyEvent") ? n.getInteger("storyEvent") : -1;
        storyRelic = n.getString("storyRelic");
        gateLayoutVersion = n.getInteger("gateLayoutVersion");
        gateZone = n.hasKey("gateZone") ? n.getInteger("gateZone") : -1;
        openingTicks = n.hasKey("opening") ? n.getInteger("opening") : -1;
    }

    public void writeToNBT(NBTTagCompound n) {
        super.writeToNBT(n);
        n.setInteger("tier", tier);
        n.setInteger("platform", platform);
        n.setString("encounter", encounter);
        n.setBoolean("clickUnlock", clickUnlock);
        n.setInteger("storyEvent", storyEvent);
        n.setString("storyRelic", storyRelic);
        n.setInteger("opening", openingTicks);
        n.setInteger("gateLayoutVersion", gateLayoutVersion);
        n.setInteger("gateZone", gateZone);
    }

    public Packet getDescriptionPacket() {
        NBTTagCompound n = new NBTTagCompound();
        writeToNBT(n);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, n);
    }

    public void onDataPacket(NetworkManager n, S35PacketUpdateTileEntity p) {
        readFromNBT(p.func_148857_g());
    }
}
