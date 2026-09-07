package com.miaokatze.gtsr.common.crossmod.ae2;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

import net.minecraftforge.fluids.FluidStack;

import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.IHubArray;

import appeng.api.AEApi;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Actionable;
import appeng.api.config.StorageFilter;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitorHandlerReceiver;
import appeng.api.storage.IStorageBusMonitor;
import appeng.api.storage.StorageChannel;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.storage.data.IItemList;
import appeng.util.item.AEFluidStack;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/**
 * 枢纽阵列（蒸汽/蓄水）控制器 → ME 网络的轮询式流体监控器（类 TTFT：存储总线贴控制器即可读写其内部流体）。
 *
 * 零侵入契约：不持久化任何 AE2 引用、不在 TileEntity 生命周期挂点；每次操作经 {@link #hub()} 活取
 * metaTileEntity 并按 {@link IHubArray} 判别，控制器被拆除/替换时自动退化为空库存。
 * 读写优先直接调 {@link IHubArray#receiveFluid}/{@link IHubArray#extractFluid} 让实现自决
 * （蒸汽枢纽内部自带蒸汽族白名单与单类型锁；蓄水枢纽接受任意流体并由单类型锁把关）。
 *
 * onTick 轮询契约（存储总线 PartStorageBus.tickingRequest → monitor.onTick，AE2U rv3-beta-1050
 * PartStorageBus:532-543，返回 TickRateModulation 而非 boolean）：缓存 (fluidName, amount) 对比，
 * 有变更先经 postChange 通知监听器再返回 URGENT，无变更返回 SLOWER（镜像 AE2U 内置
 * MEMonitorIFluidHandler:156-195）。
 */
public class HubArrayMEInventory implements IStorageBusMonitor<IAEFluidStack> {

    private final BaseMetaTileEntity tile;
    private final HashMap<IMEMonitorHandlerReceiver<IAEFluidStack>, Object> listeners = new HashMap<>();
    private BaseActionSource actionSource = null;
    // 存储总线过滤设置（PartStorageBus getInternalHandler 每次重建缓存时回设，AE2U PartStorageBus:654-657）。
    // 本库存单流体且全量可抽取，EXTRACTABLE_ONLY 与 REPORTABLE_ONLY 上报内容一致，故仅存契约不参与过滤。
    private StorageFilter mode = StorageFilter.EXTRACTABLE_ONLY;
    /** 轮询缓存：上次已上报内容按 (fluidName, amount) 记账；cacheValid=false 表示上次为空。 */
    private boolean cacheValid = false;
    private String cachedFluidName = null;
    private long cachedAmount = 0;
    /** 负侧 diff 用的完整拷贝（保留 NBT），对比仍按 (fluidName, amount)。 */
    private IAEFluidStack cachedStack = null;

    HubArrayMEInventory(BaseMetaTileEntity tile) {
        this.tile = tile;
    }

    /** 活取枢纽实现：只认 BaseMetaTileEntity 上的 IHubArray，机器被拆/换后返回 null。 */
    private IHubArray hub() {
        if (tile == null) return null;
        IMetaTileEntity metaTileEntity = tile.getMetaTileEntity();
        return metaTileEntity instanceof IHubArray hub ? hub : null;
    }

    /**
     * 读取枢纽当前所存流体（类型+数量，数量已由两实现 clamp 到 Integer.MAX_VALUE）。
     * getStoredFluidStack 在 IHubArray 接口之外，按具体类分派（MTESteamHubArray:615-619 /
     * MTEWaterHubArray:597-601），不为此扩接口。
     */
    private static FluidStack readStoredFluidStack(IHubArray hub) {
        if (hub instanceof MTESteamHubArray steamHub) return steamHub.getStoredFluidStack();
        if (hub instanceof MTEWaterHubArray waterHub) return waterHub.getStoredFluidStack();
        return null;
    }

    @Override
    public IItemList<IAEFluidStack> getStorageList() {
        IItemList<IAEFluidStack> fluidList = AEApi.instance()
            .storage()
            .createFluidList();
        FluidStack stored = readStoredFluidStack(hub());
        if (stored != null) {
            fluidList.add(AEFluidStack.create(stored));
        }
        return fluidList;
    }

    @Override
    public IItemList<IAEFluidStack> getAvailableItems(IItemList<IAEFluidStack> out, int iteration) {
        FluidStack stored = readStoredFluidStack(hub());
        if (stored != null) {
            IAEFluidStack stack = AEFluidStack.create(stored);
            stack.setStackSize(stored.amount);
            out.add(stack);
        }
        return out;
    }

    @Override
    public IAEFluidStack injectItems(IAEFluidStack input, Actionable type, BaseActionSource src) {
        if (input == null) return null;
        IHubArray hub = hub();
        if (hub == null) return input;
        FluidStack fluidStack = input.getFluidStack();
        if (fluidStack == null || fluidStack.getFluid() == null) return null;
        // AE2 请求量为 long、枢纽入参为 int：按 Integer.MAX_VALUE 钳制，余量经返回栈回账
        FluidStack toFill = fluidStack.copy();
        toFill.amount = (int) Math.min(input.getStackSize(), Integer.MAX_VALUE);
        boolean modulate = type != Actionable.SIMULATE;
        // SIMULATE 不落账（doFill=false）；蒸汽族白名单/单类型锁/容量由枢纽实现自决，拒收返回原栈
        int accepted = hub.receiveFluid(toFill, modulate);
        if (modulate) {
            if (accepted > 0 && tile != null) tile.markDirty();
            onTick();
        }
        if (accepted <= 0) return input;
        if (accepted >= input.getStackSize()) return null;
        IAEFluidStack remainder = input.copy();
        remainder.setStackSize(input.getStackSize() - accepted);
        return remainder;
    }

    @Override
    public IAEFluidStack extractItems(IAEFluidStack request, Actionable type, BaseActionSource src) {
        if (request == null) return null;
        IHubArray hub = hub();
        if (hub == null) return null;
        FluidStack requested = request.getFluidStack();
        if (requested == null || requested.getFluid() == null) return null;
        // extractFluid 无类型参数（抽出即所存类型），包装器按所存类型门控，防止网络按请求类型错账
        FluidStack stored = readStoredFluidStack(hub);
        if (stored == null || !stored.isFluidEqual(requested)) return null;
        // AE2 long 请求按 Integer.MAX_VALUE 钳制；SIMULATE 不落账（doDrain=false）
        int maxDrain = (int) Math.min(request.getStackSize(), Integer.MAX_VALUE);
        boolean modulate = type != Actionable.SIMULATE;
        FluidStack drained = hub.extractFluid(maxDrain, modulate);
        if (drained == null || drained.amount <= 0) return null;
        if (modulate) {
            if (tile != null) tile.markDirty();
            onTick();
        }
        if (drained.amount >= request.getStackSize()) return request.copy();
        IAEFluidStack result = request.copy();
        result.setStackSize(drained.amount);
        return result;
    }

    @Override
    public boolean canAccept(IAEFluidStack input) {
        if (input == null) return false;
        IHubArray hub = hub();
        if (hub == null) return false;
        FluidStack fluidStack = input.getFluidStack();
        if (fluidStack == null || fluidStack.getFluid() == null) return false;
        // 判别让实现自决：SIMULATE 1L 直试（蒸汽族白名单/任意流体、单类型锁、容量均由枢纽内部把关）
        FluidStack probe = fluidStack.copy();
        probe.amount = 1;
        if (hub.receiveFluid(probe, false) > 0) return true;
        // 满仓但同类型：与 GT5U TFFT canAccept 的 contains 语义一致（该类型已通过白名单入库）
        FluidStack stored = readStoredFluidStack(hub);
        return stored != null && stored.isFluidEqual(probe);
    }

    @Override
    public boolean isPrioritized(IAEFluidStack input) {
        return canAccept(input);
    }

    @Override
    public AccessRestriction getAccess() {
        return AccessRestriction.READ_WRITE;
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public int getSlot() {
        return 0;
    }

    @Override
    public boolean validForPass(int i) {
        return true;
    }

    @Override
    public StorageChannel getChannel() {
        return StorageChannel.FLUIDS;
    }

    @Override
    public IAEStackType<?> getStackType() {
        return FLUID_STACK_TYPE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addListener(IMEMonitorHandlerReceiver l, Object verificationToken) {
        listeners.put(l, verificationToken);
    }

    @Override
    public void removeListener(IMEMonitorHandlerReceiver l) {
        listeners.remove(l);
    }

    /** 遍历通知：isValid 失效的监听器（令牌不符，如存储总线缓存重建）就地移除（AE2U MEMonitorIFluidHandler:197-211）。 */
    private void notifyListeners(Iterable<IAEFluidStack> change) {
        Iterator<Map.Entry<IMEMonitorHandlerReceiver<IAEFluidStack>, Object>> iterator = listeners.entrySet()
            .iterator();
        while (iterator.hasNext()) {
            Map.Entry<IMEMonitorHandlerReceiver<IAEFluidStack>, Object> entry = iterator.next();
            IMEMonitorHandlerReceiver<IAEFluidStack> receiver = entry.getKey();
            if (receiver.isValid(entry.getValue())) {
                receiver.postChange(this, change, actionSource);
            } else {
                iterator.remove();
            }
        }
    }

    @Override
    public TickRateModulation onTick() {
        FluidStack current = readStoredFluidStack(hub());
        boolean hasContent = current != null;

        if (hasContent != cacheValid || (hasContent && (cachedStack == null || cachedAmount != current.amount
            || !Objects.equals(
                cachedFluidName,
                current.getFluid()
                    .getName())))) {
            IItemList<IAEFluidStack> changes = AEApi.instance()
                .storage()
                .createFluidList();
            if (cacheValid && cachedStack != null) {
                IAEFluidStack removed = cachedStack.copy();
                removed.setStackSize(-removed.getStackSize());
                changes.add(removed);
            }
            if (hasContent) {
                changes.add(AEFluidStack.create(current));
            }
            for (Iterator<IAEFluidStack> iterator = changes.iterator(); iterator.hasNext();) {
                if (iterator.next()
                    .getStackSize() == 0L) {
                    iterator.remove();
                }
            }
            cacheValid = hasContent;
            cachedFluidName = hasContent ? current.getFluid()
                .getName() : null;
            cachedAmount = hasContent ? current.amount : 0;
            cachedStack = hasContent ? AEFluidStack.create(current) : null;
            if (!changes.isEmpty()) {
                notifyListeners(changes);
                return TickRateModulation.URGENT;
            }
        }
        return TickRateModulation.SLOWER;
    }

    @Override
    public void setMode(StorageFilter mode) {
        this.mode = mode;
    }

    @Override
    public void setActionSource(BaseActionSource mySource) {
        this.actionSource = mySource;
    }
}
