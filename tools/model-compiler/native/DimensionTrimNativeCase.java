package coreglobal.followup.qa;

import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Actual product objects, native NBT, server command dispatch and the stitched block atlas. */
public final class DimensionTrimNativeCase implements FollowupQA.Case {
    private static final String ROOT="com.miaokatze.gtsr.";
    private static final String PROSPERITY=ROOT+"common.dimension.prosperity.";
    // Frozen released wire order: expectations do not come from the current enum implementation.
    private static final String[] WIRE=("dr-01 dr-03 dr-05 dr-07 dr-10 dr-11 dr-12 dr-15 dr-20 di-02 di-05 di-08 di-10 di-13 dc-02 dc-08 do-01 di-01 di-03 di-04 di-06 di-07 di-09 di-11 di-12 di-14 di-15 do-02 dr-02 dr-04 dr-06 dr-08 dr-09 dr-13 dr-14 dr-16 dr-17 dr-18 dr-19").split(" ");
    private static final String[][] RETIRED={{"dr-02","dr-04"},{"dr-05","dr-04"},{"dr-06","dr-04"},{"dr-07","dr-04"},{"dr-08","dr-04"},{"dr-10","dr-09"},{"dr-13","dr-14"},{"dr-16","dr-04"},{"dr-19","dr-20"},{"di-03","di-12"},{"di-06","di-11"},{"di-09","di-04"},{"di-14","di-11"},{"di-15","di-04"}};
    private static final String[][] ALIASES={{"riveted_hatch","rust_riveted_plate"},{"boiler_casing","rust_riveted_plate"},{"pressure_reservoir","rust_riveted_plate"},{"patina_trim","cast_iron_pillar"},{"silent_bell_base","slag_masonry"},{"sootstone_tiles","slag_masonry"},{"salvage_workbench","rootwood_archive"}};
    private final Map<String,Object> result=new LinkedHashMap<String,Object>();
    private final List<String> checks=new ArrayList<String>();
    private Path run;
    private java.util.zip.ZipFile productResources;
    private Object spriteResourceManager;
    private boolean serverDone, atlasDone, finished;
    private static Class<?> type(String name)throws Exception{return Class.forName(name);}
    private static Object call(Object target,String names,Object...args)throws Exception{return ExtensionSupport.call(target,names,args);}
    private static Object get(Object target,String names)throws Exception{return ExtensionSupport.get(target,names);}
    private void check(boolean value,String label){ExtensionSupport.check(value,label);checks.add(label);}
    private static Object construct(String name,Object...args)throws Exception{
        Class<?> t=type(name);for(Constructor<?> c:t.getDeclaredConstructors())if(ExtensionSupport.compatible(c.getParameterTypes(),args)){
            c.setAccessible(true);try{return c.newInstance(args);}catch(InvocationTargetException e){Throwable v=e.getCause();if(v instanceof Exception)throw (Exception)v;if(v instanceof Error)throw (Error)v;throw e;}
        }throw new NoSuchMethodException(name+" constructor");
    }
    private static Object nbt()throws Exception{return construct("net.minecraft.nbt.NBTTagCompound");}
    private static int integer(Object value){return ((Number)value).intValue();}
    private static String string(Object n,String key)throws Exception{return String.valueOf(call(n,"getString|func_74779_i",key));}
    private static void text(Object n,String key,String value)throws Exception{call(n,"setString|func_74778_a",key,value);}
    private static void flag(Object n,String key,boolean value)throws Exception{call(n,"setBoolean|func_74757_a",key,value);}
    private static String code(Object kind)throws Exception{return String.valueOf(get(kind,"code"));}
    private Set<String> manifest()throws Exception{
        Set<String> values=new LinkedHashSet<String>();
        try(BufferedReader input=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/rigmesh-reference/active-models.txt"),StandardCharsets.UTF_8))){
            for(String line;(line=input.readLine())!=null;){String value=line.trim();if(!value.isEmpty()&&!value.startsWith("#"))check(values.add(value),"Unique manifest "+value);}
        }return values;
    }
    private void echoContracts(Object world)throws Exception{
        Class<?> kinds=type(PROSPERITY+"echo.EchoKind");Object[] values=kinds.getEnumConstants();
        check(values.length==WIRE.length,"Released enum slot count retained");
        Map<String,String> expected=new LinkedHashMap<String,String>();for(String value:WIRE)expected.put(value,value);for(String[] pair:RETIRED)expected.put(pair[0],pair[1]);
        String[] oldAliases={"di-04","di-04","di-05","di-12","di-08","di-10","di-11","di-13","di-04","di-12"};
        String[] oldAliasBodies={"di-15","di-09","di-05","di-12","di-08","di-10","di-06","di-13","di-04","di-03"};
        for(int i=0;i<oldAliases.length;i++)expected.put("di-"+(16+i),oldAliases[i]);
        Set<String> admitted=new LinkedHashSet<String>();
        for(int index=0;index<values.length;index++){
            check(code(values[index]).equals(WIRE[index]),"Frozen wire ordinal "+index+" "+WIRE[index]);
            Object canonical=call(values[index],"canonical");admitted.add(code(canonical));
            check(code(call(kinds,"byWireId",index)).equals(expected.get(WIRE[index])),"Wire admission canonical "+WIRE[index]);
        }
        Set<String> active=manifest();active.remove("dc-10");check(admitted.equals(active),"Every active echo species exactly matches runtime manifest (separate Silent King excluded)");
        List<Map<String,Object>> migrated=new ArrayList<Map<String,Object>>();
        for(Map.Entry<String,String> pair:expected.entrySet()){
            String old=pair.getKey(),replacement=pair.getValue();
            check(code(call(kinds,"byCode",old)).equals(replacement),"Saved code canonical "+old+" -> "+replacement);
            check(code(call(kinds,"byCode",old.toUpperCase(Locale.ROOT))).equals(replacement),"Uppercase saved code "+old);
            Object entity=construct(PROSPERITY+"echo.EntityOldEcho",world);
            String originalBody=old;if(old.startsWith("di-")&&Integer.parseInt(old.substring(3))>=16)originalBody=oldAliasBodies[Integer.parseInt(old.substring(3))-16];
            Object kind=call(kinds,"byCode",old);for(Object raw:values)if(code(raw).equals(originalBody))kind=raw;
            call(entity,"initializeEcho",kind,"owned-retired-fixture",8D,80D,8D,false);
            check(code(call(entity,"getKind")).equals(replacement),"Initialized canonical body "+old);
            if(old.equals(replacement))continue; // Migration fixtures target the retired and earlier released aliases.
            Object saved=nbt();call(entity,"writeToNBT|func_70109_d",saved);text(saved,"echoKind",old);flag(saved,"echoDeathRecorded",true);flag(saved,"encounterDeathXPReleased",true);
            text(saved,"encounter","owned-retired-fixture");text(saved,"echoSummoner","owned-original-summoner");
            for(String key:new String[]{"echoSkill","echoAnnouncedSkill","echoPhaseLockTicks"})call(saved,"setInteger|func_74768_a",key,2);
            call(saved,"setInteger|func_74768_a","echoSkillTicks",17);call(saved,"setInteger|func_74768_a","objectiveZone",3);
            float oldMax=((Number)get(kind,"maxHealth")).floatValue();call(saved,"setFloat|func_74776_a","Health",oldMax*.5F);call(saved,"setFloat|func_74776_a","HealF",oldMax*.5F);
            Object attributes=call(saved,"getTagList|func_150295_c","Attributes",10);boolean maxFound=false;
            for(int i=0;i<integer(call(attributes,"tagCount|func_74745_c"));i++){Object attribute=call(attributes,"getCompoundTagAt|func_150305_b",i);if("generic.maxHealth".equals(string(attribute,"Name"))){call(attribute,"setDouble|func_74780_a","Base",(double)oldMax);maxFound=true;}}
            check(maxFound,"Fixture contains native saved maxHealth attribute "+old);
            Object loaded=construct(PROSPERITY+"echo.EntityOldEcho",world);call(loaded,"readFromNBT|func_70020_e",saved);
            Object output=nbt();call(loaded,"writeToNBT|func_70109_d",output);
            check(string(output,"echoKind").equals(replacement),"Old NBT writes canonical code "+old);
            check((Boolean)call(output,"getBoolean|func_74767_n","echoDeathRecorded"),"Spent death accounting retained "+old);
            Object canonical=call(kinds,"byCode",old);float max=((Number)get(canonical,"maxHealth")).floatValue(),health=((Number)call(loaded,"getHealth|func_110143_aJ")).floatValue();
            check(Math.abs(health-max*.5F)<.001F,"Migration retains 50 percent health "+old);
            check(Math.abs(((Number)get(loaded,"width|field_70130_N")).floatValue()-((Number)get(canonical,"width")).floatValue())<.001F&&Math.abs(((Number)get(loaded,"height|field_70131_O")).floatValue()-((Number)get(canonical,"height")).floatValue())<.001F,"Canonical physical body size "+old);
            check(code(call(loaded,"getKind")).equals(replacement)&&call(call(loaded,"getKind"),"flies").equals(call(canonical,"flies")),"Canonical movement species "+old);
            check(integer(call(loaded,"getSkillId"))==0&&integer(call(loaded,"getSkillTicks"))==0&&integer(call(loaded,"getPhaseLockTicks"))==0&&integer(call(loaded,"getAnnouncedSkill"))==0,"Retired partial cast and locks cancelled "+old);
            check(call(loaded,"getUniqueID|func_110124_au").equals(call(entity,"getUniqueID|func_110124_au")),"Migration retains UUID "+old);
            check(string(output,"encounter").equals("owned-retired-fixture")&&string(output,"echoSummoner").equals("owned-original-summoner")&&integer(call(output,"getInteger|func_74762_e","objectiveZone"))==3&&(Boolean)call(output,"getBoolean|func_74767_n","encounterDeathXPReleased"),"Migration retains ownership objective and spent XP flags "+old);
            Object again=construct(PROSPERITY+"echo.EntityOldEcho",world);call(again,"readFromNBT|func_70020_e",output);Object second=nbt();call(again,"writeToNBT|func_70109_d",second);
            check(string(second,"echoKind").equals(replacement)&&(Boolean)call(second,"getBoolean|func_74767_n","echoDeathRecorded"),"Second NBT roundtrip retains canonical spent body "+old);
            check(Math.abs(((Number)call(again,"getHealth|func_110143_aJ")).floatValue()-health)<.001F&&call(again,"getUniqueID|func_110124_au").equals(call(loaded,"getUniqueID|func_110124_au")),"Second migration is idempotent for health and UUID "+old);
            Object deadSave=call(saved,"copy|func_74737_b");call(deadSave,"setFloat|func_74776_a","Health",0F);call(deadSave,"setFloat|func_74776_a","HealF",0F);Object dead=construct(PROSPERITY+"echo.EntityOldEcho",world);call(dead,"readFromNBT|func_70020_e",deadSave);
            check(((Number)call(dead,"getHealth|func_110143_aJ")).floatValue()==0F,"Retired migration cannot revive a dead body "+old);
            Map<String,Object> row=new LinkedHashMap<String,Object>();row.put("old",old);row.put("canonical",replacement);row.put("health",call(again,"getHealth|func_110143_aJ"));row.put("status","PASS");migrated.add(row);
        }
        // A live survivor can be consumed once after legacy compaction, never twice.
        Object site=nbt(),spawner=nbt();String cage="old-cage";
        flag(site,"spawner-admitted:"+cage+":0",true);flag(site,"spawner-dead:"+cage+":0",true);flag(site,"spawner-admitted:"+cage+":1",true);
        call(spawner,"setInteger|func_74768_a","deadCount",7);Class<?> ledger=type(PROSPERITY+"remaster.RemasterSpawnerLedger");
        check((Boolean)call(ledger,"compact",site,spawner,cage),"Native legacy ledger compacted once");
        check(!(Boolean)call(ledger,"died",site,spawner,cage,0),"Already spent legacy admission cannot award again");
        check((Boolean)call(ledger,"died",site,spawner,cage,1),"Outstanding legacy admission counted once");
        check(!(Boolean)call(ledger,"died",site,spawner,cage,1),"Repeated death cannot increment reward progress");
        check(integer(call(spawner,"getInteger|func_74762_e","deadCount"))==8,"Only one new death counted");
        Object oldSite=construct(PROSPERITY+"remaster.RemasterSite","subsided_factory",0,731L,128,80,128);
        Object record=call(oldSite,"save"),state=nbt(),cageState=nbt();
        for(String marker:new String[]{"boss-dead","mission-boss-ready","claimed:old-reward","dead:old-retired-body","spawner-dead:old-cage:0"})flag(state,marker,true);
        text(cageState,"code","dr-05");call(cageState,"setInteger|func_74768_a","serial",9);call(cageState,"setInteger|func_74768_a","deadCount",7);call(cageState,"setInteger|func_74768_a","count",3);
        call(state,"setTag|func_74782_a","spawner:old-cage",cageState);call(record,"setTag|func_74782_a","state",state);
        Object list=construct("net.minecraft.nbt.NBTTagList");call(list,"appendTag|func_74742_a",record);Object oldData=nbt();call(oldData,"setTag|func_74782_a","sites",list);
        Object data=construct(PROSPERITY+"remaster.RemasterData");call(data,"readFromNBT|func_76184_a",oldData);Object savedData=nbt();call(data,"writeToNBT|func_76187_b",savedData);
        check(savedData.equals(oldData),"Real RemasterData roundtrip preserves claimed rewards old species and cumulative spawner progress");
        Object twice=construct(PROSPERITY+"remaster.RemasterData");call(twice,"readFromNBT|func_76184_a",savedData);Object twiceSaved=nbt();call(twice,"writeToNBT|func_76187_b",twiceSaved);
        check(twiceSaved.equals(oldData),"Second world ledger roundtrip cannot reset old mission or reward flags");
        result.put("savedEntityMigrations",migrated);
    }
    private void commands(Object actor)throws Exception{
        Object command=construct(ROOT+"common.commands.GTSRCommand");int dimension=ExtensionSupport.dimension(actor);double[] point=ExtensionSupport.position(actor);
        for(String verb:new String[]{"tpdim","diag","locate","tplocate"})for(String selector:new String[]{"A","B","a","b"}){
            String[] args=(verb.equals("locate")||verb.equals("tplocate"))?new String[]{verb,selector,"ruined_town"}:new String[]{verb,selector};
            boolean rejected=false;try{call(command,"processCommand|func_71515_b",actor,args);}catch(Exception e){rejected=type("net.minecraft.command.WrongUsageException").isInstance(e);if(!rejected)throw e;}
            check(rejected,"Actual command rejects retired selector "+Arrays.toString(args));
            check(ExtensionSupport.dimension(actor)==dimension&&Arrays.equals(ExtensionSupport.position(actor),point),"Rejected command leaves native player in place");
        }
        for(String verb:new String[]{"tpdim","diag","locate","tplocate"}){
            Object raw=call(command,"addTabCompletionOptions|func_71516_a",actor,new String[]{verb,""});
            if(raw!=null)for(Object value:(List<?>)raw)check(!"A".equalsIgnoreCase(String.valueOf(value))&&!"B".equalsIgnoreCase(String.valueOf(value)),"Actual "+verb+" completion removes A/B");
        }
        String usage=String.valueOf(call(command,"getCommandUsage|func_71518_a",actor));check(!usage.contains("A|B")&&!usage.contains("locate A"),"Native command usage has no dimension selector");
    }
    private void setBlock(Object world,int x,int y,int z,Object block,int metadata)throws Exception{
        if(call(world,"getBlock|func_147439_a",x,y,z)!=block||integer(call(world,"getBlockMetadata|func_72805_g",x,y,z))!=metadata)
            call(world,"setBlock|func_147465_d",x,y,z,block,metadata,2);
        check(call(world,"getBlock|func_147439_a",x,y,z)==block&&integer(call(world,"getBlockMetadata|func_72805_g",x,y,z))==metadata,"Owned scratch block state applied");
    }
    private void spawnClearance(Object world,Object actor)throws Exception{
        // This executes exclusively from the server Case phase. Reuse one already-loaded chunk;
        // never generate a full prefab, spawn an entity, or touch a saved encounter ledger.
        check(!Thread.currentThread().getName().toLowerCase(Locale.ROOT).contains("client"),"Collision fixture executes on native server thread");
        com.google.gson.JsonObject reference;
        try(Reader input=new InputStreamReader(getClass().getResourceAsStream("/dimension-trim-reference/prefab-spawn-clearance.json"),StandardCharsets.UTF_8)){
            reference=new com.google.gson.JsonParser().parse(input).getAsJsonObject();
        }
        com.google.gson.JsonArray nodes=reference.getAsJsonArray("nodes");check(nodes.size()>0&&nodes.size()==reference.getAsJsonObject("summary").get("total").getAsInt(),"All independently scanned retired prefab nodes supplied");
        double[] point=ExtensionSupport.position(actor);int cx=((int)Math.floor(point[0]))>>4,cz=((int)Math.floor(point[2]))>>4;
        check((Boolean)call(call(world,"getChunkProvider|func_72863_F"),"chunkExists|func_73149_a",cx,cz),"Scratch reuses an already loaded owned chunk");
        int bx=(cx<<4)+8,bz=(cz<<4)+8,by=192;
        Object air=get(type("net.minecraft.init.Blocks"),"air|field_150350_a"),registry=get(type("net.minecraft.block.Block"),"blockRegistry|field_149771_c");
        List<int[]> positions=new ArrayList<int[]>();List<Object> blocks=new ArrayList<Object>();List<Integer> metadata=new ArrayList<Integer>();
        for(int x=bx-4;x<=bx+4;x++)for(int z=bz-4;z<=bz+4;z++)for(int y=by-2;y<=by+8;y++){
            check((Boolean)call(world,"blockExists|func_72899_e",x,y,z),"Scratch cell is already loaded");
            check(call(world,"getTileEntity|func_147438_o",x,y,z)==null,"Scratch does not overwrite a native tile entity");
            Object block=call(world,"getBlock|func_147439_a",x,y,z);int meta=integer(call(world,"getBlockMetadata|func_72805_g",x,y,z));
            check(block==air,"High owned scratch is empty before fixture");positions.add(new int[]{x,y,z});blocks.add(block);metadata.add(meta);
        }
        int admitted=0;List<Map<String,Object>> evidence=new ArrayList<Map<String,Object>>();
        try{
            for(com.google.gson.JsonElement raw:nodes){
                com.google.gson.JsonObject node=raw.getAsJsonObject(),replacement=node.getAsJsonObject("replacement");
                check(replacement.getAsJsonArray("bodyNonAir").size()==0&&replacement.getAsJsonArray("unknownBody").size()==0,"Reference source body is explicit AIR");
                com.google.gson.JsonArray box=replacement.getAsJsonArray("box");double[] expected=new double[6];for(int i=0;i<6;i++)expected[i]=box.get(i).getAsDouble();
                int dx=bx-(int)Math.floor((expected[0]+expected[3])*.5),dy=by-(int)Math.floor(expected[1]),dz=bz-(int)Math.floor((expected[2]+expected[5])*.5);
                List<int[]> floor=new ArrayList<int[]>();
                try{
                    for(com.google.gson.JsonElement part:replacement.getAsJsonArray("floorNonAir")){
                        com.google.gson.JsonArray cell=part.getAsJsonArray();String token=cell.get(3).getAsString();
                        check(token.equals("gtsr:ruins_sootstone_tiles#0")||token.equals("gtsr:ruins_slag_masonry#0"),"Collision reference uses the independently audited full-cube support");
                        String key=token.substring(0,token.indexOf('#')).replace("gtsr:ruins_","gtsr:Ruins_");Object support=call(registry,"getObject|func_82594_a",key);
                        check(String.valueOf(call(registry,"getNameForObject|func_148750_c",support)).equals(key),"Actual original support registry identity "+key);
                        int x=cell.get(0).getAsInt()+dx,y=cell.get(1).getAsInt()+dy,z=cell.get(2).getAsInt()+dz;
                        check(x>=bx-4&&x<=bx+4&&z>=bz-4&&z<=bz+4&&y>=by-2&&y<=by+8,"Support stays inside bounded scratch");
                        setBlock(world,x,y,z,support,0);floor.add(new int[]{x,y,z});
                    }
                    String target=node.get("target").getAsString();Object kind=call(type(PROSPERITY+"echo.EchoKind"),"byCode",target),entity=construct(PROSPERITY+"echo.EntityOldEcho",world);
                    double x=(expected[0]+expected[3])*.5+dx,y=expected[1]+dy,z=(expected[2]+expected[5])*.5+dz;
                    call(entity,"initializeEcho",kind,"owned-clearance-fixture",x,y,z,false);Object actual=get(entity,"boundingBox|field_70121_D");
                    String[] fields={"minX|field_72340_a","minY|field_72338_b","minZ|field_72339_c","maxX|field_72336_d","maxY|field_72337_e","maxZ|field_72334_f"};
                    double[] translated=new double[6];for(int i=0;i<6;i++){translated[i]=((Number)get(actual,fields[i])).doubleValue();check(Math.abs(translated[i]-(expected[i]+(i%3==0?dx:i%3==1?dy:dz)))<.00001,"Actual native canonical AABB preserves source fractional coordinate "+target);}
                    check((Boolean)call(type(PROSPERITY+"remaster.RemasterSpawn"),"safe",world,entity,!(Boolean)call(kind,"flies")),"Actual RemasterSpawn.safe admits "+node.get("prefab").getAsString()+"/"+node.get("node").getAsString()+" -> "+target);
                    Map<String,Object> row=new LinkedHashMap<String,Object>();row.put("prefab",node.get("prefab").getAsString());row.put("variant",node.get("variant").getAsInt());row.put("index",node.get("index").getAsInt());row.put("source",node.get("source").getAsString());row.put("target",target);row.put("nativeTranslatedAabb",translated);row.put("status","PASS");evidence.add(row);admitted++;
                }finally{for(int[] cell:floor)setBlock(world,cell[0],cell[1],cell[2],air,0);}
            }
        }finally{
            for(int i=0;i<positions.size();i++){int[] cell=positions.get(i);setBlock(world,cell[0],cell[1],cell[2],blocks.get(i),metadata.get(i));}
        }
        check(admitted==nodes.size(),"Every retired prefab node passes actual native AABB admission");
        Map<String,Object> proof=new LinkedHashMap<String,Object>();proof.put("scope","One existing owned overworld chunk; translated explicit AIR/full-cube fixtures preserving fractional AABB; real server RemasterSpawn.safe, no spawned actors or progress writes; original blocks and metadata restored");proof.put("thread",Thread.currentThread().getName());proof.put("chunk",new int[]{cx,cz});proof.put("scratchCells",positions.size());proof.put("admittedNodes",admitted);proof.put("nodes",evidence);proof.put("restored",true);result.put("prefabSpawnAdmission",proof);
    }
    private String realAtlasIcon(Object atlas,Map<String,Object> sprites,Object icon,String label)throws Exception{
        check(icon!=null,label+" native icon exists");String name=String.valueOf(call(icon,"getIconName|func_94215_i"));
        check(!name.toLowerCase(Locale.ROOT).contains("missing"),label+" icon is not named missingno");
        check(icon!=call(atlas,"getAtlasSprite|func_110572_b","missingno"),label+" icon is not the actual atlas missing sprite");
        check(sprites.containsKey(name)&&sprites.get(name)==icon,label+" icon identity is uploaded in actual atlas: "+name);return name;
    }
    private int[] sourceSprite(Object sprite,String name,String kind)throws Exception{
        String derivedName="gtsr:machine_casing_gearbox_tungstensteel",derivedClass=ROOT+"client.machinevisual.TungstenSteelGearboxSprite";
        if(name.equals(derivedName)||sprite.getClass().getName().equals(derivedClass)){
            check(kind.equals("blocks")&&name.equals(derivedName)&&sprite.getClass().getName().equals(derivedClass),"Only exact known gearbox class/name uses derived block sprite contract");
            List<String> inputs=new ArrayList<String>();
            for(String inputName:new String[]{"MACHINE_CASING_GEARBOX_STEEL","MACHINE_CASING_ROBUST_TUNGSTENSTEEL"}){
                String path="textures/blocks/iconsets/"+inputName+".png";Object location=construct("net.minecraft.util.ResourceLocation","gregtech",path);java.awt.image.BufferedImage image;
                try(InputStream input=(InputStream)call(call(spriteResourceManager,"getResource|func_110536_a",location),"getInputStream|func_110527_b")){image=javax.imageio.ImageIO.read(input);}
                check(image!=null&&image.getWidth()==16&&image.getHeight()==16,"Native GT input of derived gearbox sprite is 16x16 "+inputName);inputs.add("gregtech:"+path);
            }
            boolean animated=(Boolean)call(sprite,"hasAnimationMetadata|func_130098_m");int retained=integer(call(sprite,"getFrameCount|func_110970_k"));
            check(!animated,"Known generated gearbox sprite is static; uploaded CPU frames may be released");
            Map<String,Object> proof=new LinkedHashMap<String,Object>();proof.put("name",name);proof.put("spriteClass",sprite.getClass().getName());proof.put("sourceKind","Derived from two native GT resource inputs; no GTSR source PNG asserted");proof.put("nativeInputs",inputs);proof.put("retainedFrames",retained);result.put("derivedBlockSprite",proof);
            return new int[]{retained,0,0,1};
        }
        String path="assets/gtsr/textures/"+kind+"/"+name.substring("gtsr:".length())+".png";
        java.util.zip.ZipEntry entry=productResources.getEntry(path);check(entry!=null,"Actual product jar contains sprite PNG "+path);
        java.awt.image.BufferedImage image;try(InputStream input=productResources.getInputStream(entry)){image=javax.imageio.ImageIO.read(input);}
        check(image!=null&&image.getWidth()==16&&image.getHeight()>0&&image.getHeight()%16==0,"Actual product PNG has 16x16 source frames "+name);
        com.google.gson.JsonObject authoredAnimation=null;java.util.zip.ZipEntry metadata=productResources.getEntry(path+".mcmeta");
        if(metadata!=null)try(Reader input=new InputStreamReader(productResources.getInputStream(metadata),StandardCharsets.UTF_8)){com.google.gson.JsonObject value=new com.google.gson.JsonParser().parse(input).getAsJsonObject();if(value.has("animation"))authoredAnimation=value.getAsJsonObject("animation");}
        boolean animated=(Boolean)call(sprite,"hasAnimationMetadata|func_130098_m");int retained=integer(call(sprite,"getFrameCount|func_110970_k"));
        check(authoredAnimation==null||animated,"Authored animation metadata remains active in actual sprite "+name);
        check(!animated||retained>0,"Animated sprite retains decoded frames "+name);
        if(authoredAnimation!=null){
            Set<Integer> referenced=new LinkedHashSet<Integer>();com.google.gson.JsonArray sequence=authoredAnimation.getAsJsonArray("frames");
            if(sequence!=null)for(com.google.gson.JsonElement frame:sequence)referenced.add(frame.isJsonObject()?frame.getAsJsonObject().get("index").getAsInt():frame.getAsInt());
            if(referenced.isEmpty())for(int frame=0;frame<image.getHeight()/16;frame++)referenced.add(frame);
            for(int frame:referenced)check(frame>=0&&frame<retained&&call(sprite,"getFrameTextureData|func_147965_a",frame)!=null,"Every authored animation frame remains decoded "+name+" #"+frame);
        }
        // Patched MC TextureMap uploads every sprite first, then legitimately clears only static
        // CPU frame arrays. Atlas identity, dimensions and original product PNG remain checked.
        return new int[]{retained,animated?1:0,image.getHeight()/16,0};
    }
    private File productJar()throws Exception{
        java.net.URL source=type(ROOT+"client.encounter.RigAsset").getProtectionDomain().getCodeSource().getLocation();
        java.net.URL archive=source;
        if("jar".equals(source.getProtocol())){
            java.net.URLConnection connection=source.openConnection();connection.setUseCaches(false);
            check(connection instanceof java.net.JarURLConnection,"FML product source uses a native jar URL connection");
            archive=((java.net.JarURLConnection)connection).getJarFileURL();
        }
        check("file".equals(archive.getProtocol()),"Actual loaded product archive is a local file");
        File path=new File(archive.toURI());check(path.isFile(),"Actual loaded product jar exists");result.put("actualProductJar",path.toString());return path;
    }
    @SuppressWarnings("unchecked") private void itemAtlas(Object mc)throws Exception{
        Object location=get(type("net.minecraft.client.renderer.texture.TextureMap"),"locationItemsTexture|field_110576_c");
        Object atlas=call(call(mc,"getTextureManager|func_110434_K"),"getTexture|func_110581_b",location);
        check(atlas!=null&&type("net.minecraft.client.renderer.texture.TextureMap").isInstance(atlas),"Native item texture resolves to stitched TextureMap");
        Map<String,Object> sprites=(Map<String,Object>)get(atlas,"mapUploadedSprites|field_94252_e");
        Class<?> lore=type(PROSPERITY+"lore.LoreRegistry"),items=type("net.minecraft.item.Item");Map<String,Object> selected=new LinkedHashMap<String,Object>();
        selected.put("gtsr:lore/history",get(lore,"journal"));for(Map.Entry<?,?> entry:((Map<?,?>)get(lore,"RELICS")).entrySet())selected.put("gtsr:lore/"+entry.getKey(),entry.getValue());
        Object registry=get(items,"itemRegistry|field_150901_e");selected.put("gtsr:spacetime_anchor_beacon",call(registry,"getObject|func_82594_a","gtsr:SpacetimeAnchorBeacon"));selected.put("gtsr:AbyssalObsessionBucket",call(registry,"getObject|func_82594_a","gtsr:AbyssalObsessionBucket"));
        List<Map<String,Object>> evidence=new ArrayList<Map<String,Object>>();Set<String> mapped=new LinkedHashSet<String>();int decoded=0;
        for(Map.Entry<String,Object> entry:selected.entrySet()){
            check(entry.getValue()!=null,"Dimension item registry entry exists "+entry.getKey());Object icon=call(entry.getValue(),"getIconFromDamage|func_77617_a",0);
            String name=realAtlasIcon(atlas,sprites,icon,"Dimension item "+entry.getKey());check(name.equals(entry.getKey()),"Actual item icon matches its registered dimension resource "+entry.getKey());
            check(integer(call(icon,"getIconWidth|func_94211_a"))==16&&integer(call(icon,"getIconHeight|func_94216_b"))==16,"Dimension item stitched sprite is 16x16 "+name);
            int[] source=sourceSprite(icon,name,"items");int count=source[0];
            for(int frame=0;frame<count;frame++){int[][] levels=(int[][])call(icon,"getFrameTextureData|func_147965_a",frame);check(levels!=null&&levels.length>0&&levels[0]!=null&&levels[0].length==256,"Dimension item native frame is 16x16 "+name+" #"+frame);decoded++;}
            if(name.equals("gtsr:spacetime_anchor_beacon")){check(icon.getClass().getName().equals(ROOT+"client.travel.SpacetimeBeaconSprite"),"Actual beacon uses its custom composite sprite");check(count==integer(get(icon.getClass(),"FRAME_COUNT")),"All custom beacon needle rotation frames decoded");}
            Map<String,Object> row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("retainedFrames",count);row.put("animated",source[1]==1);row.put("sourceFrames",source[2]);row.put("staticCpuFramesReleased",count==0&&source[1]==0);row.put("spriteClass",icon.getClass().getName());row.put("status","PASS");evidence.add(row);mapped.add(name);
        }
        // The needle is an input of the composite sprite, not an independently stitched item icon.
        Object manager=call(mc,"getResourceManager|func_110442_L");List<String> nativeInputs=new ArrayList<String>();
        for(String path:new String[]{"textures/items/spacetime_anchor_beacon.png","textures/items/spacetime_anchor_beacon_needle.png"}){
            Object resource=construct("net.minecraft.util.ResourceLocation","gtsr",path);java.awt.image.BufferedImage image;
            try(InputStream input=(InputStream)call(call(manager,"getResource|func_110536_a",resource),"getInputStream|func_110527_b")){image=javax.imageio.ImageIO.read(input);}
            check(image!=null&&image.getWidth()==16&&image.getHeight()==16,"Native custom beacon resource input is 16x16 "+path);nativeInputs.add("gtsr:"+path);
        }
        Map<String,Object> proof=new LinkedHashMap<String,Object>();proof.put("scope","Only registered lore, beacon and abyssal bucket dimension items; needle is verified native resource input to the actual custom sprite, not a separate atlas sprite");proof.put("mappedItemSprites",mapped.size());proof.put("decodedFrames",decoded);proof.put("loreSprites",selected.size()-2);proof.put("sourceImagesRepresented",mapped.size()+1);proof.put("compositeNativeInputs",nativeInputs);proof.put("sprites",evidence);result.put("dimensionItemAtlas",proof);
    }
    @SuppressWarnings("unchecked") private void atlas(Object mc)throws Exception{
        Object atlas=call(mc,"getTextureMapBlocks|func_147117_R");Map<String,Object> sprites=(Map<String,Object>)get(atlas,"mapUploadedSprites|field_94252_e");
        int count=0,frames=0,transparent=0,emissive=0,staticReleased=0,animatedSprites=0,sourceFrames=0;List<Map<String,Object>> rows=new ArrayList<Map<String,Object>>();
        for(Map.Entry<String,Object> entry:sprites.entrySet()){
            String name=entry.getKey();if(!name.startsWith("gtsr:"))continue;Object sprite=entry.getValue();
            realAtlasIcon(atlas,sprites,sprite,"Block sprite "+name);
            check(integer(call(sprite,"getIconWidth|func_94211_a"))==16&&integer(call(sprite,"getIconHeight|func_94216_b"))==16,"Actual stitched block sprite 16x16 "+name);
            int[] source=sourceSprite(sprite,name,"blocks");int n=source[0];sourceFrames+=source[2];if(n==0)staticReleased++;if(source[1]==1)animatedSprites++;
            for(int i=0;i<n;i++){Object raw=call(sprite,"getFrameTextureData|func_147965_a",i);if(raw==null)continue;int[][] levels=(int[][])raw;check(levels.length>0&&levels[0]!=null&&levels[0].length==256,"Native frame pixels 16x16 "+name+" #"+i);frames++;for(int pixel:levels[0])if((pixel>>>24)<255){transparent++;break;}}
            Map<String,Object> row=new LinkedHashMap<String,Object>();row.put("name",name);row.put("retainedFrames",n);row.put("animated",source[1]==1);row.put("sourceFrames",source[2]);row.put("derivedSprite",source[3]==1);row.put("staticCpuFramesReleased",n==0&&source[1]==0);rows.add(row);count++;
        }
        // Pair identity follows the production material contract. A static albedo may intentionally
        // accompany an animated emission, so equality of their animation lengths is not required.
        Object catalog=call(type(ROOT+"common.critical.CriticalMaterialDefinitions"),"catalog");Map<?,?> definitions=(Map<?,?>)call(catalog,"definitions");
        Set<Object> pairs=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(Object definition:definitions.values()){
            pairs.addAll(((Map<?,?>)call(definition,"sprites")).values());pairs.addAll(((Map<?,?>)call(definition,"ctmSprites")).values());
            if(!(Boolean)call(definition,"hasSprites")&&(Boolean)get(definition,"emissive")){
                String base="gtsr:critical/"+get(definition,"id"),emission=base+"_emission";
                check(sprites.containsKey(base)&&sprites.containsKey(emission),"Legacy critical base and emission both stitched "+base);emissive++;
            }
        }
        for(Object pair:pairs)for(boolean frozen:new boolean[]{false,true}){
            String albedo=(String)call(pair,"texture",false,frozen),emission=(String)call(pair,"texture",true,frozen);
            if(albedo!=null)check(sprites.containsKey(albedo),"Material albedo contract resolves native atlas "+albedo);
            if(emission!=null){check(albedo!=null&&sprites.containsKey(emission),"Material emission corresponds to actual albedo "+emission);emissive++;}
        }
        check(count>0&&animatedSprites>0&&frames>0,"Actual GTSR block atlas and retained animation frames traversed");check(transparent>0,"Retained animation frames preserve transparency");check(emissive>0,"Atlas includes emissive companions");
        Object registry=get(type("net.minecraft.block.Block"),"blockRegistry|field_149771_c");Class<?> architecture=type(PROSPERITY+"architecture.RuinsArchitecture");
        Set<Object> creativeItems=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
        for(Object stack:(List<?>)call(type(ROOT+"register.CreativeTabManager"),"getItemsToAdd"))creativeItems.add(call(stack,"getItem|func_77973_b"));
        Class<?> items=type("net.minecraft.item.Item");
        for(String[] pair:ALIASES){Object old=call(registry,"getObject|func_82594_a","gtsr:Ruins_"+pair[0]),base=call(registry,"getObject|func_82594_a","gtsr:Ruins_"+pair[1]);check(old!=null&&base!=null&&old!=base&&String.valueOf(call(registry,"getNameForObject|func_148750_c",old)).equals("gtsr:Ruins_"+pair[0]),"Old decorative registry identity retained "+pair[0]);check(call(architecture,"get",pair[0])==base,"New block lookup canonical "+pair[0]);check(!creativeItems.contains(call(items,"getItemFromBlock|func_150898_a",old)),"Retired alias hidden from actual creative catalog "+pair[0]);for(int side=0;side<6;side++){Object icon=call(old,"getIcon|func_149691_a",side,0);check(icon==call(base,"getIcon|func_149691_a",side,0),"Legacy alias native atlas icon "+pair[0]+" face "+side);realAtlasIcon(atlas,sprites,icon,"Legacy alias "+pair[0]+" face "+side);}}
        result.put("blockAtlasSprites",count);result.put("decodedRetainedFrames",frames);result.put("sourcePngFrames",sourceFrames);result.put("staticCpuFramesReleased",staticReleased);result.put("animatedAtlasSprites",animatedSprites);result.put("transparentRetainedFrames",transparent);result.put("emissivePairs",emissive);result.put("atlas",rows);
    }
    public boolean tick(String phase,Map<String,Object> context)throws Exception{
        if(finished)return true;
        try{
            if(run==null){run=ExtensionSupport.owned(context);result.put("scope","Native product canonical admissions/NBT, spent ledger, actual command rejection/completion, stitched atlas; no gameplay travel or reward spawning");}
            if(phase.equals("server")&&!serverDone){Object server=context.get("server"),world=context.get("world");if(server==null||world==null)return false;Object manager=call(server,"getConfigurationManager|func_71203_ab");List<?> players=(List<?>)get(manager,"playerEntityList|field_72404_b");if(players.isEmpty())return false;Object actor=players.get(0);echoContracts(world);commands(actor);spawnClearance(world,actor);serverDone=true;}
            if(phase.equals("render")&&!atlasDone){try(java.util.zip.ZipFile archive=new java.util.zip.ZipFile(productJar())){productResources=archive;spriteResourceManager=call(context.get("minecraft"),"getResourceManager|func_110442_L");atlas(context.get("minecraft"));itemAtlas(context.get("minecraft"));}finally{productResources=null;spriteResourceManager=null;}atlasDone=true;}
            if(serverDone&&atlasDone){result.put("status","PASS");result.put("checksPassed",checks.size());result.put("checks",checks);ExtensionSupport.write(run.resolve("client/dimension-trim-native-case.json"),result);finished=true;return true;}return false;
        }catch(Throwable error){result.put("status","FAIL");result.put("failure",error.toString());result.put("checksPassed",checks.size());result.put("checks",checks);if(run!=null)ExtensionSupport.write(run.resolve("client/dimension-trim-native-case.json"),result);if(error instanceof Exception)throw (Exception)error;if(error instanceof Error)throw (Error)error;throw new IllegalStateException(error);}
    }
    public Map<String,Object> result(){return new LinkedHashMap<String,Object>(result);}
}
