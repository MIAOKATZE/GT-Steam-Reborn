import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import net.minecraft.launchwrapper.*;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.*;

/** Applies exactly the candidate's three protection mixins without FML or Minecraft startup. */
public final class CriticalV31MixinLauncher {
    public static void main(String[] args)throws Exception {
        ArrayList<URL> urls=new ArrayList<>();for(String item:System.getProperty("java.class.path").split(File.pathSeparator))urls.add(new File(item).toURI().toURL());
        Launch.classLoader=new LaunchClassLoader(urls.toArray(URL[]::new));Launch.blackboard=new HashMap<>();Launch.blackboard.put("TweakClasses",new ArrayList<String>());Launch.blackboard.put("Tweaks",new ArrayList<>());
        Launch.blackboard.put("fml.deobfuscatedEnvironment",true);
        for(String prefix:new String[]{"org.spongepowered.","com.llamalad7.mixinextras.","org.lwjgl.","CriticalV31MixinLauncher"})Launch.classLoader.addClassLoaderExclusion(prefix);
        Field platform=MixinBootstrap.class.getDeclaredField("platform");platform.setAccessible(true);platform.set(null,new org.spongepowered.asm.launch.platform.MixinPlatformManager(){@Override public void init(){}@Override public Collection<String> getPhaseProviderClasses(){return Collections.emptyList();}});
        MixinBootstrap.init();MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);Mixins.addConfiguration("mixins.gtsr.probe.v31.json");Method phase=MixinEnvironment.class.getDeclaredMethod("gotoPhase",MixinEnvironment.Phase.class);phase.setAccessible(true);phase.invoke(null,MixinEnvironment.Phase.DEFAULT);Launch.classLoader.registerTransformer("org.spongepowered.asm.mixin.transformer.Proxy");
        var report=new com.google.gson.JsonObject();var transformed=new com.google.gson.JsonArray();
        for(String target:new String[]{"net.minecraft.block.Block","net.minecraft.entity.Entity","gregtech.common.blocks.BlockMachines"}){
            Class<?> type=Class.forName(target,false,Launch.classLoader);if(type.getClassLoader()!=Launch.classLoader)throw new AssertionError("Escaped transforming loader "+target);long merged=Arrays.stream(type.getDeclaredMethods()).filter(m->m.getName().contains("gtsr$")).count();if(merged==0)throw new AssertionError("Missing actual candidate mixin "+target);var row=new com.google.gson.JsonObject();row.addProperty("class",target);row.addProperty("mergedMethods",merged);transformed.add(row);
        }
        report.add("transformed",transformed);Class<?> runtime=Class.forName("com.miaokatze.gtsr.common.critical.CriticalV31ProtectionMixinRuntime",true,Launch.classLoader);Method exercise=runtime.getDeclaredMethod("exercise");exercise.setAccessible(true);Object result=exercise.invoke(null);report.add("runtime",new com.google.gson.JsonParser().parse(result.toString()));report.addProperty("fullMinecraftStarted",false);report.addProperty("FMLStarted",false);report.addProperty("status","PASS");Files.writeString(Path.of(args[0]),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
