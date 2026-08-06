package com.miaokatze.gtsr.common.machine.base;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;
import gregtech.api.util.GTUtility;

/**
 * 旧 ID 占位转换器（V2 meta 迁移）。
 * <p>
 * 注册于全部非重置机的旧 V1 绝对 ID 上（BASE_V1 = 14620 号段），构造即注册，仅服务端有效语义。
 * 双通道触发换机：
 * <ul>
 * <li>存档加载通道：旧 TE 加载时由 GT5U 构造占位实例并调用 {@link #loadNBTData}，天然仅服务端；
 * <li>旧物品放置通道：占位实例首个 tick 的 {@link #onFirstTick}（服务端门控）。
 * </ul>
 * 两条通道最终都调用 {@link #convert}：经 GT5U 公开换机入口
 * {@code te.setInitialValuesAsNBT(null, newId)} 把 TE 的 mID 改为新 ID 并重建新机实例
 * （mTickTimer 归零，新机 onFirstTick 自动补跑），再搬运物品栏与私有 NBT。
 */
public class MTELegacyConverter extends MetaTileEntity {

    private int targetId;

    public MTELegacyConverter(int aId, String aBasicName, String aRegionalName, int aTargetId) {
        super(aId, aBasicName, aRegionalName, 0);
        this.targetId = aTargetId;
    }

    private MTELegacyConverter(String aName, int aInvSlotCount) {
        super(aName, aInvSlotCount);
    }

    @Override
    public MetaTileEntity newMetaEntity(IGregTechTileEntity aTileEntity) {
        MTELegacyConverter c = new MTELegacyConverter(mName, 0);
        c.targetId = this.targetId;
        return c;
    }

    @Override
    public void loadNBTData(NBTTagCompound aNBT) {
        convert(aNBT);
    }

    @Override
    public void onFirstTick(IGregTechTileEntity aBaseMetaTileEntity) {
        if (aBaseMetaTileEntity.isServerSide()) {
            convert(null);
        }
    }

    @Override
    public void saveNBTData(NBTTagCompound aNBT) {}

    @Override
    public byte getTileEntityBaseType() {
        return 0;
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity aBaseMetaTileEntity, ForgeDirection side, ForgeDirection facing,
        int colorIndex, boolean active, boolean redstoneLevel) {
        return new ITexture[0];
    }

    @Override
    public String[] getDescription() {
        return new String[0];
    }

    @Override
    public boolean allowPullStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    @Override
    public boolean allowPutStack(IGregTechTileEntity aBaseMetaTileEntity, int aIndex, ForgeDirection side,
        ItemStack aStack) {
        return false;
    }

    /**
     * 换机核心序列。aNBT 非 null 时为存档加载通道（原始 TE 私有 tag），
     * null 时为物品放置通道（无私有数据，仅执行换机）。
     */
    private void convert(NBTTagCompound aNBT) {
        IGregTechTileEntity te = getBaseMetaTileEntity();
        if (te == null) return;

        ItemStack[] inv = getRealInventory();
        ItemStack[] invCopy = inv == null ? null : inv.clone();

        te.setInitialValuesAsNBT(null, (short) targetId);

        IMetaTileEntity n = te.getMetaTileEntity();
        if (n != null) {
            ItemStack[] nInv = n.getRealInventory();
            if (invCopy != null && nInv != null) {
                System.arraycopy(invCopy, 0, nInv, 0, Math.min(invCopy.length, nInv.length));
            }
            if (aNBT != null) {
                if (nInv != null) {
                    restoreInventoryFromNBT(aNBT, nInv);
                }
                n.loadNBTData(aNBT);
            }
        }

        te.issueTileUpdate();
        te.issueTextureUpdate();
        if (te instanceof TileEntity) {
            ((TileEntity) te).markDirty();
        }
    }

    /**
     * 存档加载通道：占位实例注册时槽位数为 0，GT5U 不会把旧机器物品装入占位实例的物品栏
     * （CommonBaseMetaTileEntity#loadMetaTileNBT 按槽位索引写入），因此物品必须直接从
     * 原始 TE NBT 的 "Inventory" tag 还原进新机实例（与 GT5U 自身加载路径同构）。
     */
    private static void restoreInventoryFromNBT(NBTTagCompound aNBT, ItemStack[] nInv) {
        NBTTagList tItemList = aNBT.getTagList("Inventory", 10);
        for (int i = 0; i < tItemList.tagCount(); i++) {
            NBTTagCompound tTag = tItemList.getCompoundTagAt(i);
            int tSlot = tTag.getInteger("IntSlot");
            if (tSlot >= 0 && tSlot < nInv.length) {
                nInv[tSlot] = GTUtility.loadItem(tTag);
            }
        }
    }
}
