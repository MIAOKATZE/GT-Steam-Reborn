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

    private final TranslatableText message;

    public ClusterStructureError(String langKey) {
        this(TranslatableText.lang(langKey));
    }

    public ClusterStructureError(TranslatableText message) {
        this.message = message;
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
