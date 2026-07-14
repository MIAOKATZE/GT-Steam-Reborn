package com.miaokatze.gtsr.register;

/**
 * 多方块机器注册器
 * 负责注册多方块机器。
 * 当前 GTSR 的多方块机器均通过 MachineLoader 直接注册，此处保留作为扩展点。
 */
public class MultiblockMachineRegistrar extends MachineRegistrar {

    @Override
    protected void setupRegistrations() {
        // 当前无多方块机器须在此注册
    }
}
