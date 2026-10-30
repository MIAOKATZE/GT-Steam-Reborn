package com.miaokatze.gtsr.common.critical;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.server.dedicated.*;
import net.minecraft.server.management.*;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.world.*;
import net.minecraftforge.common.util.FakePlayer;

/** Actual vanilla world/server/op-list protection with preallocated actors; no complete server or FakePlayer constructor boot. */
public final class NativeConstructionPermissionCheck {
 static sun.misc.Unsafe unsafe;
 static int checks;
 static final UUID OWNER=UUID.fromString("11111111-2222-3333-4444-555555555555");
 static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
 static void set(Object target,Class<?> type,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
 static <T>T alloc(Class<T> t)throws Exception{return t.cast(unsafe.allocateInstance(t));}
 static class Server extends DedicatedServer {Server(){super(new File("."));} DedicatedPlayerList players;@Override public DedicatedPlayerList getConfigurationManager(){return players;} @Override public int getSpawnProtectionSize(){return 16;} }
 static class TargetWorld extends WorldServer {TargetWorld(){super(null,null,"unused",0,null,null);} @Override public ChunkCoordinates getSpawnPoint(){return new ChunkCoordinates(0,64,0);} }
 static FakePlayer actor(World w,UUID id)throws Exception{FakePlayer p=alloc(FakePlayer.class);p.worldObj=w;set(p,Entity.class,"entityUniqueID",id);set(p,EntityPlayer.class,"gameProfile",new GameProfile(id,"Fixture Owner"));return p;}
 static TargetWorld world(Server server,int dimension)throws Exception{TargetWorld w=alloc(TargetWorld.class);set(w,World.class,"playerEntities",new ArrayList<>());set(w,World.class,"provider",alloc(WorldProviderSurface.class));w.provider.dimensionId=dimension;set(w,WorldServer.class,"mcServer",server);return w;}
 public static void main(String[] args)throws Exception{
  Field u=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);unsafe=(sun.misc.Unsafe)u.get(null);
  Server server=alloc(Server.class);server.players=alloc(DedicatedPlayerList.class);
  UserListOps ops=alloc(UserListOps.class);Map<String,UserListOpsEntry> entries=new HashMap<>();
  UUID admin=UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");
  entries.put(admin.toString(),new UserListOpsEntry(new GameProfile(admin,"Fixture Admin"),4));
  set(ops,UserList.class,"values",entries);set(server.players,ServerConfigurationManager.class,"ops",ops);
  set(server.players,ServerConfigurationManager.class,"mcServer",server);
  TargetWorld w=world(server,0),other=world(server,1);FakePlayer owner=actor(w,OWNER),otherOwner=actor(other,OWNER);
  CriticalConstructionAccess access=new CriticalConstructionAccess();set(access,CriticalConstructionAccess.class,"offlineOwner",owner);
  check(!access.canEdit(w,OWNER.toString(),1,64,1),"actual spawn protection denies offline non-op");
  check(access.canEdit(w,OWNER.toString(),32,64,32),"actual spawn protection permits outside protected range");
  w.playerEntities.add(owner);check(!access.canEdit(w,OWNER.toString(),1,64,1),"actual spawn protection denies online non-op");
  entries.put(OWNER.toString(),new UserListOpsEntry(new GameProfile(OWNER,"Actual Owner"),4));check(access.canEdit(w,OWNER.toString(),1,64,1),"actual owner UUID op permission respected");entries.remove(OWNER.toString());
  w.playerEntities.clear();other.playerEntities.add(otherOwner);check(!access.canEdit(w,OWNER.toString(),1,64,1),"owner in another dimension does not bypass target-world protection");
  set(access,CriticalConstructionAccess.class,"offlineOwner",otherOwner);check(access.canEdit(other,OWNER.toString(),1,64,1),"actual vanilla non-overworld policy unchanged");
  check(!access.canEdit(w,"LegacyName",1,64,1),"invalid legacy owner is not authorization");
  System.out.println("SOURCE WorldServer "+WorldServer.class.getProtectionDomain().getCodeSource().getLocation());
  System.out.println("SOURCE DedicatedServer "+DedicatedServer.class.getProtectionDomain().getCodeSource().getLocation());
  System.out.println("SOURCE CriticalConstructionAccess "+CriticalConstructionAccess.class.getProtectionDomain().getCodeSource().getLocation());
  System.out.println("PASS NativeConstructionPermissionCheck checks="+checks+"; actor allocation and server lifecycle are fixtures; real spawn-protection and UUID op lookup execute");
 }
}
