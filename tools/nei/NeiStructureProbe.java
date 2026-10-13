import java.io.File;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import net.minecraft.launchwrapper.*;

/** Isolated upstream scanner constructor and actual GTSR prototype definition. No game/FML lifecycle. */
public final class NeiStructureProbe {
 public static void main(String[] args)throws Exception {
  List<URL> urls=new ArrayList<>();
  for(String path:System.getProperty("java.class.path").split(File.pathSeparator))urls.add(Path.of(path).toUri().toURL());
  Launch.classLoader=new LaunchClassLoader(urls.toArray(new URL[0]));Launch.blackboard=new HashMap<>();
  Launch.blackboard.put("fml.deobfuscatedEnvironment",true);Launch.minecraftHome=new File(".");
  Thread.currentThread().setContextClassLoader(Launch.classLoader);
  Launch.classLoader.loadClass("cpw.mods.fml.common.Loader").getMethod("injectData",Object[].class).invoke(null,(Object)new Object[]{"7","10","99","99","1.7.10","9.05",new File("."),new ArrayList<>()});
  Class<?> runtime=Launch.classLoader.loadClass("NeiStructureRuntime");
  Method run=runtime.getMethod("run",String.class);run.setAccessible(true);run.invoke(null,args[0]);
 }
}

class NeiStructureRuntime {
 static void set(Object o,Class<?> c,String n,Object v)throws Exception {Field f=c.getDeclaredField(n);f.setAccessible(true);f.set(o,v);}
 public static void run(String version)throws Exception {
  Thread.currentThread().setName("Server thread");
  Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);sun.misc.Unsafe u=(sun.misc.Unsafe)uf.get(null);
  Object side=Proxy.newProxyInstance(NeiStructureRuntime.class.getClassLoader(),new Class<?>[]{cpw.mods.fml.common.IFMLSidedHandler.class},(p,m,a)->m.getName().equals("getSide")?cpw.mods.fml.relauncher.Side.SERVER:null);
  set(cpw.mods.fml.common.FMLCommonHandler.instance(),cpw.mods.fml.common.FMLCommonHandler.class,"sidedDelegate",side);
  cpw.mods.fml.common.ModContainer gt=(cpw.mods.fml.common.ModContainer)Proxy.newProxyInstance(NeiStructureRuntime.class.getClassLoader(),new Class<?>[]{cpw.mods.fml.common.ModContainer.class},(p,m,a)->switch(m.getName()){case "getVersion"->version;case "getModId"->"gregtech";case "getName"->"GregTech";default->null;});
  set(cpw.mods.fml.common.Loader.instance(),cpw.mods.fml.common.Loader.class,"namedMods",Map.of("gregtech",gt));
  net.minecraft.init.Bootstrap.func_151354_b();
  // Reproduce an early compatibility call while the mod blocks are not registered yet.
  if(com.miaokatze.gtsr.api.compat.GTVersionCompat.getReinforcedGlassBlock()!=null)throw new AssertionError("fixture must start before glass registration");
  net.minecraft.block.Block glass=new net.minecraft.block.Block(net.minecraft.block.material.Material.glass){};
  if(version.contains("52.594")) {
   Object registry=cpw.mods.fml.common.registry.GameData.getBlockRegistry();
   Method add=registry.getClass().getDeclaredMethod("addObjectRaw",int.class,String.class,Object.class);add.setAccessible(true);add.invoke(registry,3500,"IC2:blockAlloyGlass",glass);
  }
  else gregtech.api.GregTechAPI.sBlockGlass1=glass;
  System.out.println("FIXTURE selected="+com.miaokatze.gtsr.api.compat.GTVersionCompat.gtVersion()+" registered="+cpw.mods.fml.common.registry.GameRegistry.findBlock("IC2","blockAlloyGlass")+" expected="+glass);
  for(Field f:gregtech.api.GregTechAPI.class.getFields())if(f.getName().startsWith("sBlockCasings")&&net.minecraft.block.Block.class.isAssignableFrom(f.getType())) {
   try{f.set(null,u.allocateInstance(Class.forName("gregtech.common.blocks.BlockCasings"+f.getName().substring("sBlockCasings".length()))));}catch(ClassNotFoundException ignored){}
  }
  gregtech.api.GregTechAPI.sBlockFrames=(net.minecraft.block.Block)u.allocateInstance(gregtech.common.blocks.BlockFrameBox.class);
  com.miaokatze.gtsr.common.api.enums.GTSRHatchElement.registerMachineHatchClasses(com.miaokatze.gtsr.common.machine.base.MTESteamCoolingHatch.class,com.miaokatze.gtsr.common.machine.base.MTEPressureSteamCoolingHatch.class,com.miaokatze.gtsr.common.machine.base.MTEHatchPressureSteamInput.class);
  Object prototype=u.allocateInstance(com.miaokatze.gtsr.common.machine.cluster.MTESteamMineralLogisticsCluster.class);
  Class<?> scanner=Class.forName("blockrenderer6343.integration.gregtech.GTConstructableScan");
  for(Class<?> type:new Class<?>[]{gregtech.api.GregTechAPI.class,com.gtnewhorizon.structurelib.structure.StructureDefinition.class,scanner,prototype.getClass(),com.miaokatze.gtsr.api.compat.GTVersionCompat.class})System.out.println("SOURCE "+type.getName()+" "+type.getProtectionDomain().getCodeSource().getLocation());
  try {
   Object scan=scanner.getConstructor(java.util.function.Consumer.class,Collection.class).newInstance((java.util.function.Consumer<Object>)v->{},List.of(prototype));
   Field entries=scanner.getDeclaredField("constructables");entries.setAccessible(true);
   if(((Collection<?>)entries.get(scan)).size()!=1)throw new AssertionError("prototype was skipped");
   if(com.miaokatze.gtsr.api.compat.GTVersionCompat.getReinforcedGlassBlock()!=glass)throw new AssertionError("late glass identity lost");
   int meta=com.miaokatze.gtsr.api.compat.GTVersionCompat.getReinforcedGlassMeta();
   if(meta!=(version.contains("52.594")?0:10))throw new AssertionError("wrong glass metadata");
   System.out.println("RESULT PASS version="+version+" entries=1 glassIdentity=true meta="+meta);
  } catch(InvocationTargetException failure) {Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();System.out.println("RESULT FAIL version="+version+" glass="+com.miaokatze.gtsr.api.compat.GTVersionCompat.getReinforcedGlassBlock()+" cause="+cause);throw failure;}
 }
}
