package com.miaokatze.gtsr.common.critical;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Common-side identities. Animated parts are ordinary blocks, never per-voxel tile entities. */
public final class CriticalMaterials {

    public static int renderId;

    private static final Map<String, MaterialBlock> STATIC = new LinkedHashMap<>();
    private static final Map<String, MaterialBlock> PROXY = new LinkedHashMap<>();
    private static JsonObject palette;

    private CriticalMaterials() {}

    public static void register() {
        if (!STATIC.isEmpty()) return;
        palette = CriticalGeometry.readJson("materials.json");
        for (Map.Entry<String, JsonElement> entry : palette.entrySet()) {
            JsonObject value = entry.getValue()
                .getAsJsonObject();
            String id = value.get("id")
                .getAsString();
            if (STATIC.containsKey(id)) continue;
            boolean glass = !"opaque".equals(
                value.get("alphaMode")
                    .getAsString());
            boolean animated = value.get("animated")
                .getAsBoolean();
            boolean emissive = value.get("emissionStrength")
                .getAsDouble() > 0;
            MaterialBlock normal = new MaterialBlock(id, false, glass, animated, emissive);
            MaterialBlock proxy = new MaterialBlock(id, true, glass, animated, emissive);
            STATIC.put(id, normal);
            PROXY.put(id, proxy);
            GameRegistry.registerBlock(normal, ConstructionOnlyItemBlock.class, "critical_" + id);
            GameRegistry.registerBlock(proxy, ConstructionOnlyItemBlock.class, "critical_proxy_" + id);
        }
    }

    public static JsonObject properties(String alias) {
        if (palette == null) palette = CriticalGeometry.readJson("materials.json");
        JsonElement value = palette.get(alias);
        if (value == null) throw new IllegalArgumentException("Unknown critical material: " + alias);
        return value.getAsJsonObject();
    }

    public static Block block(String alias, boolean dynamic) {
        String id = properties(alias).get("id")
            .getAsString();
        Block block = (dynamic ? PROXY : STATIC).get(id);
        if (block == null) throw new IllegalStateException("Critical materials not registered");
        return block;
    }

    public static final class MaterialBlock extends Block {

        public final String materialId;
        public final boolean proxy, transparent, animated, emissive;
        @SideOnly(Side.CLIENT)
        public IIcon emission;
        @SideOnly(Side.CLIENT)
        private IIcon top;

        private MaterialBlock(String id, boolean proxy, boolean transparent, boolean animated, boolean emissive) {
            super(transparent ? Material.glass : Material.iron);
            this.materialId = id;
            this.proxy = proxy;
            this.transparent = transparent;
            this.animated = animated;
            this.emissive = emissive;
            setBlockName("critical_" + (proxy ? "proxy_" : "") + id);
            setHardness(500);
            setResistance(10000);
            setLightOpacity(transparent ? 0 : 255);
            setBlockTextureName("gtsr:critical/" + id);
        }

        @Override
        public boolean canPlaceBlockAt(net.minecraft.world.World world, int x, int y, int z) {
            return false;
        }

        @Override
        public boolean isOpaqueCube() {
            return !proxy && !transparent;
        }

        @Override
        public float getExplosionResistance(net.minecraft.entity.Entity entity) {
            return 10000F;
        }

        @Override
        public boolean renderAsNormalBlock() {
            return !proxy;
        }

        /** The moving mesh has no fixed invisible collision wall; original static supports retain collision. */
        @Override
        public net.minecraft.util.AxisAlignedBB getCollisionBoundingBoxFromPool(net.minecraft.world.World world, int x,
            int y, int z) {
            return proxy ? null : super.getCollisionBoundingBoxFromPool(world, x, y, z);
        }

        @Override
        public int getRenderType() {
            return proxy ? -1 : renderId;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public int getRenderBlockPass() {
            return transparent ? 1 : 0;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void registerBlockIcons(IIconRegister icons) {
            blockIcon = icons.registerIcon("gtsr:critical/" + materialId);
            top = icons.registerIcon("gtsr:critical/" + materialId + "__top");
            if (emissive) emission = icons.registerIcon("gtsr:critical/" + materialId + "_emission");
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIcon(int side, int meta) {
            return side < 2 ? top : blockIcon;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public boolean shouldSideBeRendered(net.minecraft.world.IBlockAccess world, int x, int y, int z, int side) {
            if (!proxy && transparent && world.getBlock(x, y, z) == this) return false;
            return super.shouldSideBeRendered(world, x, y, z, side);
        }
    }

    /** Only the controller's paid world mutation may create a structure material. */
    public static final class ConstructionOnlyItemBlock extends net.minecraft.item.ItemBlock {

        public ConstructionOnlyItemBlock(Block block) {
            super(block);
        }

        @Override
        public boolean placeBlockAt(net.minecraft.item.ItemStack stack, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.world.World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ,
            int metadata) {
            return false;
        }
    }
}
