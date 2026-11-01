package diagnostic.launchwrapper;

import java.io.File;
import java.util.List;

import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;

/** Runs the normal Launch.launch/MixinTweaker bootstrap, with a small diagnostic as its main class. */
public final class ProbeTweaker implements ITweaker {

    private String[] arguments;

    @Override
    @SuppressWarnings("unchecked")
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        arguments = args.subList(0, 3).toArray(new String[3]);
        ((List<String>) Launch.blackboard.get("TweakClasses")).add("org.spongepowered.asm.launch.MixinTweaker");
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        // MixinTweaker performs the following bootstrap turn.
    }

    @Override
    public String getLaunchTarget() {
        return "diagnostic.launchwrapper.MixinPipelineProbe";
    }

    @Override
    public String[] getLaunchArguments() {
        return arguments;
    }
}
