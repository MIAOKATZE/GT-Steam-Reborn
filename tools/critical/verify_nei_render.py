"""Invisible LWJGL2 Pbuffer: actual NEI showcase bytecode, fixture fonts/catalog, GL state + bounded cache."""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/v208-nei-render'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

STUBS = {
    'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;
public class Minecraft { static final Minecraft instance=new Minecraft();
public net.minecraft.client.gui.FontRenderer fontRenderer=new net.minecraft.client.gui.FontRenderer();
public net.minecraft.client.settings.GameSettings gameSettings=new net.minecraft.client.settings.GameSettings();
public static Minecraft getMinecraft(){return instance;} }''',
    'net/minecraft/client/settings/GameSettings.java': '''package net.minecraft.client.settings;
public class GameSettings { public String language="en_US"; }''',
    'net/minecraft/client/gui/FontRenderer.java': '''package net.minecraft.client.gui; import java.util.*;
public class FontRenderer { public int wraps; public boolean fail;
public int getStringWidth(String s){return s.length()*6;}
public List<String> listFormattedStringToWidth(String s,int width){wraps++;List<String> r=new ArrayList<>();
int n=Math.max(1,width/6);for(int p=0;p<s.length();p+=n)r.add(s.substring(p,Math.min(s.length(),p+n)));return r;}
public int drawString(String s,int x,int y,int c){if(fail)throw new IllegalStateException("injected font failure");
for(int i=0;i<s.length();i++)Gui.drawRect(x+i*6,y,x+i*6+3,y+6,c);return s.length()*6;}}
''',
    'net/minecraft/client/gui/Gui.java': '''package net.minecraft.client.gui; import org.lwjgl.opengl.GL11;
public class Gui { public static void drawRect(int x1,int y1,int x2,int y2,int c){
GL11.glEnable(GL11.GL_BLEND);GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glColor4f((c>>16&255)/255f,(c>>8&255)/255f,(c&255)/255f,(c>>>24)/255f);
GL11.glBegin(GL11.GL_QUADS);GL11.glVertex2i(x1,y1);GL11.glVertex2i(x2,y1);GL11.glVertex2i(x2,y2);GL11.glVertex2i(x1,y2);GL11.glEnd();
GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_BLEND);}}
''',
    'net/minecraft/util/StatCollector.java': '''package net.minecraft.util;
public class StatCollector {public static String translateToLocal(String k){return k.substring(k.lastIndexOf('.')+1);}}''',
    'gregtech/api/util/GTRecipe.java': 'package gregtech.api.util; public class GTRecipe {}',
    'gregtech/nei/GTNEIDefaultHandler.java': '''package gregtech.nei;
public class GTNEIDefaultHandler {public static int ticks; public static int getDrawTicks(){return ticks;}}''',
    'com/miaokatze/gtsr/api/recipe/CriticalRecipeMaps.java': '''package com.miaokatze.gtsr.api.recipe;
import java.util.*; import gregtech.api.util.GTRecipe;import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
public class CriticalRecipeMaps {public static final Map<GTRecipe,DisplayInfo> data=new IdentityHashMap<>();
public static DisplayInfo displayInfo(GTRecipe r){return data.get(r);}
public static String text(String k){return k.substring(k.lastIndexOf('.')+1);}
public static List<String> description(GTRecipe r){return Arrays.asList("Rule one: exact price and timing", "Rule two: preserved outputs");}
public static class DisplayInfo {public String operation;public CriticalMachineKind kind;
public DisplayInfo(CriticalMachineKind k,String o){kind=k;operation=o;}
public boolean construction(){return operation.startsWith("build_");}}}''',
}

PROBE = '''import java.lang.reflect.*;import java.nio.*;import java.util.*;
import org.lwjgl.*;import org.lwjgl.opengl.*;
import com.miaokatze.gtsr.client.nei.CriticalNEIShowcase;
import com.miaokatze.gtsr.api.recipe.CriticalRecipeMaps;
import com.miaokatze.gtsr.common.critical.CriticalMachineKind;
import gregtech.api.util.GTRecipe;
public class CriticalNEIRenderContract {
static int checks;
static void check(boolean v,String label){if(!v)throw new AssertionError(label);checks++;}
static int[] state(){return new int[]{GL11.glGetInteger(GL11.GL_MODELVIEW_STACK_DEPTH),GL11.glGetInteger(GL11.GL_ATTRIB_STACK_DEPTH),
GL11.glIsEnabled(GL11.GL_TEXTURE_2D)?1:0,GL11.glIsEnabled(GL11.GL_BLEND)?1:0,GL11.glIsEnabled(GL11.GL_LIGHTING)?1:0};}
static GTRecipe recipe(CriticalMachineKind k,String op){GTRecipe r=new GTRecipe();CriticalRecipeMaps.data.put(r,new CriticalRecipeMaps.DisplayInfo(k,op));return r;}
public static void main(String[] a)throws Exception{
Pbuffer p=new Pbuffer(170,174,new PixelFormat(8,0,0),null,null);p.makeCurrent();
GL11.glViewport(0,0,170,174);GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,170,174,0,-1,1);
GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glDisable(GL11.GL_BLEND);GL11.glEnable(GL11.GL_LIGHTING);
int[] baseline=state();CriticalNEIShowcase.background(0,0,170,174,0);check(Arrays.equals(baseline,state()),"background restores all state");
List<GTRecipe> recipes=new ArrayList<>();for(CriticalMachineKind k:CriticalMachineKind.releasedValues())recipes.add(recipe(k,k.key));
for(int i=0;i<3;i++)recipes.add(recipe(CriticalMachineKind.SUN,"build_"+i));
for(GTRecipe r:recipes){CriticalNEIShowcase.draw(r);check(Arrays.equals(baseline,state()),"page restores all state");}
var font=net.minecraft.client.Minecraft.getMinecraft().fontRenderer;int wraps=font.wraps;
for(int i=0;i<500;i++){gregtech.nei.GTNEIDefaultHandler.ticks=i;CriticalNEIShowcase.draw(recipes.get(i%recipes.size()));}
check(font.wraps==wraps,"500 warm draws do not rewrap or rebuild page");
net.minecraft.client.Minecraft.getMinecraft().gameSettings.language="zh_CN";CriticalNEIShowcase.draw(recipes.get(0));
check(font.wraps>wraps,"language change invalidates text cache");
Field f=CriticalNEIShowcase.class.getDeclaredField("PAGES");f.setAccessible(true);Map<?,?> cache=(Map<?,?>)f.get(null);
check(cache.size()==1,"language switch discards old pages");
for(int i=0;i<300;i++)CriticalNEIShowcase.draw(recipe(CriticalMachineKind.SUN,"sun"));
check(cache.size()==256,"bounded LRU after 300 recipes");
font.fail=true;try{CriticalNEIShowcase.draw(CriticalRecipeMaps.data.keySet().iterator().next());throw new AssertionError("exception not injected");}
catch(IllegalStateException expected){}check(Arrays.equals(baseline,state()),"exception path restores matrices and attributes");font.fail=false;
check(GL11.glGetError()==0,"zero GL errors");
System.out.println("PASS NEI render "+checks+" checks; real "+GL11.glGetString(GL11.GL_RENDERER)+"; fixture catalog/font; no native NEI UI screenshot");p.destroy();}}
'''


def main():
    (OUT / 'verification.json').unlink(missing_ok=True)
    source = ROOT / 'src/main/java/com/miaokatze/gtsr/client/nei/CriticalNEIShowcase.java'
    digest = hashlib.sha256(source.read_bytes()).hexdigest()
    compilation = json.loads((ROOT / 'build/v208-nei-contract/verification.json').read_text(encoding='utf-8'))
    bound = {key.replace('\\', '/'): value for key, value in compilation['sourceSHA256'].items()}
    assert bound[str(source.relative_to(ROOT)).replace('\\', '/')] == digest, 'Renderer changed after overlay compile'
    classes, src, natives = OUT / 'classes', OUT / 'src', OUT / 'natives'
    for folder in (classes, src, natives):
        folder.mkdir(parents=True, exist_ok=True)
    for name, body in STUBS.items():
        path = src / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(body, encoding='utf-8')
    (src / 'CriticalNEIRenderContract.java').write_text(PROBE, encoding='utf-8')
    native = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(native) as archive:
        archive.extractall(natives)
    lwjgl = next(CACHE.glob('org.lwjgl.lwjgl/lwjgl/*/*/*.jar'))
    cp = os.pathsep.join(map(str, [classes, ROOT / 'build/v208-nei-contract/classes',
        ROOT / 'build/libs/gtsr-v2.0.7-dev.jar', lwjgl]))
    def run(exe, name, args):
        argfile = OUT / (name + '.args')
        argfile.write_text('\n'.join('"' + str(a).replace('\\', '/') + '"' for a in args), encoding='utf-8')
        result = subprocess.run([str(JAVA / exe), '@' + str(argfile)], cwd=OUT, capture_output=True,
            timeout=60, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        output = (result.stdout + result.stderr).decode('utf-8', errors='replace')
        (OUT / (name + '.log')).write_text(output, encoding='utf-8')
        if result.returncode:
            raise RuntimeError(output[-5000:])
        return output
    run('javac.exe', 'compile', ['-encoding', 'UTF-8', '-proc:none', '-cp', cp, '-d', classes, *src.rglob('*.java')])
    result = run('java.exe', 'render', ['-Djava.library.path=' + str(natives), '-cp', cp, 'CriticalNEIRenderContract'])
    compiled = ROOT / 'build/v208-nei-contract/classes/com/miaokatze/gtsr/client/nei/CriticalNEIShowcase.class'
    (OUT / 'verification.json').write_text(json.dumps({'status': 'PASS', 'sourceSHA256': digest,
        'compiledClassSHA256': hashlib.sha256(compiled.read_bytes()).hexdigest(),
        'result': result.splitlines(), 'scope': 'Actual overlay renderer bytecode; real LWJGL2 Pbuffer; fixture catalog/font/Gui. Not full NEI client.'}, indent=2), encoding='utf-8')
    print(result)


if __name__ == '__main__':
    main()
