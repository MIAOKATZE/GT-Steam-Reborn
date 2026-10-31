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
 * Selects optional GTNL transforms from the startup config before target classes are loaded.
 * The native identity bridge remains active to preserve upstream input/output ownership when features are off.
 */
public class GTSRMixinPlugin implements IMixinConfigPlugin {

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
        if (mixinClassName.startsWith("com.miaokatze.gtsr.mixin.nei.")) {
            return BlockRendererPreviewShape.SUPPORTED;
        }
        if (mixinClassName.startsWith("com.miaokatze.gtsr.mixin.angelica.")) {
            return AngelicaCaptureShape.SUPPORTED;
        }
        // GTNL 专用 mixin 仅在 GTNL 加载时应用
        if (mixinClassName.startsWith("com.miaokatze.gtsr.mixin.gtnl.")) {
            return com.miaokatze.gtsr.compat.gtnl.GTNLMixinSelection.shouldApply(mixinClassName);
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

    /** The optional optimization is coupled to the inspected 1.4.25 scene/batch contract. */
    private static final class BlockRendererPreviewShape {

        private static final boolean SUPPORTED = hasPreviewShape();

        private static boolean hasPreviewShape() {
            ClassLoader loader = GTSRMixinPlugin.class.getClassLoader();
            try (
                InputStream sceneBytes = loader
                    .getResourceAsStream("blockrenderer6343/client/renderer/WorldSceneRenderer.class");
                InputStream worldBytes = loader
                    .getResourceAsStream("blockrenderer6343/client/world/TrackedDummyWorld.class")) {
                if (sceneBytes == null || worldBytes == null) return false;
                ClassNode scene = new ClassNode(), world = new ClassNode();
                new ClassReader(sceneBytes).accept(scene, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                new ClassReader(worldBytes).accept(world, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return method(
                    scene,
                    "renderBlocks",
                    "(Lnet/minecraft/client/renderer/Tessellator;" + "Lit/unimi/dsi/fastutil/longs/LongCollection;Z)V")
                    && method(scene, "<init>", "(Lblockrenderer6343/client/world/TrackedDummyWorld;)V")
                    && field(scene, "bufferBuilder", "Lnet/minecraft/client/renderer/RenderBlocks;")
                    && field(scene, "renderAllFaces", "Z")
                    && field(scene, "renderedBlocks", "Lit/unimi/dsi/fastutil/longs/LongSet;")
                    && field(scene, "onRender", "Ljava/util/function/Consumer;")
                    && method(scene, "resetRenderedBlocks", "()V")
                    && method(scene, "setRenderAllFaces", "(Z)V")
                    && AngelicaCaptureShape.calls(
                        scene,
                        "drawWorld",
                        "it/unimi/dsi/fastutil/longs/Long2ObjectMaps",
                        "fastIterable",
                        "(Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;)Lit/unimi/dsi/fastutil/objects/ObjectIterable;")
                    && field(world, "tileMap", "Lit/unimi/dsi/fastutil/longs/Long2ObjectMap;")
                    && method(world, "clear", "()V")
                    && (method(world, "setBlock", "(IIILnet/minecraft/block/Block;II)Z")
                        || method(world, "func_147465_d", "(IIILnet/minecraft/block/Block;II)Z"))
                    && (method(world, "setBlockMetadataWithNotify", "(IIIII)Z")
                        || method(world, "func_72921_c", "(IIIII)Z"))
                    && (method(world, "setTileEntity", "(IIILnet/minecraft/tileentity/TileEntity;)V")
                        || method(world, "func_147455_a", "(IIILnet/minecraft/tileentity/TileEntity;)V"))
                    && (method(world, "removeTileEntity", "(III)V") || method(world, "func_147475_p", "(III)V"))
                    && method(world, "setVisibleYLevel", "(I)V");
            } catch (IOException unreadable) {
                return false;
            }
        }

        private static boolean method(ClassNode node, String name, String descriptor) {
            for (MethodNode method : node.methods)
                if (method.name.equals(name) && method.desc.equals(descriptor)) return true;
            return false;
        }

        private static boolean field(ClassNode node, String name, String descriptor) {
            for (org.spongepowered.asm.lib.tree.FieldNode field : node.fields)
                if (field.name.equals(name) && field.desc.equals(descriptor)) return true;
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
                String descriptor = "([III[I[ILorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;"
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;ZZ)V";
                String encodeDescriptor = "([III[I[ILnet/minecraft/block/Block;"
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/compile/ChunkBuildBuffers;"
                    + "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext;"
                    + "Lorg/embeddedt/embeddium/impl/render/chunk/terrain/material/Material;Z"
                    + "Lcom/gtnewhorizons/angelica/rendering/celeritas/iris/ContextAwareChunkVertexEncoder;)V";
                return calls(
                    taskNode,
                    "encodeTessellated",
                    "com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuildContext",
                    "copyRawBuffer",
                    descriptor)
                    && calls(
                        taskNode,
                        "renderBlock",
                        "com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask",
                        "encodeTessellated",
                        encodeDescriptor)
                    && calls(
                        taskNode,
                        "finishDeferred",
                        "com/gtnewhorizons/angelica/rendering/celeritas/AngelicaChunkBuilderMeshingTask",
                        "encodeTessellated",
                        encodeDescriptor)
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
