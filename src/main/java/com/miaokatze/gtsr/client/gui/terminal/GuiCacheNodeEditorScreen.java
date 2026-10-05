package com.miaokatze.gtsr.client.gui.terminal;

import net.minecraft.item.ItemStack;

import com.miaokatze.gtsr.common.machine.base.IHubCacheNode;
import com.miaokatze.gtsr.common.terminal.TerminalUiType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.metatileentity.MetaTileEntity;

/** Same editor and network semantics as hub list; one nearby, immutable node anchor. */
@SideOnly(Side.CLIENT)
public final class GuiCacheNodeEditorScreen extends GuiCacheHubStatusScreen {

    private boolean nameInitialized;

    public GuiCacheNodeEditorScreen(int x, int y, int z, int dim) {
        super(TerminalUiType.CACHE_NODE, x, y, z, dim);
        selectAnchorNode();
    }

    @Override
    protected String getTitleLangKey() {
        return "gtsr.cache_hub_status.node_editor";
    }

    @Override
    protected ItemStack getNodeIcon(String type) {
        return null;
    }

    @Override
    protected Class<? extends IMetaTileEntity> targetMachineClass() {
        return MetaTileEntity.class;
    }

    @Override
    protected boolean isAnchorValid() {
        if (!super.isAnchorValid()) return false;
        return ((gregtech.api.interfaces.tileentity.IGregTechTileEntity) this.mc.theWorld
            .getTileEntity(this.anchorX, this.anchorY, this.anchorZ)).getMetaTileEntity() instanceof IHubCacheNode;
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        var snapshot = com.miaokatze.gtsr.client.terminal.HubTerminalClientCache.cacheSnapshot();
        if (!nameInitialized && snapshot != null
            && snapshot.matchesAnchor(anchorX, anchorY, anchorZ, anchorDim)
            && !snapshot.nodes.isEmpty()) {
            this.renameField.setText(snapshot.nodes.get(0).name);
            this.nameInitialized = true;
        }
        for (Object entry : this.buttonList) {
            net.minecraft.client.gui.GuiButton button = (net.minecraft.client.gui.GuiButton) entry;
            if (button.id == 2 || button.id == 5 || button.id == 6) button.enabled = false;
        }
    }
}
