package com.miaokatze.gtsr.client.architecture;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.lwjgl.opengl.GL11;

import com.miaokatze.gtsr.client.encounter.GlScope;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Forge's block helpers supply the view transforms; existing models need no dummy world or tile. */
@SideOnly(Side.CLIENT)
public final class BlockModelItemRenderer implements IItemRenderer {

    private final Runnable model;

    private BlockModelItemRenderer(Runnable model) {
        this.model = model;
    }

    /** Model coordinates are centered on X/Z, with the base at Y=0, as in the world TESRs. */
    public static void register(Block block, Runnable model) {
        MinecraftForgeClient.registerItemRenderer(Item.getItemFromBlock(block), new BlockModelItemRenderer(model));
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return type != ItemRenderType.FIRST_PERSON_MAP;
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        try (GlScope scope = new GlScope()) {
            // Inventory and dropped blocks are centered; equipped blocks use the unit cube.
            if (type == ItemRenderType.EQUIPPED || type == ItemRenderType.EQUIPPED_FIRST_PERSON) {
                GL11.glTranslatef(.5F, 0, .5F);
            } else {
                GL11.glTranslatef(0, -.5F, 0);
            }
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glColor4f(1, 1, 1, 1);
            model.run();
        }
    }
}
