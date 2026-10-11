package com.miaokatze.gtsr.common.critical;

import net.minecraftforge.common.util.ForgeDirection;

import gregtech.api.GregTechAPI;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.IIconContainer;
import gregtech.api.interfaces.ITexture;
import gregtech.api.render.TextureFactory;
import gregtech.api.util.GTUtility;

/** Standard GT casing remains visible through transparent controller icon overlays. */
public final class CriticalControllerTextures {

    private CriticalControllerTextures() {}

    public static int casingIndex() {
        return GTUtility.getCasingTextureIndex(GregTechAPI.sBlockCasings8, 6);
    }

    public static ITexture[] get(CriticalMachineKind kind, ForgeDirection side, ForgeDirection facing, boolean active) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(casingIndex());
        if (side != facing) return new ITexture[] { casing };
        IIconContainer[] icons = icons(kind);
        return new ITexture[] { casing, TextureFactory.builder()
            .addIcon(icons[active ? 1 : 0])
            .extFacing()
            .build(),
            TextureFactory.builder()
                .addIcon(icons[active ? 3 : 2])
                .extFacing()
                .glow()
                .build() };
    }

    private static IIconContainer[] icons(CriticalMachineKind kind) {
        if (kind == null) return new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE,
            Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE,
            Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_GLOW,
            Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_LINE_ACTIVE_GLOW };
        return switch (kind) {
            case SOLAR -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER,
                Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_LARGE_BOILER_ACTIVE_GLOW };
            case TURBINE -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE,
                Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_DIESEL_ENGINE_ACTIVE_GLOW };
            case PROCESSING -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_MULTI_COMPRESSOR,
                Textures.BlockIcons.OVERLAY_FRONT_MULTI_COMPRESSOR_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_MULTI_COMPRESSOR_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_MULTI_COMPRESSOR_ACTIVE_GLOW };
            case ENTANGLER -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR,
                Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_LARGE_CHEMICAL_REACTOR_ACTIVE_GLOW };
            case SUN -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE,
                Textures.BlockIcons.OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_ELECTRIC_BLAST_FURNACE_ACTIVE_GLOW };
            case DIMENSION -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_SCANNER,
                Textures.BlockIcons.OVERLAY_FRONT_SCANNER_ACTIVE, Textures.BlockIcons.OVERLAY_FRONT_SCANNER_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_SCANNER_ACTIVE_GLOW };
            case BATTERY -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER,
                Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_VACUUM_FREEZER_ACTIVE_GLOW };
            case ASSEMBLY -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_MATRIX,
                Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_MATRIX_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_MATRIX_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_ASSEMBLY_MATRIX_ACTIVE_GLOW };
            case ACCELERATOR -> new IIconContainer[] { Textures.BlockIcons.OVERLAY_FRONT_OIL_CRACKER,
                Textures.BlockIcons.OVERLAY_FRONT_OIL_CRACKER_ACTIVE,
                Textures.BlockIcons.OVERLAY_FRONT_OIL_CRACKER_GLOW,
                Textures.BlockIcons.OVERLAY_FRONT_OIL_CRACKER_ACTIVE_GLOW };
        };
    }
}
