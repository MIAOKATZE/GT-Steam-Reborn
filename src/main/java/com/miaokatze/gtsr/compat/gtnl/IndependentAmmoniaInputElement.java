package com.miaokatze.gtsr.compat.gtnl;

import java.util.ArrayList;
import java.util.List;

import com.miaokatze.gtsr.common.machine.MTEAmmoniaPlant;

import gregtech.api.enums.HatchElement;
import gregtech.api.interfaces.IHatchElement;
import gregtech.api.interfaces.metatileentity.IMetaTileEntity;
import gregtech.api.util.IGTHatchAdder;

/** Only the ammonia plant's fluid input slots gain the optional steam source. */
public final class IndependentAmmoniaInputElement implements IHatchElement<MTEAmmoniaPlant> {

    public static final IndependentAmmoniaInputElement INSTANCE = new IndependentAmmoniaInputElement();

    private IndependentAmmoniaInputElement() {}

    @Override
    public String name() {
        return HatchElement.InputHatch.name();
    }

    @Override
    public boolean matchesHatch(IMetaTileEntity tile) {
        return PipelessSteamSupport.matches(tile, mteClasses());
    }

    @Override
    public List<Class<? extends IMetaTileEntity>> mteClasses() {
        List<Class<? extends IMetaTileEntity>> classes = new ArrayList<>(HatchElement.InputHatch.mteClasses());
        classes.add(PipelessSteamSupport.hatchClass());
        return classes;
    }

    @Override
    public IGTHatchAdder<? super MTEAmmoniaPlant> adder() {
        return (machine, tile, casing) -> PipelessSteamSupport.add(machine, tile, casing)
            || HatchElement.InputHatch.adder()
                .apply(machine, tile, casing);
    }

    @Override
    public long count(MTEAmmoniaPlant machine) {
        return HatchElement.InputHatch.count(machine) + PipelessSteamSupport.count(machine);
    }

    @Override
    public String getDescriptionLangKey() {
        return HatchElement.InputHatch.getDescriptionLangKey();
    }

    @Override
    public String getDisplayName() {
        return HatchElement.InputHatch.getDisplayName();
    }
}
