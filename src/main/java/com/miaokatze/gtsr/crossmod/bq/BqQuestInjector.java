package com.miaokatze.gtsr.crossmod.bq;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.Tags;
import com.miaokatze.gtsr.config.Config;
import com.miaokatze.gtsr.main.GTSteamReborn;

import betterquesting.api.questing.IQuest;
import betterquesting.api.questing.IQuestLine;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.JsonHelper;
import betterquesting.api.utils.NBTConverter;
import betterquesting.handlers.SaveLoadHandler;
import betterquesting.network.handlers.NetChapterSync;
import betterquesting.network.handlers.NetQuestSync;
import betterquesting.network.handlers.NetSettingSync;
import betterquesting.questing.QuestDatabase;
import betterquesting.questing.QuestInstance;
import betterquesting.questing.QuestLine;
import betterquesting.questing.QuestLineDatabase;
import betterquesting.questing.QuestLineEntry;
import cpw.mods.fml.common.Loader;
import gregtech.api.enums.Materials;
import gregtech.api.enums.OrePrefixes;
import gregtech.api.util.GTOreDictUnificator;

/**
 * BetterQuesting 运行时任务注入器。
 * <p>
 * 本 mod 内全部 betterquesting 类型引用都收敛在这一个类中，
 * 且只在 {@link BqCompat#isBqLoaded()} 为 true 时才会被类加载
 * （BQ 缺席环境下本类永不加载，无需 @Optional 字节码剥离）。
 * <p>
 * 挂载点：CommonProxy.serverStarted（FMLServerStartedEvent，整波 ServerStarting
 * 结束、tick 与玩家登录前）。@Mod 声明 after:betterquesting 保证 BQ 的 default
 * load（clear 后从 config 重载）已同步完成，本注入器在其后做幂等追加，不会被清库。
 * 专用服根因：GTNH 2.9.0-beta-3 专用服上 dreamcraft 会在同波次更晚的
 * FMLServerStartingEvent 因 "Modpack has been updated" 整库重载默认任务库，
 * 同波次注入被覆盖，故迁移至 ServerStarted（BQ 3.8.84 全源码零
 * FMLServerStartedEvent 订阅，该阶段无 BQ 写库方，注入不被覆盖）。
 * <p>
 * 流程（照抄 BQ 官方装载路径 QuestCommandDefaults.load 的同构形状）：
 * <ol>
 * <li>读 assets/gtsr/bqquests/index.json 清单（规避 1.7.10 jar 目录枚举）</li>
 * <li>整包预检：逐文件解析、UUID/前置身份与清单一致性校验，再以脱离数据库的实例验证 BQ 定义解析；失败不写库</li>
 * <li>任务与任务线幂等 get-or-create 装载（get(id)==null 才 new + readFromNBT）</li>
 * <li>QuestLineEntry 挂线（未挂才 put）与 QuestLineDatabase.setOrderIndex</li>
 * <li>版本戳对账（世界目录 gtsr-injected.json vs Tags.VERSION）：不一致时执行
 * 「定义刷新」——快照进度→重读定义→merge 回填，完成/领取状态保留，
 * 挂线坐标同步替换；老世界无版本戳视为待升级刷一次。
 * 由此实现「新任务覆盖老任务」：发新版只需替换 jar，玩家免手动迁移</li>
 * <li>剪枝（无条件执行）：仅摘除 GTSR 线上已删除的 GTSR 任务；未被任何其他线引用时才
 * 移出任务数据库。外部任务/任务线只做 get-or-create，不覆盖定义、坐标或排序</li>
 * <li>进度回填：仅对本次补建任务先重放 QuestProgress.json，再逐玩家 merge=true 重放目录记录
 * （对抗 default load 对"库内不存在任务"进度的静默丢弃；不覆盖已存任务的当前进度）</li>
 * <li>前排有序规则收尾（gtsr→gtswn→gtit 依存在线序位 setOrderIndex）</li>
 * <li>同步四连（NetSettingSync / NetQuestSync.quickSync / NetChapterSync / markDirty）</li>
 * </ol>
 */
public final class BqQuestInjector {

    /** jar 内任务清单资源路径（index 声明文件树，规避 1.7.10 jar 目录枚举坑） */
    private static final String INDEX_RESOURCE = "assets/gtsr/bqquests/index.json";

    /** 世界侧版本戳文件名（位于 BQ_Settings.curWorldDir 下），记录最近一次注入所用定义版本 */
    private static final String STAMP_FILE_NAME = "gtsr-injected.json";

    /** Only this UUID namespace is owned by the bundled pack; shared/foreign quests are not deletions. */
    private static final long MANAGED_QUEST_HIGH = 0x4754535200000000L;

    /**
     * 前排有序常量表（规范 §6 条款 10，2026-09-07 拍板 gtsr→gtswn→gtit）。
     * 值为任务线 UUID 高 32 位（ASCII 记忆 GTSR/GTWN/GTIT）；仅重排表内命中线，官方线零写入。
     */
    private static final long[] FRONT_ORDER_UUID_HIGH = { 0x47545352L, // gtsr
        0x4754574eL, // gtswn
        0x47544954L // gtit
    };

    private BqQuestInjector() {}

    /**
     * 任务注入入口（serverStarted，整波 ServerStarting 结束后、tick 与玩家登录前）。
     * <p>
     * 双哨兵加固：BqCompat 探测标志 + Loader.isModLoaded 二次确认；
     * {@code BQ_Settings.curWorldDir != null} 表示 BQ 已完成世界装载流程，
     * 任何一项不满足即静默返回（BQ 缺席环境零副作用）。
     * 整体 try-catch 兜底：注入失败不影响 GTSR 主功能。
     */
    public static void inject() {
        if (!BqCompat.isBqLoaded() || !Loader.isModLoaded("betterquesting")) {
            return;
        }
        if (BQ_Settings.curWorldDir == null) {
            GTSteamReborn.LOG.warn("[BQ] curWorldDir 未就绪（BQ 世界装载未完成），跳过本次任务注入");
            return;
        }
        try {
            JsonObject index = readJsonResource(INDEX_RESOURCE);
            if (index == null) {
                GTSteamReborn.LOG.warn("[BQ] 未找到任务清单 {}，跳过任务注入", INDEX_RESOURCE);
                return;
            }
            JsonArray lines = index.getAsJsonArray("questLines");
            if (lines == null) {
                GTSteamReborn.LOG.warn("[BQ] 任务清单缺少 questLines 数组，跳过任务注入");
                return;
            }
            // A missing/corrupt asset is not an intentional deletion. Resolve and validate the
            // entire pack before changing live definitions, pruning entries, or stamping success.
            Map<String, NBTTagCompound> resources = prepareResources(lines, BqQuestInjector::readNbtResource);
            Set<UUID> pruneCandidates = new HashSet<>();
            Set<UUID> progressTargets = new HashSet<>();
            int questCount = 0;
            int refreshedCount = 0;
            int prunedCount = 0;
            int lineCreatedCount = 0;
            int skippedCount = 0;
            // 版本戳对账：版本变化（含老世界无戳）→ 刷新 GTSR 自有任务的定义与挂线坐标
            boolean refresh = isDefinitionRefreshNeeded();
            for (int i = 0; i < lines.size(); i++) {
                int[] r = loadQuestLine(
                    lines.get(i)
                        .getAsJsonObject(),
                    refresh,
                    resources,
                    pruneCandidates,
                    progressTargets);
                questCount += r[0];
                refreshedCount += r[1];
                prunedCount += r[2];
                lineCreatedCount += r[3];
                skippedCount += r[4];
            }
            // Loading all lines first also preserves quests moved between lines in this update.
            for (UUID id : pruneCandidates) {
                boolean referenced = QuestLineDatabase.INSTANCE.values()
                    .stream()
                    .anyMatch(line -> line.get(id) != null);
                if (!referenced) QuestDatabase.INSTANCE.remove(id);
            }
            restoreProgress(progressTargets);
            if (refresh) {
                writeStamp(Tags.VERSION);
            }
            int frontOrdered = applyFrontOrder();
            // 同步四连（与 QuestCommandDefaults.load 尾部同款）
            NetSettingSync.sendSync(null);
            NetQuestSync.quickSync(null, true, true);
            NetChapterSync.sendSync(null, null);
            SaveLoadHandler.INSTANCE.markDirty();
            GTSteamReborn.LOG.info(
                "[BQ] 任务注入完成（serverStarted）：任务线总数 {}（线新建 {}），任务新建 {}，跳过 {}，删除 {}，定义刷新 {}{}，"
                    + "setOrderIndex 落位 {}（清单序位 {} + 前排有序 {}）；新建>0 即疑似被第三方整库重载覆盖后重建",
                lines.size(),
                lineCreatedCount,
                questCount,
                skippedCount,
                prunedCount,
                refreshedCount,
                refresh ? "（对齐版本 " + Tags.VERSION + "）" : "",
                lines.size() + frontOrdered,
                lines.size(),
                frontOrdered);
        } catch (Throwable t) {
            GTSteamReborn.LOG.error("[BQ] 任务注入失败（不影响 GTSR 主功能）", t);
        }
    }

    /** Preflight custom packs as well as the bundled pack; an explicitly empty entries list is valid. */
    static Map<String, NBTTagCompound> prepareResources(JsonArray lines, Function<String, NBTTagCompound> reader) {
        Map<String, NBTTagCompound> resources = new HashMap<>();
        Set<UUID> lineIds = new HashSet<>();
        Map<UUID, String> questFiles = new HashMap<>();
        for (JsonElement element : lines) {
            JsonObject line = element.getAsJsonObject();
            UUID lineId = new UUID(requireLong(line, "idHigh"), requireLong(line, "idLow"));
            Math.toIntExact(requireLong(line, "orderIndex"));
            if (!lineIds.add(lineId)) throw new IllegalStateException("Duplicate BQ quest line: " + lineId);
            NBTTagCompound lineTag = requireResource(
                resources,
                reader,
                line.get("lineFile")
                    .getAsString());
            if (!lineId.equals(requireId(lineTag, "questLineID"))) {
                throw new IllegalStateException("BQ quest line identity disagrees with manifest: " + lineId);
            }
            Set<UUID> entriesSeen = new HashSet<>();
            JsonArray entries = line.getAsJsonArray("entries");
            if (entries == null) throw new IllegalStateException("Missing BQ entries array: " + lineId);
            for (JsonElement entryElement : entries) {
                JsonObject entry = entryElement.getAsJsonObject();
                String questFile = entry.get("questFile")
                    .getAsString();
                NBTTagCompound quest = requireResource(resources, reader, questFile);
                UUID questId = requireId(quest, "questID");
                String priorFile = questFiles.putIfAbsent(questId, questFile);
                if ((priorFile != null && !priorFile.equals(questFile)) || !entriesSeen.add(questId)) {
                    throw new IllegalStateException("Conflicting BQ quest definition/entry: " + questId);
                }
                NBTTagCompound entryTag = requireResource(
                    resources,
                    reader,
                    entry.get("entryFile")
                        .getAsString());
                if (!questId.equals(requireId(entryTag, "questID"))) {
                    throw new IllegalStateException("BQ quest entry identity disagrees with definition: " + questId);
                }
            }
        }
        // JSON/NBT and UUID validity do not imply that a registered BQ task/reward parser
        // accepts the definition. For example, Standard's completion reward throws on a
        // malformed target UUID, after readFromNBT has already replaced a live quest's
        // properties and tasks. Exercise native deserialization on detached instances for
        // the entire pack before the first live write. This is not plugin-side-effect rollback.
        for (String questFile : new HashSet<>(questFiles.values())) {
            try {
                NBTTagCompound definition = resources.get(questFile);
                validatePrerequisites(definition);
                new QuestInstance().readFromNBT((NBTTagCompound) definition.copy());
            } catch (RuntimeException e) {
                throw new IllegalStateException("Invalid BQ quest definition: " + questFile, e);
            }
        }
        return resources;
    }

    private static void validatePrerequisites(NBTTagCompound quest) {
        if (!quest.hasKey("preRequisites")) return;
        // Native BQ supports legacy integer arrays, but silently drops malformed UUID-list
        // entries. Losing a prerequisite is not a safe interpretation of a damaged quest pack.
        if (quest.hasKey("preRequisites", Constants.NBT.TAG_INT_ARRAY)) return;
        if (!quest.hasKey("preRequisites", Constants.NBT.TAG_LIST)) {
            throw new IllegalStateException("Invalid BQ prerequisite list");
        }
        NBTTagList prerequisites = (NBTTagList) quest.getTag("preRequisites");
        if (prerequisites.tagCount() > 0 && prerequisites.func_150303_d() != Constants.NBT.TAG_COMPOUND) {
            throw new IllegalStateException("Invalid BQ prerequisite entry type");
        }
        for (int i = 0; i < prerequisites.tagCount(); i++) {
            requireId(prerequisites.getCompoundTagAt(i), "questID");
        }
    }

    private static long requireLong(JsonObject object, String key) {
        JsonElement value = object.get(key);
        if (value == null || !value.isJsonPrimitive()
            || !value.getAsJsonPrimitive()
                .isNumber()) {
            throw new IllegalStateException("Missing/non-numeric BQ identity or order: " + key);
        }
        // Gson getAsLong silently truncates fractional and overflowing values.
        return new BigDecimal(value.getAsString()).longValueExact();
    }

    private static UUID requireId(NBTTagCompound tag, String prefix) {
        // Native BQ readId defaults absent or wrongly typed halves to zero.
        if (!tag.hasKey(prefix + "High", Constants.NBT.TAG_LONG)
            || !tag.hasKey(prefix + "Low", Constants.NBT.TAG_LONG)) {
            throw new IllegalStateException("Missing/invalid BQ identity: " + prefix);
        }
        return new UUID(tag.getLong(prefix + "High"), tag.getLong(prefix + "Low"));
    }

    private static NBTTagCompound requireResource(Map<String, NBTTagCompound> resources,
        Function<String, NBTTagCompound> reader, String path) {
        NBTTagCompound tag = resources.get(path);
        if (tag == null) {
            tag = reader.apply(path);
            if (tag == null) throw new IllegalStateException("Incomplete BQ quest pack: " + path);
            resources.put(path, tag);
        }
        return tag;
    }

    /**
     * 装载单条任务线及其下所有任务（全幂等 + 版本化定义刷新）。
     * <p>
     * 装载规则：线 get(id)==null 才 new QuestLine + readFromNBT + put；
     * 任务 get(id)==null 才 new QuestInstance + readFromNBT + put；
     * 线内条目 line.get(questId)==null 才 put(new QuestLineEntry)。
     * <p>
     * 刷新规则（{@code refresh==true}，版本戳对账失败时）：已存在任务重读定义
     * （进度快照→readFromNBT→merge 回填，完成/领取保留），挂线坐标用
     * {@code line.put} 直接替换（UuidDatabase map 语义）。
     * <p>
     * 剪枝规则（无条件执行）：GTSR 线上存在、但清单中已不存在的 GTSR 任务摘线；
     * 全部线装载后确认没有其他线引用才从数据库移除。外部 UUID 的任务和线不剪枝。
     *
     * @param lineSpec index.json 中该线的声明对象
     * @param refresh  是否对已存在任务执行定义刷新
     * @return int[]{本次新建任务数, 本次刷新定义任务数, 本次剪枝删除任务数, 线新建(0/1), 本次跳过任务数}
     */
    private static int[] loadQuestLine(JsonObject lineSpec, boolean refresh, Map<String, NBTTagCompound> resources,
        Set<UUID> pruneCandidates, Set<UUID> progressTargets) {
        UUID lineId = new UUID(
            lineSpec.get("idHigh")
                .getAsLong(),
            lineSpec.get("idLow")
                .getAsLong());
        int orderIndex = lineSpec.get("orderIndex")
            .getAsInt();

        IQuestLine line = QuestLineDatabase.INSTANCE.get(lineId);
        boolean lineCreated = false;
        if (line == null) {
            NBTTagCompound lineTag = resources.get(
                lineSpec.get("lineFile")
                    .getAsString());
            line = new QuestLine();
            line.readFromNBT(lineTag);
            QuestLineDatabase.INSTANCE.put(lineId, line);
            lineCreated = true;
        }
        // Existing foreign/custom lines are get-or-create only; do not reorder another pack.
        boolean managedLine = lineId.getMostSignificantBits() == MANAGED_QUEST_HIGH;
        if (lineCreated || managedLine) QuestLineDatabase.INSTANCE.setOrderIndex(lineId, orderIndex);

        int created = 0;
        int refreshed = 0;
        int skipped = 0;
        Set<UUID> expected = new HashSet<>();
        JsonArray entries = lineSpec.getAsJsonArray("entries");
        for (int i = 0; i < entries.size(); i++) {
            JsonObject entry = entries.get(i)
                .getAsJsonObject();
            NBTTagCompound questTag = resources.get(
                entry.get("questFile")
                    .getAsString());
            UUID questId = requireId(questTag, "questID");
            expected.add(questId);
            IQuest existing = QuestDatabase.INSTANCE.get(questId);
            if (existing == null) {
                // Only missing definitions lose their progress during BQ's default reload.
                // Existing quests already have authoritative in-memory progress (including
                // resets/claims made by earlier startup handlers). Never replay older disk
                // records over that state, even when the UUID belongs to this pack.
                progressTargets.add(questId);
                IQuest quest = new QuestInstance();
                quest.readFromNBT(questTag);
                QuestDatabase.INSTANCE.put(questId, quest);
                created++;
            } else if (refresh && questId.getMostSignificantBits() == MANAGED_QUEST_HIGH) {
                // 版本升级：重读定义（名称/描述/前置/奖励/任务），玩家进度保留
                refreshDefinition(existing, questTag);
                refreshed++;
            } else {
                // 已存在且无需刷新：幂等跳过（用于对账日志判读库是否被第三方整库重载）
                skipped++;
            }
            // 任务必须挂线（/bq_admin purge_hidden_quests 会清未挂线任务）：未挂才 put；
            // 已挂线且刷新中则替换条目以同步编辑器坐标
            if (line.get(questId) == null
                || (refresh && managedLine && questId.getMostSignificantBits() == MANAGED_QUEST_HIGH)) {
                NBTTagCompound entryTag = resources.get(
                    entry.get("entryFile")
                        .getAsString());
                line.put(questId, new QuestLineEntry(entryTag));
            }
        }
        // Only remove owned stale entries. Foreign editor-added quests and quests shared with
        // another line must survive; global deletion waits until all declared lines are loaded.
        int pruned = 0;
        List<UUID> stale = line.orderedEntries()
            .map(Map.Entry::getKey)
            .filter(id -> managedLine && id.getMostSignificantBits() == MANAGED_QUEST_HIGH && !expected.contains(id))
            .collect(Collectors.toList());
        for (UUID id : stale) {
            line.remove(id);
            pruneCandidates.add(id);
            pruned++;
        }
        if (pruned > 0) {
            GTSteamReborn.LOG.info("[BQ] 任务线 {} 清理已删除任务 {} 个: {}", lineId, pruned, stale);
        }
        if (lineCreated || created > 0 || refreshed > 0) {
            GTSteamReborn.LOG.info("[BQ] 任务线 {} 装载：线新建={}，新任务={}，刷新定义={}", lineId, lineCreated, created, refreshed);
        }
        return new int[] { created, refreshed, pruned, lineCreated ? 1 : 0, skipped };
    }

    /**
     * 前排有序规则（注入收尾、同步四连之前执行一次）。
     * 按常量表序扫描 QuestLineDatabase.INSTANCE.keySet()，UUID 高 32 位命中库内
     * 已存在线者依存在线序位 i（命中才递增，缺线无空洞）setOrderIndex(lineId, i)；
     * setOrderIndex 为 remove+clamp-insert（QuestLineDatabase.java:84-87），幂等收敛，
     * 最后执行者定最终序。禁用 setOrderedEntries（其首行整库 clear）。
     * 客户端顺序经 NetChapterSync order 列表自动镜像，零客户端代码。
     *
     * @return 实际 setOrderIndex 的线数（计数日志用）
     */
    private static int applyFrontOrder() {
        int applied = 0;
        int i = 0;
        for (long high : FRONT_ORDER_UUID_HIGH) {
            UUID lineId = null;
            for (UUID id : QuestLineDatabase.INSTANCE.keySet()) {
                if ((id.getMostSignificantBits() >>> 32) == high) {
                    lineId = id;
                    break;
                }
            }
            if (lineId != null) {
                QuestLineDatabase.INSTANCE.setOrderIndex(lineId, i);
                i++;
                applied++;
            }
        }
        return applied;
    }

    /**
     * 定义刷新（新任务覆盖老任务的核心步骤）：先快照进度，再重读定义，最后 merge 回填。
     * <p>
     * 完成状态与奖励领取标记整体保留在 completeUsers；子任务进度随快照回填，
     * 若新版定义的任务数量/顺序变化，超出部分的子任务细粒度进度被丢弃
     * （任务级完成与奖励领取不受影响）。
     */
    private static void refreshDefinition(IQuest quest, NBTTagCompound defTag) {
        NBTTagCompound progress = quest.writeProgressToNBT(new NBTTagCompound(), null);
        quest.readFromNBT(defTag);
        quest.readProgressFromNBT(progress, true);
    }

    /**
     * 版本戳判定：世界目录缺少版本戳文件（旧版注入器未写版本戳）或版本号
     * 与当前构建 {@link Tags#VERSION} 不同时返回 true。
     */
    private static boolean isDefinitionRefreshNeeded() {
        String stamped = readStamp();
        return stamped == null || !stamped.equals(Tags.VERSION);
    }

    /**
     * 读取世界侧版本戳（{@value #STAMP_FILE_NAME}），缺失或解析失败返回 null。
     */
    private static String readStamp() {
        File f = new File(BQ_Settings.curWorldDir, STAMP_FILE_NAME);
        if (!f.isFile()) {
            return null;
        }
        try (BufferedReader br = new BufferedReader(
            new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8),
            1024)) {
            JsonObject o = new JsonParser().parse(br)
                .getAsJsonObject();
            return o.has("version") && o.get("version")
                .isJsonPrimitive() ? o.get("version")
                    .getAsString() : null;
        } catch (Exception e) {
            GTSteamReborn.LOG.warn("[BQ] 版本戳读取失败，本次按需要刷新处理", e);
            return null;
        }
    }

    /**
     * 写入世界侧版本戳。失败仅告警不阻断：下次启动会多刷一次定义（幂等无害）。
     */
    private static void writeStamp(String version) {
        File f = new File(BQ_Settings.curWorldDir, STAMP_FILE_NAME);
        JsonObject o = new JsonObject();
        o.addProperty("version", version);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            new Gson().toJson(o, w);
        } catch (Exception e) {
            GTSteamReborn.LOG.warn("[BQ] 版本戳写入失败（下次启动可能重复刷新一次定义）", e);
        }
    }

    /**
     * 进度回填。
     * <p>
     * default load 的进度快照恢复只对"库内存在"的任务生效，本 mod 任务
     * 在其 clear→重载窗口内不在库中，进度被静默丢弃；此处注册完成后
     * （早于任何玩家登录）先从旧版 QuestProgress.json，再从 QuestProgress 目录逐玩家
     * merge=true 重放，仅限本次补建的任务；已存任务以内存为准，不以旧磁盘记录撤销领取/重置。
     * 补建任务没有内存进度；已存任务的定义刷新通过快照回填保留其当前状态。
     * 不调用 savePlayerProgress/markDirtyPlayers，也不再 quickSync（无在线玩家）。
     */
    private static void restoreProgress(Set<UUID> progressTargets) {
        if (progressTargets.isEmpty()) return;
        // BQ 3.8.87 loads the legacy aggregate first, then newer per-player files. The aggregate
        // may still be the only copy of progress for quests absent during BQ's default reload.
        restoreProgressFile(new File(BQ_Settings.curWorldDir, "QuestProgress.json"), progressTargets);
        File dir = new File(BQ_Settings.curWorldDir, "QuestProgress");
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.getName()
                .endsWith(".json")) restoreProgressFile(f, progressTargets);
        }
    }

    private static void restoreProgressFile(File file, Set<UUID> progressTargets) {
        if (!file.isFile()) return;
        try {
            JsonObject json = JsonHelper.ReadFromFile(file);
            // Validate before the converter can silently truncate fractional/overflowing UUIDs.
            validateResourceIds(json);
            NBTTagCompound nbt = NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
            if (!nbt.hasKey("questProgress", Constants.NBT.TAG_LIST)) {
                throw new IllegalStateException("Missing BQ questProgress list");
            }
            NBTTagList entries = nbt.getTagList("questProgress", Constants.NBT.TAG_COMPOUND);
            List<NBTTagCompound> selected = new ArrayList<>();
            for (int i = 0; i < entries.tagCount(); i++) {
                NBTTagCompound entry = entries.getCompoundTagAt(i);
                // Old integer-ID quests belong to the upstream book, not this UUID-based pack.
                if (!entry.hasKey("questIDHigh") && !entry.hasKey("questIDLow")) continue;
                UUID id = requireId(entry, "questID");
                if (progressTargets.contains(id)) selected.add(entry);
            }
            // Validate the complete file's identities before replaying any selected record.
            for (NBTTagCompound entry : selected) {
                IQuest quest = QuestDatabase.INSTANCE.get(requireId(entry, "questID"));
                if (quest != null) quest.readProgressFromNBT(entry, true);
            }
        } catch (Throwable t) {
            GTSteamReborn.LOG.warn("[BQ] 进度回填跳过无法解析的文件: {}", file.getName(), t);
        }
    }

    /**
     * 从 classloader 资源读 JSON 并转为 NBTTagCompound。
     * <p>
     * 与 BQ 官方装载同构：{@code NBTConverter.JSONtoNBT_Object(json, tag, format=true)}
     * （"键:类型ID" 后缀在此剥离）。dev（classes 目录）与 prod（jar）行为一致。
     *
     * @param path 资源路径（相对 assets/gtsr/bqquests/ 的完整 classpath 路径）
     * @return NBT 化的定义，资源缺失或解析失败返回 null
     */
    private static NBTTagCompound readNbtResource(String path) {
        JsonObject json = readJsonResource(path);
        if (json == null) {
            return null;
        }
        validateResourceIds(json);
        resolveRuntimeTargets(json);
        return NBTConverter.JSONtoNBT_Object(json, new NBTTagCompound(), true);
    }

    private static void validateResourceIds(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) validateResourceIds(child);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                String key = entry.getKey();
                for (String id : new String[] { "questIDHigh", "questIDLow", "questLineIDHigh", "questLineIDLow" }) {
                    if (key.equals(id) || key.startsWith(id + ":")) {
                        // Reject alternate type tags too: JSONtoNBT strips suffixes and could
                        // otherwise let a second same-name tag overwrite the checked UUID half.
                        if (!key.equals(id + ":4")) throw new IllegalStateException("Invalid BQ UUID tag: " + key);
                        requireLong(object, key);
                    }
                }
                validateResourceIds(entry.getValue());
            }
        }
    }

    /** Resolve only GTSR's explicitly marked targets after GT has registered its material cells. */
    private static void resolveRuntimeTargets(JsonElement element) {
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) resolveRuntimeTargets(child);
        } else if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("gtsrDimension:8") && "prosperity".equals(
                object.get("gtsrDimension:8")
                    .getAsString())) {
                if (!object.has("taskID:8") || !"bq_standard:location".equals(
                    object.get("taskID:8")
                        .getAsString())) {
                    throw new IllegalStateException("GTSR prosperity dimension marker requires a location task");
                }
                // A disabled/conflicting Prosperity dimension must not turn this into a Nether (-1) task.
                object.addProperty(
                    "dimension:3",
                    Config.prosperityDimId < 0 ? Integer.MAX_VALUE : Config.prosperityDimId);
            }
            if (object.has("OreDict:8")) {
                String ore = object.get("OreDict:8")
                    .getAsString();
                String materialName = switch (ore) {
                    case "cellwastesigh" -> "wastesigh";
                    case "cellthickgrease" -> "thickgrease";
                    case "cellmetalgrit" -> "metalgrit";
                    case "cellumbralmire" -> "umbralmire";
                    case "cellsanzu_residual_steam" -> "sanzu_residual_steam";
                    case "cellwithered_breath" -> "withered_breath";
                    default -> null;
                };
                if (materialName != null) {
                    int count = object.get("Count:3")
                        .getAsInt();
                    Materials material = Materials.get(materialName);
                    ItemStack cell = GTOreDictUnificator.get(OrePrefixes.cell, material, count);
                    FluidStack fluid = cell == null ? null : FluidContainerRegistry.getFluidForFilledItem(cell);
                    if (count <= 0 || fluid == null
                        || fluid.amount != 1000
                        || !materialName.equals(
                            fluid.getFluid()
                                .getName())) {
                        throw new IllegalStateException("GTSR quest cell unavailable or invalid: " + ore);
                    }
                    JsonObject stack = NBTConverter
                        .NBTtoJSON_Compound(cell.writeToNBT(new NBTTagCompound()), new JsonObject(), true);
                    object.addProperty("id:8", Item.itemRegistry.getNameForObject(cell.getItem()));
                    object.add("Damage:2", stack.get("Damage:2"));
                    object.remove("tag:10");
                    if (stack.has("tag:10")) object.add("tag:10", stack.get("tag:10"));
                }
            }
            for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                resolveRuntimeTargets(entry.getValue());
            }
        }
    }

    /**
     * 从 classloader 资源读 JsonObject（Gson 用 MC 1.7.10 自带 com.google.gson，
     * 与 BQ JsonHelper.ReadFromFile 的解析方式对齐：UTF-8 + 32KB 缓冲）。
     *
     * @param path 资源路径
     * @return 解析结果，资源缺失或解析失败返回 null
     */
    private static JsonObject readJsonResource(String path) {
        InputStream is = BqQuestInjector.class.getClassLoader()
            .getResourceAsStream(path);
        if (is == null) {
            GTSteamReborn.LOG.warn("[BQ] 资源不存在: {}", path);
            return null;
        }
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8), 32768)) {
            return new JsonParser().parse(br)
                .getAsJsonObject();
        } catch (Exception e) {
            GTSteamReborn.LOG.error("[BQ] 资源 JSON 解析失败: {}", path, e);
            return null;
        }
    }
}
