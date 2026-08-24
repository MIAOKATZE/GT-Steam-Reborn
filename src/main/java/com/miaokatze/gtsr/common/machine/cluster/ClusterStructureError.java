package com.miaokatze.gtsr.common.machine.cluster;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;

import com.cleanroommc.modularui.api.widget.IWidget;
import com.cleanroommc.modularui.utils.Alignment;

import gregtech.api.enums.StructureErrorId;
import gregtech.api.structure.error.StructureError;
import gregtech.api.structure.error.TranslatableText;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 集群结构错误（可翻译 lang 键）。不能直接用 GT5U 的 TranslatableStructureError——那是 Java 16
 * record，1.7.10 编译环境无 jvmdg stub 不可构造（GTSRRedstoneHatchLimitError 同一结论）。
 * 本类按 TranslatableStructureError 的网络格式原样序列化 TranslatableText 并复用其
 * {@code TRANSLATABLE_ERROR} id：客户端反序列化走 GT5U 自带的 record 原型正常渲染，无需覆盖注册表。
 */
public class ClusterStructureError implements StructureError {

    private static final int TEXT_COLOR = 0xFFE0E0E0;

    /**
     * E1a 新增错误类型（ID 沿用既有 lang 键空间<b>尾部追加</b>，不重排、不删除既有键）：
     * 延伸段断裂。lang 键由 lang 切片补齐（缺键时按现有机制显示原键）。
     * <p>
     * 语义：延伸链在第 {@code expectedSegment} 段（1 基，1..9）缺失/不完整，且其后仍存在可识别的
     * 延伸结构；该段及之后不收集。写入方为总控 checkMachine（E1b），配合
     * {@code ClusterTopology#setBrokenExtensionSegment} 使用。
     */
    public static final String LANG_KEY_EXTENSION_BREAK = "gtsr.gui.cluster.structure.extension_break";

    /**
     * E1a 新增错误类型（同上尾部追加）：模块冲突。同段同类挂点出现第二个正确类型模块控制器——
     * 结构仍成型，仅首个模块接入。由 {@code ClusterStructureDef} 挂点回调记录（模块冲突检查点），
     * 总控经 {@code ClusterStructureDef#drainModuleConflicts()} 取走。
     */
    public static final String LANG_KEY_MODULE_CONFLICT = "gtsr.gui.cluster.structure.module_conflict";

    private final TranslatableText message;

    public ClusterStructureError(String langKey) {
        this(TranslatableText.lang(langKey));
    }

    public ClusterStructureError(TranslatableText message) {
        this.message = message;
    }

    /** 延伸段断裂错误（expectedSegment 为 1 基延伸段号，1..9）。 */
    public static ClusterStructureError extensionBreak(int expectedSegment) {
        return new ClusterStructureError(
            TranslatableText.lang(LANG_KEY_EXTENSION_BREAK, TranslatableText.literal(expectedSegment)));
    }

    /** 模块冲突错误（segment 为段号 0..9，padId 为 {@link ClusterTopology} 的 PAD_* 值）。 */
    public static ClusterStructureError moduleConflict(int segment, int padId) {
        return new ClusterStructureError(
            TranslatableText
                .lang(LANG_KEY_MODULE_CONFLICT, TranslatableText.literal(segment), TranslatableText.literal(padId)));
    }

    @Override
    public StructureErrorId getId() {
        return StructureErrorId.TRANSLATABLE_ERROR;
    }

    @Override
    public void serialize(PacketBuffer buffer) throws IOException {
        message.serialize(buffer);
    }

    @Override
    public StructureError deserialize(PacketBuffer buffer) throws IOException {
        return new ClusterStructureError(TranslatableText.deserialize(buffer));
    }

    @Override
    public IWidget createWidget(MTEMultiBlockBaseGui<?> gui) {
        return message.toIKey()
            .color(TEXT_COLOR)
            .alignment(Alignment.CenterLeft)
            .asWidget();
    }

    @Override
    public String getDisplayString() {
        return message.translate();
    }

    @Override
    public StructureError copy() {
        return new ClusterStructureError(message);
    }
}
