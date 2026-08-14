package com.miaokatze.gtsr.common.structure;

import java.io.IOException;

import net.minecraft.network.PacketBuffer;
import net.minecraft.util.StatCollector;

import com.cleanroommc.modularui.api.drawable.IKey;
import com.cleanroommc.modularui.api.widget.IWidget;

import gregtech.api.enums.StructureErrorId;
import gregtech.api.structure.error.StructureError;
import gregtech.common.gui.modularui.multiblock.base.MTEMultiBlockBaseGui;

/**
 * 红石仓数量超限的结构错误（StructureErrorId.TOO_MANY_HATCHES）。
 * 由 StructureCheckerMixin 在结构校验访问到第 5 个红石仓时写入结构错误列表，
 * 经 GT5U 结构错误网络通道同步到控制器 GUI，提示"红石仓最多 4 个"并高亮超限位置。
 * 注意：不能引用 GT5U 的 TranslatableStructureError（Java 16 record，1.7.10 编译环境
 * 无 jvmdg stub 不可访问），故以普通类实现 StructureError 接口。
 */
public class GTSRRedstoneHatchLimitError implements StructureError {

    private static final int TEXT_COLOR = 0xFFE0E0E0;

    /** 本轮结构校验实际发现的红石仓数量（第 5 个起报错） */
    private final int found;

    /** Registry 原型（found=0，仅用于反序列化分发，见 StructureErrorRegistry.register） */
    public GTSRRedstoneHatchLimitError() {
        this(0);
    }

    public GTSRRedstoneHatchLimitError(int found) {
        this.found = found;
    }

    @Override
    public StructureErrorId getId() {
        return StructureErrorId.TOO_MANY_HATCHES;
    }

    @Override
    public void serialize(PacketBuffer buffer) throws IOException {
        buffer.writeInt(found);
    }

    @Override
    public StructureError deserialize(PacketBuffer buffer) throws IOException {
        return new GTSRRedstoneHatchLimitError(buffer.readInt());
    }

    @Override
    public IWidget createWidget(MTEMultiBlockBaseGui<?> gui) {
        return IKey.lang("gtsr.gui.redstone_hatch.limit", found)
            .color(TEXT_COLOR)
            .asWidget()
            .expanded();
    }

    @Override
    public String getDisplayString() {
        return StatCollector.translateToLocalFormatted("gtsr.gui.redstone_hatch.limit", found);
    }

    @Override
    public StructureError copy() {
        return new GTSRRedstoneHatchLimitError(found);
    }
}
