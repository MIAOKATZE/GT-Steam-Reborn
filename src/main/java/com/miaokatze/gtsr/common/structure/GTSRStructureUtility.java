package com.miaokatze.gtsr.common.structure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

import com.gtnewhorizon.structurelib.structure.AutoPlaceEnvironment;
import com.gtnewhorizon.structurelib.structure.IItemSource;
import com.gtnewhorizon.structurelib.structure.IStructureElement;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.LoaderState;
import gregtech.api.GregTechAPI;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.HatchElementBuilder;

/** Shared structure catalog and mechanical-casing policy for non-critical machines. */
public final class GTSRStructureUtility {

    private GTSRStructureUtility() {}

    /** Only replaceable mechanical-casing positions belong in {@code shellPositions}. */
    public static int minimumCasings(int shellPositions) {
        return shellPositions <= 0 ? 0 : Math.max(1, Math.min(8, shellPositions / 10));
    }

    /** Reserve enough positions for required ports on very small structures. */
    public static int minimumCasings(int shellPositions, int requiredPorts) {
        return Math.min(minimumCasings(shellPositions), Math.max(0, shellPositions - Math.max(0, requiredPorts)));
    }

    public static boolean hasMinimumCasings(int actualCasings, int shellPositions) {
        return actualCasings >= minimumCasings(shellPositions);
    }

    public static boolean hasMinimumCasings(int actualCasings, int shellPositions, int requiredPorts) {
        return actualCasings >= minimumCasings(shellPositions, requiredPorts);
    }

    public static int countShape(String[][] shape, char... symbols) {
        int count = 0;
        for (String[] slice : shape) {
            for (String row : slice) {
                for (int i = 0; i < row.length(); i++) {
                    for (char symbol : symbols) {
                        if (row.charAt(i) == symbol) {
                            count++;
                            break;
                        }
                    }
                }
            }
        }
        return count;
    }

    /**
     * Keep GT's check, hint, hatch-channel, skip and placement behavior, while exposing concrete registered stacks
     * to structure renderers. Pass every allowed hatch type, including optional ones; the builder's current item
     * predicate is still applied, including caller-added tier or voltage filters. Chain this element before the
     * counted casing fallback. Use {@code anyOf} for optional hatches rather than manufacturing a required count.
     */
    @SafeVarargs
    public static <T> IStructureElement<T> hatchWithCandidates(HatchElementBuilder<T> builder,
        IHatchElement<? super T>... candidateTypes) {
        if (candidateTypes.length == 0) throw new IllegalArgumentException("Missing hatch candidate types");
        IStructureElement<T> delegate = builder.build();
        List<IHatchElement<? super T>> types = Arrays.asList(candidateTypes.clone());
        return new IStructureElement<>() {

            private List<ItemStack> cachedCandidates;

            private synchronized List<ItemStack> candidates() {
                if (cachedCandidates != null) return cachedCandidates;
                List<ItemStack> stacks = new ArrayList<>();
                for (IMetaTileEntity mte : GregTechAPI.METATILEENTITIES) {
                    if (mte == null) continue;
                    if (types.stream()
                        .noneMatch(
                            type -> type.matchesHatch(mte) && !type.mteBlacklist()
                                .contains(mte.getClass())))
                        continue;
                    ItemStack stack = mte.getStackForm(1);
                    if (stack != null && stack.getItem() != null) stacks.add(stack);
                }
                // A structure can be queried during machine registration. Never freeze that incomplete catalog.
                // Normal NEI/world queries occur after AVAILABLE, so each element scans the registry only once.
                if (Loader.instance()
                    .hasReachedState(LoaderState.AVAILABLE)) {
                    cachedCandidates = Collections.unmodifiableList(stacks);
                }
                return stacks;
            }

            @Override
            public boolean check(T t, World world, int x, int y, int z) {
                return delegate.check(t, world, x, y, z);
            }

            @Override
            public boolean couldBeValid(T t, World world, int x, int y, int z, ItemStack trigger) {
                return delegate.couldBeValid(t, world, x, y, z, trigger);
            }

            @Override
            public boolean spawnHint(T t, World world, int x, int y, int z, ItemStack trigger) {
                return delegate.spawnHint(t, world, x, y, z, trigger);
            }

            @Override
            public boolean placeBlock(T t, World world, int x, int y, int z, ItemStack trigger) {
                return delegate.placeBlock(t, world, x, y, z, trigger);
            }

            @Override
            public List<String> getDescription(T context) {
                return delegate.getDescription(context);
            }

            @Override
            public BlocksToPlace getBlocksToPlace(T t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                BlocksToPlace nativeBlocks = delegate.getBlocksToPlace(t, world, x, y, z, trigger, env);
                if (nativeBlocks == null || nativeBlocks.getStacks() != null) return nativeBlocks;
                Predicate<ItemStack> filter = nativeBlocks.getPredicate();
                List<ItemStack> stacks = new ArrayList<>();
                for (ItemStack stack : candidates()) {
                    if (filter.test(stack)) stacks.add(stack.copy());
                }
                return stacks.isEmpty() ? BlocksToPlace.createEmpty() : BlocksToPlace.create(stacks);
            }

            @Deprecated
            @Override
            public PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z, ItemStack trigger,
                IItemSource source, EntityPlayerMP actor, Consumer<IChatComponent> chatter) {
                return delegate.survivalPlaceBlock(t, world, x, y, z, trigger, source, actor, chatter);
            }

            @Override
            public PlaceResult survivalPlaceBlock(T t, World world, int x, int y, int z, ItemStack trigger,
                AutoPlaceEnvironment env) {
                return delegate.survivalPlaceBlock(t, world, x, y, z, trigger, env);
            }
        };
    }
}
