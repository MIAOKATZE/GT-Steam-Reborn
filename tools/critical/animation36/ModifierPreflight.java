import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** CLI reflection only: never initializes or registers the FML listener. */
public final class ModifierPreflight {
    public static void main(String[] args) throws Exception {
        Class<?> type=Class.forName("audit.UiGalleryBatchAnimation36",false,ModifierPreflight.class.getClassLoader());
        for(Class<?> c=type;c!=null;c=c.getEnclosingClass())if(!Modifier.isPublic(c.getModifiers()))throw new AssertionError("Non-public listener/enclosing class: "+c);
        Set<String> expected=new HashSet<String>(Arrays.asList("client","server","render"));
        List<String> rows=new ArrayList<String>();
        for(Method method:type.getDeclaredMethods())for(Annotation annotation:method.getDeclaredAnnotations())if(annotation.annotationType().getName().equals("cpw.mods.fml.common.eventhandler.SubscribeEvent")){
            if(!Modifier.isPublic(method.getModifiers())||method.getReturnType()!=void.class||method.getParameterCount()!=1||!Modifier.isPublic(method.getDeclaringClass().getModifiers()))throw new AssertionError(method);
            String event=method.getParameterTypes()[0].getName();
            String wanted="cpw.mods.fml.common.gameevent.TickEvent$"+(method.getName().equals("client")?"ClientTickEvent":method.getName().equals("server")?"ServerTickEvent":"RenderTickEvent");
            if(!event.equals(wanted)||!expected.remove(method.getName()))throw new AssertionError("Unexpected signature: "+method);
            rows.add("{\"method\":\""+method.getName()+"\",\"modifiers\":\""+Modifier.toString(method.getModifiers())+"\",\"declaringClass\":\""+method.getDeclaringClass().getName()+"\",\"event\":\""+event+"\",\"return\":\"void\"}");
        }
        if(!expected.isEmpty())throw new AssertionError("Missing subscribers: "+expected);
        String json="{\"status\":\"PASS\",\"classInitialized\":false,\"listener\":\""+type.getName()+"\",\"modifiers\":\""+Modifier.toString(type.getModifiers())+"\",\"enclosingClass\":null,\"subscribers\":["+String.join(",",rows)+"]}";
        Files.write(Paths.get(args[0]),json.getBytes(StandardCharsets.UTF_8));System.out.println(json);
    }
}
