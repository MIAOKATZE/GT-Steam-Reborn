package com.miaokatze.gtsr.mixin;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.ClassReader;
import org.spongepowered.asm.lib.tree.AbstractInsnNode;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.lib.tree.MethodInsnNode;
import org.spongepowered.asm.lib.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * GTSR Mixin 配置插件。
 * <p>
 * 用于控制 GTNL 专用 mixin（{@code com.miaokatze.gtsr.mixin.gtnl.*}）仅在 GTNL 模组加载时应用，
 * 避免 GTNL 未加载时因目标类不存在导致 mixin 应用失败。
 * <p>
 * 注意：本插件只在 mixin 应用期（类加载阶段）判断 GTNL 是否加载，
 * 配置开关 {@code Config.gtnlEnhancement} 在 mixin 方法体内运行时判断（远晚于此）。
 */
public class GTSRMixinPlugin implements IMixinConfigPlugin {

    /** GTNL 是否已加载（静态初始化，类加载阶段即确定） */
    private static final boolean GTNL_LOADED = isClassPresent("com.science.gtnl.ScienceNotLeisure");

    @Override
    public void onLoad(String mixinPackage) {
        // 无需初始化
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.startsWith("com.miaokatze.gtsr.mixin.angelica.")) {
            return AngelicaCaptureShape.SUPPORTED;
        }
        // GTNL 专用 mixin 仅在 GTNL 加载时应用
        if (mixinClassName.startsWith("com.miaokatze.gtsr.mixin.gtnl.")) {
            return GTNL_LOADED;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
        // 无需处理
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需预处理
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        // 无需后处理
    }

    /**
     * 判断指定类是否存在于类路径中。
     *
     * @param className 完全限定类名
     * @return 存在返回 true，否则 false
     */
    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className, false, GTSRMixinPlugin.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Read class bytes without defining optional client classes during Mixin selection. */
    private static final class AngelicaCaptureShape {

        private static final boolean SUPPORTED = hasCaptureShape();

        private static boolean hasCaptureShape() {
            String context = "com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext.class";
            String task = "com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask.class";
            ClassLoader loader = GTSRMixinPlugin.class.getClassLoader();
            try (InputStream contextBytes = loader.getResourceAsStream(context);
                InputStream taskBytes = loader.getResourceAsStream(task)) {
                if (contextBytes == null || taskBytes == null) return false;
                ClassNode contextNode = new ClassNode(), taskNode = new ClassNode();
                new ClassReader(contextBytes).accept(contextNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                new ClassReader(taskBytes).accept(taskNode, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                String descriptor = "([II[I[ILorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;"
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZZ)V";
                return calls(
                    taskNode,
                    "renderBlock",
                    "com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext",
                    "copyRawBuffer",
                    descriptor)
                    && calls(
                        contextNode,
                        "copyRawBuffer",
                        "org/embeddedt/embeddium/impl/render/chunk/compile/buffers/ChunkModelBuilder",
                        "getVertexBuffer",
                        "(Lorg/embeddedt/embeddium/impl/model/quad/properties/ModelQuadFacing;)"
                            + "Lorg/embeddedt/embeddium/impl/render/chunk/vertex/builder/ChunkMeshBufferBuilder;");
            } catch (IOException unreadable) {
                return false;
            }
        }

        private static boolean calls(ClassNode node, String methodName, String owner, String name, String descriptor) {
            int matches = 0;
            for (MethodNode method : node.methods) {
                if (!method.name.equals(methodName)) continue;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof MethodInsnNode invocation && invocation.owner.equals(owner)
                        && invocation.name.equals(name)
                        && invocation.desc.equals(descriptor)) matches++;
                }
            }
            return matches == 1;
        }
    }
}
