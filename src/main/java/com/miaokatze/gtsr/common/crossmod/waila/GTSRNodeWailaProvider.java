package com.miaokatze.gtsr.common.crossmod.waila;

import java.util.List;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.miaokatze.gtsr.common.machine.base.MTEFilteredCacheNode;
import com.miaokatze.gtsr.common.machine.base.MTERemoteWorkerNode;

import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import mcp.mobius.waila.api.IWailaConfigHandler;
import mcp.mobius.waila.api.IWailaDataAccessor;
import mcp.mobius.waila.api.IWailaDataProvider;

/**
 * GTSR 节点自定义名的 WAILA 显示 provider（参照 GT5U GregtechTEWailaDataProvider 结构）。
 * <p>
 * 工作机制：Waila 服务端每间隔约 250ms 对玩家准星指向的 TE 调用 {@link #getNBTData} 收集自定义 NBT
 * 并同步到客户端缓存；客户端渲染 tooltip 时调 {@link #getWailaHead}，
 * 从 accessor 的 NBT 缓存取回自定义名并替换头部第一行（方块名）。
 * 该链路自带同步，与 GT5U description packet 机制相互独立，改名后最多一个同步周期即生效。
 * <p>
 * Waila 1.19.30 的 IWailaDataProvider 中 getWailaStack/getWailaHead/getWailaBody/getWailaTail/getNBTData
 * 为抽象方法（无默认实现），必须全量覆写；hasWailaAdvancedBody/getWailaAdvancedBody 为默认方法，不需覆写。
 */
public class GTSRNodeWailaProvider implements IWailaDataProvider {

    /** Waila NBT 同步通道中自定义名的标签 key。 */
    private static final String TAG_CUSTOM_NAME = "gtsr.customName";

    @Override
    public ItemStack getWailaStack(IWailaDataAccessor accessor, IWailaConfigHandler config) {
        // 不覆盖 Waila 默认的方块取样逻辑
        return null;
    }

    @Override
    public List<String> getWailaHead(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        // 客户端调用：NBT 缓存中有自定义名时替换头部第一行（默认方块名）
        NBTTagCompound nbt = accessor.getNBTData();
        if (nbt != null && nbt.hasKey(TAG_CUSTOM_NAME) && !currenttip.isEmpty()) {
            currenttip.set(0, nbt.getString(TAG_CUSTOM_NAME));
        }
        return currenttip;
    }

    @Override
    public List<String> getWailaBody(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        // 不修改正文，GT5U 自身的 body provider 照常工作
        return currenttip;
    }

    @Override
    public List<String> getWailaTail(ItemStack itemStack, List<String> currenttip, IWailaDataAccessor accessor,
        IWailaConfigHandler config) {
        // 不修改尾部（mod 归属行）
        return currenttip;
    }

    @Override
    public NBTTagCompound getNBTData(EntityPlayerMP player, TileEntity tile, NBTTagCompound tag, World world, int x,
        int y, int z) {
        // 服务端调用：目标是两类节点基类（采矿/钻井节点与 4 种缓存节点）时写入自定义名；
        // 自定义名为空时不写 key，客户端取不到 key 即保持默认名显示
        if (tile instanceof IGregTechTileEntity gte) {
            IMetaTileEntity mte = gte.getMetaTileEntity();
            String customName = null;
            if (mte instanceof MTERemoteWorkerNode workerNode) {
                customName = workerNode.getCustomName();
            } else if (mte instanceof MTEFilteredCacheNode cacheNode) {
                customName = cacheNode.getCustomName();
            }
            if (customName != null && !customName.isEmpty()) {
                tag.setString(TAG_CUSTOM_NAME, customName);
            }
        }
        return tag;
    }
}
