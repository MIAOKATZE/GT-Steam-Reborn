"""Bounded offscreen fallback contract using candidate showcase bytecode and fixture hosts.

Prepare: python tools/critical/verify_nei_fallback_v209.py --prepare
Run:     python tools/critical/verify_nei_fallback_v209.py --jar <final-dev.jar>
This is an LWJGL2 Pbuffer fixture, never a native NEI/client screenshot.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile

from verify_nei_render import STUBS as BASE_STUBS, CACHE, JAVA

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-v209-nei-fallback'
PREFIX = 'com/miaokatze/gtsr/'
STUBS = dict(BASE_STUBS)
STUBS.update({
    'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
public class Minecraft { static final Minecraft instance=new Minecraft();
public net.minecraft.client.gui.FontRenderer fontRenderer=new net.minecraft.client.gui.FontRenderer();
public net.minecraft.client.settings.GameSettings gameSettings=new net.minecraft.client.settings.GameSettings();
public static Minecraft getMinecraft(){return instance;}
public net.minecraft.client.resources.IResourceManager getResourceManager(){return null;} }''',
    'net/minecraft/client/resources/IResourceManager.java': 'package net.minecraft.client.resources; public interface IResourceManager {}',
    'net/minecraft/client/resources/IReloadableResourceManager.java': '''package net.minecraft.client.resources;
public interface IReloadableResourceManager extends IResourceManager {void registerReloadListener(IResourceManagerReloadListener listener);}''',
    'net/minecraft/client/resources/IResourceManagerReloadListener.java': '''package net.minecraft.client.resources;
public interface IResourceManagerReloadListener {void onResourceManagerReload(IResourceManager manager);}''',
    'net/minecraft/client/gui/FontRenderer.java': '''package net.minecraft.client.gui;
import java.util.*;
public class FontRenderer { public final List<String> drawn=new ArrayList<>();
public int getStringWidth(String s){return s.length()*6;}
public String trimStringToWidth(String s,int width){return s.substring(0,Math.min(s.length(),Math.max(0,width/6)));}
public List<String> listFormattedStringToWidth(String s,int width){List<String> r=new ArrayList<>();int n=Math.max(1,width/6);
for(int p=0;p<s.length();p+=n)r.add(s.substring(p,Math.min(s.length(),p+n)));return r;}
public int drawString(String s,int x,int y,int c){drawn.add(s);
for(int i=0;i<s.length();i++)Gui.drawRect(x+i*6,y,x+i*6+3,y+6,c);
if(s.equals(net.minecraft.util.StatCollector.translateToLocal("gtsr.critical.nei.showcase.simplified")))Gui.capture=false;
return s.length()*6;}}''',
    'net/minecraft/client/gui/Gui.java': '''package net.minecraft.client.gui;
import org.lwjgl.opengl.GL11;import org.lwjgl.BufferUtils;import java.nio.FloatBuffer;
public class Gui {public static boolean capture,failNextRect;public static int rectangles;
public static double minX,minY,maxX,maxY;private static final FloatBuffer matrix=BufferUtils.createFloatBuffer(16);
public static void beginCapture(){capture=true;rectangles=0;minX=minY=Double.POSITIVE_INFINITY;maxX=maxY=Double.NEGATIVE_INFINITY;}
private static void point(int x,int y){double xx=matrix.get(0)*x+matrix.get(4)*y+matrix.get(12),yy=matrix.get(1)*x+matrix.get(5)*y+matrix.get(13);
minX=Math.min(minX,xx);maxX=Math.max(maxX,xx);minY=Math.min(minY,yy);maxY=Math.max(maxY,yy);}
public static void drawRect(int x1,int y1,int x2,int y2,int c){
if(failNextRect){failNextRect=false;throw new IllegalStateException("injected fallback schematic rectangle failure");}
if(capture){matrix.clear();GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX,matrix);point(x1,y1);point(x2,y2);rectangles++;}
GL11.glEnable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(c>>>24)/255f);
GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2i(x1,y1);GL11.glVertex2i(x2,y1);GL11.glVertex2i(x2,y2);GL11.glVertex2i(x1,y2);GL11.glEnd();
GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_BLEND);}}''',
    'net/minecraft/util/StatCollector.java': '''package net.minecraft.util;
import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class StatCollector {static final Map<String,Map<String,String>> languages=new HashMap<>();
public static String translateToLocal(String key){String lang=net.minecraft.client.Minecraft.getMinecraft().gameSettings.language;
Map<String,String> map=languages.get(lang);if(map==null){map=new HashMap<>();
try(BufferedReader reader=new BufferedReader(new InputStreamReader(StatCollector.class.getResourceAsStream("/assets/gtsr/lang/"+lang+".lang"),StandardCharsets.UTF_8))){
String line;while((line=reader.readLine())!=null){int p=line.indexOf('=');if(p>0)map.put(line.substring(0,p),line.substring(p+1));}}
catch(IOException e){throw new IllegalStateException(e);}languages.put(lang,map);}return map.getOrDefault(key,key);}}''',
    'com/miaokatze/gtsr/api/recipe/CriticalRecipeMaps.java': '''package com.miaokatze.gtsr.api.recipe;
import java.util.*;import gregtech.api.util.GTRecipe;import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
public class CriticalRecipeMaps {public static final Map<GTRecipe,DisplayInfo> data=new IdentityHashMap<>();
public static DisplayInfo displayInfo(GTRecipe r){return data.get(r);}
public static String text(String key){return net.minecraft.util.StatCollector.translateToLocal("gtsr.critical.nei."+key);}
public static List<String> description(GTRecipe r){return Arrays.asList(text("showcase.demo_only"),text("showcase.reference"));}
public static class DisplayInfo {public String operation="showcase";public CriticalMachineKind kind;public int page=0,pages=1;
public DisplayInfo(CriticalMachineKind k){kind=k;}public boolean construction(){return false;}public boolean showcase(){return true;}}}''',
    'com/miaokatze/gtsr/common/critical/CriticalPreviewDescriptor.java': '''package com.miaokatze.gtsr.common.critical;
public class CriticalPreviewDescriptor {public CriticalMachineKind kind;
public static CriticalPreviewDescriptor demo(CriticalMachineKind kind,CriticalTier tier){CriticalPreviewDescriptor d=new CriticalPreviewDescriptor();d.kind=kind;return d;}}''',
    'com/miaokatze/gtsr/common/critical/CriticalTier.java': 'package com.miaokatze.gtsr.common.critical; public enum CriticalTier {T1,T2,T3}',
    'com/miaokatze/gtsr/config/Config.java': 'package com.miaokatze.gtsr.config; public class Config {public static boolean criticalPreviewReducedMotion;}',
    'com/miaokatze/gtsr/client/critical/CriticalMiniatureRenderer.java': '''package com.miaokatze.gtsr.client.critical;
import com.miaokatze.gtsr.common.critical.CriticalPreviewDescriptor;
public class CriticalMiniatureRenderer {public static boolean inject;public static int calls;
public static boolean draw(CriticalPreviewDescriptor d,int x,int y,int width,int height,double seconds,boolean reduced){
if(x!=8||y!=55||width!=154||height!=80)throw new AssertionError("viewport contract changed");calls++;
net.minecraft.client.gui.Gui.beginCapture();if(inject)net.minecraft.client.gui.Gui.failNextRect=true;return false;}}''',
})

PROBE = r'''import java.io.*;import java.nio.*;import java.util.*;import java.awt.image.BufferedImage;import javax.imageio.ImageIO;
import org.lwjgl.*;import org.lwjgl.opengl.*;
import com.miaokatze.gtsr.client.nei.CriticalNEIShowcase;import com.miaokatze.gtsr.client.critical.CriticalMiniatureRenderer;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import com.miaokatze.gtsr.config.Config;import net.minecraft.client.Minecraft;import net.minecraft.client.gui.Gui;import gregtech.api.util.GTRecipe;
public class CriticalNEIFallbackV209 {
static int checks;static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
static int[] state(){List<Integer> result=new ArrayList<>();
for(int key:new int[]{GL11.GL_MODELVIEW_STACK_DEPTH,GL11.GL_PROJECTION_STACK_DEPTH,GL11.GL_ATTRIB_STACK_DEPTH,GL11.GL_MATRIX_MODE,GL11.GL_TEXTURE_BINDING_2D})result.add(GL11.glGetInteger(key));
for(int key:new int[]{GL11.GL_BLEND,GL11.GL_LIGHTING,GL11.GL_TEXTURE_2D,GL11.GL_SCISSOR_TEST,GL11.GL_DEPTH_TEST})result.add(GL11.glIsEnabled(key)?1:0);
for(int key:new int[]{GL11.GL_MODELVIEW_MATRIX,GL11.GL_PROJECTION_MATRIX,GL11.GL_CURRENT_COLOR}){int n=key==GL11.GL_CURRENT_COLOR?4:16;FloatBuffer b=BufferUtils.createFloatBuffer(16);GL11.glGetFloat(key,b);for(int i=0;i<n;i++)result.add(Float.floatToIntBits(b.get(i)));}
for(int key:new int[]{GL11.GL_VIEWPORT,GL11.GL_SCISSOR_BOX}){IntBuffer b=BufferUtils.createIntBuffer(16);GL11.glGetInteger(key,b);for(int i=0;i<4;i++)result.add(b.get(i));}
return result.stream().mapToInt(Integer::intValue).toArray();}
static byte[] pixels(){ByteBuffer b=BufferUtils.createByteBuffer(170*174*4);GL11.glReadPixels(0,0,170,174,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);byte[] data=new byte[b.capacity()];b.get(data);return data;}
static void image(byte[] data,File file)throws Exception{BufferedImage image=new BufferedImage(170,174,BufferedImage.TYPE_INT_ARGB);
for(int y=0;y<174;y++)for(int x=0;x<170;x++){int p=((173-y)*170+x)*4;image.setRGB(x,y,0xff000000|(data[p]&255)<<16|(data[p+1]&255)<<8|data[p+2]&255);}ImageIO.write(image,"png",file);}
static void reset(){Gui.capture=false;GL11.glClearColor(0,0,0,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);CriticalNEIShowcase.background(0,0,170,174,0);}
public static void main(String[] args)throws Exception{Pbuffer surface=new Pbuffer(170,174,new PixelFormat(8,0,0),null,null);
try{surface.makeCurrent();GL11.glViewport(0,0,170,174);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,170,174,0,-1,1);
GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_LIGHTING);GL11.glColor4f(.375f,.625f,.125f,.75f);
int[] baseline=state();int index=0;
for(CriticalMachineKind kind:CriticalMachineKind.releasedValues()){
Minecraft.getMinecraft().gameSettings.language=index++%2==0?"en_US":"zh_CN";
GTRecipe recipe=new GTRecipe();CriticalRecipeMaps.data.put(recipe,new CriticalRecipeMaps.DisplayInfo(kind));
Config.criticalPreviewReducedMotion=false;reset();CriticalNEIShowcase.draw(recipe);
check(Arrays.equals(baseline,state()),kind+" GL state restored");
check(Gui.rectangles>4&&Gui.minX>=8&&Gui.maxX<=162&&Gui.minY>=55&&Gui.maxY<=135,kind+" schematic and caption bounded: "+Gui.minX+","+Gui.minY+".."+Gui.maxX+","+Gui.maxY);
check(Minecraft.getMinecraft().fontRenderer.drawn.contains(CriticalRecipeMaps.text("showcase.simplified")),kind+" candidate localized fallback caption drawn");
byte[] normal=pixels();image(normal,new File(args[0],kind.key+".png"));
Config.criticalPreviewReducedMotion=true;gregtech.nei.GTNEIDefaultHandler.ticks=993;reset();CriticalNEIShowcase.draw(recipe);
check(Arrays.equals(normal,pixels()),kind+" reduced motion preserves static fallback pixels");
CriticalMiniatureRenderer.inject=true;boolean failed=false;
try{CriticalNEIShowcase.draw(recipe);}catch(IllegalStateException expected){failed=expected.getMessage().contains("fallback schematic");}
finally{CriticalMiniatureRenderer.inject=false;Gui.capture=false;}
check(failed&&Arrays.equals(baseline,state()),kind+" mid-schematic exception restores nested and outer GL scopes");
reset();CriticalNEIShowcase.draw(recipe);check(Arrays.equals(baseline,state())&&Arrays.equals(normal,pixels()),kind+" next draw recovers after exception");
check(GL11.glGetError()==0,kind+" zero GL errors");}
check(CriticalMiniatureRenderer.calls==16,"renderer stub forced false on 16 production draws");
System.out.println("FALLBACK_V209_PASS checks="+checks+" renderer="+GL11.glGetString(GL11.GL_RENDERER));
}finally{surface.destroy();}}
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--prepare', action='store_true')
    parser.add_argument('--jar', type=Path, help='Final candidate development jar; required for execution')
    options = parser.parse_args()
    classes, source, natives, images = [OUT / name for name in ('classes', 'src', 'natives', 'images')]
    for path in (classes, source, natives, images):
        path.mkdir(parents=True, exist_ok=True)
    for name, body in STUBS.items():
        target = source / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(body, encoding='utf-8')
    (source / 'CriticalNEIFallbackV209.java').write_text(PROBE, encoding='utf-8')
    preparation = {'status': 'PREPARED', 'scope': 'Actual candidate showcase bytecode with forced-false renderer; fixture catalog/font/Gui; real LWJGL2 Pbuffer; at most four fixture images', 'javaHeapLimitMiB': 256, 'renderSurface': [170, 174]}
    (OUT / 'preparation.json').write_text(json.dumps(preparation, indent=2), encoding='utf-8')
    if options.prepare:
        print(json.dumps(preparation))
        return
    if options.jar is None:
        parser.error('--jar <final-dev.jar> is required; --prepare only prepares the fixture')
    (OUT / 'verification.json').unlink(missing_ok=True)
    jar = options.jar.resolve()
    if not jar.name.endswith('-dev.jar'):
        parser.error('--jar must be a development jar with deobfuscated Minecraft method references')
    actual = [PREFIX + 'client/nei/CriticalNEIShowcase.class', PREFIX + 'client/nei/CriticalNEIShowcase$Page.class', PREFIX + 'common/critical/CriticalMachineKind.class']
    digests = {}
    with zipfile.ZipFile(jar) as archive:
        for name in actual:
            content = archive.read(name)
            if name.endswith('/CriticalNEIShowcase.class'):
                assert b'showcase.simplified' in content, 'Candidate bytecode does not contain the new fallback'
            target = classes / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(content)
            digests[name] = hashlib.sha256(content).hexdigest()
        for name in ('assets/gtsr/lang/en_US.lang', 'assets/gtsr/lang/zh_CN.lang'):
            target = classes / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_bytes(archive.read(name))
    native = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(native) as archive:
        archive.extractall(natives)
    lwjgl = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl/*/*/*.jar'))
    cp = os.pathsep.join(map(str, [classes, lwjgl]))

    def run(executable, label, args):
        argfile = OUT / (label + '.args')
        argfile.write_text('\n'.join('"' + str(arg).replace('\\', '/') + '"' for arg in args), encoding='utf-8')
        result = subprocess.run([str(JAVA / executable), '@' + str(argfile)], cwd=OUT,
                                capture_output=True, timeout=60,
                                creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        (OUT / (label + '.log')).write_text(output, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(output[-5000:])
        return output

    run('javac.exe', 'compile', ['-J-Xmx256m', '-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *source.rglob('*.java')])
    output = run('java.exe', 'render', ['-Xmx256m', '-XX:MaxDirectMemorySize=64m', '-XX:MaxMetaspaceSize=64m', '-XX:ReservedCodeCacheSize=32m', '-Djava.library.path=' + str(natives), '-cp', cp, 'CriticalNEIFallbackV209', images])
    assert 'FALLBACK_V209_PASS checks=29' in output, output
    receipt = {**preparation, 'status': 'PASS', 'checks': 29, 'jar': str(jar), 'jarSHA256': hashlib.sha256(jar.read_bytes()).hexdigest(), 'actualClassSHA256': digests,
               'scriptSHA256': hashlib.sha256(Path(__file__).read_bytes()).hexdigest(), 'images': [str(path.relative_to(OUT)) for path in images.glob('*.png')], 'result': output.splitlines()}
    (OUT / 'verification.json').write_text(json.dumps(receipt, indent=2), encoding='utf-8')
    print(output)


if __name__ == '__main__':
    main()
