package com.miaokatze.gtsr.common.gui.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import com.cleanroommc.modularui.utils.item.IItemHandler;
import com.cleanroommc.modularui.utils.item.InvWrapper;
import com.cleanroommc.modularui.utils.item.SlotItemHandler;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;

import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 地壳物质聚合器「终端配置界面」原版 Container（terminal-native-ui N36，PLAN §4.4）。
 * <p>
 * 槽位序核对表（inventorySlots 下标 = Container slot index，UI 序=旧 MUI2 槽序）：
 * <table>
 * <tr>
 * <th>index</th>
 * <th>UI 序</th>
 * <th>数据源</th>
 * <th>语义</th>
 * </tr>
 * <tr>
 * <td>0</td>
 * <td>槽 1</td>
 * <td>{@code aggregator.inventoryHandler} index=
 * {@code getControllerSlotIndex()}（=mInventory[1]，与主 GUI 同源）</td>
 * <td>控制器槽，limit 1（旧 ModularSlot limit1 :265-270 + filter）</td>
 * </tr>
 * <tr>
 * <td>1-24</td>
 * <td>槽 2-25</td>
 * <td>{@code new InvWrapper(aggregator.getPluginSlotInventory())}</td>
 * <td>插件槽 ×24，limit 1 + isItemValid=isDimensionDisplayItem（客户端即拒 +
 * 服务端 IInventory 适配器兜底双层保留，旧 filter+singletonSlotGroup :514-528）</td>
 * </tr>
 * <tr>
 * <td>25-51</td>
 * <td>背包 27 格</td>
 * <td>{@code player.inventory} index 9-35</td>
 * <td>玩家主背包 3×9</td>
 * </tr>
 * <tr>
 * <td>52-60</td>
 * <td>快捷栏</td>
 * <td>{@code player.inventory} index 0-8</td>
 * <td>玩家快捷栏 1×9</td>
 * </tr>
 * </table>
 * <p>
 * shift-click（transferStackInSlot，PLAN §4.4 语义表）：玩家背包 → 仅尝试插件槽
 * （isItemValid 过滤，逐格放 1——对齐旧 singletonSlotGroup 语义），放不下再尝试控制器槽
 * （同 limit1）；来自插件/控制器槽 → 移回玩家背包；组间不互通（插件↔控制器不互移）；
 * 转移失败不吞物品（vanilla 默认）。拖拽（drag-split）走 vanilla 默认（分发受
 * getSlotStackLimit 天然钳制）。快速移动后矿池不自动重建的语义保留：插件槽适配器仅
 * markPoolDirty（下次 checkProcessing 惰性重建），浏览器列表即时刷新仍由 REFRESH_POOL
 * 显式触发（旧注释 :278 明示）。
 * <p>
 * canInteractWith：距离² ≤{@value #MAX_INTERACTION_DISTANCE_SQ} && 基 TE 可访问
 * （canAccessData）&& 机器类 instanceof——与旧 MUI2 工厂基类 canInteractWith（:72-76）语义等价
 * （GTNH 服务端 tick 复核 EntityPlayer.onUpdate→ForgeHooks.canInteractWith→
 * PlayerOpenContainerEvent 走本方法，超距自动关窗口等价旧轨）。
 * <p>
 * vanilla 窗口语义：本 Container 由 FML openGui 双端配对（AggregatorGuiHandler），
 * windowId 由 FML 分配（服务端创建时定号，客户端 OpenGuiHandler 回填同号），
 * 槽内容走原版窗口包同步，关闭走 C0D。
 */
public class ContainerAggregatorConfig extends Container {

    /** 机器槽总数（UI 槽 1-25：index 0 控制器 + index 1-24 插件） */
    public static final int MACHINE_SLOT_COUNT = 25;
    /** 玩家背包槽起始 index（main 3×9 + hotbar 1×9 = 36） */
    public static final int PLAYER_SLOT_START = MACHINE_SLOT_COUNT;
    /** 玩家背包槽结束（exclusive） */
    public static final int PLAYER_SLOT_END = MACHINE_SLOT_COUNT + 36;

    /** 最大交互距离（距离²，与旧 factory MAX_INTERACTION_DISTANCE=64 同值） */
    public static final double MAX_INTERACTION_DISTANCE_SQ = 64.0d;

    /** 面板尺寸（PLAN §4.5-B 冻结 475×350，GUI 绘制侧同源） */
    public static final int PANEL_WIDTH = 475;
    public static final int PANEL_HEIGHT = 350;

    // 左列 5×5 槽网格（维度槽面板外框 (18,58) 158×110 内均匀展开：
    // 槽区 x24..170（列距 32）/ y63..161（行距 20），格内物品区 16×16；
    // 公共常量即唯一权威，GUI 绘制（槽框/面板/命中提示）同源引用本公式）
    public static final int GRID_X = 24;
    public static final int GRID_Y = 63;
    public static final int GRID_PITCH_X = 32;
    public static final int GRID_PITCH_Y = 20;

    /** 玩家背包 162×76 右下（PLAN §4.5-B：x=PANEL_W-172=303, y=PANEL_H-86=264） */
    private static final int PLAYER_INV_X = PANEL_WIDTH - 172;
    private static final int PLAYER_INV_Y = PANEL_HEIGHT - 86;

    private final MTECrustMatterAggregator aggregator;

    public ContainerAggregatorConfig(EntityPlayer player, MTECrustMatterAggregator aggregator) {
        this.aggregator = aggregator;
        // index 0：控制器槽（UI 槽 1，网格位 0）——mInventory[1] 同源，limit 1
        this.addSlotToContainer(
            new ControllerSlot(aggregator.inventoryHandler, aggregator.getControllerSlotIndex(), GRID_X, GRID_Y));
        // index 1-24：插件槽（UI 槽 2-25，网格位 1-24）——IInventory 适配器包装，limit 1 + 维度显示过滤
        IInventory pluginInventory = aggregator.getPluginSlotInventory();
        for (int i = 0; i < 24; i++) {
            final int grid = i + 1;
            this.addSlotToContainer(
                new PluginSlot(
                    new InvWrapper(pluginInventory),
                    i,
                    GRID_X + (grid % 5) * GRID_PITCH_X,
                    GRID_Y + (grid / 5) * GRID_PITCH_Y));
        }
        // index 25-51：玩家主背包 3×9（slot index 9-35）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlotToContainer(
                    new Slot(player.inventory, col + row * 9 + 9, PLAYER_INV_X + col * 18, PLAYER_INV_Y + row * 18));
            }
        }
        // index 52-60：玩家快捷栏 1×9（slot index 0-8）
        for (int col = 0; col < 9; col++) {
            this.addSlotToContainer(new Slot(player.inventory, col, PLAYER_INV_X + col * 18, PLAYER_INV_Y + 58));
        }
    }

    /** 供 GUI/自关锚点复核：当前绑定的聚合器（双端各自的 MTE 实例） */
    public MTECrustMatterAggregator getAggregator() {
        return this.aggregator;
    }

    // ==================== canInteractWith（64 格语义，PLAN §4.1 轨 B） ====================

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        if (player == null || !player.isEntityAlive()) {
            return false;
        }
        IGregTechTileEntity base = this.aggregator.getBaseMetaTileEntity();
        if (base == null || !base.canAccessData()) {
            return false;
        }
        if (!(base.getMetaTileEntity() instanceof MTECrustMatterAggregator)) {
            return false;
        }
        return player.getDistanceSq(base.getXCoord() + 0.5d, base.getYCoord() + 0.5d, base.getZCoord() + 0.5d)
            <= MAX_INTERACTION_DISTANCE_SQ;
    }

    // ==================== shift-click（PLAN §4.4 语义表，唯一手工映射的原生交互） ====================

    @Override
    public ItemStack transferStackInSlot(EntityPlayer player, int index) {
        Slot slot = (Slot) this.inventorySlots.get(index);
        if (slot == null || !slot.getHasStack()) {
            return null;
        }
        ItemStack stack = slot.getStack();
        ItemStack remainder = stack.copy();
        if (index < PLAYER_SLOT_START) {
            // 机器槽（控制器/插件）→ 玩家背包（reverse=true：快捷栏优先，vanilla 同款）
            if (!this.mergeItemStack(stack, PLAYER_SLOT_START, PLAYER_SLOT_END, true)) {
                return null;
            }
        } else {
            // 玩家背包 → 仅维度显示物品可进机器槽；组间不互通（插件↔控制器不互移）
            if (!MTECrustMatterAggregator.isDimensionDisplayItem(stack)) {
                return null;
            }
            // 先插件槽逐格放 1（isItemValid 过滤 + singleton limit1 语义），再控制器槽（同 limit1）
            if (!this.mergeOnePerSlot(stack, 1, MACHINE_SLOT_COUNT - 1) && !this.mergeOnePerSlot(stack, 0, 0)) {
                return null;
            }
        }
        if (stack.stackSize == 0) {
            slot.putStack(null);
        } else {
            slot.onSlotChanged();
        }
        if (stack.stackSize == remainder.stackSize) {
            return null; // 一件未动：不吞物品
        }
        slot.onPickupFromSlot(player, stack);
        return remainder;
    }

    /**
     * 逐格放 1 的合并（对齐旧 singletonSlotGroup：每格至多 1 个，可跨格摊开）。
     * 目标格必须为空且 {@link Slot#isItemValid}（=维度显示过滤）通过。
     */
    private boolean mergeOnePerSlot(ItemStack stack, int fromIndex, int toIndex) {
        boolean moved = false;
        for (int i = fromIndex; i <= toIndex && stack.stackSize > 0; i++) {
            Slot target = (Slot) this.inventorySlots.get(i);
            if (target == null || target.getHasStack() || !target.isItemValid(stack)) {
                continue;
            }
            ItemStack one = stack.copy();
            one.stackSize = 1;
            target.putStack(one);
            stack.stackSize -= 1;
            moved = true;
        }
        return moved;
    }

    // ==================== 槽位定义（PLAN §4.4 映射表实现） ====================

    /** 控制器槽（index 0）：limit 1 + isItemValid=isDimensionDisplayItem（客户端即拒；服务端同构生效）。 */
    private static final class ControllerSlot extends SlotItemHandler {

        ControllerSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return MTECrustMatterAggregator.isDimensionDisplayItem(stack);
        }
    }

    /** 插件槽（index 1-24）：limit 1 + isItemValid=isDimensionDisplayItem（双层之一；服务端 IInventory 适配器兜底）。 */
    private static final class PluginSlot extends SlotItemHandler {

        PluginSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public int getSlotStackLimit() {
            return 1;
        }

        @Override
        public boolean isItemValid(ItemStack stack) {
            return MTECrustMatterAggregator.isDimensionDisplayItem(stack);
        }
    }
}
