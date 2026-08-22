package com.miaokatze.gtsr.common.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;

import gregtech.GTMod;
import gregtech.common.UndergroundOil;

/**
 * GT5U {@link UndergroundOil} 反射访问助手（唯一消费方 MTEVeinSteamPyrolyzer）。
 * SR-OPT-05/O5 修复口径：全部反射目标（Field/Method）静态懒解析缓存，仅查找一次；
 * 查找失败缓存失败态并不再重试（读点直接返回安全默认 0/false），失败仅一次性告警——
 * 避免 GT5U 签名漂移后表现为「每次调用重复反射解析 + 热解机周期内反复刷 error 日志」。
 */
public class UndergroundOilHelper {

    /**
     * 单个反射目标的懒解析缓存：成功缓存解析结果；失败置 failed 后永久短路（get 恒返回 null，
     * 读点走安全默认）。owner 为解析宿主实例（实例方法表用其运行时类解析；静态字段传 null）。
     * 失败告警一次性：含目标签名与降级后果，后续调用不再重试、不再刷日志。
     */
    private abstract static class CachedReflection<R> {

        private final String target;
        private final String degradedTo;
        private volatile R resolved;
        private volatile boolean failed;

        CachedReflection(String target, String degradedTo) {
            this.target = target;
            this.degradedTo = degradedTo;
        }

        /** 执行一次实际反射查找（方法表用 owner 运行时类）；任何查找异常视为失败并缓存失败态。 */
        protected abstract R lookup(Object owner) throws Exception;

        final R get(Object owner) {
            if (failed) return null;
            R r = resolved;
            if (r != null) return r;
            synchronized (this) {
                if (failed || resolved != null) return resolved;
                try {
                    resolved = lookup(owner);
                } catch (Exception e) {
                    failed = true;
                    GTMod.GT_FML_LOGGER.warn(
                        "[GTSR] UndergroundOilHelper reflection lookup failed for " + target
                            + "; caching failure and degrading to "
                            + degradedTo
                            + " until restart (no retry, no log spam). Likely GT5U signature change.",
                        e);
                }
                return resolved;
            }
        }
    }

    private static final CachedReflection<Field> STORAGE_FIELD = new CachedReflection<Field>(
        "UndergroundOil.STORAGE (static field)",
        "null storage => all oil reads/writes return 0/false") {

        @Override
        protected Field lookup(Object owner) throws Exception {
            Field f = UndergroundOil.class.getDeclaredField("STORAGE");
            f.setAccessible(true);
            return f;
        }
    };

    private static final CachedReflection<Method> STORAGE_GET = new CachedReflection<Method>(
        "storage.get(World,int,int)",
        "0/false (chunk data unreachable)") {

        @Override
        protected Method lookup(Object owner) throws Exception {
            Method m = owner.getClass()
                .getMethod("get", World.class, int.class, int.class);
            m.setAccessible(true);
            return m;
        }
    };

    private static final CachedReflection<Method> CHUNK_GET_FLUID = new CachedReflection<Method>(
        "chunkData.getFluid()",
        "0 (treated as no fluid)") {

        @Override
        protected Method lookup(Object owner) throws Exception {
            Method m = owner.getClass()
                .getMethod("getFluid");
            m.setAccessible(true);
            return m;
        }
    };

    private static final CachedReflection<Method> CHUNK_GET_AMOUNT = new CachedReflection<Method>(
        "chunkData.getAmount()",
        "0/false") {

        @Override
        protected Method lookup(Object owner) throws Exception {
            Method m = owner.getClass()
                .getMethod("getAmount");
            m.setAccessible(true);
            return m;
        }
    };

    private static final CachedReflection<Method> CHUNK_CHANGE_AMOUNT = new CachedReflection<Method>(
        "chunkData.changeAmount(int)",
        "0 (no amount written)") {

        @Override
        protected Method lookup(Object owner) throws Exception {
            Method m = owner.getClass()
                .getMethod("changeAmount", int.class);
            m.setAccessible(true);
            return m;
        }
    };

    private static final CachedReflection<Method> CHUNK_SET_AMOUNT = new CachedReflection<Method>(
        "chunkData.setAmount(int)",
        "false (no cap applied)") {

        @Override
        protected Method lookup(Object owner) throws Exception {
            Method m = owner.getClass()
                .getMethod("setAmount", int.class);
            m.setAccessible(true);
            return m;
        }
    };

    /** storage 空值的一次性告警开关（查找失败已由 STORAGE_FIELD 一次性告警，此处防值空态每调用刷日志）。 */
    private static boolean storageNullLogged = false;

    private static Object getStorage() {
        Field storageField = STORAGE_FIELD.get(null);
        if (storageField == null) return null; // 查找失败已一次性告警，读点直接走安全默认
        try {
            Object storage = storageField.get(null);
            if (storage == null) logStorageNullOnce();
            return storage;
        } catch (IllegalAccessException e) {
            // setAccessible 已放开的静态字段读取理论上不可达；按空值同路径一次性告警
            logStorageNullOnce();
            return null;
        }
    }

    private static synchronized void logStorageNullOnce() {
        if (storageNullLogged) return;
        storageNullLogged = true;
        GTMod.GT_FML_LOGGER.error(
            "[GTSR] UndergroundOilHelper: UndergroundOil storage is null (logged once); oil reads/writes return 0/false");
    }

    public static int increaseFluidAmount(World w, int chunkX, int chunkZ, int increase, int maxAmount) {
        try {
            Object storage = getStorage();
            if (storage == null) return 0;

            Method getMethod = STORAGE_GET.get(storage);
            if (getMethod == null) return 0;
            Object chunkData = getMethod.invoke(storage, w, chunkX, chunkZ);
            if (chunkData == null) {
                GTMod.GT_FML_LOGGER.warn("[GTSR] increaseFluidAmount: chunkData is null");
                return 0;
            }

            Method getFluidMethod = CHUNK_GET_FLUID.get(chunkData);
            if (getFluidMethod == null) return 0;
            Fluid fluid = (Fluid) getFluidMethod.invoke(chunkData);
            if (fluid == null) {
                GTMod.GT_FML_LOGGER.warn("[GTSR] increaseFluidAmount: fluid is null");
                return 0;
            }

            Method getAmountMethod = CHUNK_GET_AMOUNT.get(chunkData);
            if (getAmountMethod == null) return 0;
            int currentAmount = (int) getAmountMethod.invoke(chunkData);

            if (currentAmount >= maxAmount) {
                return 0;
            }

            int actualIncrease = Math.min(increase, maxAmount - currentAmount);
            if (actualIncrease <= 0) {
                GTMod.GT_FML_LOGGER.warn("[GTSR] increaseFluidAmount: actualIncrease={}, skipping", actualIncrease);
                return 0;
            }

            Method changeAmountMethod = CHUNK_CHANGE_AMOUNT.get(chunkData);
            if (changeAmountMethod == null) return 0;
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

            Method getMethod = STORAGE_GET.get(storage);
            if (getMethod == null) return false;
            Object chunkData = getMethod.invoke(storage, w, chunkX, chunkZ);
            if (chunkData == null) return false;

            Method getAmountMethod = CHUNK_GET_AMOUNT.get(chunkData);
            if (getAmountMethod == null) return false;
            int currentAmount = (int) getAmountMethod.invoke(chunkData);
            if (currentAmount <= bugThreshold) return false;

            Method setAmountMethod = CHUNK_SET_AMOUNT.get(chunkData);
            if (setAmountMethod == null) return false;
            setAmountMethod.invoke(chunkData, capAmount);
            return true;
        } catch (Exception e) {
            GTMod.GT_FML_LOGGER.error("[GTSR] capFluidAmountIfBug failed", e);
            return false;
        }
    }
}
