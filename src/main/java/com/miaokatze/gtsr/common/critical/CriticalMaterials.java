package com.miaokatze.gtsr.common.critical;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.util.StatCollector;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.miaokatze.gtsr.client.critical.CriticalMaterialTextures;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Common-side identities. Animated parts are ordinary blocks, never per-voxel tile entities. */
public final class CriticalMaterials {

    public static int renderId;

    private static final Map<String, MaterialBlock> STATIC = new LinkedHashMap<>();
    private static final Map<String, MaterialBlock> PROXY = new LinkedHashMap<>();
    private static final Map<String, MaterialBlock> LOOM_STATIC = new LinkedHashMap<>();
    private static final Map<String, MaterialBlock> LOOM_PROXY = new LinkedHashMap<>();
    private static final Set<String> CURRENT_LOOM_FORMS = new HashSet<>();
    private static JsonObject palette;

    private CriticalMaterials() {}

    public static void register() {
        if (!STATIC.isEmpty()) return;
        palette = CriticalGeometry.readJson("materials.json");
        java.util.Set<String> loomForms = new java.util.HashSet<>();
        for (String slug : new String[] { "loom", "v2_loom", "v2_v25_loom", "v2_v27_loom", "v2_v30_loom",
            "v2_legacy_loom" }) {
            JsonObject loomGeometry = CriticalGeometry.readJson("models/" + slug + ".geometry.json");
            for (JsonElement element : loomGeometry.getAsJsonArray("blocks")) {
                com.google.gson.JsonArray row = element.getAsJsonArray();
                String id = properties(
                    row.get(3)
                        .getAsString()).get("id")
                            .getAsString();
                // Registration precedes getModel(): resolving Voxel.block here would recurse into the empty registry.
                // The imported V2 animationGroups contain only genuinely moving groups.
                boolean dynamic = loomGeometry.getAsJsonObject("animationGroups")
                    .has(
                        row.get(4)
                            .getAsString());
                String form = id + ':' + dynamic;
                loomForms.add(form);
                if ("v2_loom".equals(slug)) CURRENT_LOOM_FORMS.add(form);
            }
        }
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
            if (loomForms.contains(id + ":false")) {
                MaterialBlock loom = new MaterialBlock(id, false, glass, animated, emissive, true);
                LOOM_STATIC.put(id, loom);
                GameRegistry.registerBlock(loom, MaterialItemBlock.class, "critical_loom_" + id);
                addCurrentLoomItem(loom);
            }
            if (loomForms.contains(id + ":true")) {
                MaterialBlock loomProxy = new MaterialBlock(id, true, glass, animated, emissive, true);
                LOOM_PROXY.put(id, loomProxy);
                GameRegistry.registerBlock(loomProxy, MaterialItemBlock.class, "critical_loom_proxy_" + id);
                addCurrentLoomItem(loomProxy);
            }
        }
        CriticalControllerTextures.register();
    }

    private static void addCurrentLoomItem(MaterialBlock block) {
        if (block.isCurrentLoomForm())
            com.miaokatze.gtsr.register.CreativeTabManager.addItemToTab(new net.minecraft.item.ItemStack(block));
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

    public static Block loomBlock(String alias, boolean dynamic) {
        String id = properties(alias).get("id")
            .getAsString();
        Block block = (dynamic ? LOOM_PROXY : LOOM_STATIC).get(id);
        if (block == null) throw new IllegalStateException("Critical materials not registered");
        return block;
    }

    public static boolean matchesLoomVoxel(Block block, int metadata, CriticalGeometry.Voxel voxel) {
        return CriticalLoomStructure.isLoomVoxel(voxel) && metadata == 0
            && (block == voxel.block || block == loomBlock(voxel.material, ((MaterialBlock) voxel.block).proxy));
    }

    public static final class MaterialBlock extends Block {

        public final String materialId;
        public final boolean proxy, transparent, animated, emissive, loom;
        public final int lightLevel;
        @SideOnly(Side.CLIENT)
        public IIcon emission;
        @SideOnly(Side.CLIENT)
        private IIcon top;

        private MaterialBlock(String id, boolean proxy, boolean transparent, boolean animated, boolean emissive) {
            this(id, proxy, transparent, animated, emissive, false);
        }

        private MaterialBlock(String id, boolean proxy, boolean transparent, boolean animated, boolean emissive,
            boolean loom) {
            super(transparent ? Material.glass : Material.iron);
            this.loom = loom;
            this.materialId = id;
            this.proxy = proxy;
            this.transparent = transparent;
            this.animated = animated;
            this.emissive = emissive;
            if (isCurrentLoomForm()) setCreativeTab(com.miaokatze.gtsr.register.CreativeTabManager.CREATIVE_TAB);
            JsonObject properties = properties(id);
            lightLevel = properties.has("lightValue") ? properties.get("lightValue")
                .getAsInt() : 0;
            if (lightLevel < 0 || lightLevel > 15)
                throw new IllegalArgumentException("Invalid critical light level: " + id);
            setBlockName("critical_" + (loom ? "loom_" : "") + (proxy ? "proxy_" : "") + id);
            setHardness(loom ? 5 : 500);
            setResistance(10000);
            setLightOpacity(
                properties.has("lightOpacity") ? properties.get("lightOpacity")
                    .getAsInt() : transparent ? 0 : 255);
            // Native light is an immutable registered block property, never a texture-frame or machine-state update.
            setLightLevel(lightLevel / 15F);
            setBlockTextureName("gtsr:critical/" + id);
        }

        public boolean isCurrentLoomForm() {
            return loom && CURRENT_LOOM_FORMS.contains(materialId + ':' + proxy);
        }

        @Override
        public boolean canPlaceBlockAt(net.minecraft.world.World world, int x, int y, int z) {
            return loom && super.canPlaceBlockAt(world, x, y, z);
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
        public float getBlockHardness(net.minecraft.world.World world, int x, int y, int z) {
            return CriticalBlockProtection.protects(world, x, y, z) ? -1F : super.getBlockHardness(world, x, y, z);
        }

        @Override
        public float getExplosionResistance(net.minecraft.entity.Entity entity, net.minecraft.world.World world, int x,
            int y, int z, double explosionX, double explosionY, double explosionZ) {
            return CriticalBlockProtection.protects(world, x, y, z) ? Float.MAX_VALUE
                : super.getExplosionResistance(entity, world, x, y, z, explosionX, explosionY, explosionZ);
        }

        @Override
        public void onBlockExploded(net.minecraft.world.World world, int x, int y, int z,
            net.minecraft.world.Explosion explosion) {
            if (!CriticalBlockProtection.protects(world, x, y, z)) super.onBlockExploded(world, x, y, z, explosion);
        }

        @Override
        public boolean renderAsNormalBlock() {
            return !proxy;
        }

        /**
         * Every material is a real structure block at its placed coordinate. Proxy only selects the renderer;
         * the TESR's visual motion does not remove the paid block's native collision.
         */
        @Override
        public net.minecraft.util.AxisAlignedBB getCollisionBoundingBoxFromPool(net.minecraft.world.World world, int x,
            int y, int z) {
            return super.getCollisionBoundingBoxFromPool(world, x, y, z);
        }

        @Override
        public int getRenderType() {
            // Inventory uses this render id too. The world handler suppresses paid moving proxies itself.
            return renderId;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public int getRenderBlockPass() {
            return transparent ? 1 : 0;
        }

        @Override
        @SideOnly(Side.CLIENT)
        public void registerBlockIcons(IIconRegister icons) {
            if (materialId.startsWith("v2_")) {
                CriticalMaterialTextures.register(materialId, icons);
                blockIcon = CriticalMaterialTextures.icon(materialId, 3, 0, false, false);
                top = CriticalMaterialTextures.icon(materialId, 1, 0, false, false);
                emission = CriticalMaterialTextures.icon(materialId, 3, 0, true, false);
                return;
            }
            blockIcon = icons.registerIcon("gtsr:critical/" + materialId);
            top = icons.registerIcon("gtsr:critical/" + materialId + "__top");
            if (emissive) emission = icons.registerIcon("gtsr:critical/" + materialId + "_emission");
        }

        @Override
        @SideOnly(Side.CLIENT)
        public IIcon getIcon(int side, int meta) {
            if (materialId.startsWith("v2_")) return CriticalMaterialTextures.icon(materialId, side, 0, false, false);
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
    public static class MaterialItemBlock extends net.minecraft.item.ItemBlock {

        public MaterialItemBlock(Block block) {
            super(block);
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("unchecked")
        public void addInformation(net.minecraft.item.ItemStack stack, net.minecraft.entity.player.EntityPlayer player,
            java.util.List tooltip, boolean advanced) {
            super.addInformation(stack, player, tooltip, advanced);
            if (!(field_150939_a instanceof MaterialBlock material) || !material.materialId.startsWith("v2_")) return;
            JsonObject data = properties(material.materialId);
            if (data.has("tooltipKeys")) for (JsonElement key : data.getAsJsonArray("tooltipKeys"))
                tooltip.add(StatCollector.translateToLocal(key.getAsString()));
        }
    }

    public static final class ConstructionOnlyItemBlock extends MaterialItemBlock {

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
