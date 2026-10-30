package com.miaokatze.gtsr.common.gui.mui2.hub;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.FakePlayer;

import com.cleanroommc.modularui.api.UIFactory;
import com.cleanroommc.modularui.factory.GuiManager;
import com.cleanroommc.modularui.factory.PosGuiData;
import com.cleanroommc.modularui.screen.ModularPanel;
import com.cleanroommc.modularui.screen.ModularScreen;
import com.cleanroommc.modularui.screen.UISettings;
import com.cleanroommc.modularui.value.sync.PanelSyncManager;
import com.miaokatze.gtsr.common.gui.mui2.BoundedTerminalScreen;
import com.miaokatze.gtsr.common.gui.mui2.Mui2ServerActions;
import com.miaokatze.gtsr.common.gui.mui2.Mui2TerminalRoutes;
import com.miaokatze.gtsr.common.machine.MTESingularityDrillingHub;
import com.miaokatze.gtsr.common.machine.MTESteamHubArray;
import com.miaokatze.gtsr.common.machine.MTEWaterHubArray;
import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.terminal.CacheNodeTerminalData;
import com.miaokatze.gtsr.common.terminal.TerminalServerSessions;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;

/** Independent handheld factories. Normal machine GUIs still follow GregTech's global switch. */
public final class HubTerminalFactory implements UIFactory<PosGuiData> {

    public static final String STEAM_FACTORY = "gtsr:steam_hub_terminal";
    public static final String WATER_FACTORY = "gtsr:water_hub_terminal";
    public static final String DRILLING_FACTORY = "gtsr:drilling_hub_terminal";
    public static final String NODE_FACTORY = "gtsr:cache_node_editor";
    public static final String PANEL_NAME = "hub_terminal";
    private static final HubTerminalFactory STEAM = new HubTerminalFactory(TerminalUiType.STEAM_HUB, STEAM_FACTORY);
    private static final HubTerminalFactory WATER = new HubTerminalFactory(TerminalUiType.WATER_HUB, WATER_FACTORY);
    private static final HubTerminalFactory DRILLING = new HubTerminalFactory(
        TerminalUiType.SINGULARITY_HUB,
        DRILLING_FACTORY);
    static final HubTerminalFactory NODE = new HubTerminalFactory(TerminalUiType.CACHE_NODE, NODE_FACTORY);

    public final TerminalUiType type;
    private final String name;
    private boolean registered;

    private HubTerminalFactory(TerminalUiType type, String name) {
        this.type = type;
        this.name = name;
    }

    public static void register() {
        STEAM.registerInstance();
        WATER.registerInstance();
        DRILLING.registerInstance();
    }

    void registerInstance() {
        if (registered) return;
        GuiManager.registerFactory(this);
        Mui2ServerActions.register();
        registered = true;
    }

    public static HubTerminalFactory forType(TerminalUiType type) {
        switch (type) {
            case STEAM_HUB:
                return STEAM;
            case WATER_HUB:
                return WATER;
            case SINGULARITY_HUB:
                return DRILLING;
            case CACHE_NODE:
                return NODE;
            default:
                throw new IllegalArgumentException("Not a hub terminal: " + type);
        }
    }

    public static boolean open(TerminalUiType type, EntityPlayer player, IGregTechTileEntity base) {
        if (!(player instanceof EntityPlayerMP mp) || base == null || base.getWorld() != player.worldObj) return false;
        HubTerminalFactory factory = forType(type);
        PosGuiData data = new PosGuiData(player, base.getXCoord(), base.getYCoord(), base.getZCoord());
        if (data.getTileEntity() != base || !factory.canInteractWith(player, data)) return false;
        Object previousContainer = player.openContainer;
        GuiManager.open(factory, data, mp);
        com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.recordOpened(player, base, previousContainer);
        return true;
    }

    public boolean enabled() {
        switch (type) {
            case STEAM_HUB:
            case WATER_HUB:
                return Mui2TerminalRoutes.cacheHubTerminal;
            case SINGULARITY_HUB:
                return Mui2TerminalRoutes.singularityHubTerminal;
            case CACHE_NODE:
                return Mui2TerminalRoutes.cacheNodeEditor;
            default:
                return false;
        }
    }

    public IMetaTileEntity machine(PosGuiData data) {
        TileEntity tile = data.getTileEntity();
        if (tile == null || tile.isInvalid() || !(tile instanceof IGregTechTileEntity base)) return null;
        IMetaTileEntity mte = base.getMetaTileEntity();
        switch (type) {
            case STEAM_HUB:
                return mte instanceof MTESteamHubArray ? mte : null;
            case WATER_HUB:
                return mte instanceof MTEWaterHubArray ? mte : null;
            case SINGULARITY_HUB:
                return mte instanceof MTESingularityDrillingHub ? mte : null;
            case CACHE_NODE:
                return mte instanceof IHubCacheNode ? mte : null;
            default:
                return null;
        }
    }

    @Override
    public String getFactoryName() {
        return name;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player, PosGuiData data) {
        if (com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.remote(data))
            return com.miaokatze.gtsr.common.terminal.MachineTerminalHistory.canUseRemote(player, data);
        if (!TerminalServerSessions.canInspectLocalPosition(player, data, 64 * 64)) return false;
        if (player == null || data.getWorld() == null
            || player != data.getPlayer()
            || player.isDead
            || player instanceof FakePlayer
            || player.worldObj != data.getWorld()
            || (!data.getWorld().isRemote && !enabled())
            || machine(data) == null) return false;
        if (type == TerminalUiType.CACHE_NODE) {
            // The server alone evaluates the authoritative held item and binding contract.
            return data.getWorld().isRemote ? data.getSquaredDistance(player) <= 64
                : CacheNodeTerminalData.canUse(player, (IGregTechTileEntity) data.getTileEntity());
        }
        // Preserve legacy read range. Cache hub writes and all teleports have an additional 8-block gate.
        return data.getSquaredDistance(player) <= 64 * 64;
    }

    @Override
    public ModularPanel createPanel(PosGuiData data, PanelSyncManager manager, UISettings settings) {
        if (!canInteractWith(data.getPlayer(), data)) {
            settings.canInteractWith(player -> false);
            return ModularPanel.defaultPanel("hub_terminal_denied");
        }
        IMetaTileEntity machine = machine(data);
        TileEntity tile = data.getTileEntity();
        settings.canInteractWith(
            player -> data.getTileEntity() == tile && machine(data) == machine && canInteractWith(player, data));
        return HubTerminalPanel.build(this, data, machine, manager);
    }

    @SideOnly(Side.CLIENT)
    @Override
    public ModularScreen createScreen(PosGuiData data, ModularPanel panel) {
        return new BoundedTerminalScreen("gtsr", panel, 400, 240);
    }

    @Override
    public void writeGuiData(PosGuiData data, PacketBuffer buffer) {
        buffer.writeInt(data.getX());
        buffer.writeInt(data.getY());
        buffer.writeInt(data.getZ());
    }

    @Override
    public PosGuiData readGuiData(EntityPlayer player, PacketBuffer buffer) {
        return new PosGuiData(player, buffer.readInt(), buffer.readInt(), buffer.readInt());
    }
}
