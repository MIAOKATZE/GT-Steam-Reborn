import java.nio.file.*;
import java.lang.reflect.*;
import java.util.*;
import javax.imageio.ImageIO;
import net.minecraft.client.gui.FontRenderer;

/** Actual vanilla FontRenderer width/wrapping with vanilla atlas metrics; no GL or client launch. */
public class CriticalNEIFontContract {
    static int checks;
    static void set(Object o,String name,Object value)throws Exception {
        Field f=FontRenderer.class.getDeclaredField(name);f.setAccessible(true);f.set(o,value);
    }
    public static void main(String[] args)throws Exception {
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
            for(long eut:List.of(com.miaokatze.gtsr.common.critical.CriticalStellarPolicy.IGNITION_EU
                /com.miaokatze.gtsr.common.critical.CriticalStellarPolicy.DURATION_TICKS,
                com.miaokatze.gtsr.common.critical.CriticalStellarPolicy.SUSTAIN_EUT)) {
                String power=String.format(Locale.ROOT,"%,d EU/t",eut);
                if(font.getStringWidth(power)>154)throw new AssertionError("Stellar power width: "+power);
                checks++;
            }

            for(String line:Files.readAllLines(Path.of(args[0],lang+".lang"))) {
                if ((line.startsWith("gtsr.critical.") || line.startsWith("gtsr.recipe.critical.")
                    || line.startsWith("gtsr.recipe.stellar_") || line.startsWith("gt.blockmachines.gtsr.critical.")
                    || line.startsWith("gtsr.tooltip.machine.compact.sun."))
                    && (line.contains("??") || line.indexOf('\uFFFD') >= 0))
                    throw new AssertionError("Corrupt localization encoding: " + lang + " " + line);
                if(!line.startsWith("gtsr.critical.nei.simple."))continue;
                String key=line.substring(0,line.indexOf('='));
                if (lang.equals("zh_CN") && line.substring(line.indexOf('=')+1).codePoints()
                    .noneMatch(c -> c >= 0x4E00 && c <= 0x9FFF))
                    throw new AssertionError("Chinese NEI label lost its Han characters: " + key);
                String format=line.substring(line.indexOf('=')+1);
                String text=key.endsWith("turbine.efficiency")?String.format(Locale.ROOT,format,1,20)
                    :key.endsWith("turbine.trend")?String.format(Locale.ROOT,format,"0.1")
                    :key.endsWith("stellar.voltage")?String.format(Locale.ROOT,format,"MAX")
                    :key.endsWith("stellar.voltage_amps")||key.endsWith("chemical.equivalent_amps")?String.format(Locale.ROOT,format,"MAX","1,165")
                    :key.endsWith("chemical.average")?String.format(Locale.ROOT,format,"5,664,600")
                    :key.endsWith("chemical.equivalent_tier")?String.format(Locale.ROOT,format,"ZPM")
                    :key.endsWith("turbine.heat_per_l")?String.format(Locale.ROOT,format,"2,500")
                    :String.format(Locale.ROOT,format,"9999999999",3,999999);

                if(key.endsWith(".footer")||key.endsWith("card.footer"))text=text.replace("9999999999","99");
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
                    int limit=key.contains(".build.note.")||key.endsWith(".build.title")?132:key.contains(".build.row.")?144:154;
                    if(width>limit)throw new AssertionError(lang+" unicode="+unicode+" "+key+" width="+width+" "+text);
                }
                checks++;
            }
        }
        System.out.println("PASS "+checks+" native FontRenderer bilingual bounds; maximum authored line width="+maximum+"; no client/GL");
    }
}
