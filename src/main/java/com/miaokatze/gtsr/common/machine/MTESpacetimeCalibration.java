package com.miaokatze.gtsr.common.machine;

import static com.gtnewhorizon.structurelib.structure.StructureUtility.isAir;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.ofBlock;
import static com.gtnewhorizon.structurelib.structure.StructureUtility.transpose;
import static gregtech.api.util.GTStructureUtility.buildHatchAdder;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;
import net.minecraft.util.Vec3;
import net.minecraftforge.common.util.ForgeDirection;

import com.gtnewhorizon.structurelib.alignment.IAlignmentLimits;
import com.gtnewhorizon.structurelib.alignment.constructable.ISurvivalConstructable;
import com.gtnewhorizon.structurelib.alignment.enumerable.Flip;
import com.gtnewhorizon.structurelib.alignment.enumerable.Rotation;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.ISurvivalBuildEnvironment;
import com.gtnewhorizon.structurelib.structure.StructureDefinition;
import com.miaokatze.gtsr.common.dimension.prosperity.portal.SpacetimeAttraction;
import com.miaokatze.gtsr.common.dimension.prosperity.portal.SpacetimeGeometry;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeEffects;

import bartworks.system.material.WerkstoffLoader;
import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.GregTechAPI;
import gregtech.api.enums.HatchElement;
import gregtech.api.enums.Textures;
import gregtech.api.interfaces.ITexture;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.render.TextureFactory;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.StructureErrorRegistry;
import gregtech.api.util.MultiblockTooltipBuilder;

/** LuV utility machine. No recipe or synthetic progress timer is used to sustain its portal. */
public class MTESpacetimeCalibration extends MTEEnhancedMultiBlockBase<MTESpacetimeCalibration>
    implements ISurvivalConstructable {

    private static IStructureDefinition<MTESpacetimeCalibration> definition;
    private static final int OFFSET_X = 21, OFFSET_Y = 43, OFFSET_Z = 0;
    private final SpacetimeAttraction attraction = new SpacetimeAttraction();
    private int preheatTicks;
    private boolean portalActive;

    public MTESpacetimeCalibration(int id, String name, String localizedName) {
        super(id, name, localizedName);
    }

    public MTESpacetimeCalibration(String name) {
        super(name);
    }

    @Override
    public IMetaTileEntity newMetaEntity(IGregTechTileEntity tile) {
        return new MTESpacetimeCalibration(mName);
    }

    @Override
    protected IAlignmentLimits getInitialAlignmentLimits() {
        return (direction, rotation, flip) -> direction.offsetY == 0 && direction != ForgeDirection.UNKNOWN
            && rotation == Rotation.NORMAL
            && flip == Flip.NONE;
    }

    private boolean legalFacing() {
        return getExtendedFacing().getDirection().offsetY == 0
            && getExtendedFacing().getDirection() != ForgeDirection.UNKNOWN
            && getExtendedFacing().getRotation() == Rotation.NORMAL
            && getExtendedFacing().getFlip() == Flip.NONE;
    }

    private static Block requiredBlock(String mod, String name) {
        Block block = GameRegistry.findBlock(mod, name);
        if (block == null) throw new IllegalStateException("Missing portal material " + mod + ":" + name);
        return block;
    }

    @Override
    public IStructureDefinition<MTESpacetimeCalibration> getStructureDefinition() {
        if (definition == null) {
            if (WerkstoffLoader.RhodiumPlatedPalladium == null)
                throw new IllegalStateException("Missing Rhodium Plated Palladium material");
            definition = StructureDefinition.<MTESpacetimeCalibration>builder()
                .addShape("main", transpose(SpacetimeGeometry.shape()))
                .addElement('C', ofBlock(GregTechAPI.sBlockCasings1, 6))
                .addElement('Q', ofBlock(GregTechAPI.sBlockCasings1, 15))
                .addElement(
                    'R',
                    ofBlock(requiredBlock("gregtech", "bw.frames"), WerkstoffLoader.RhodiumPlatedPalladium.getmID()))
                .addElement('G', ofBlock(requiredBlock("bartworks", "BW_TieredGlass"), 3))
                .addElement('F', ofBlock(GregTechAPI.sBlockCasings2, 0))
                .addElement('-', isAir())
                // Dedicated functional bays have no casing fallback. Exactly one normal energy and maintenance hatch.
                .addElement(
                    'E',
                    buildHatchAdder(MTESpacetimeCalibration.class).atLeast(HatchElement.Energy)
                        .casingIndex(6)
                        .hint(1)
                        .build())
                .addElement(
                    'M',
                    buildHatchAdder(MTESpacetimeCalibration.class).atLeast(HatchElement.Maintenance)
                        .casingIndex(6)
                        .hint(2)
                        .build())
                .build();
        }
        return definition;
    }

    @Override
    public void construct(ItemStack stack, boolean hintsOnly) {
        if (legalFacing()) buildPiece("main", stack, hintsOnly, OFFSET_X, OFFSET_Y, OFFSET_Z);
    }

    @Override
    public int survivalConstruct(ItemStack stack, int budget, ISurvivalBuildEnvironment env) {
        if (mMachine || !legalFacing()) return -1;
        return survivalBuildPiece("main", stack, OFFSET_X, OFFSET_Y, OFFSET_Z, budget, env, false, true);
    }

    @Override
    public void checkMachine(IGregTechTileEntity tile, ItemStack stack, List<StructureError> errors) {
        if (!legalFacing() || !chunksLoaded()
            || !checkPiece("main", OFFSET_X, OFFSET_Y, OFFSET_Z, errors)
            || mEnergyHatches.size() != 1
            || mMaintenanceHatches.size() != 1
            || mEnergyHatches.get(0)
                .maxEUInput() < 32768) {
            if (errors.isEmpty()) errors.add(StructureErrorRegistry.UNKNOWN_STRUCTURE_ERROR);
        }
    }

    private boolean chunksLoaded() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        // Check corners and all covering chunks without loading any; partial unload disables attraction immediately.
        Vec3 a = localToWorld(2, 0, 0), b = localToWorld(45, 46, 9);
        if (Math.min(a.yCoord, b.yCoord) < 0 || Math.max(a.yCoord, b.yCoord) > tile.getWorld()
            .getHeight()) return false;
        int minX = ((int) Math.floor(Math.min(a.xCoord, b.xCoord))) >> 4;
        int maxX = ((int) Math.floor(Math.max(a.xCoord, b.xCoord))) >> 4;
        int minZ = ((int) Math.floor(Math.min(a.zCoord, b.zCoord))) >> 4;
        int maxZ = ((int) Math.floor(Math.max(a.zCoord, b.zCoord))) >> 4;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++) if (!tile.getWorld()
            .getChunkProvider()
            .chunkExists(x, z)) return false;
        return true;
    }

    @Override
    protected void runMachine(IGregTechTileEntity tile, long tick) {
        // The standard base maintains structure/maintenance/error flags; this utility has no recipes.
    }

    @Override
    public void onPostTick(IGregTechTileEntity tile, long tick) {
        super.onPostTick(tile, tick);
        if (!tile.isServerSide()) return;
        // Honor GT's dirty flag immediately; periodically recheck remote cells which may not notify the controller.
        checkStructure(tick % 20 == 0, tile);
        checkMaintenance();
        if (!chunksLoaded() || !legalFacing()
            || !mMachine
            || !tile.isAllowedToWork()
            || getRepairStatus() != getIdealStatus()
            || !drainEnergyInput(preheatTicks < 600 ? 30720 : 15360)) {
            deactivate();
            return;
        }
        if (preheatTicks < 600) preheatTicks++;
        portalActive = preheatTicks >= 600;
        tile.setActive(true);
        if (portalActive) {
            Vec3 centre = localToWorld(23.5, 24.5, 4.5);
            if (tick % 20 == 0 || preheatTicks == 600 && !lastPublishedActive) publish(true, centre);
            attraction.tick(this, centre);
        }
    }

    private boolean lastPublishedActive;

    /** Contact is an authorization boundary: validate the complete structure before any dimension transfer. */
    public boolean validatePortalForTravel() {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile == null || !tile.isServerSide()) return false;
        checkStructure(true, tile);
        checkMaintenance();
        if (!portalActive || preheatTicks < 600
            || !mMachine
            || !legalFacing()
            || !chunksLoaded()
            || !tile.isAllowedToWork()
            || getRepairStatus() != getIdealStatus()) {
            deactivate();
            return false;
        }
        return true;
    }

    private void publish(boolean active, Vec3 centre) {
        SpacetimeEffects.portalState(
            getBaseMetaTileEntity(),
            active,
            centre.xCoord,
            centre.yCoord,
            centre.zCoord,
            getExtendedFacing().getDirection());
        lastPublishedActive = active;
    }

    private void deactivate() {
        attraction.releaseAll();
        preheatTicks = 0;
        portalActive = false;
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        if (tile != null && tile.isServerSide()) {
            tile.setActive(false);
            if (lastPublishedActive) publish(false, localToWorld(23.5, 24.5, 4.5));
        }
    }

    @Override
    public void onUnload() {
        deactivate();
        super.onUnload();
    }

    @Override
    public void onRemoval() {
        deactivate();
        super.onRemoval();
    }

    @Override
    public void onTickFail(IGregTechTileEntity tile, long tick) {
        deactivate();
        super.onTickFail(tile, tick);
    }

    /** Approved model points map from tile centre through the same ExtendedFacing as checkPiece. */
    public Vec3 localToWorld(double x, double y, double z) {
        Vec3 delta = getExtendedFacing().getWorldOffset(Vec3.createVectorHelper(x - 23.5, 2.5 - y, 8.5 - z));
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        return Vec3.createVectorHelper(
            tile.getXCoord() + .5 + delta.xCoord,
            tile.getYCoord() + .5 + delta.yCoord,
            tile.getZCoord() + .5 + delta.zCoord);
    }

    public Vec3 worldToLocal(double x, double y, double z) {
        IGregTechTileEntity tile = getBaseMetaTileEntity();
        Vec3 abc = getExtendedFacing().getOffsetABC(
            Vec3.createVectorHelper(x - tile.getXCoord() - .5, y - tile.getYCoord() - .5, z - tile.getZCoord() - .5));
        return Vec3.createVectorHelper(23.5 + abc.xCoord, 2.5 - abc.yCoord, 8.5 - abc.zCoord);
    }

    @Override
    protected MultiblockTooltipBuilder createTooltip() {
        return new MultiblockTooltipBuilder().addMachineType(StatCollector.translateToLocal("gtsr.spacetime.type"))
            .addInfo(StatCollector.translateToLocal("gtsr.spacetime.preheat"))
            .addInfo(StatCollector.translateToLocal("gtsr.spacetime.maintain"))
            .addInfo(StatCollector.translateToLocal("gtsr.spacetime.safety"))
            .beginStructureBlock(43, 46, 9, false)
            .addController(StatCollector.translateToLocal("gtsr.spacetime.controller"))
            .addEnergyHatch(StatCollector.translateToLocal("gtsr.spacetime.energy"), 1)
            .addMaintenanceHatch(StatCollector.translateToLocal("gtsr.spacetime.maintenance"), 2)
            .toolTipFinisher("GT Steam Reborn");
    }

    @Override
    public ITexture[] getTexture(IGregTechTileEntity tile, ForgeDirection side, ForgeDirection facing, int color,
        boolean active, boolean redstone) {
        ITexture casing = Textures.BlockIcons.getCasingTextureForId(6);
        if (side != facing) return new ITexture[] { casing };
        return new ITexture[] { casing, TextureFactory.of(
            active ? Textures.BlockIcons.OVERLAY_FRONT_SCANNER_ACTIVE : Textures.BlockIcons.OVERLAY_FRONT_SCANNER) };
    }
}
