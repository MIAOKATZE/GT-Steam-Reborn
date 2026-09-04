package com.miaokatze.gtsr.main;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.client.HubBindClientHandler;
import com.miaokatze.gtsr.client.gui.terminal.GuiAggregatorConfigScreen;
import com.miaokatze.gtsr.common.fx.GTSRFXEngine;
import com.miaokatze.gtsr.common.gui.terminal.ContainerAggregatorConfig;
import com.miaokatze.gtsr.common.machine.MTECrustMatterAggregator;
import com.miaokatze.gtsr.common.tick.SingularityClientFXHandler;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/**
 * 客户端代理类
 * 继承自 CommonProxy，用于处理仅在客户端（Client Side）执行的逻辑。
 * 例如：渲染注册、按键绑定、GUI 打开等。
 */
public class ClientProxy extends CommonProxy {

    /**
     * 初始化阶段 (Init)
     * 在此阶段注册客户端特定的事件处理器，如 HUD 渲染器。
     */
    @Override
    public void init(FMLInitializationEvent event) {
        // 调用父类的 init 方法，确保通用逻辑正常执行
        super.init(event);

        FMLCommonHandler.instance()
            .bus()
            .register(new SingularityClientFXHandler());

        MinecraftForge.EVENT_BUS.register(GTSRFXEngine.instance());
        MinecraftForge.EVENT_BUS.register(new HubBindClientHandler());
        FMLCommonHandler.instance()
            .bus()
            .register(GTSRFXEngine.instance());

        GTSteamReborn.LOG.info("[2/3] 客户端初始化完成");
    }

    /** 后初始化阶段 (PostInit)。 */
    @Override
    public void postInit(FMLPostInitializationEvent event) {
        super.postInit(event);

        GTSteamReborn.LOG.info("[3/3] 客户端后初始化完成。");
    }

    /**
     * 聚合器终端配置界面的客户端构造（terminal-native-ui N35/N14，FML IGuiHandler 委托）。
     * <p>
     * 仅由 {@code AggregatorGuiHandler#getClientGuiElement} 在客户端调用（FML openGui 双端
     * 配对的客户端半边）：解析锚点 TE（基 TE + 机器类双校验，与服务端 getServerGuiElement
     * 同口径）→ 构造 {@link ContainerAggregatorConfig} + {@link GuiAggregatorConfigScreen}。
     * 本类只在客户端加载（@SidedProxy），client 包 GUI 实例仅在此构造（PLAN §4.7-4）；
     * TE 不符返回 null（FML 静默不开界面，与服务端守卫一致）。
     */
    public static Object createAggregatorConfigClientGui(EntityPlayer player, World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof IGregTechTileEntity base)) {
            return null;
        }
        if (!(base.getMetaTileEntity() instanceof MTECrustMatterAggregator aggregator)) {
            return null;
        }
        return new GuiAggregatorConfigScreen(new ContainerAggregatorConfig(player, aggregator));
    }

}
