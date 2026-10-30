import java.nio.file.*;
import java.util.jar.*;
import org.objectweb.asm.*;
/** Compile-only Forge API additions; never used to execute the opt-in server audit command. */
public final class CommandApiFixture implements Opcodes {
    public static void main(String[] args) throws Exception {
        try (JarFile input = new JarFile(args[0]); JarOutputStream output = new JarOutputStream(Files.newOutputStream(Paths.get(args[1])))) {
            String[] names = {"net/minecraft/item/Item", "net/minecraft/enchantment/Enchantment", "net/minecraft/command/CommandBase"};
            for (String name : names) {
                ClassWriter writer = new ClassWriter(0);
                new ClassReader(input.getInputStream(input.getJarEntry(name + ".class"))).accept(new ClassVisitor(ASM9, writer) {
                    @Override public MethodVisitor visitMethod(int access, String method, String descriptor, String signature, String[] exceptions) {
                        // Remapping dropped the interface generic signature: expose the existing native bridge to javac.
                        if (name.endsWith("/CommandBase") && method.equals("compareTo") && descriptor.equals("(Ljava/lang/Object;)I"))
                            access &= ~(ACC_BRIDGE | ACC_SYNTHETIC);
                        return super.visitMethod(access, method, descriptor, signature, exceptions);
                    }
                }, 0);
                if (!name.endsWith("/CommandBase")) {
                    boolean item = name.endsWith("/Item");
                    MethodVisitor m = writer.visitMethod(ACC_PUBLIC, item ? "isBookEnchantable" : "canApplyAtEnchantingTable",
                        item ? "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemStack;)Z" : "(Lnet/minecraft/item/ItemStack;)Z", null, null);
                    m.visitCode(); m.visitTypeInsn(NEW, "java/lang/UnsupportedOperationException"); m.visitInsn(DUP);
                    m.visitMethodInsn(INVOKESPECIAL,"java/lang/UnsupportedOperationException","<init>","()V",false);
                    m.visitInsn(ATHROW); m.visitMaxs(2,item ? 3 : 2); m.visitEnd();
                }
                output.putNextEntry(new JarEntry(name + ".class")); output.write(writer.toByteArray()); output.closeEntry();
            }
        }
    }
}
