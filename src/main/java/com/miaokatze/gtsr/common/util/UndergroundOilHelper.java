package com.miaokatze.gtsr.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;

import gregtech.GTMod;
import gregtech.common.UndergroundOil;

public class UndergroundOilHelper {

    private static Object getStorage() {
        try {
            Field storageField = UndergroundOil.class.getDeclaredField("STORAGE");
            storageField.setAccessible(true);
            return storageField.get(null);
        } catch (Exception e) {
            GTMod.GT_FML_LOGGER.error("[GTSR] getStorage failed", e);
            return null;
        }
    }

    public static int increaseFluidAmount(World w, int chunkX, int chunkZ, int increase, int maxAmount) {
        try {
            Object storage = getStorage();
            if (storage == null) {
                GTMod.GT_FML_LOGGER.error("[GTSR] increaseFluidAmount: storage is null");
                return 0;
            }

            Method getMethod = storage.getClass()
                .getMethod("get", World.class, int.class, int.class);
            Object chunkData = getMethod.invoke(storage, w, chunkX, chunkZ);
            if (chunkData == null) {
                GTMod.GT_FML_LOGGER.warn("[GTSR] increaseFluidAmount: chunkData is null");
                return 0;
            }

            Method getFluidMethod = chunkData.getClass()
                .getMethod("getFluid");
            getFluidMethod.setAccessible(true);
            Fluid fluid = (Fluid) getFluidMethod.invoke(chunkData);
            if (fluid == null) {
                GTMod.GT_FML_LOGGER.warn("[GTSR] increaseFluidAmount: fluid is null");
                return 0;
            }

            Method getAmountMethod = chunkData.getClass()
                .getMethod("getAmount");
            getAmountMethod.setAccessible(true);
            int currentAmount = (int) getAmountMethod.invoke(chunkData);

            if (currentAmount >= maxAmount) {
                return 0;
            }

            int actualIncrease = Math.min(increase, maxAmount - currentAmount);
            if (actualIncrease <= 0) {
                GTMod.GT_FML_LOGGER.warn("[GTSR] increaseFluidAmount: actualIncrease={}, skipping", actualIncrease);
                return 0;
            }

            Method changeAmountMethod = chunkData.getClass()
                .getMethod("changeAmount", int.class);
            changeAmountMethod.setAccessible(true);
            changeAmountMethod.invoke(chunkData, actualIncrease);

            return actualIncrease;
        } catch (Exception e) {
            GTMod.GT_FML_LOGGER.error("[GTSR] increaseFluidAmount failed", e);
            return 0;
        }
    }

    /**
     * 储量保险：区块内部储量超过 bugThreshold 时强制收回 capAmount（v1.10.77 起 VSP 增量有等级上限，
     * 此方法负责修正无上限时期涨出的异常区块）。正常路径不产生任何日志。
     *
     * @return 是否实际发生了修正
     */
    public static boolean capFluidAmountIfBug(World w, int chunkX, int chunkZ, int capAmount, int bugThreshold) {
        try {
            Object storage = getStorage();
            if (storage == null) return false;

            Method getMethod = storage.getClass()
                .getMethod("get", World.class, int.class, int.class);
            Object chunkData = getMethod.invoke(storage, w, chunkX, chunkZ);
            if (chunkData == null) return false;

            Method getAmountMethod = chunkData.getClass()
                .getMethod("getAmount");
            getAmountMethod.setAccessible(true);
            int currentAmount = (int) getAmountMethod.invoke(chunkData);
            if (currentAmount <= bugThreshold) return false;

            Method setAmountMethod = chunkData.getClass()
                .getMethod("setAmount", int.class);
            setAmountMethod.setAccessible(true);
            setAmountMethod.invoke(chunkData, capAmount);
            return true;
        } catch (Exception e) {
            GTMod.GT_FML_LOGGER.error("[GTSR] capFluidAmountIfBug failed", e);
            return false;
        }
    }

    public static int getFluidAmount(World w, int chunkX, int chunkZ) {
        try {
            Object storage = getStorage();
            if (storage == null) return 0;

            Method getMethod = storage.getClass()
                .getMethod("get", World.class, int.class, int.class);
            Object chunkData = getMethod.invoke(storage, w, chunkX, chunkZ);
            if (chunkData == null) return 0;

            Method getAmountMethod = chunkData.getClass()
                .getMethod("getAmount");
            getAmountMethod.setAccessible(true);
            int amount = (int) getAmountMethod.invoke(chunkData);
            return amount;
        } catch (Exception e) {
            GTMod.GT_FML_LOGGER.error("[GTSR] getFluidAmount failed", e);
            return 0;
        }
    }
}
