import java.nio.file.*;
import java.util.zip.*;
import org.objectweb.asm.*;
import static org.objectweb.asm.Opcodes.*;

/** Link-only missing Forge APIs for unused history persistence; fail loudly if the fixture is called. */
public final class ForgeHistoryApiFixture {
    public static void main(String[] args) throws Exception {
        try (ZipFile source=new ZipFile(args[0]);ZipOutputStream out=new ZipOutputStream(Files.newOutputStream(Path.of(args[1])))) {
            for(String type:new String[]{"net/minecraft/entity/Entity","net/minecraft/entity/player/EntityPlayer"}) {
                ClassReader reader=new ClassReader(source.getInputStream(source.getEntry(type+".class")));
                ClassWriter writer=new ClassWriter(0);
                reader.accept(new ClassVisitor(ASM9,writer) {
                    @Override public void visitEnd() {
                        if(type.endsWith("/Entity")) {
                            MethodVisitor method=cv.visitMethod(ACC_PUBLIC,"getEntityData","()Lnet/minecraft/nbt/NBTTagCompound;",null,null);
                            method.visitCode();method.visitTypeInsn(NEW,"java/lang/AssertionError");method.visitInsn(DUP);method.visitLdcInsn("Link-only Forge Entity#getEntityData fixture invoked");method.visitMethodInsn(INVOKESPECIAL,"java/lang/AssertionError","<init>","(Ljava/lang/Object;)V",false);method.visitInsn(ATHROW);method.visitMaxs(3,1);method.visitEnd();
                        } else cv.visitField(ACC_PUBLIC|ACC_STATIC|ACC_FINAL,"PERSISTED_NBT_TAG","Ljava/lang/String;",null,"PlayerPersisted").visitEnd();
                        super.visitEnd();
                    }
                },0);
                out.putNextEntry(new ZipEntry(type+".class"));out.write(writer.toByteArray());out.closeEntry();
            }
        }
    }
}
