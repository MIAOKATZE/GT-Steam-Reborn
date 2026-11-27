// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.mixin;

import java.util.List;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gtnewhorizon.structurelib.alignment.enumerable.ExtendedFacing;
import com.gtnewhorizon.structurelib.structure.IStructureDefinition;
import com.gtnewhorizon.structurelib.structure.IStructureElement;
import com.gtnewhorizon.structurelib.structure.IStructureWalker;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.miaokatze.gtsr.common.machine.recovery.GtsrStructureRecovery;

import appeng.me.helpers.IGridProxyable;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.metatileentity.implementations.MTEEnhancedMultiBlockBase;
import gregtech.api.metatileentity.implementations.MTEMultiBlockBase;
import gregtech.api.structure.error.StructureError;

/** Do not let the forced StructureChecker turn a GTSR structure check into a chunk load. */
@Mixin(value = MTEEnhancedMultiBlockBase.class, remap = false)
public abstract class MTEEnhancedRecoveryMixin {

    @Inject(method = "checkPiece(Ljava/lang/String;IIILjava/util/List;)Z", at = @At("HEAD"))
    private void gtsr$beginPiece(String piece, int a, int b, int c, List<StructureError> errors,
        CallbackInfoReturnable<Boolean> cir) {
        GtsrStructureRecovery.State state = GtsrStructureRecovery.state((MTEMultiBlockBase) (Object) this);
        if (state != null) state.beginPiece(errors, false);
    }

    @Inject(method = "checkPiece(Ljava/lang/String;III)Z", at = @At("HEAD"))
    private void gtsr$beginLegacyPiece(String piece, int a, int b, int c, CallbackInfoReturnable<Boolean> cir) {
        GtsrStructureRecovery.State state = GtsrStructureRecovery.state((MTEMultiBlockBase) (Object) this);
        if (state != null) state.beginPiece(null, true);
    }

    @Inject(method = "checkPiece", at = @At("RETURN"), cancellable = true)
    private void gtsr$incompletePiece(CallbackInfoReturnable<Boolean> cir) {
        GtsrStructureRecovery.State state = GtsrStructureRecovery.state((MTEMultiBlockBase) (Object) this);
        if (state != null && state.finishPiece()) cir.setReturnValue(false);
    }

    @WrapOperation(
        method = "checkPiece",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizon/structurelib/structure/IStructureDefinition;iterate(Ljava/lang/String;Lnet/minecraft/world/World;Lcom/gtnewhorizon/structurelib/alignment/enumerable/ExtendedFacing;IIIIIILcom/gtnewhorizon/structurelib/structure/IStructureWalker;)V"))
    private void gtsr$loadedStructureOnly(IStructureDefinition definition, String piece, World world,
        ExtendedFacing facing, int x, int y, int z, int a, int b, int c, IStructureWalker walker,
        Operation<Void> original) {
        MTEMultiBlockBase machine = (MTEMultiBlockBase) (Object) this;
        if (!GtsrStructureRecovery.applies(machine)) {
            original.call(definition, piece, world, facing, x, y, z, a, b, c, walker);
            return;
        }
        GtsrStructureRecovery.State recovery = GtsrStructureRecovery.state(machine);
        IStructureWalker safeWalker = new IStructureWalker() {

            @Override
            public boolean visit(IStructureElement element, World world, int x, int y, int z, int a, int b, int c) {
                if (!world.blockExists(x, y, z)) return blockNotLoaded(element, world, x, y, z, a, b, c);
                if (recovery != null && (recovery.active || recovery.phase == GtsrStructureRecovery.State.VERIFIED)
                    && world.getBlock(x, y, z) == GregTechAPI.sBlockMachines) {
                    TileEntity tile = world.getTileEntity(x, y, z);
                    Object meta = tile instanceof IGregTechTileEntity ? ((IGregTechTileEntity) tile).getMetaTileEntity()
                        : null;
                    if (meta == null || meta instanceof IGridProxyable
                        && (((IGridProxyable) meta).getProxy() == null || !((IGridProxyable) meta).getProxy()
                            .isReady())) {
                        recovery.unknownPiece(machine);
                        return false;
                    }
                }
                return walker.visit(element, world, x, y, z, a, b, c);
            }

            @Override
            public boolean blockNotLoaded(IStructureElement element, World world, int x, int y, int z, int a, int b,
                int c) {
                if (recovery != null) recovery.unknownPiece(machine);
                // Deliberately do not delegate: GT's forced checker calls element.check here.
                return false;
            }
        };
        original.call(definition, piece, world, facing, x, y, z, a, b, c, safeWalker);
    }
}
