package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import com.google.gson.JsonObject;
import com.google.gson.JsonElement;
import net.minecraft.entity.DataWatcher;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;

/** Offline production predicates/profile loader, not a running Forge server. */
public final class AuthoredEchoTimelineCheck {
    static final class WatchedEcho extends EntityOldEcho {
        private WatchedEcho() { super(null); }
        @Override public void func_145781_i(int index) { }
    }
    static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) throws Exception {
        Field uf = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); uf.setAccessible(true);
        sun.misc.Unsafe u = (sun.misc.Unsafe) uf.get(null);
        EntityOldEcho owner = (EntityOldEcho) u.allocateInstance(WatchedEcho.class);
        DataWatcher watcher = new DataWatcher(owner);
        watcher.addObject(25, EchoKind.DR19.ordinal()); watcher.addObject(23, -1);
        Field dw = Entity.class.getDeclaredField("dataWatcher"); dw.setAccessible(true); dw.set(owner, watcher);
        owner.setNodeIndex(314159); check(owner.getPlatformId() == 314159, "large node index");
        AuthoredEchoAbilities abilities = new AuthoredEchoAbilities(owner);
        int profiles=0, timelineEvents=0;
        for (EchoKind kind : EchoKind.values()) {
            JsonObject p=AuthoredEchoAbilities.profile(kind); if(p==null)continue; profiles++;
            watcher.updateObject(25,kind.ordinal());
            int index=0;
            for(JsonElement element:p.getAsJsonArray("skills")) {
                JsonObject s=element.getAsJsonObject(); index++;
                check(abilities.clip(index).equals(s.get("clip").getAsString()),"skill clip " + kind);
                check(abilities.duration(index)>0 && abilities.range(index)>0,"duration/range " + kind);
                for(JsonElement event:s.getAsJsonArray("events")) {
                    int tick=event.getAsJsonObject().get("at").getAsInt();
                    check(tick>=0 && tick<abilities.duration(index),"event inside cast " + kind); timelineEvents++;
                }
            }
        }
        check(profiles==22,"profiles");
        watcher.updateObject(25,EchoKind.DR19.ordinal());
        Field tx=AuthoredEchoAbilities.class.getDeclaredField("targetX"),tz=AuthoredEchoAbilities.class.getDeclaredField("targetZ");
        tx.setAccessible(true);tz.setAccessible(true);tx.setDouble(abilities,0);tz.setDouble(abilities,3);
        Method contains=AuthoredEchoAbilities.class.getDeclaredMethod("contains",JsonObject.class,double[].class,EntityPlayer.class); contains.setAccessible(true);
        EntityPlayerMP player=(EntityPlayerMP)u.allocateInstance(EntityPlayerMP.class); player.width=.6F;player.height=2;
        JsonObject cone=new JsonObject();cone.addProperty("shape","cone");cone.addProperty("radius",3);cone.addProperty("angle",70);
        player.posX=Math.sin(Math.toRadians(20))*2;player.posZ=Math.cos(Math.toRadians(20))*2;
        check((Boolean)contains.invoke(abilities,cone,new double[]{0,1,0},player),"inside 70 degree authored cone");
        player.posX=Math.sin(Math.toRadians(40))*2;player.posZ=Math.cos(Math.toRadians(40))*2;
        check(!(Boolean)contains.invoke(abilities,cone,new double[]{0,1,0},player),"outside 70 degree authored cone");
        JsonObject sphere=new JsonObject();sphere.addProperty("radius",2);
        player.posX=0;player.posZ=0;player.posY=10;
        check(!(Boolean)contains.invoke(abilities,sphere,new double[]{0,1,0},player),"sphere rejects another floor");
        System.out.println("PASS: " + profiles + " production profiles, " + timelineEvents + " timed events, variable cones/floor isolation/large node");
    }
}
