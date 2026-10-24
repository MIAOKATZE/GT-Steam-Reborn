package com.miaokatze.gtsr.client.terminal;

import com.miaokatze.gtsr.common.gui.mui2.cluster.ClusterSnapshot;
import com.miaokatze.gtsr.common.terminal.ClusterTerminalData;
import com.miaokatze.gtsr.common.terminal.PacketTerminalData;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/** Legacy route facade; its immutable wire decoder is shared with the MUI2 route. */
@SideOnly(Side.CLIENT)
public final class ClusterTerminalClientCache {

    private static volatile ClusterSnapshot current;
    private static int anchorX, anchorY, anchorZ, anchorDim;

    private ClusterTerminalClientCache() {}

    public static void clear() {
        current = null;
    }

    public static boolean isAnchored(int x, int y, int z, int dim) {
        return current != null && x == anchorX && y == anchorY && z == anchorZ && dim == anchorDim;
    }

    public static boolean getBool(String key, boolean fallback) {
        ClusterSnapshot s = current;
        return s == null ? fallback : s.getBool(key, fallback);
    }

    public static int getInt(String key, int fallback) {
        ClusterSnapshot s = current;
        return s == null ? fallback : s.getInt(key, fallback);
    }

    public static long getLong(String key, long fallback) {
        ClusterSnapshot s = current;
        return s == null ? fallback : s.getLong(key, fallback);
    }

    public static byte[] getBytes(String key) {
        ClusterSnapshot s = current;
        return s == null ? null : s.getBytes(key);
    }

    public static String getStr(String key, String fallback) {
        ClusterSnapshot s = current;
        return s == null ? fallback : s.getStr(key, fallback);
    }

    public static String getFDetail(String fallback) {
        return getStr(ClusterTerminalData.KEY_F_DETAIL, fallback);
    }

    public static String getStats(String fallback) {
        return getStr(ClusterTerminalData.KEY_STATS, fallback);
    }

    public static String getLeChains(String fallback) {
        return getStr(ClusterTerminalData.KEY_LE_CHAINS, fallback);
    }

    public static void accept(PacketTerminalData msg) {
        ClusterSnapshot previous = isAnchored(msg.getX(), msg.getY(), msg.getZ(), msg.getDim()) ? current : null;
        ClusterSnapshot next = ClusterSnapshot.merge(previous, msg.getSnapshotVersion(), msg.getPayload());
        if (next == previous || next == null) return;
        anchorX = msg.getX();
        anchorY = msg.getY();
        anchorZ = msg.getZ();
        anchorDim = msg.getDim();
        current = next;
    }

    public static void invalidate(PacketTerminalData msg) {
        if (isAnchored(msg.getX(), msg.getY(), msg.getZ(), msg.getDim())) current = null;
    }
}
