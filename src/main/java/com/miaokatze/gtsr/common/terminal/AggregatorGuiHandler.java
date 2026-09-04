package com.miaokatze.gtsr.common.terminal;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

import com.miaokatze.gtsr.common.gui.terminal.ContainerAggregatorConfig;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.main.ClientProxy;
import com.miaokatze.gtsr.main.GTSteamReborn;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.ModContainer;
import cpw.mods.fml.common.network.IGuiHandler;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 聚合器终端配置界面的 FML 原生 {@link IGuiHandler}（terminal-native-ui N35，PLAN §4.1 轨 B）。
 * <p>
 * 双端 openGui 配对（windowId 由 FML 分配、槽内容走原版窗口包、关闭走 C0D）：
 * <ul>
 * <li><b>服务端</b>（{@code getServerGuiElement}）：TE 解析 + 守卫逐字对齐旧 MUI2 工厂 open
 * 语义（EntityPlayerMP / 非 FakePlayer / 基 TE 判空），通过后
 * {@code new ContainerAggregatorConfig(player, aggregator)}；</li>
 * <li><b>客户端</b>（{@code getClientGuiElement}）：经 {@code main/ClientProxy} 静态委托方法
 * 构造 client 包内 Gui 实例（{@code GuiAggregatorConfigScreen}，内部自带 Container）。</li>
 * </ul>
 * <p>
 * <b>服务端不加载论证（javadoc 载证，PLAN §4.7 客户端类隔离纪律）</b>：
 * {@code getClientGuiElement} 仅在客户端被 FML 调用（FMLNetworkHandler.openGui 的
 * {@code !world.isRemote} 分支只走 getServerGuiElement；客户端分支
 * NetworkRegistry.getLocalGuiContainer → getClientGuiElement → FMLCommonHandler.showGuiScreen）。
 * 本方法体对 {@code ClientProxy} 仅为 <b>惰性符号引用</b>——JVM 常量池解析在方法首次执行时发生，
 * 服务端永不执行该方法体，故 {@code com.miaokatze.gtsr.main.ClientProxy} 及其 client 类引用
 * 在专用服务器上零类加载（GT5U/GTSWN IGuiHandler 同款惯例）。本文件
 * <b>零 {@code net.minecraft.client} import</b>；客户端实例只在 ClientProxy 内构造（§4.7-4）。
 * <p>
 * 注册（M7）：{@code CommonProxy.init} 中
 * {@code NetworkRegistry.INSTANCE.registerGuiHandler(modInstance(), new AggregatorGuiHandler())}，
 * 双端执行；{@link #modInstance()} 为 @Mod 实例的 Loader 索引等价获取
 * （主类无 @Instance 字段，FMLModContainer.constructMod 注入的 mod 对象经
 * {@code getIndexedModList().get(MODID).getMod()} 取得，openGui 与 registerGuiHandler 必须同一对象）。
 */
public class AggregatorGuiHandler implements IGuiHandler {

    /**
     * FML modGuiId（gtsr 自有 IGuiHandler 首个也是唯一一个注册：全仓此前零
     * registerGuiHandler，本 id 占用 0；如后续追加走尾追，禁复用）。
     */
    public static final int ID_AGGREGATOR = 0;

    /** @Mod 实例缓存（register/modInstance 惰性解析一次；openGui 与注册必须同一对象） */
    private static Object modInstance;

    /** @return @Mod 主类实例（Loader 索引等价获取；解析失败返回 null 并 error 日志） */
    public static Object modInstance() {
        if (modInstance == null) {
            ModContainer container = Loader.instance()
                .getIndexedModList()
                .get(GTSteamReborn.MODID);
            if (container == null || container.getMod() == null) {
                GTSteamReborn.LOG
                    .error("[AggregatorGuiHandler] 无法从 Loader 索引解析 @Mod 实例（{}），openGui 将不可用", GTSteamReborn.MODID);
                return null;
            }
            modInstance = container.getMod();
        }
        return modInstance;
    }

    /**
     * 服务端：解析 TE → 原版 Container（守卫逐字对齐旧 factory.open：EntityPlayerMP /
     * 非 FakePlayer / 基 TE 判空 + 机器类匹配；不符返回 null，FML 静默不开界面）。
     */
    @Override
    public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ID_AGGREGATOR) {
            return null;
        }
        if (!(player instanceof EntityPlayerMP) || player instanceof FakePlayer) {
            return null;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity base)) {
            return null;
        }
        if (!(base.getMetaTileEntity() instanceof MTECrustMatterAggregator aggregator)) {
            return null;
        }
        return new ContainerAggregatorConfig(player, aggregator);
    }

    /**
     * 客户端：经 ClientProxy 静态委托构造 Gui 实例（惰性引用，见类 javadoc 服务端不加载论证）。
     */
    @Override
    public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        if (id != ID_AGGREGATOR) {
            return null;
        }
        return ClientProxy.createAggregatorConfigClientGui(player, world, x, y, z);
    }
}
