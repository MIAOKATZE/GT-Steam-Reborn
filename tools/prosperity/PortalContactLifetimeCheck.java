import java.lang.reflect.*;
import java.util.*;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.util.*;
import net.minecraft.world.*;
import com.miaokatze.gtsr.common.dimension.prosperity.portal.SpacetimeAttraction;
import com.miaokatze.gtsr.common.dimension.prosperity.travel.SpacetimeTravel;
import com.miaokatze.gtsr.common.machine.MTESpacetimeCalibration;

/** Actual Entity.equals, vector/box, and complete attraction logic; explicit machine/travel/world seams. */
public final class PortalContactLifetimeCheck {
    private static sun.misc.Unsafe unsafe;
    private static int checks,failures;
    public static final class Player extends EntityPlayerMP {
        Player(){super(null,null,null,null);}
        @Override public boolean isSneaking(){return false;}
    }
    public static final class Handler extends NetHandlerPlayServer {
        int pins;
        Handler(){super(null,null,null);}
        @Override public void setPlayerLocation(double x,double y,double z,float yaw,float pitch){
            pins++;playerEntity.posX=x;playerEntity.posY=y;playerEntity.posZ=z;
        }
    }
    public static final class FixtureWorld extends WorldServer {
        List<Entity> nearby;long time;
        FixtureWorld(){super(null,null,"fixture",0,null,null);}
        @Override public List getEntitiesWithinAABB(Class type,AxisAlignedBB box){return nearby;}
        @Override public List getCollidingBoundingBoxes(Entity entity,AxisAlignedBB box){return Collections.emptyList();}
        @Override public long getTotalWorldTime(){return time;}
    }
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static void check(boolean value,String name){checks++;if(!value){failures++;System.err.println("FAIL "+name);}}
    private static Player player(FixtureWorld world,int id,double y,double z)throws Exception{
        Player p=(Player)unsafe.allocateInstance(Player.class);p.setEntityId(id);p.worldObj=world;p.posX=23.5;p.posY=y;p.posZ=z;
        p.width=.6F;p.height=1.8F;unsafe.putObject(p,unsafe.objectFieldOffset(field(Entity.class,"boundingBox")),AxisAlignedBB.getBoundingBox(23.2,y,z-.3,23.8,y+1.8,z+.3));
        Field capabilities=field(net.minecraft.entity.player.EntityPlayer.class,"capabilities");unsafe.putObject(p,unsafe.objectFieldOffset(capabilities),new PlayerCapabilities());
        Handler h=(Handler)unsafe.allocateInstance(Handler.class);h.playerEntity=p;p.playerNetServerHandler=h;return p;
    }
    public static void main(String[]args)throws Exception{
        unsafe=(sun.misc.Unsafe)field(sun.misc.Unsafe.class,"theUnsafe").get(null);
        FixtureWorld world=(FixtureWorld)unsafe.allocateInstance(FixtureWorld.class);world.time=100;
        MTESpacetimeCalibration machine=new MTESpacetimeCalibration(world);SpacetimeAttraction attraction=new SpacetimeAttraction();
        Player old=player(world,17,24.5,4.5);world.nearby=Collections.<Entity>singletonList(old);
        attraction.tick(machine,Vec3.createVectorHelper(23.5,24.5,4.5));
        check(SpacetimeTravel.attempts==1,"touching core admits an initial transfer attempt");
        check(((Handler)old.playerNetServerHandler).pins==1,"failed transfer retains collision-checked core contact");
        Player replacement=player(world,17,1,13);old.isDead=true;world.nearby=Collections.<Entity>singletonList(replacement);world.time=140;
        check(old.equals(replacement),"native entity equality aliases replacement numeric ID");
        attraction.tick(machine,Vec3.createVectorHelper(23.5,24.5,4.5));
        check(SpacetimeTravel.attempts==1,"replacement outside core cannot inherit old transfer authorization");
        check(((Handler)replacement.playerNetServerHandler).pins==0&&replacement.posY==1,"replacement is not pinned to old core contact");
        Map<?,?> stages=(Map<?,?>)field(SpacetimeAttraction.class,"stages").get(attraction);
        Map<?,?> contacts=(Map<?,?>)field(SpacetimeAttraction.class,"contacts").get(attraction);
        check(stages.size()==1&&stages.keySet().iterator().next()==replacement,"attraction stage belongs to exact replacement entity");
        check(contacts.isEmpty(),"retiring old stage releases old contact even when an equal-ID player remains nearby");
        replacement.worldObj=(FixtureWorld)unsafe.allocateInstance(FixtureWorld.class);replacement.motionX=3;replacement.motionY=4;replacement.motionZ=5;
        attraction.releaseAll();check(stages.isEmpty()&&contacts.isEmpty(),"releaseAll clears all instance leases");
        check(replacement.motionX==3&&replacement.motionY==4&&replacement.motionZ==5,"source cleanup does not alter actor transferred to another world");
        if(failures!=0)throw new AssertionError(failures+"/"+checks+" portal contact assertions failed");
        System.out.println("PASS PortalContactLifetimeCheck: "+checks+" native identity/geometry assertions; fixture travel/world/machine");
    }
}
