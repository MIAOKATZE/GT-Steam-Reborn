import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.FontRenderer;

/** Actual vanilla FontRenderer width/wrapping with vanilla atlas metrics; no GL or client launch. */
public class CriticalNEIFontContract {
    public static void main(String[] args)throws Exception {
        System.setProperty("critical.nei.lang",args[0]);
        Issue37RegistrationRegression.launch("CriticalNEIFontRuntime","5.09.54.205");
    }
}

class CriticalNEIFontRuntime {
    static int checks;
    static java.util.List<com.miaokatze.gtsr.common.critical.CriticalPlasmaCatalog.Entry> nativeProfiles = new ArrayList<>();
    static void set(Object o,String name,Object value)throws Exception {
        Field f=FontRenderer.class.getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    public static void run(String version)throws Exception {
        Issue37Runtime.bootstrap(version);
        CriticalPlasmaFixture.seed();
        loadNativeProfiles();
        String expectedJar=System.getProperty("critical.nei.expectedJar");
        if(expectedJar!=null){
            String expected=new java.io.File(expectedJar).toURI().toURL().toExternalForm();
            for(Class<?> type:List.of(com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.class,
                com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.class)){
                String source=type.getProtectionDomain().getCodeSource().getLocation().toExternalForm();
                if(!source.contains(expected))throw new AssertionError("Wrong font product CodeSource "+type+": "+source);
            }
        }
        Field field=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);
        var font=(FontRenderer)((sun.misc.Unsafe)field.get(null)).allocateInstance(FontRenderer.class);
        byte[] glyphs=FontRenderer.class.getResourceAsStream("/assets/minecraft/font/glyph_sizes.bin").readAllBytes();
        set(font,"glyphWidth",glyphs);
        var atlas=ImageIO.read(FontRenderer.class.getResourceAsStream("/assets/minecraft/textures/font/ascii.png"));
        int[] widths=new int[256];int cell=atlas.getWidth()/16;
        for(int c=0;c<256;c++) {
            int last=cell-1;
            outer:for(;last>=0;last--)for(int y=0;y<cell;y++)
                if((atlas.getRGB((c%16)*cell+last,(c/16)*cell+y)>>>24)!=0)break outer;
            widths[c]=(int)(0.5+(last+1)*8.0/cell)+1;
        }
        set(font,"charWidth",widths);
        int maximum=0;
        for(String lang:List.of("en_US","zh_CN"))for(boolean unicode:List.of(false,true)) {
            set(font,"unicodeFlag",unicode);
            List<String> locale;
            if(expectedJar==null)locale=Files.readAllLines(Path.of(System.getProperty("critical.nei.lang"),lang+".lang"));
            else try(var jar=new java.util.jar.JarFile(expectedJar)){
                var entry=jar.getJarEntry("assets/gtsr/lang/"+lang+".lang");
                locale=new String(jar.getInputStream(entry).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8).lines().toList();
            }
            solarBounds(font,locale,lang,unicode);
            for(String key:List.of("instant","construction_immediate","scope")) {
                String prefix="gtsr.wand.debug."+key+"=";
                String value=locale.stream().filter(s->s.startsWith(prefix)).findFirst().orElseThrow().substring(prefix.length());
                int width=key.equals("instant")?184:300;
                int maxLines=key.equals("instant")?1:key.equals("construction_immediate")?2:3;
                var lines=font.listFormattedStringToWidth(value,width);
                if(lines.size()>maxLines)throw new AssertionError("Debug speed text crosses neighboring controls: "+lang+" "+key);
                for(String line:lines)if(font.getStringWidth(line)>width)
                    throw new AssertionError("Debug speed text exceeds native control width: "+lang+" "+key);
                checks++;
            }
            for(String name:List.of("gtsr.recipe.critical.sun","gtsr.critical.nei.simple.sun.title","gtsr.critical.nei.simple.intro.sun")) {
                String value=locale.stream().filter(line->line.startsWith(name+"="))
                    .findFirst().orElseThrow().substring(name.length()+1);
                if(value.toLowerCase(Locale.ROOT).contains("mkv")||font.getStringWidth(value)>154)
                    throw new AssertionError("Fusion name must omit capability tier and fit: "+name+" "+value);
                checks++;
            }
            for(String key:List.of("title","optional","interval","reduction","scope")) {
                String prefix="gtsr.critical.nei.showcase.curing."+key+"=";
                if(locale.stream().noneMatch(line->line.startsWith(prefix)&&line.length()>prefix.length()))
                    throw new AssertionError("Missing localized singularity tooltip fact: "+lang+" "+key);
                checks++;
            }
            for(String key:List.of("bus","not_consumed","number")) {
                String prefix="gtsr.critical.nei.showcase.circuit."+key+"=";
                if(locale.stream().noneMatch(line->line.startsWith(prefix)&&line.length()>prefix.length()))
                    throw new AssertionError("Missing localized selector tooltip: "+lang+" "+key);
                checks++;
            }
            for(int group:List.of(1,2))for(int row=0;row<3;row++)for(int steps:List.of(1,8,25,64,128,200,256)) {
                String titleKey="gtsr.critical.nei.simple.build.row."+group+"."+row+"=";
                String title=locale.stream().filter(s->s.startsWith(titleKey)).findFirst().orElseThrow().substring(titleKey.length());
                String format=locale.stream().filter(s->s.startsWith("gtsr.critical.nei.simple.build.time.steps="))
                    .findFirst().orElseThrow().split("=",2)[1];
                String duration=String.format(Locale.ROOT,format,20,steps);
                int timeX=com.miaokatze.gtsr.api.recipe.CriticalNEILayout.BUILD_TIME_RIGHT_X-font.getStringWidth(duration);
                int available=timeX-com.miaokatze.gtsr.api.recipe.CriticalNEILayout.BUILD_X-5;
                var lines=font.listFormattedStringToWidth(title,available);
                int slotY=com.miaokatze.gtsr.api.recipe.CriticalNEILayout.BUILD_Y+row*36;
                int y=slotY-(lines.size()==1?11:18);
                if(lines.size()>2||y<(row==0?44:slotY-18))
                    throw new AssertionError("Construction step title crosses tabs or previous slots: "+lang+" "+title+" "+duration);
                for(String titleLine:lines)if(font.getStringWidth(titleLine)>available)
                    throw new AssertionError("Construction title collides with step duration: "+lang);
                checks++;
            }
            int chemicalLines=0;
            for(String key:List.of("efficiency","input_heat","output_heat")) {
                String prefix="gtsr.critical.nei.simple.chemical."+key+"=";
                String format=locale.stream().filter(s->s.startsWith(prefix)).findFirst().orElseThrow().substring(prefix.length());
                String value=String.format(Locale.ROOT,format,key.equals("efficiency")?"100":"123,456,789,012,345,678");
                var lines=font.listFormattedStringToWidth(value,com.miaokatze.gtsr.api.recipe.CriticalNEILayout.CHEMICAL_TEXT_WIDTH);
                chemicalLines+=lines.size();
                for(String resultLine:lines)if(font.getStringWidth(resultLine)>140)
                    throw new AssertionError("Chemical result crosses native buttons: "+lang);
                checks++;
            }
            if(com.miaokatze.gtsr.api.recipe.CriticalNEILayout.CHEMICAL_TEXT_Y+(chemicalLines-1)*11+9>174)
                throw new AssertionError("Chemical results leave card: "+lang+" lines="+chemicalLines);
            for(String key:List.of("inputs","bar")) {
                String prefix="gtsr.critical.nei.simple.chemical."+key+"=";
                String value=locale.stream().filter(s->s.startsWith(prefix)).findFirst().orElseThrow().substring(prefix.length());
                if(font.getStringWidth(value)>(key.equals("inputs")?81:66))
                    throw new AssertionError("Chemical label crosses efficiency heading or card: "+lang);
                checks++;
            }
            for(long eut:List.of(com.miaokatze.gtsr.common.critical.CriticalStellarPolicy.IGNITION_EU
                /com.miaokatze.gtsr.common.critical.CriticalStellarPolicy.DURATION_TICKS,
                com.miaokatze.gtsr.common.critical.CriticalStellarPolicy.SUSTAIN_EUT)) {
                String power=String.format(Locale.ROOT,"%,d EU/t",eut);
                if(font.getStringWidth(power)>154)throw new AssertionError("Stellar power width: "+power);
                checks++;
            }

            for(String line:locale) {
                if ((line.startsWith("gtsr.critical.") || line.startsWith("gtsr.recipe.critical.")
                    || line.startsWith("gtsr.recipe.stellar_") || line.startsWith("gt.blockmachines.gtsr.critical.")
                    || line.startsWith("gtsr.tooltip.machine.compact.sun."))
                    && (line.contains("??") || line.indexOf('\uFFFD') >= 0))
                    throw new AssertionError("Corrupt localization encoding: " + lang + " " + line);
                if(!line.startsWith("gtsr.critical.nei.simple."))continue;
                String key=line.substring(0,line.indexOf('='));
                if (lang.equals("zh_CN") && !key.endsWith("plasma.maximum") && line.substring(line.indexOf('=')+1).codePoints()
                    .noneMatch(c -> c >= 0x4E00 && c <= 0x9FFF))
                    throw new AssertionError("Chinese NEI label lost its Han characters: " + key);
                String format=line.substring(line.indexOf('=')+1);
                String text=key.endsWith("turbine.efficiency")?String.format(Locale.ROOT,format,1,20)
                    :key.endsWith("turbine.trend")||key.endsWith("turbine.warmup")?String.format(Locale.ROOT,format,
                        com.miaokatze.gtsr.common.critical.CriticalEnergyMath.TURBINE_WARMUP_TICKS/20)
                    :key.endsWith("solar.rated")||key.endsWith("solar.salt_rated")?String.format(Locale.ROOT,format,"8,192A MAX")
                    :key.endsWith("solar.formula")?String.format(Locale.ROOT,format,"60T","100G")
                    :key.endsWith("solar.example")?String.format(Locale.ROOT,format,"500G","1.35")
                    :key.contains("solar.input.")?String.format(Locale.ROOT,format,"421.875G")
                    :key.endsWith("solar.output")?String.format(Locale.ROOT,format,"67.5T")
                    :key.endsWith("solar.heat")?String.format(Locale.ROOT,format,"112.5T")
                    :key.endsWith("plasma.maximum")?String.format(Locale.ROOT,format,"258.344M")
                    :key.endsWith("plasma.half")?String.format(Locale.ROOT,format,"344.459M")
                    :key.endsWith("plasma.output")?String.format(Locale.ROOT,format,"174.382M")
                    :key.endsWith("plasma.heat")?String.format(Locale.ROOT,format,"84.375T")
                    :key.endsWith("ignition.helium")?String.format(Locale.ROOT,format,"50,000,000")
                    :key.endsWith("sustain.helium")?String.format(Locale.ROOT,format,"40,000")
                    :key.endsWith("stellar.voltage")?String.format(Locale.ROOT,format,"MAX")
                    :key.endsWith("stellar.voltage_amps")||key.endsWith("chemical.equivalent_amps")?String.format(Locale.ROOT,format,"MAX","1,165")
                    :key.endsWith("chemical.average")?String.format(Locale.ROOT,format,"5,664,600")
                    :key.endsWith("chemical.efficiency")?String.format(Locale.ROOT,format,100)
                    :key.endsWith("chemical.input_heat")?String.format(Locale.ROOT,format,"1,234,567")
                    :key.endsWith("chemical.output_heat")?String.format(Locale.ROOT,format,"123,456,700")
                    :key.endsWith("chemical.equivalent_tier")?String.format(Locale.ROOT,format,"ZPM")
                    :key.endsWith("fusion.discount")?String.format(Locale.ROOT,format,"99.9")
                    :key.endsWith(".build.time.steps")?String.format(Locale.ROOT,format,20,4096)
                    :key.contains(".build.time.")?String.format(Locale.ROOT,format,
                        key.endsWith(".minutes")?30:key.endsWith(".hours")?24:key.endsWith(".cell")?2:20)
                    :key.endsWith("turbine.heat_per_l")?String.format(Locale.ROOT,format,"2,500")
                    :String.format(Locale.ROOT,format,"9999999999",3,999999);

                if(key.endsWith(".footer")||key.endsWith("card.footer"))text=text.replace("9999999999","99");
                if(key.endsWith(".build.row.0.2")) {
                    String durationFormat=locale.stream().filter(s->s.startsWith("gtsr.critical.nei.simple.build.time.hours="))
                        .findFirst().orElseThrow().split("=",2)[1];
                    String duration=String.format(Locale.ROOT,durationFormat,24);
                    int timeX=com.miaokatze.gtsr.api.recipe.CriticalNEILayout.BUILD_TIME_RIGHT_X-font.getStringWidth(duration);
                    int available=timeX-com.miaokatze.gtsr.api.recipe.CriticalNEILayout.BUILD_X-5;
                    var lines=font.listFormattedStringToWidth(text,available);
                    if(lines.size()>2)throw new AssertionError(lang+" unicode="+unicode+" curing title has "+lines.size()+" lines");
                    int y=lines.size()==1?120:113;
                    for(String titleLine:lines){
                        int width=font.getStringWidth(titleLine);maximum=Math.max(maximum,width);
                        if(width>available||y<113||y+9>131)throw new AssertionError("Curing title/time or slot overlap: "+lang);
                        y+=9;checks++;
                    }
                    checks++;continue;
                }
                if(key.contains(".body.")) {
                    int rows=0;
                    for(String row:text.split("\\|")) {
                        int width=font.getStringWidth(row);maximum=Math.max(maximum,width);
                        rows+=font.listFormattedStringToWidth(row,154).size();
                    }
                    int limit=key.contains(".build.")?4:6;
                    if(rows>limit)throw new AssertionError(lang+" unicode="+unicode+" "+key+" rows="+rows+" limit="+limit+" "+text);
                } else {
                    int width=font.getStringWidth(text);maximum=Math.max(maximum,width);
                    int limit=key.contains(".build.group.")?44:key.endsWith(".build.time.steps")?100:key.contains(".build.time.")?51:key.contains(".build.row.")?88:
                        key.contains(".build.note.")||key.endsWith(".build.title")?132:154;
                    if(width>limit)throw new AssertionError(lang+" unicode="+unicode+" "+key+" width="+width+" "+text);
                }
                checks++;
            }
        }
        System.out.println("PASS "+checks+" native FontRenderer bilingual bounds; maximum authored line width="+maximum+"; no client/GL");
    }

    static void loadNativeProfiles()throws Exception {
        String source=System.getProperty("critical.nei.nativePlasmaSummary");
        if(source==null)return;
        var ctor=com.miaokatze.gtsr.common.critical.CriticalPlasmaCatalog.Entry.class
            .getDeclaredConstructor(goodgenerator.api.recipe.ExtremeHeatExchangerRecipe.class);
        ctor.setAccessible(true);
        com.google.gson.JsonArray captured;
        try(java.io.Reader reader=Files.newBufferedReader(Paths.get(source),java.nio.charset.StandardCharsets.UTF_8)) {
            captured=new com.google.gson.JsonParser().parse(reader).getAsJsonArray();
        }
        for(var value:captured) {
            var profile=value.getAsJsonObject();
            if(!profile.get("availableForNativeEHEHeating").getAsBoolean())continue;
            var nativeRecipe=profile.getAsJsonArray("nativeEHE").get(0).getAsJsonObject();
            int hot=nativeRecipe.get("A_hotLitresPerNativeSecond").getAsInt();
            int eut=nativeRecipe.get("nativeEUt").getAsInt();
            var plasma=CriticalPlasmaFixture.fluid(profile.get("registryName").getAsString());
            var cold=CriticalPlasmaFixture.fluid(nativeRecipe.get("cooledFluidRegistryName").getAsString());
            var recipe=new goodgenerator.api.recipe.ExtremeHeatExchangerRecipe(
                new net.minecraftforge.fluids.FluidStack[]{new net.minecraftforge.fluids.FluidStack(plasma,hot),
                    new net.minecraftforge.fluids.FluidStack(CriticalPlasmaFixture.fluid("ic2distilledwater"),eut/8)},
                new net.minecraftforge.fluids.FluidStack[]{new net.minecraftforge.fluids.FluidStack(
                    CriticalPlasmaFixture.fluid("ic2superheatedsteam"),1),
                    gregtech.api.enums.Materials.DenseSupercriticalSteam.getGas(1),
                    new net.minecraftforge.fluids.FluidStack(cold,1)},1);
            var entry=ctor.newInstance(recipe);
            if(!entry.denseSteamLitres.equals(new java.math.BigInteger(nativeRecipe
                .get("B_realDenseSteamLitresPerNativeSecond").getAsString()))
                ||!entry.halfSaturation.equals(new java.math.BigInteger(nativeRecipe.get("H_litres").getAsString())))
                throw new AssertionError("Captured native EHE reconstruction differs: "+entry.id);
            var output=entry.batchLitres(entry.halfSaturation.multiply(java.math.BigInteger.valueOf(5)),
                com.miaokatze.gtsr.common.critical.CriticalSolarPolicy.PRIVATE_DIMENSION_FACTOR,
                com.miaokatze.gtsr.common.critical.CriticalSolarPolicy.COOLANTS.get(1),
                com.miaokatze.gtsr.common.critical.CriticalSolarPolicy.EXCITERS.get(1));
            var heat=output.multiply(entry.denseSteamLitres).multiply(java.math.BigInteger.valueOf(2500))
                .divide(entry.hotLitres);
            var target=new java.math.BigInteger("84375000000000");
            var oneLitreLoss=entry.denseSteamLitres.multiply(java.math.BigInteger.valueOf(2500))
                .add(entry.hotLitres.subtract(java.math.BigInteger.ONE)).divide(entry.hotLitres);
            if(heat.compareTo(target)>0||target.subtract(heat).compareTo(oneLitreLoss)>0)
                throw new AssertionError("Captured native plasma normalized heat differs: "+entry.id);
            nativeProfiles.add(entry);
            System.out.println("NATIVE_PROFILE "+entry.id+" hot="+entry.hotLitres+" dense="+entry.denseSteamLitres
                +" H="+entry.halfSaturation);
            checks++;
        }
        if(nativeProfiles.isEmpty())throw new AssertionError("Native plasma summary contains no usable profiles");
        System.out.println("PASS native plasma font profiles="+nativeProfiles.size()+" source="+source);
    }

    static void solarBounds(FontRenderer font,List<String> locale,String lang,boolean unicode)throws Exception {
        // Inject the actual locale into vanilla's translation singleton, then measure the production row builder.
        Class<?> translations=Class.forName("net.minecraft.util.StringTranslate");
        Object singleton=null;
        for(Field f:translations.getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&f.getType()==translations){
            f.setAccessible(true);singleton=f.get(null);break;
        }
        if(singleton==null)throw new AssertionError("Missing native translation singleton");
        for(Field f:translations.getDeclaredFields())if(Map.class.isAssignableFrom(f.getType())){
            f.setAccessible(true);
            @SuppressWarnings("unchecked") var values=(Map<String,String>)f.get(singleton);
            values.clear();
            for(String line:locale){int split=line.indexOf('=');if(split>0)values.put(line.substring(0,split),line.substring(split+1));}
        }
        String intro=net.minecraft.util.StatCollector.translateToLocal("gtsr.critical.nei.simple.solar.intro");
        if(font.getStringWidth(intro)>154)throw new AssertionError("Solar ratio/cycle intro width: "+lang+" "+intro);
        var ctor=com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.SolarDisplay.class
            .getDeclaredConstructor(com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.SteamMode.class);
        ctor.setAccessible(true);
        for(var mode:com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps.SteamMode.values()){
            var profile=ctor.newInstance(mode);
            String[] rows=com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.solarRows(profile);
            if(com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.solarTooltip(profile).stream()
                .anyMatch(line -> line.startsWith("gtsr.")))
                throw new AssertionError("Untranslated solar tooltip: "+lang+" "+mode);
            checks++;
            if(rows.length!=7)throw new AssertionError("Solar batch requires exactly seven authored rows");
            for(int row=0;row<rows.length;row++){
                int width=row>=5?139:154;
                var lines=font.listFormattedStringToWidth(rows[row],width);
                if(lines.size()!=1||font.getStringWidth(rows[row])>width||97+row*11+9>174)
                    throw new AssertionError("Solar card crosses rows/card/native button: "+lang+" unicode="+unicode+" "+mode+" row="+row+" width="+font.getStringWidth(rows[row])+" "+rows[row]);
                System.out.println("SOLAR_FONT "+lang+" unicode="+unicode+" "+mode+" row="+row
                    +" px="+font.getStringWidth(rows[row])+" "+rows[row]);
                if(rows[row].contains("E+")||rows[row].contains("E-"))throw new AssertionError("Scientific solar quantity");
                checks++;
            }
        }
        String plasmaIntro=net.minecraft.util.StatCollector.translateToLocal("gtsr.critical.nei.simple.plasma.intro");
        if(font.getStringWidth(plasmaIntro)>154)throw new AssertionError("Plasma intro width: "+lang+" "+plasmaIntro);
        var profiles=new ArrayList<>(com.miaokatze.gtsr.common.critical.CriticalPlasmaCatalog.entries());
        profiles.addAll(nativeProfiles);
        for(var plasma:profiles) {
            String[] rows=com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.plasmaRows(plasma);
            if(rows.length!=7)throw new AssertionError("Plasma batch requires exactly seven authored rows");
            if(!rows[6].contains("84.375T"))throw new AssertionError("Plasma heat is not normalized: "+plasma.id+" "+rows[6]);
            for(int row=0;row<rows.length;row++) {
                int width=row>=5?139:154;
                if(font.getStringWidth(rows[row])>width||font.listFormattedStringToWidth(rows[row],width).size()!=1
                    ||97+row*11+9>174)throw new AssertionError("Plasma card overflow: "+lang+" unicode="+unicode
                    +" "+plasma.id+" row="+row+" px="+font.getStringWidth(rows[row])+" "+rows[row]);
                System.out.println((nativeProfiles.contains(plasma)?"NATIVE_PLASMA_FONT ":"PLASMA_FONT ")+lang+" unicode="+unicode+" "+plasma.id+" row="+row
                    +" px="+font.getStringWidth(rows[row])+" "+rows[row]);
                checks++;
            }
            var tip=com.miaokatze.gtsr.client.nei.CriticalNEIShowcase.plasmaTooltip(plasma);
            if(tip.stream().anyMatch(s->s.contains("GG")||s.contains("DSC")||s.contains("floor")||s.contains("ceil")))
                throw new AssertionError("Plasma tooltip exposes unexplained implementation terms");
            checks++;
        }
    }
}
