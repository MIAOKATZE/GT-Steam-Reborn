"""Real LWJGL2 Pbuffer + production renderer; minimal Minecraft texture/world bootstrap fixture."""
from pathlib import Path
import argparse, hashlib, json, subprocess, zipfile

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/critical-machines-v12115/gl'
CACHE = Path.home() / '.gradle/caches/modules-2/files-2.1'
JAVA = Path.home() / '.gradle/jdks/azul_systems__inc_-17-amd64-windows.2/bin'

STUBS = {
'net/minecraft/client/renderer/tileentity/TileEntitySpecialRenderer.java': '''package net.minecraft.client.renderer.tileentity;
public abstract class TileEntitySpecialRenderer {
 public abstract void renderTileEntityAt(net.minecraft.tileentity.TileEntity t,double x,double y,double z,float p);
 protected void bindTexture(net.minecraft.util.ResourceLocation r) { CriticalTexture.bind(r.toString()); }
}''',
'net/minecraft/client/renderer/tileentity/CriticalTexture.java': '''package net.minecraft.client.renderer.tileentity;
import java.util.*;import java.awt.image.*;import javax.imageio.*;import java.nio.*;import org.lwjgl.*;import org.lwjgl.opengl.*;
public class CriticalTexture {
 public static final Map<String,Integer> textures=new HashMap<>();
 public static void bind(String r){try{Integer id=textures.get(r);if(id==null){String[] p=r.split(":",2);
 BufferedImage im=ImageIO.read(CriticalTexture.class.getResourceAsStream("/assets/"+p[0]+"/"+p[1]));
 if(im==null)throw new IllegalStateException(r);ByteBuffer b=BufferUtils.createByteBuffer(im.getWidth()*im.getHeight()*4);
 for(int y=0;y<im.getHeight();y++)for(int x=0;x<im.getWidth();x++){int c=im.getRGB(x,y);b.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>24));}b.flip();
 id=GL11.glGenTextures();textures.put(r,id);GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
 GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
 GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,im.getWidth(),im.getHeight(),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,b);}
 GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);}catch(Exception e){throw new RuntimeException(r,e);}}
}''',
'net/minecraft/client/renderer/OpenGlHelper.java': '''package net.minecraft.client.renderer;
import org.lwjgl.opengl.*; public class OpenGlHelper {
public static int defaultTexUnit=GL13.GL_TEXTURE0,lightmapTexUnit=GL13.GL_TEXTURE1; public static float lastBrightnessX,lastBrightnessY;
public static void setLightmapTextureCoords(int u,float x,float y){lastBrightnessX=x;lastBrightnessY=y;GL13.glMultiTexCoord2f(u,x,y);}
public static void setClientActiveTexture(int u){GL13.glClientActiveTexture(u);}public static void setActiveTexture(int u){GL13.glActiveTexture(u);}
public static void glBlendFunc(int a,int b,int c,int d){GL14.glBlendFuncSeparate(a,b,c,d);}
}''',
'com/miaokatze/gtsr/common/critical/CriticalMaterials.java': '''package com.miaokatze.gtsr.common.critical;
import com.google.gson.*; public class CriticalMaterials {
public static int renderId;
private static JsonObject palette;public static JsonObject properties(String alias){if(palette==null)palette=CriticalGeometry.readJson("materials.json");return palette.getAsJsonObject(alias);}
public static net.minecraft.block.Block block(String alias,boolean dynamic){return null;}
}'''
}

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--jar',type=Path);args=parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    src=OUT/'src'; classes=OUT/'classes'; natives=OUT/'natives'
    classes.mkdir(exist_ok=True);natives.mkdir(exist_ok=True)
    for path,body in STUBS.items():
        p=src/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
    archive=next(CACHE.glob('org.lwjgl.lwjgl/lwjgl-platform/*/*/*natives-windows.jar'))
    with zipfile.ZipFile(archive) as z:z.extractall(natives)
    jars=list(CACHE.glob('*/*/*/*/*.jar'))
    # Fixture API substitutes precede production; all actual project renderer classes are untouched build output.
    production=[args.jar.resolve()] if args.jar else [ROOT/'build/classes/java/main',ROOT/'build/resources/main']
    cp=';'.join(map(str,[classes]+production+[ROOT/'build/classes/java/patchedMc']+jars))
    binding=dict(candidate=str(args.jar.resolve()) if args.jar else 'EXPLORATORY BUILD CLASSES',sha256=hashlib.sha256(args.jar.read_bytes()).hexdigest() if args.jar else None,
        fixture_overrides=list(STUBS),native_archive=str(archive),production= list(map(str,production)))
    (OUT/'binding.json').write_text(json.dumps(binding,indent=2))
    files=list(src.rglob('*.java'))+[Path(__file__).with_name(n) for n in ['CriticalGLProbe.java','CriticalGUIProbe.java','CriticalStaticGLProbe.java','CriticalClientStateProbe.java','CriticalNightProbe.java']]
    compile_args=['-encoding','UTF-8','-proc:none','-cp',cp,'-d',str(classes)]+list(map(str,files))
    argfile=OUT/'compile.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in compile_args))
    result=subprocess.run([str(JAVA/'javac.exe'),'@'+str(argfile)],capture_output=True,text=True)
    (OUT/'compile.log').write_text(result.stdout+result.stderr)
    if result.returncode:raise RuntimeError(result.stderr)
    runargs=['-Djava.library.path='+str(natives),'-Djava.awt.headless=true','-cp',cp,'CriticalGLProbe',str(OUT)]
    argfile=OUT/'run.args';argfile.write_text('\n'.join('"'+a.replace('\\','/')+'"' for a in runargs))
    with (OUT/'stdout.log').open('w') as log:
        result=subprocess.run([str(JAVA/'java.exe'),'@'+str(argfile)],stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
    print((OUT/'stdout.log').read_text())
    if result.returncode:raise RuntimeError('GL probe failed')
    checks=json.loads((OUT/'checks.json').read_text())
    for section in ['gui','staticMaterials','clientState','nightClock']:
        if checks[section]['status']!='PASS':raise RuntimeError('Failed scope: '+section)
    if checks['status']!='PASS':raise RuntimeError('GL checks failed')
    if args.jar:
        after=hashlib.sha256(args.jar.read_bytes()).hexdigest()
        if after!=binding['sha256']:raise RuntimeError('Candidate changed during GL probe')
        expected=args.jar.resolve().as_uri()
        from urllib.parse import unquote,urlparse
        for key in ['productionRendererCodeSource','productionGeometryCodeSource']:
            if unquote(urlparse(checks[key]).path).lower()!=unquote(urlparse(expected).path).lower():raise RuntimeError('Wrong CodeSource: '+key)
        binding['postrun_sha256']=after;binding['candidate_binding_status']='PASS'
        (OUT/'binding.json').write_text(json.dumps(binding,indent=2))

if __name__=='__main__':main()
