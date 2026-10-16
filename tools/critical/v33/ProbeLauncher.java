import java.net.*;import java.nio.file.*;import java.util.*;import net.minecraft.launchwrapper.*;
public class ProbeLauncher {
 public static void main(String[] args)throws Exception {
  List<URL> urls=new ArrayList<>();for(String path:System.getProperty("java.class.path").split(";"))urls.add(Paths.get(path).toUri().toURL());
  java.io.File home=Paths.get(args[0]).getParent().resolve("runtime").toFile();home.mkdirs();
  Launch.classLoader=new LaunchClassLoader(urls.toArray(new URL[0]));Launch.blackboard=new HashMap<>();Launch.blackboard.put("fml.deobfuscatedEnvironment",true);Launch.minecraftHome=home;Thread.currentThread().setContextClassLoader(Launch.classLoader);
  Class<?> injection=Launch.classLoader.loadClass("cpw.mods.fml.relauncher.FMLInjectionData");java.lang.reflect.Field homeField=injection.getDeclaredField("minecraftHome");homeField.setAccessible(true);homeField.set(null,home);
  Launch.classLoader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData",Object[].class).invoke(null,(Object)new Object[]{"7","10","99","99","1.7.10","9.05",home,new ArrayList<>()});
  Launch.classLoader.loadClass(System.getProperty("probe.main")).getMethod("main",String[].class).invoke(null,(Object)args);
 }
}
