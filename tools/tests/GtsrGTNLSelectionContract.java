import java.io.File;
import java.lang.reflect.Field;

import com.miaokatze.gtsr.compat.gtnl.GTNLMixinSelection;
import com.miaokatze.gtsr.compat.gtnl.PipelessSteamSupport;

/** Reads real optional JAR bytes and on-disk config without defining its controller or hatch. */
public final class GtsrGTNLSelectionContract {

    public static void main(String[] args) throws Exception {
        Field home = cpw.mods.fml.relauncher.FMLInjectionData.class.getDeclaredField("minecraftHome");
        home.setAccessible(true);
        home.set(null, new File("."));
        boolean present = Boolean.parseBoolean(args[0]), enhancement = Boolean.parseBoolean(args[1]),
            pipeless = Boolean.parseBoolean(args[2]);
        check(GTNLMixinSelection.shouldApply("GTNLNativeSteamMachineMixin") == present, "identity bridge");
        check(
            GTNLMixinSelection.shouldApply("SteamMultiMachineBaseGTNLMixin") == (present && enhancement),
            "enhancement bridge");
        check(
            GTNLMixinSelection.shouldApply("SharedSteamSourceMixin") == (present && pipeless),
            "shared pipeless bridge");
        check(GTNLMixinSelection.pipelessEnabled() == (present && pipeless), "ordinary method pipeless gate");
        check(PipelessSteamSupport.count(new Object()) == 0, "unrelated controller has no optional sources");
        if (!present || !pipeless) check(
            PipelessSteamSupport.candidates(java.util.Collections.emptyList(), true)
                .isEmpty(),
            "disabled candidate path does not load optional hatch");
        System.out.println(
            "PASS GTNL native-resource shape/config matrix I=" + present + " E=" + enhancement + " P=" + pipeless);
    }

    static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
