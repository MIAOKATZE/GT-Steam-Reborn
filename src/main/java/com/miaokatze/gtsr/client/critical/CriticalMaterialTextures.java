package com.miaokatze.gtsr.client.critical;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.common.MinecraftForge;

import com.miaokatze.gtsr.common.critical.CriticalMaterialDefinitions;
import com.miaokatze.gtsr.common.critical.CriticalMaterialDefinitions.Definition;
import com.miaokatze.gtsr.common.critical.CriticalMaterialDefinitions.FaceSprite;
import com.miaokatze.gtsr.common.critical.CriticalMaterialDefinitions.SpritePair;
import com.miaokatze.gtsr.common.critical.CriticalMaterials;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Stable terrain-atlas UVs: Minecraft advances sprite frames without rebuilding mesh display lists. */
@SideOnly(Side.CLIENT)
public final class CriticalMaterialTextures {

    public static final String[] FACE_NAMES = { "ny", "py", "nz", "pz", "nx", "px" };
    // Same outward face basis as the approved V2 pixel contract; PNG north is -v.
    public static final int[][] NORMALS = { { 0, -1, 0 }, { 0, 1, 0 }, { 0, 0, -1 }, { 0, 0, 1 }, { -1, 0, 0 },
        { 1, 0, 0 } };
    public static final int[][] U = { { 1, 0, 0 }, { 1, 0, 0 }, { -1, 0, 0 }, { 1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 } };
    public static final int[][] V = { { 0, 0, 1 }, { 0, 0, -1 }, { 0, 1, 0 }, { 0, 1, 0 }, { 0, 1, 0 }, { 0, 1, 0 } };

    private static final Map<String, RegisteredMaterial> REGISTERED = new HashMap<>();
    private static volatile Map<String, MaterialIcons> tables = Collections.emptyMap();
    private static boolean listening, stitchComplete;

    private CriticalMaterialTextures() {}

    static void listen() {
        if (listening) return;
        listening = true;
        MinecraftForge.EVENT_BUS.register(new CriticalMaterialTextures());
    }

    @SubscribeEvent
    public void beforeStitch(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != 0) return;
        // Vanilla gathers block icons before emitting Pre, so those fresh registrations must survive.
        tables = Collections.emptyMap();
    }

    @SubscribeEvent
    public void afterStitch(TextureStitchEvent.Post event) {
        if (event.map.getTextureType() != 0) return;
        Map<String, MaterialIcons> next = new HashMap<>();
        for (Definition definition : CriticalMaterialDefinitions.catalog()
            .definitions()
            .values()) next.put(definition.id, resolve(definition));
        for (Map.Entry<String, String> alias : CriticalMaterialDefinitions.catalog()
            .aliases()
            .entrySet()) next.put(alias.getKey(), next.get(alias.getValue()));
        // Publish complete immutable atlas data once. Chunk workers never see a table being filled.
        tables = Collections.unmodifiableMap(next);
        stitchComplete = true;
    }

    public static void register(String alias, IIconRegister register) {
        if (stitchComplete) {
            tables = Collections.emptyMap();
            REGISTERED.clear();
            stitchComplete = false;
        }
        Definition definition = CriticalMaterialDefinitions.definition(alias);
        RegisteredMaterial material = new RegisteredMaterial();
        if (definition.hasSprites()) {
            for (SpritePair pair : definition.sprites()
                .values()) material.register(pair, register);
            for (SpritePair pair : definition.ctmSprites()
                .values()) material.register(pair, register);
        } else {
            material.side[0] = register.registerIcon("gtsr:critical/" + definition.id);
            material.top[0] = register.registerIcon("gtsr:critical/" + definition.id + "__top");
            if (definition.emissive)
                material.side[1] = register.registerIcon("gtsr:critical/" + definition.id + "_emission");
        }
        REGISTERED.put(definition.id, material);
    }

    public static IIcon icon(String alias, int side, int mask, boolean emission, boolean frozen) {
        MaterialIcons icons = tables.get(alias);
        // registerBlockIcons asks for its block icons before the atlas Post event.
        if (icons == null) icons = resolve(CriticalMaterialDefinitions.definition(alias));
        return icons.faces[side][mask & 15][(frozen ? 2 : 0) + (emission ? 1 : 0)];
    }

    private static MaterialIcons resolve(Definition definition) {
        MaterialIcons result = new MaterialIcons();
        RegisteredMaterial material = REGISTERED.get(definition.id);
        if (material == null) return result;
        for (int side = 0; side < 6; side++) {
            String face = FACE_NAMES[side];
            for (int mask = 0; mask < 16; mask++) {
                IIcon[] selected;
                if (definition.hasCtm()) {
                    selected = material.pairs.get(
                        definition.ctmSprite(definition.ctmFaceGroup(face), mask & definition.ctmAllowedMask(face)));
                } else if (definition.hasFaceSprites()) {
                    FaceSprite target = definition.faceSprite(face);
                    Definition targetDefinition = CriticalMaterialDefinitions.definition("v2_" + target.material);
                    RegisteredMaterial targetMaterial = REGISTERED.get(targetDefinition.id);
                    selected = targetMaterial == null ? null
                        : targetMaterial.pairs.get(targetDefinition.sprite(target.group));
                } else if (definition.hasSprites()) {
                    selected = material.pairs.get(definition.sprite(side < 2 ? "top" : "side"));
                } else selected = side < 2 ? material.top : material.side;
                if (selected == null) continue;
                System.arraycopy(selected, 0, result.faces[side][mask], 0, 4);
                // The legacy side/top path deliberately ignores the frozen flag.
                if (!definition.hasCtm() && !definition.hasFaceSprites()) {
                    result.faces[side][mask][2] = selected[0];
                    result.faces[side][mask][3] = selected[1];
                }
            }
        }
        return result;
    }

    private static final class MaterialIcons {

        final IIcon[][][] faces = new IIcon[6][16][4];
    }

    private static final class RegisteredMaterial {

        final Map<SpritePair, IIcon[]> pairs = new IdentityHashMap<>();
        final IIcon[] side = new IIcon[4], top = new IIcon[4];

        void register(SpritePair pair, IIconRegister register) {
            IIcon[] icons = new IIcon[4];
            for (int variant = 0; variant < 4; variant++) {
                String name = pair.texture((variant & 1) != 0, (variant & 2) != 0);
                if (name != null) icons[variant] = register.registerIcon(name);
            }
            pairs.put(pair, icons);
        }
    }

    /** Static chunk faces connect only matching exposed materials. Moving ownership is resolved by the TESR. */
    public static int worldMask(IBlockAccess world, int x, int y, int z, CriticalMaterials.MaterialBlock material,
        int side) {
        WorldMaskContext context = staticContext(world, x, y, z, material);
        if (context == null || CriticalMaterials.isAnimatedAt(world, x, y, z)) return 0;
        return worldMask(context, side);
    }

    /** One synchronous block render only: no neighbor/world state survives a chunk rebuild callback. */
    static WorldMaskContext staticContext(IBlockAccess world, int x, int y, int z,
        CriticalMaterials.MaterialBlock material) {
        Definition definition = CriticalMaterialDefinitions.definition(material.materialId);
        return definition.hasCtm() ? new WorldMaskContext(world, x, y, z, material, definition) : null;
    }

    static int worldMask(WorldMaskContext context, int side) {
        if (context == null) return 0;
        int allowed = context.definition.ctmAllowedMask(FACE_NAMES[side]);
        int result = 0;
        for (int bit = 1; bit <= 8; bit <<= 1) {
            if ((allowed & bit) == 0) continue;
            int[] basis = bit == 1 || bit == 4 ? V[side] : U[side];
            int sign = bit == 1 || bit == 8 ? -1 : 1;
            int dx = basis[0] * sign, dy = basis[1] * sign, dz = basis[2] * sign;
            int neighbor = dy < 0 ? 0 : dy > 0 ? 1 : dz < 0 ? 2 : dz > 0 ? 3 : dx < 0 ? 4 : 5;
            int nx = context.x + dx, ny = context.y + dy, nz = context.z + dz;
            CriticalMaterials.MaterialBlock next = context.matchingNeighbor(neighbor, nx, ny, nz);
            if (next == null) continue;
            int[] normal = NORMALS[side];
            if (!next.shouldSideBeRendered(context.world, nx + normal[0], ny + normal[1], nz + normal[2], side))
                continue;
            result |= bit;
        }
        return result;
    }

    static final class WorldMaskContext {

        final IBlockAccess world;
        final net.minecraft.world.World ownerWorld;
        final int x, y, z;
        final CriticalMaterials.MaterialBlock material;
        final Definition definition;
        final CriticalMaterials.MaterialBlock[] neighbors = new CriticalMaterials.MaterialBlock[6];
        int checked;

        WorldMaskContext(IBlockAccess world, int x, int y, int z, CriticalMaterials.MaterialBlock material,
            Definition definition) {
            this.world = world;
            this.ownerWorld = world instanceof net.minecraft.world.World real ? real
                : net.minecraft.client.Minecraft.getMinecraft().theWorld;
            this.x = x;
            this.y = y;
            this.z = z;
            this.material = material;
            this.definition = definition;
        }

        CriticalMaterials.MaterialBlock matchingNeighbor(int index, int x, int y, int z) {
            int bit = 1 << index;
            if ((checked & bit) == 0) {
                checked |= bit;
                if (world.getBlock(x, y, z) instanceof CriticalMaterials.MaterialBlock next
                    && material.materialId.equals(next.materialId)
                    && !com.miaokatze.gtsr.common.critical.CriticalLoomProxyVisibility
                        .animated(ownerWorld, x, y, z, next, world.getBlockMetadata(x, y, z)))
                    neighbors[index] = next;
            }
            return neighbors[index];
        }
    }
}
