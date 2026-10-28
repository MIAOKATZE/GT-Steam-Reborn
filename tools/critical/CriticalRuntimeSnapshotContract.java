package com.miaokatze.gtsr.common.critical;

import static com.miaokatze.gtsr.common.critical.CriticalV32Fixture.*;

import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.ForgeDirection;
import io.netty.buffer.Unpooled;
import com.miaokatze.gtsr.common.machine.MTECriticalMachine;
import gregtech.api.metatileentity.BaseMetaTileEntity;

/** Production capture and version-2 wire roundtrip for the four missing native gallery states. */
public final class CriticalRuntimeSnapshotContract {
    static Method method(Class<?> type,String name,Class<?>... parameters)throws Exception {
        Method m=type.getDeclaredMethod(name,parameters);m.setAccessible(true);return m;
    }
    @SuppressWarnings("unchecked") public static void main(String[] args)throws Exception {
        Field f=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");f.setAccessible(true);unsafe=(sun.misc.Unsafe)f.get(null);
        Class<?> snapshot=Class.forName("com.miaokatze.gtsr.common.gui.CriticalBusinessSnapshot");
        Path candidate=Path.of(args[0]).toRealPath();
        for(Class<?> type:new Class<?>[]{CriticalMachineState.class,MTECriticalMachine.class,snapshot})
            check(Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI()).toRealPath().equals(candidate),
                "production class loaded solely from candidate jar: "+type.getName());
        Properties lang=new Properties();
        try(var stream=CriticalMachineState.class.getResourceAsStream("/assets/gtsr/lang/zh_CN.lang")) {
            lang.load(new InputStreamReader(stream,StandardCharsets.UTF_8));
        }
        Field localized=StatCollector.class.getDeclaredField("localizedName");localized.setAccessible(true);
        Object translations=localized.get(null);
        Field names=translations.getClass().getDeclaredField("languageList");names.setAccessible(true);
        Map<String,String> strings=(Map<String,String>)names.get(translations);
        for(String key:lang.stringPropertyNames())strings.put(key,lang.getProperty(key));
        materials();
        Method capture=method(snapshot,"capture",CriticalMachineState.class,MTECriticalMachine.class);
        Method write=method(snapshot,"write",PacketBuffer.class),read=method(snapshot,"read",PacketBuffer.class);
        Method status=method(snapshot,"runtimeStatusKey"),blocker=method(snapshot,"runtimeBlockerKey"),detail=method(snapshot,"runtimeBlockerDetail");
        String[] keys={"runtime.solar.missing_working_fluid","runtime.solar.missing_exciter","runtime.output_blocked","runtime.sun.production_paused"};
        String[] raw={"本批工作流体不足；未扣料","本批缺少激活剂；未扣料","等待批次输出空间","加工已暂停，恒星持续消耗氦三与电力"};
        for(int i=0;i<keys.length;i++) {
            SparseWorld world=world();CriticalMachineState state=state(world,31,ForgeDirection.NORTH);
            if(i<3) {
                NBTTagCompound n=new NBTTagCompound();state.writeToNBT(n);
                n.setTag("config",new CriticalConfiguration(CriticalTier.T3,CriticalMachineKind.SOLAR,0,0,0).write());
                state.readFromNBT(n);geometry(state);
            }
            fill(state);CriticalV32Contract.attach(state);
            BaseMetaTileEntity main=(BaseMetaTileEntity)world.getTileEntity(state.blockWorldX(56,35),state.yCoord,state.blockWorldZ(56,35));
            main.enableWorking();
            state.energyRuntimeData().setBoolean("funded",true);state.energyRuntimeData().setInteger("progress",123);
            if(i==3) {
                state.getStellarRuntimeData().setBoolean("hot",true);
                state.getStellarRuntimeData().setBoolean("productionPaused",true);
                state.fail(raw[i]);
            } else state.failRuntime(keys[i],raw[i]);
            String paid=state.energyRuntimeData().toString(),thermal=state.getStellarRuntimeData().toString();
            Object captured=capture.invoke(null,state,null);
            PacketBuffer packet=new PacketBuffer(Unpooled.buffer());write.invoke(captured,packet);
            int bytes=packet.readableBytes();Object decoded=read.invoke(null,packet);
            check(status.invoke(decoded).equals(keys[i]),"captured status survives wire: "+keys[i]);
            check(blocker.invoke(decoded).equals(keys[i]),"captured blocker survives wire: "+keys[i]);
            String expected=lang.getProperty("gtsr.critical."+keys[i]);
            check(expected!=null&&detail.invoke(decoded).equals(expected),"decoded blocker detail localizes from candidate language: "+keys[i]);
            check(packet.readableBytes()==0,"full version-2 packet consumed: "+keys[i]);
            check(state.energyRuntimeData().toString().equals(paid)&&state.getStellarRuntimeData().toString().equals(thermal),
                "capture and wire leave paid ledgers unchanged: "+keys[i]);
            System.out.println("ROUNDTRIP "+keys[i]+" bytes="+bytes+" localizedDetailMatchesCandidate=true");
            packet.release();
        }
        System.out.println("PASS CriticalRuntimeSnapshotContract checks="+checks);
    }
}
