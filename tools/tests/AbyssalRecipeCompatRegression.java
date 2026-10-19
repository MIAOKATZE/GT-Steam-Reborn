import java.lang.reflect.Field;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Real upstream field owners and the production call site; does not run full recipe registration. */
public final class AbyssalRecipeCompatRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("AbyssalRecipeCompatRuntime", args[0]);
    }
}

class AbyssalRecipeCompatRuntime {
    private static final String FIELD = "multiblockRockBreakerRecipes";

    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        boolean beta1 = version.equals("5.09.52.594");
        String owner = beta1 ? "gtPlusPlus.api.recipe.GTPPRecipeMaps" : "gregtech.api.recipe.RecipeMaps";
        Field expected = Class.forName(owner).getDeclaredField(FIELD);
        if (beta1) {
            try {
                legacyReference();
                throw new AssertionError("Old direct reference unexpectedly linked on beta1");
            } catch (NoSuchFieldError reproduced) {
                System.out.println("RESULT original beta1 NoSuchFieldError reproduced: " + reproduced.getMessage());
            }
        } else if (legacyReference() != expected.get(null)) {
            throw new AssertionError("RC2 direct field identity changed");
        }
        Object actual = com.miaokatze.gtsr.api.compat.GTVersionCompat.gppRecipeMap(FIELD);
        if (actual == null || actual != expected.get(null)) throw new AssertionError("Wrong recipe map: " + actual);
        if (actual != com.miaokatze.gtsr.api.compat.GTVersionCompat.gppRecipeMap(FIELD))
            throw new AssertionError("Recipe map identity changed on repeated lookup");
        System.out.println("RESULT owner PASS version=" + version + " owner=" + owner);

        ClassNode production = new ClassNode();
        new ClassReader(AbyssalRecipeCompatRuntime.class.getClassLoader().getResourceAsStream(
            "com/miaokatze/gtsr/loader/recipes/AbyssalObsessionRecipes.class")).accept(production, 0);
        boolean lookup = false, addTo = false;
        for (Object entry : production.methods) {
            MethodNode method = (MethodNode) entry;
            if (!method.name.equals("register")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode field && field.name.equals(FIELD))
                    throw new AssertionError("Production still directly links recipe field: " + field.owner);
                if (instruction instanceof MethodInsnNode call) {
                    if (call.owner.equals("com/miaokatze/gtsr/api/compat/GTVersionCompat")
                        && call.name.equals("gppRecipeMap") && instruction.getPrevious() instanceof LdcInsnNode key
                        && FIELD.equals(key.cst)) lookup = true;
                    if (lookup && call.name.equals("addTo")
                        && call.desc.startsWith("(Lgregtech/api/interfaces/IRecipeMap;")) addTo = true;
                }
            }
        }
        if (!lookup || !addTo) throw new AssertionError("Missing production compatibility lookup/addTo");
        System.out.println("RESULT production-callsite PASS version=" + version);
    }

    // Compiled against RC2: reproduces the exact old GETSTATIC linkage on the actual beta1 jar.
    private static Object legacyReference() {
        return gregtech.api.recipe.RecipeMaps.multiblockRockBreakerRecipes;
    }
}
