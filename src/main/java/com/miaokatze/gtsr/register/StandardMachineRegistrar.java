package com.miaokatze.gtsr.register;

/**
 * 标准机器注册器
 * 继承自 MachineRegistrar，负责具体定义并注册标准单方块机器。
 * 当前 GTSR 的标准机器均通过 MachineLoader 直接注册，此处保留作为扩展点。
 */
public class StandardMachineRegistrar extends MachineRegistrar {

    @Override
    protected void setupRegistrations() {
        // 当前无标准机器需在此注册
    }
}
