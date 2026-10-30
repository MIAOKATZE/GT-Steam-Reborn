package com.miaokatze.gtsr.crossmod.bq;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import com.google.gson.*;
import betterquesting.api.properties.NativeProps;
import betterquesting.api.storage.BQ_Settings;
import betterquesting.api.utils.NBTConverter;
import betterquesting.questing.*;

/** Public inject-path contract, using native BQ 3.8.87 databases, definitions and NBT conversion. */
public final class BqInjectionSafetyRegression {
    private static final long HIGH = 0x4754535200000000L;
    private static final UUID LINE = new UUID(HIGH, 1), QUEST = new UUID(HIGH, 1);
    private static final UUID FOREIGN = new UUID(0x0102030405060708L, 7), PLAYER = new UUID(123, 456);
    private static Path resources;
    private static int checks, failures;
    private static QuestInstance live;
    private static QuestLine line;

    public static void main(String[] args) throws Exception {
        try {
            run(args);
        } finally {
            betterquesting.api2.utils.BQThreadedIO.INSTANCE.shutdown();
            betterquesting.api2.utils.BQThreadedIO.DISK_IO.shutdown();
        }
    }

    private static void run(String[] args) throws Exception {
        resources = Paths.get(args[0]);
        net.minecraft.init.Bootstrap.register();
        BqCompat.detect();
        betterquesting.questing.rewards.RewardRegistry.INSTANCE.register(
            bq_standard.rewards.factory.FactoryRewardQuestCompletion.INSTANCE);
        invalid("missing quest file", i -> remove("quest.json"));
        invalid("missing entry file", i -> remove("entry.json"));
        invalid("missing line file even for existing line", i -> remove("line.json"));
        invalid("missing UUID low", i -> edit("quest.json", q -> q.remove("questIDLow:4")));
        invalid("wrong UUID type", i -> edit("quest.json", q -> {q.remove("questIDLow:4");q.addProperty("questIDLow:8", "1");}));
        invalid("fractional UUID", i -> edit("quest.json", q -> q.addProperty("questIDLow:4", 1.75)));
        invalid("overflow UUID", i -> edit("quest.json", q -> q.add("questIDLow:4", new JsonParser().parse("18446744073709551617"))));
        invalid("shadow UUID type", i -> edit("quest.json", q -> q.addProperty("questIDLow:3", 2)));
        invalid("line manifest mismatch", i -> edit("line.json", q -> q.addProperty("questLineIDLow:4", 9)));
        invalid("entry mismatch", i -> edit("entry.json", q -> q.addProperty("questIDLow:4", 9)));
        invalid("fractional manifest", i -> spec(i).addProperty("idLow", 1.75));
        invalid("overflow order", i -> spec(i).addProperty("orderIndex", Long.MAX_VALUE));
        invalid("duplicate line", i -> i.getAsJsonArray("questLines").add(new JsonParser().parse(spec(i).toString())));
        invalid("duplicate quest entry", i -> spec(i).getAsJsonArray("entries").add(new JsonParser().parse(spec(i).getAsJsonArray("entries").get(0).toString())));
        invalid("late missing second line", i -> {JsonObject second=new JsonParser().parse(spec(i).toString()).getAsJsonObject();second.addProperty("idLow", 2);second.addProperty("lineFile", "missing.json");i.getAsJsonArray("questLines").add(second);});
        invalid("incomplete native prerequisite identity", i -> edit("quest.json", q -> {
            JsonObject requirement = new JsonObject(); requirement.addProperty("questIDHigh:4", HIGH);
            JsonObject requirements = new JsonObject(); requirements.add("0:10", requirement);
            q.add("preRequisites:9", requirements);
        }));
        invalid("empty native prerequisite identity", i -> edit("quest.json", q -> {
            JsonObject requirements = new JsonObject(); requirements.add("0:10", new JsonObject());
            q.add("preRequisites:9", requirements);
        }));
        invalid("wrong native prerequisite element type", i -> edit("quest.json", q -> {
            JsonObject requirements = new JsonObject(); requirements.addProperty("0:8", QUEST.toString());
            q.add("preRequisites:9", requirements);
        }));
        invalid("native registered reward parser", i -> edit("quest.json", q -> completionReward(q, "invalid-native-quest-reference-uuid")));
        reset();
        BqQuestInjector.inject();
        check("valid pack stamps", stamp());
        check("owned refresh preserves completed/claimed", live.isComplete(PLAYER) && claimed(live));
        check("owned definition refreshed", "replacement".equals(live.getProperty(NativeProps.NAME)));
        check("idempotent rerun", QuestDatabase.INSTANCE.size()==1 && line.size()==1);
        BqQuestInjector.inject();
        check("idempotent native database", QuestDatabase.INSTANCE.get(QUEST)==live && line.size()==1);

        reset(); QuestInstance foreign=seed(FOREIGN, line); JsonObject empty=index();spec(empty).add("entries",new JsonArray());writeIndex(empty);BqQuestInjector.inject();
        check("explicit empty removes stale owned quest", QuestDatabase.INSTANCE.get(QUEST)==null && line.get(QUEST)==null);
        check("explicit empty preserves foreign quest and entry", QuestDatabase.INSTANCE.get(FOREIGN)==foreign && line.get(FOREIGN)!=null);

        reset(); QuestLine shared=new QuestLine();QuestLineDatabase.INSTANCE.put(FOREIGN,shared);shared.put(QUEST,new QuestLineEntry(new NBTTagCompound()));empty=index();spec(empty).add("entries",new JsonArray());writeIndex(empty);BqQuestInjector.inject();
        check("shared quest survives global deletion", QuestDatabase.INSTANCE.get(QUEST)==live && shared.get(QUEST)!=null && line.get(QUEST)==null);

        reset(); foreign=seed(FOREIGN,line);NBTTagCompound before=foreign.writeToNBT(new NBTTagCompound());NBTTagCompound progress=foreign.writeProgressToNBT(new NBTTagCompound(),null);
        edit("quest.json",q->{q.addProperty("questIDHigh:4",FOREIGN.getMostSignificantBits());q.addProperty("questIDLow:4",FOREIGN.getLeastSignificantBits());});edit("entry.json",q->{q.addProperty("questIDHigh:4",FOREIGN.getMostSignificantBits());q.addProperty("questIDLow:4",FOREIGN.getLeastSignificantBits());});BqQuestInjector.inject();
        check("existing foreign definition unchanged",before.equals(foreign.writeToNBT(new NBTTagCompound())));
        check("existing foreign progress unchanged",progress.equals(foreign.writeProgressToNBT(new NBTTagCompound(),null)));

        reset(); QuestDatabase.INSTANCE.clear();QuestLineDatabase.INSTANCE.clear();JsonObject custom=index();spec(custom).addProperty("idHigh",FOREIGN.getMostSignificantBits());spec(custom).addProperty("idLow",7);writeIndex(custom);edit("line.json",q->{q.addProperty("questLineIDHigh:4",FOREIGN.getMostSignificantBits());q.addProperty("questLineIDLow:4",7);});edit("quest.json",q->{q.addProperty("questIDHigh:4",FOREIGN.getMostSignificantBits());q.addProperty("questIDLow:4",7);});edit("entry.json",q->{q.addProperty("questIDHigh:4",FOREIGN.getMostSignificantBits());q.addProperty("questIDLow:4",7);});BqQuestInjector.inject();
        check("custom valid UUID pack get-or-create",QuestDatabase.INSTANCE.get(FOREIGN)!=null && QuestLineDatabase.INSTANCE.get(FOREIGN).get(FOREIGN)!=null && stamp());
        QuestLine customLine=(QuestLine)QuestLineDatabase.INSTANCE.get(FOREIGN);QuestInstance customQuest=(QuestInstance)QuestDatabase.INSTANCE.get(FOREIGN);
        seed(QUEST,customLine);customQuest.setProperty(NativeProps.NAME,"custom-live");
        QuestLineDatabase.INSTANCE.put(new UUID(42,42),new QuestLine());QuestLineDatabase.INSTANCE.setOrderIndex(new UUID(42,42),0);QuestLineDatabase.INSTANCE.setOrderIndex(FOREIGN,1);
        custom= index();spec(custom).addProperty("orderIndex",0);spec(custom).add("entries",new JsonArray());writeIndex(custom);BqQuestInjector.inject();
        check("existing custom line keeps order and entries",QuestLineDatabase.INSTANCE.getOrderIndex(FOREIGN)==1 && customLine.size()==2 && QuestDatabase.INSTANCE.get(QUEST)!=null);

        reset();JsonObject moved=index();JsonObject old=spec(moved);JsonObject destination=new JsonParser().parse(old.toString()).getAsJsonObject();destination.addProperty("idLow",2);destination.addProperty("lineFile","line2.json");JsonObject l2=read("line.json");l2.addProperty("questLineIDLow:4",2);write("line2.json",l2);old.add("entries",new JsonArray());moved.getAsJsonArray("questLines").add(destination);writeIndex(moved);BqQuestInjector.inject();
        check("moving quest to later line retains progress",QuestDatabase.INSTANCE.get(QUEST)==live && live.isComplete(PLAYER) && claimed(live) && line.get(QUEST)==null && QuestLineDatabase.INSTANCE.get(new UUID(HIGH,2)).get(QUEST)!=null);

        reset();QuestLineDatabase.INSTANCE.put(new UUID(0x4754574e00000000L,1),new QuestLine());QuestLineDatabase.INSTANCE.put(new UUID(0x4754495400000000L,1),new QuestLine());BqQuestInjector.inject();
        check("front order GTSR GTSWN GTIT",QuestLineDatabase.INSTANCE.getOrderIndex(LINE)==0 && QuestLineDatabase.INSTANCE.getOrderIndex(new UUID(0x4754574e00000000L,1))==1 && QuestLineDatabase.INSTANCE.getOrderIndex(new UUID(0x4754495400000000L,1))==2);

        reset();JsonObject noLines=new JsonObject();noLines.add("questLines",new JsonArray());writeIndex(noLines);BqQuestInjector.inject();
        check("empty index keeps prior no-op semantics",QuestDatabase.INSTANCE.get(QUEST)==live && line.get(QUEST)!=null && stamp());
        reset();
        NBTTagCompound saved = savedProgress(QUEST, true);
        QuestDatabase.INSTANCE.remove(QUEST); // The definition was absent after BQ's default reload.
        writeProgressFile("QuestProgress.json", saved);
        BqQuestInjector.inject();
        check("legacy progress restored with no per-player directory", current().isComplete(PLAYER) && claimed(current()));

        reset();
        writeProgressFile("QuestProgress.json", savedProgress(QUEST, false));
        writeProgressFile("QuestProgress/" + PLAYER + ".json", savedProgress(QUEST, true));
        QuestDatabase.INSTANCE.remove(QUEST);
        BqQuestInjector.inject();
        check("per-player progress supersedes legacy file", current().isComplete(PLAYER) && claimed(current()));

        reset();
        QuestInstance untouched = seed(FOREIGN, line);
        writeProgressFile("QuestProgress/" + PLAYER + ".json", savedProgress(FOREIGN, false));
        BqQuestInjector.inject();
        check("progress replay does not overwrite foreign live claimed state", claimed(untouched));

        reset();
        JsonObject malformed = NBTConverter.NBTtoJSON_Compound(savedProgress(QUEST, false), new JsonObject(), true);
        malformed.getAsJsonObject("questProgress:9").getAsJsonObject("0:10").addProperty("questIDLow:4", 1.75);
        writeWorld("QuestProgress/" + PLAYER + ".json", malformed);
        BqQuestInjector.inject();
        check("fractional progress UUID cannot overwrite claimed state", claimed(live));

        reset();
        saved = savedProgress(QUEST, false);
        saved.getTagList("questProgress", 10).getCompoundTagAt(0).setInteger("questIDLow", 1);
        writeProgressFile("QuestProgress/" + PLAYER + ".json", saved);
        BqQuestInjector.inject();
        check("wrong-typed progress UUID cannot overwrite claimed state", claimed(live));

        reset();
        saved = savedProgress(QUEST, true);
        QuestDatabase.INSTANCE.remove(QUEST);
        writeWorld("QuestProgress/broken.json", new JsonObject());
        writeProgressFile("QuestProgress/" + PLAYER + ".json", saved);
        BqQuestInjector.inject();
        check("valid progress restored despite malformed sibling file", claimed(current()));

        reset();
        QuestInstance unlisted = seed(new UUID(HIGH, 99), new QuestLine());
        writeProgressFile("QuestProgress/" + PLAYER + ".json", savedProgress(new UUID(HIGH, 99), false));
        BqQuestInjector.inject();
        check("unlisted owned-namespace progress is not replayed", claimed(unlisted));

        reset();
        saved = savedProgress(QUEST, false);
        NBTTagCompound later = saved.getTagList("questProgress", 10).getCompoundTagAt(0);
        later = (NBTTagCompound) later.copy(); later.removeTag("questIDLow");
        saved.getTagList("questProgress", 10).appendTag(later);
        writeProgressFile("QuestProgress/" + PLAYER + ".json", saved);
        BqQuestInjector.inject();
        check("late malformed progress identity prevents earlier record replay", claimed(live));

        reset();
        writeProgressFile("QuestProgress/" + PLAYER + ".json", savedProgress(QUEST, false));
        BqQuestInjector.inject();
        check("owned live claimed state is not revoked by older disk progress", claimed(live));

        reset();
        writeProgressFile("QuestProgress/" + PLAYER + ".json", savedProgress(QUEST, true));
        live.resetUser(PLAYER, true);
        BqQuestInjector.inject();
        check("owned live reset is not undone by older disk progress", !live.isComplete(PLAYER));

        reset();
        QuestDatabase.INSTANCE.remove(QUEST);
        malformed = NBTConverter.NBTtoJSON_Compound(savedProgress(QUEST, true), new JsonObject(), true);
        malformed.getAsJsonObject("questProgress:9").getAsJsonObject("0:10").addProperty("questIDLow:4", 1.75);
        writeWorld("QuestProgress/" + PLAYER + ".json", malformed);
        BqQuestInjector.inject();
        check("new quest rejects fractional progress UUID before replay", !current().isComplete(PLAYER));

        reset();
        QuestDatabase.INSTANCE.remove(QUEST);
        saved = savedProgress(QUEST, true);
        saved.getTagList("questProgress", 10).getCompoundTagAt(0).setInteger("questIDLow", 1);
        writeProgressFile("QuestProgress/" + PLAYER + ".json", saved);
        BqQuestInjector.inject();
        check("new quest rejects wrong-typed progress UUID before replay", !current().isComplete(PLAYER));

        reset();
        QuestDatabase.INSTANCE.remove(QUEST);
        saved = savedProgress(QUEST, true);
        later = (NBTTagCompound) saved.getTagList("questProgress", 10).getCompoundTagAt(0).copy();
        later.removeTag("questIDLow"); saved.getTagList("questProgress", 10).appendTag(later);
        writeProgressFile("QuestProgress/" + PLAYER + ".json", saved);
        BqQuestInjector.inject();
        check("new quest rejects whole progress file with late malformed identity", !current().isComplete(PLAYER));

        reset();
        edit("quest.json", q -> completionReward(q, QUEST.toString()));
        BqQuestInjector.inject();
        check("valid registered native reward survives preflight and refresh", stamp()
            && live.getRewards().getValue(0) instanceof bq_standard.rewards.RewardQuestCompletion
            && claimed(live));

        reset();
        QuestDatabase.INSTANCE.clear(); QuestLineDatabase.INSTANCE.clear();
        edit("quest.json", q -> completionReward(q, "invalid-native-quest-reference-uuid"));
        BqQuestInjector.inject();
        check("native reward failure does not leave a newly created line", QuestLineDatabase.INSTANCE.size() == 0
            && QuestDatabase.INSTANCE.size() == 0 && !stamp());

        reset();
        JsonObject laterPack = index();
        JsonObject laterLine = new JsonParser().parse(spec(laterPack).toString()).getAsJsonObject();
        laterLine.addProperty("idLow", 2); laterLine.addProperty("lineFile", "line2.json");
        JsonObject laterLineTag = read("line.json"); laterLineTag.addProperty("questLineIDLow:4", 2);
        write("line2.json", laterLineTag);
        JsonObject laterQuest = read("quest.json"); laterQuest.addProperty("questIDLow:4", 2);
        completionReward(laterQuest, "invalid-native-quest-reference-uuid"); write("quest2.json", laterQuest);
        JsonObject laterEntry = read("entry.json"); laterEntry.addProperty("questIDLow:4", 2); write("entry2.json", laterEntry);
        JsonObject laterSpec = laterLine.getAsJsonArray("entries").get(0).getAsJsonObject();
        laterSpec.addProperty("questFile", "quest2.json"); laterSpec.addProperty("entryFile", "entry2.json");
        laterPack.getAsJsonArray("questLines").add(laterLine); writeIndex(laterPack);
        before = live.writeToNBT(new NBTTagCompound()); progress = live.writeProgressToNBT(new NBTTagCompound(), null);
        BqQuestInjector.inject();
        check("late native reward failure preserves earlier live definition and progress", before.equals(live.writeToNBT(new NBTTagCompound()))
            && progress.equals(live.writeProgressToNBT(new NBTTagCompound(), null)));
        check("late native reward failure preserves line database and stamp", QuestLineDatabase.INSTANCE.size() == 1
            && QuestDatabase.INSTANCE.size() == 1 && !stamp());

        reset();
        writeProgressFile("QuestProgress/" + PLAYER + ".json", savedProgress(QUEST, false));
        nativeDefaultReload(true);
        BqQuestInjector.inject();
        check("native default reload retained quest keeps its restored live claim", claimed(current()));

        reset();
        writeProgressFile("QuestProgress/" + PLAYER + ".json", savedProgress(QUEST, true));
        nativeDefaultReload(false);
        BqQuestInjector.inject();
        check("native default reload missing quest recovers disk progress", claimed(current()));

        reset();
        NBTTagCompound definition = NBTConverter.JSONtoNBT_Object(read("quest.json"), new NBTTagCompound(), true);
        NBTTagCompound requirement = new NBTTagCompound();
        NBTConverter.UuidValueType.QUEST.writeId(FOREIGN, requirement);
        NBTTagList requirements = new NBTTagList(); requirements.appendTag(requirement);
        definition.setTag("preRequisites", requirements);
        write("quest.json", NBTConverter.NBTtoJSON_Compound(definition, new JsonObject(), true));
        BqQuestInjector.inject();
        check("valid external UUID prerequisite remains accepted", stamp() && live.getRequirements().contains(FOREIGN));

        reset();
        NBTTagCompound legacyDefinition = NBTConverter.JSONtoNBT_Object(read("quest.json"), new NBTTagCompound(), true);
        legacyDefinition.setIntArray("preRequisites", new int[] { 42 });
        write("quest.json", NBTConverter.NBTtoJSON_Compound(legacyDefinition, new JsonObject(), true));
        BqQuestInjector.inject();
        check("native legacy integer prerequisite format remains accepted", stamp()
            && live.getRequirements().contains(betterquesting.api.utils.UuidConverter.convertLegacyId(42)));

        checkNativeParserFamilies();

        System.out.println("BQ native checks="+checks+" failures="+failures);
        if(failures!=0)throw new AssertionError(failures+" BQ safety regressions");
    }

    private static void completionReward(JsonObject quest, String target) {
        JsonObject reward = new JsonObject();
        reward.addProperty("rewardID:8", "bq_standard:questcompletion");
        reward.addProperty("index:3", 0);
        reward.addProperty("questID:8", target);
        JsonObject rewards = new JsonObject(); rewards.add("0:10", reward);
        quest.add("rewards:9", rewards);
    }

    /** The same native snapshot / definition reload / progress restore used by BQ's defaults command. */
    private static void nativeDefaultReload(boolean retainQuest) {
        NBTTagList definitions = retainQuest ? QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null) : new NBTTagList();
        NBTTagList progress = QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);
        QuestDatabase.INSTANCE.readFromNBT(definitions, false);
        QuestDatabase.INSTANCE.readProgressFromNBT(progress, false);
    }

    @SuppressWarnings("unchecked")
    private static void checkNativeParserFamilies() throws Exception {
        reset();
        NBTTagList definitions = QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null);
        NBTTagList progress = QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null);
        NBTTagList lines = QuestLineDatabase.INSTANCE.writeToNBT(new NBTTagList(), null);
        for (String family : new String[] { "TaskBlockBreak", "TaskCheckbox", "TaskCrafting", "TaskFluid",
            "TaskHunt", "TaskInteractEntity", "TaskInteractItem", "TaskLocation", "TaskMeeting",
            "TaskOptionalRetrieval", "TaskRetrieval", "TaskScoreboard", "TaskXP", "RewardChoice",
            "RewardCommand", "RewardItem", "RewardQuestCompletion", "RewardScoreboard", "RewardXP" }) {
            QuestInstance definition = new QuestInstance();
            String group = family.startsWith("Task") ? "tasks" : "rewards";
            Object factory = Class.forName("bq_standard." + group + ".factory.Factory" + family).getField("INSTANCE").get(null);
            if (family.startsWith("Task")) {
                betterquesting.api2.registry.IFactoryData<betterquesting.api.questing.tasks.ITask, NBTTagCompound> taskFactory =
                    (betterquesting.api2.registry.IFactoryData<betterquesting.api.questing.tasks.ITask, NBTTagCompound>) factory;
                betterquesting.questing.tasks.TaskRegistry.INSTANCE.register(taskFactory);
                definition.getTasks().add(0, taskFactory.createNew());
            } else {
                betterquesting.api2.registry.IFactoryData<betterquesting.api.questing.rewards.IReward, NBTTagCompound> rewardFactory =
                    (betterquesting.api2.registry.IFactoryData<betterquesting.api.questing.rewards.IReward, NBTTagCompound>) factory;
                if (!family.equals("RewardQuestCompletion")) betterquesting.questing.rewards.RewardRegistry.INSTANCE.register(rewardFactory);
                definition.getRewards().add(0, rewardFactory.createNew());
            }
            NBTTagCompound tag = definition.writeToNBT(new NBTTagCompound());
            new QuestInstance().readFromNBT((NBTTagCompound) tag.copy());
            new QuestInstance().readFromNBT((NBTTagCompound) tag.copy());
            check("native detached parser has no quest database writes: " + family,
                definitions.equals(QuestDatabase.INSTANCE.writeToNBT(new NBTTagList(), null))
                    && progress.equals(QuestDatabase.INSTANCE.writeProgressToNBT(new NBTTagList(), null))
                    && lines.equals(QuestLineDatabase.INSTANCE.writeToNBT(new NBTTagList(), null)));
        }
    }

    private static QuestInstance current() {
        return (QuestInstance) QuestDatabase.INSTANCE.get(QUEST);
    }

    private static boolean claimed(QuestInstance quest) {
        NBTTagCompound completion = quest.getCompletionInfo(PLAYER);
        return completion != null && completion.getBoolean("claimed");
    }
    private static NBTTagCompound savedProgress(UUID questId, boolean claimed) {
        QuestInstance quest = new QuestInstance();
        quest.setComplete(PLAYER, 123);
        if (claimed) quest.setClaimed(PLAYER, 124);
        NBTTagCompound entry = quest.writeProgressToNBT(new NBTTagCompound(), null);
        NBTConverter.UuidValueType.QUEST.writeId(questId, entry);
        NBTTagList entries = new NBTTagList(); entries.appendTag(entry);
        NBTTagCompound root = new NBTTagCompound(); root.setTag("questProgress", entries); return root;
    }
    private static void writeProgressFile(String file, NBTTagCompound nbt) throws Exception {
        writeWorld(file, NBTConverter.NBTtoJSON_Compound(nbt, new JsonObject(), true));
    }
    private static void writeWorld(String file, JsonObject json) throws Exception {
        Path target = BQ_Settings.curWorldDir.toPath().resolve(file);
        Files.createDirectories(target.getParent()); Files.write(target, json.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static void invalid(String name, Consumer<JsonObject> mutate) throws Exception {
        reset();JsonObject index=index();mutate.accept(index);writeIndex(index);
        NBTTagCompound before=live.writeToNBT(new NBTTagCompound());NBTTagCompound progress=live.writeProgressToNBT(new NBTTagCompound(),null);
        BqQuestInjector.inject();
        check(name+" preserves database and no stamp",QuestDatabase.INSTANCE.size()==1 && QuestDatabase.INSTANCE.get(QUEST)==live && line.get(QUEST)!=null && !stamp());
        check(name+" preserves definition/progress",before.equals(live.writeToNBT(new NBTTagCompound())) && progress.equals(live.writeProgressToNBT(new NBTTagCompound(),null)));
    }
    private static void reset() throws Exception {
        QuestDatabase.INSTANCE.clear();QuestLineDatabase.INSTANCE.clear();
        Path worlds = resources.resolve(".worlds"); Files.createDirectories(worlds);
        BQ_Settings.curWorldDir=Files.createTempDirectory(worlds, "gtsr-bq-world-").toFile();
        line=new QuestLine();QuestLineDatabase.INSTANCE.put(LINE,line);live=seed(QUEST,line);
        JsonObject l=new JsonObject();l.addProperty("questLineIDHigh:4",HIGH);l.addProperty("questLineIDLow:4",1);write("line.json",l);
        QuestInstance replacement=new QuestInstance();replacement.setProperty(NativeProps.NAME,"replacement");NBTTagCompound q=replacement.writeToNBT(new NBTTagCompound());NBTConverter.UuidValueType.QUEST.writeId(QUEST,q);write("quest.json",NBTConverter.NBTtoJSON_Compound(q,new JsonObject(),true));
        NBTTagCompound e=new NBTTagCompound();NBTConverter.UuidValueType.QUEST.writeId(QUEST,e);e.setInteger("x",12);e.setInteger("y",24);write("entry.json",NBTConverter.NBTtoJSON_Compound(e,new JsonObject(),true));
        JsonObject entry=new JsonObject();entry.addProperty("questFile","quest.json");entry.addProperty("entryFile","entry.json");JsonArray entries=new JsonArray();entries.add(entry);
        JsonObject spec=new JsonObject();spec.addProperty("idHigh",HIGH);spec.addProperty("idLow",1);spec.addProperty("orderIndex",900);spec.addProperty("lineFile","line.json");spec.add("entries",entries);JsonArray lines=new JsonArray();lines.add(spec);JsonObject index=new JsonObject();index.add("questLines",lines);writeIndex(index);
    }
    private static QuestInstance seed(UUID id,QuestLine target){QuestInstance q=new QuestInstance();q.setProperty(NativeProps.NAME,"original");q.setComplete(PLAYER,123);q.setClaimed(PLAYER,124);QuestDatabase.INSTANCE.put(id,q);target.put(id,new QuestLineEntry(new NBTTagCompound()));return q;}
    private static JsonObject index(){return read("assets/gtsr/bqquests/index.json");}
    private static JsonObject spec(JsonObject index){return index.getAsJsonArray("questLines").get(0).getAsJsonObject();}
    private static boolean stamp(){return new java.io.File(BQ_Settings.curWorldDir,"gtsr-injected.json").isFile();}
    private static void writeIndex(JsonObject index){write("assets/gtsr/bqquests/index.json",index);}
    private static JsonObject read(String path){try{return new JsonParser().parse(new String(Files.readAllBytes(resources.resolve(path)),StandardCharsets.UTF_8)).getAsJsonObject();}catch(Exception e){throw new RuntimeException(e);}}
    private static void edit(String path,Consumer<JsonObject> edit){JsonObject j=read(path);edit.accept(j);write(path,j);}
    private static void remove(String path){try{Files.delete(resources.resolve(path));}catch(Exception e){throw new RuntimeException(e);}}
    private static void write(String path,JsonObject json){try{Path target=resources.resolve(path);Files.createDirectories(target.getParent());Files.write(target,json.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception e){throw new RuntimeException(e);}}
    private static void check(String name,boolean pass){checks++;if(!pass)failures++;System.out.println((pass?"PASS ":"FAIL ")+name);}
}
