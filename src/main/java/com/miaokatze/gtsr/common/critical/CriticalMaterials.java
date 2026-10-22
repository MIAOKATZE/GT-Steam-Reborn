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

import cpw.mods.fml.common.event.FMLMissingMappingsEvent;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Common-side identities. Animated parts are ordinary blocks, never per-voxel tile entities. */
public final class CriticalMaterials {

    public static int renderId;

    private static final Map<String, MaterialBlock> MATERIALS = new LinkedHashMap<>();
    private static final Set<String> REMOVED_REGISTRATIONS = new HashSet<>();
    private static JsonObject palette;

    private CriticalMaterials() {}

    public static void register() {
        if (!MATERIALS.isEmpty()) return;
        palette = CriticalGeometry.readJson("materials.json");
        for (Map.Entry<String, JsonElement> entry : palette.entrySet()) {
            JsonObject value = entry.getValue()
                .getAsJsonObject();
            String id = value.get("id")
                .getAsString();
            if (MATERIALS.containsKey(id)) continue;
            MaterialBlock block = new MaterialBlock(
                id,
                !"opaque".equals(
                    value.get("alphaMode")
                        .getAsString()),
                value.get("animated")
                    .getAsBoolean(),
                value.get("emissionStrength")
                    .getAsDouble() > 0);
            MATERIALS.put(id, block);
            GameRegistry.registerBlock(block, MaterialItemBlock.class, "critical_" + id);
            REMOVED_REGISTRATIONS.add("gtsr:critical_proxy_" + id);
            com.miaokatze.gtsr.register.CreativeTabManager.addItemToTab(new net.minecraft.item.ItemStack(block));
        }
        for (String slug : new String[] { "loom", "v2_loom", "v2_v25_loom", "v2_v27_loom", "v2_v30_loom",
            "v2_legacy_loom" }) {
            JsonObject geometry = CriticalGeometry.readJson("models/" + slug + ".geometry.json");
            for (JsonElement element : geometry.getAsJsonArray("blocks")) {
                com.google.gson.JsonArray row = element.getAsJsonArray();
                String id = properties(
                    row.get(3)
                        .getAsString()).get("id")
                            .getAsString();
                boolean dynamic = geometry.getAsJsonObject("animationGroups")
                    .has(
                        row.get(4)
                            .getAsString());
                REMOVED_REGISTRATIONS.add("gtsr:critical_loom_" + (dynamic ? "proxy_" : "") + id);
            }
        }
        CriticalControllerTextures.register();
    }

    /** Intentional removal: old duplicate blocks/items are discarded, while unrelated missing IDs remain visible. */
    public static void ignoreRemovedMappings(FMLMissingMappingsEvent event) {
        for (FMLMissingMappingsEvent.MissingMapping mapping : event.get())
            if (REMOVED_REGISTRATIONS.contains(mapping.name)) mapping.ignore();
    }

    public static JsonObject properties(String alias) {
        if (palette == null) palette = CriticalGeometry.readJson("materials.json");
        JsonElement value = palette.get(alias);
        if (value == null) throw new IllegalArgumentException("Unknown critical material: " + alias);
        return value.getAsJsonObject();
    }

    /** Geometry roles belong to placed voxels; both construction paths resolve the same material. */
    public static Block block(String alias, boolean dynamic) {
        String id = properties(alias).get("id")
            .getAsString();
        Block block = MATERIALS.get(id);
        if (block == null) throw new IllegalStateException("Critical materials not registered");
        return block;
    }

    public static Block loomBlock(String alias, boolean dynamic) {
        return block(alias, dynamic);
    }

    public static boolean matchesLoomVoxel(Block block, int metadata, CriticalGeometry.Voxel voxel) {
        return CriticalLoomStructure.isLoomVoxel(voxel) && metadata == voxel.metadata && block == voxel.block;
    }

    /** The client index suppresses only exact coordinates owned by a currently rendered animation group. */
    @SideOnly(Side.CLIENT)
    public static boolean isAnimatedAt(net.minecraft.world.IBlockAccess world, int x, int y, int z) {
        return CriticalLoomProxyVisibility.animated(
            world instanceof net.minecraft.world.World real ? real
                : net.minecraft.client.Minecraft.getMinecraft().theWorld,
            x,
            y,
            z,
            world.getBlock(x, y, z),
            world.getBlockMetadata(x, y, z));
    }

    public static final class MaterialBlock extends Block {

        public final String materialId;
        public final boolean transparent, animated, emissive;
        public final int lightLevel;
        @SideOnly(Side.CLIENT)
        public IIcon emission;
        @SideOnly(Side.CLIENT)
        private IIcon top;

        private MaterialBlock(String id, boolean transparent, boolean animated, boolean emissive) {
            super(transparent ? Material.glass : Material.iron);
            this.materialId = id;
            this.transparent = transparent;
            this.animated = animated;
            this.emissive = emissive;
            setCreativeTab(com.miaokatze.gtsr.register.CreativeTabManager.CREATIVE_TAB);
            JsonObject properties = properties(id);
            lightLevel = properties.has("lightValue") ? properties.get("lightValue")
                .getAsInt() : 0;
            if (lightLevel < 0 || lightLevel > 15)
                throw new IllegalArgumentException("Invalid critical light level: " + id);
            setBlockName("critical_" + id);
            setHardness(5);
            setResistance(10000);
            setLightOpacity(
                properties.has("lightOpacity") ? properties.get("lightOpacity")
                    .getAsInt() : transparent ? 0 : 255);
            // Native light is an immutable registered block property, never a texture-frame or machine-state update.
            setLightLevel(lightLevel / 15F);
            setBlockTextureName("gtsr:critical/" + id);
        }

        @Override
        public boolean isOpaqueCube() {
            // Opacity cannot depend on coordinates. Keep external faces beside moving cells intact;
            // our renderer culls static material neighbors using their current positional ownership.
            return false;
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
            return false;
        }

        /**
         * Every material is a real structure block at its placed coordinate;
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
            Block neighbor = world.getBlock(x, y, z);
            if (neighbor instanceof MaterialBlock material && !isAnimatedAt(world, x, y, z)
                && (!material.transparent || material == this)) return false;
            return super.shouldSideBeRendered(world, x, y, z, side);
        }
    }

    /** One placeable inventory item for each material, shared by loom and paid construction. */
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

    /** Invisible render anchors remain controller-only; material blocks use MaterialItemBlock. */
    public static final class ConstructionOnlyItemBlock extends MaterialItemBlock {

        public ConstructionOnlyItemBlock(Block block) {
            super(block);
        }

        @Override
        @SideOnly(Side.CLIENT)
        @SuppressWarnings("rawtypes")
        public void getSubItems(net.minecraft.item.Item item, net.minecraft.creativetab.CreativeTabs tab,
            java.util.List items) {}

        @Override
        public boolean placeBlockAt(net.minecraft.item.ItemStack stack, net.minecraft.entity.player.EntityPlayer player,
            net.minecraft.world.World world, int x, int y, int z, int side, float hitX, float hitY, float hitZ,
            int metadata) {
            return false;
        }
    }

}
