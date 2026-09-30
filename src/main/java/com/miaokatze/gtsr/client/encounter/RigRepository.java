package com.miaokatze.gtsr.client.encounter;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.IResourceManagerReloadListener;

/** 资源热重载清缓存；失败只记录一次，防止缺交资产每帧刷日志。 */
public final class RigRepository implements IResourceManagerReloadListener {

    private final String modid;
    private final Map<String, RigAsset> assets = new LinkedHashMap<String, RigAsset>();
    private final Set<String> failed = new HashSet<String>();

    public RigRepository(String modid) {
        this.modid = modid;
    }

    public RigAsset get(String code) {
        if (code == null || code.isEmpty() || failed.contains(code)) return null;
        RigAsset a = assets.get(code);
        if (a != null) return a;
        try {
            a = new RigAsset(modid, code);
            assets.put(code, a);
            return a;
        } catch (Exception e) {
            failed.add(code);
            System.err.println("[GTSR/encounter] Missing or invalid asset " + code + ": " + e);
            return null;
        }
    }

    public void preload(Collection<String> codes) {
        for (String code : codes) get(code);
    }

    public void onResourceManagerReload(IResourceManager manager) {
        for (RigAsset a : assets.values()) a.release();
        assets.clear();
        failed.clear();
    }
}
