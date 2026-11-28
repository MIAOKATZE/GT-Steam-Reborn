// SPDX-License-Identifier: AGPL-3.0-only
package com.miaokatze.gtsr.common.dimension.prosperity.industrial;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTSizeTracker;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.chunk.storage.RegionFile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.miaokatze.miaokatzecore.api.io.AtomicFilePublisher;

/** Explicit offline copy only. Native NBT codecs; no World, Item registry, server or chunk loading. */
public final class MaterialIdOfflineCopy {

    static final long MAX_FILE = 256L * 1024 * 1024;
    static final long MAX_NBT = 16L * 1024 * 1024;
    static final String MARKER = ".gtsr-material-copy.json";
    static final int[] NATIVE_277_IDS = { 0, 1, 2, 3, 4, 5, 6, 8, 9, 10, 12, 13, 14, 17, 18, 19, 20, 21, 22, 23, 24, 25,
        26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37, 39, 43, 44, 45, 47, 48, 52, 54, 55, 56, 57, 58, 59, 62, 63, 64,
        65, 66, 67, 68, 69, 70, 71, 72, 73, 74, 75, 76, 77, 78, 80, 81, 83, 84, 85, 86, 87, 89, 90, 93, 94, 96, 97, 98,
        100, 101, 103, 106, 109, 110, 111, 112, 113, 114, 115, 116, 118, 119, 120, 121, 122, 123, 124, 125, 126, 127,
        129, 130, 131, 132, 133, 134, 135, 136, 137, 138, 139, 140, 141, 142, 143, 144, 145, 146, 147, 148, 149, 150,
        151, 152, 153, 154, 155, 156, 157, 158, 159, 160, 161, 162, 163, 164, 165, 166, 167, 168, 169, 170, 171, 172,
        173, 174, 175, 176, 177, 178, 179, 180, 181, 182, 183, 184, 185, 186, 187, 188, 189, 190, 191, 192, 201, 202,
        203, 204, 205, 206, 207, 208, 209, 232, 233, 234, 241, 242, 243, 244, 245, 299, 300, 301, 302, 303, 304, 305,
        306, 307, 308, 309, 310, 311, 312, 313, 314, 315, 316, 317, 318, 319, 320, 321, 322, 323, 324, 325, 326, 327,
        328, 329, 330, 331, 333, 334, 335, 336, 337, 338, 339, 340, 341, 342, 343, 344, 345, 346, 347, 348, 349, 350,
        351, 352, 353, 354, 355, 356, 357, 358, 359, 360, 361, 362, 363, 364, 365, 366, 367, 368, 369, 370, 371, 372,
        373, 374, 375, 376, 377, 378, 379, 380, 381, 382, 383, 384, 385, 386, 387, 388, 389, 390, 391, 392, 393, 394,
        395, 396, 397, 398, 399, 400, 401, 402, 403, 404, 405, 406, 407, 408, 409, 410, 411, 412, 413, 414, 415, 416,
        417, 425, 460, 470, 471, 472, 473, 485, 488, 489, 493, 494, 495, 496, 497, 500, 501, 502, 503, 504, 505, 506,
        507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 526, 527, 528,
        529, 530, 531, 532, 533, 534, 535, 536, 537, 538, 540, 541, 542, 543, 544, 545, 546, 547, 548, 549, 550, 551,
        552, 553, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 567, 568, 569, 570, 576, 581, 582,
        583, 584, 585, 586, 587, 588, 589, 590, 591, 592, 593, 594, 595, 596, 597, 598, 599, 600, 602, 603, 604, 605,
        606, 607, 608, 609, 610, 611, 612, 613, 614, 615, 616, 617, 618, 619, 620, 621, 622, 623, 624, 625, 626, 627,
        628, 629, 630, 631, 632, 633, 634, 635, 636, 637, 638, 639, 640, 641, 642, 643, 644, 645, 646, 647, 648, 649,
        650, 651, 652, 653, 654, 655, 656, 657, 658, 659, 660, 661, 662, 663, 664, 665, 666, 667, 668, 669, 670, 671,
        672, 673, 674, 675, 676, 677, 678, 679, 680, 681, 682, 683, 684, 685, 686, 687, 688, 689, 690, 691, 692, 693,
        694, 695, 696, 697, 698, 699, 700, 701, 702, 703, 704, 705, 706, 707, 708, 709, 710, 711, 712, 713, 714, 715,
        716, 717, 718, 719, 720, 721, 722, 723, 724, 725, 726, 727, 728, 729, 730, 731, 732, 733, 734, 735, 736, 737,
        738, 739, 740, 741, 742, 743, 744, 745, 746, 747, 748, 749, 750, 751, 752, 753, 754, 755, 756, 757, 758, 759,
        760, 761, 762, 763, 764, 765, 766, 767, 768, 769, 770, 771, 772, 773, 774, 775, 776, 777, 778, 779, 780, 781,
        782, 783, 784, 785, 786, 787, 788, 789, 790, 791, 792, 793, 794, 795, 796, 797, 798, 799, 800, 801, 802, 803,
        804, 805, 806, 807, 808, 809, 810, 811, 812, 813, 814, 815, 816, 817, 818, 819, 820, 821, 822, 823, 824, 825,
        826, 827, 828, 829, 830, 831, 832, 833, 834, 835, 836, 837, 838, 839, 840, 841, 842, 843, 844, 845, 846, 847,
        848, 849, 850, 851, 853, 854, 855, 856, 858, 861, 862, 865, 867, 868, 870, 871, 872, 873, 874, 876, 877, 878,
        879, 880, 881, 882, 883, 884, 885, 886, 887, 888, 889, 890, 891, 892, 893, 894, 895, 896, 897, 898, 899, 900,
        901, 902, 903, 904, 905, 906, 907, 908, 909, 910, 911, 912, 913, 914, 915, 916, 917, 918, 919, 920, 921, 922,
        923, 924, 925, 926, 927, 928, 929, 930, 931, 932, 933, 934, 935, 936, 937, 938, 939, 940, 941, 942, 943, 944,
        945, 946, 947, 948, 949, 951, 952, 956, 966, 967, 968, 969, 970, 971, 972, 973, 974, 975, 976, 977, 978, 979,
        980, 981, 982, 983, 984, 985, 986, 987, 988, 989, 990, 991, 992, 993, 994, 995, 996, 997, 998, 999 };
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting()
        .create();

    public static final class Rule {

        public String key, registryName, prefix;
        public int numericItemId, prefixIndex, oldId, newId;
    }

    public static final class Plan {

        public String schema, sourceGt, sourceGtsr, targetGt, targetGtsr;
        public String gtArtifact, gtArtifactSha256, oldMapping, oldMappingSha256, newMapping, newMappingSha256;
        public String registryEvidence, registryEvidenceSha256, occupancyEvidence, occupancyEvidenceSha256;
        public String sourceGtsrArtifact, sourceGtsrArtifactSha256, targetGtsrArtifact, targetGtsrArtifactSha256;
        public String mode;
        public List<Rule> rules;
        // v2 only: explicit legacy-cell adapters, never part of the production jar.
        public String targetGtArtifact, targetGtArtifactSha256, sourceLevelData, sourceLevelDataSha256;
        public String targetLevelData, targetLevelDataSha256, semanticEvidence, semanticEvidenceSha256;
        public String aeArtifact, aeArtifactSha256, ic2Artifact, ic2ArtifactSha256;
        public String sourceHistory;
        public String targetFingerprint;
        public List<MaterialIdFluidCellCopy.FluidRule> fluidRules;
    }

    static final class Context {

        final Plan plan;
        final Map<Integer, String> registry;
        final List<String> operations = new ArrayList<>();
        final Map<String, Long> quantities = new TreeMap<>();
        long nodes;
        MaterialIdFluidCellCopy.State fluids;

        Context(Plan plan, Map<Integer, String> registry) {
            this.plan = plan;
            this.registry = registry;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 5 || !(args[0].equals("dry-run") || args[0].equals("apply") || args[0].equals("resume")))
            throw new IllegalArgumentException(
                "Usage: dry-run|apply|resume SOURCE NEW_TARGET PLAN_JSON REVIEW_JSON; apply/resume require matching saved dry-run REVIEW_JSON");
        Path source = Paths.get(args[1])
            .toAbsolutePath()
            .normalize();
        Path target = Paths.get(args[2])
            .toAbsolutePath()
            .normalize();
        Path planFile = Paths.get(args[3])
            .toAbsolutePath()
            .normalize();
        Path reviewFile = Paths.get(args[4])
            .toAbsolutePath()
            .normalize();
        boolean dry = args[0].equals("dry-run"), resume = args[0].equals("resume");
        rejectOverlap(source, target);
        if (reviewFile.startsWith(source) || reviewFile.startsWith(target))
            throw new IOException("Review must be outside source and target");
        Path reviewAncestor = reviewFile;
        while (reviewAncestor != null && !Files.exists(reviewAncestor, LinkOption.NOFOLLOW_LINKS))
            reviewAncestor = reviewAncestor.getParent();
        if (reviewAncestor == null || !reviewAncestor.toRealPath()
            .equals(reviewAncestor)) throw new IOException("Review alias/symbolic-link ancestor rejected");
        Plan plan = JSON.fromJson(new String(Files.readAllBytes(planFile), StandardCharsets.UTF_8), Plan.class);
        Context context = validated(plan, planFile.getParent());
        if (context.fluids != null) MaterialIdFluidCellCopy.bindSource(source, context);
        String planHash = hash(planFile);
        Map<String, String> before = inventory(source);
        if (before.containsKey(MARKER))
            throw new IOException("Source is already a migration output; repeated conversion rejected");
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("source", source.toString());
        report.put("target", target.toString());
        report.put("planSha256", planHash);
        report.put("plan", plan);
        report.put("sourceFiles", before);
        report.put("status", "DRY_RUN_REVIEW_REQUIRED");
        report.put("worldReady", false);
        report.put(
            "support",
            context.fluids == null
                ? "v1 native same-item Damage migration; AE and GT delegates blocked. No native reopen/save verification."
                : "v2 reviewed MC/GT/AE legacy adapters, exact per-location semantic evidence; unknown storage blocked. No game reopen/save verification.");
        if (!dry) {
            if (!Files.isRegularFile(reviewFile)) throw new IOException("A saved dry-run review is required");
            @SuppressWarnings("unchecked")
            Map<String, Object> review = JSON
                .fromJson(new String(Files.readAllBytes(reviewFile), StandardCharsets.UTF_8), Map.class);
            if (!source.toString()
                .equals(review.get("source"))
                || !target.toString()
                    .equals(review.get("target"))
                || !planHash.equals(review.get("planSha256"))
                || !before.equals(review.get("sourceFiles"))
                || !"DRY_RUN_REVIEW_REQUIRED".equals(review.get("status")))
                throw new IOException("Dry-run inputs changed or dry-run did not succeed");
            if (Files.exists(target)) {
                if (!resume || !Files.isRegularFile(target.resolve(MARKER))) throw new IOException(
                    "Target must be a new directory; only matching incomplete outputs can resume");
                @SuppressWarnings("unchecked")
                Map<String, Object> previous = JSON.fromJson(
                    new String(Files.readAllBytes(target.resolve(MARKER)), StandardCharsets.UTF_8),
                    Map.class);
                if (!source.toString()
                    .equals(previous.get("source")) || !planHash.equals(previous.get("planSha256"))
                    || !before.equals(previous.get("sourceFiles"))
                    || !"INCOMPLETE".equals(previous.get("status")))
                    throw new IOException("Resume marker/source/plan mismatch or already converted");
                Map<String, String> existing = inventory(target);
                for (String path : existing.keySet()) if (!path.equals(MARKER) && !before.containsKey(path))
                    throw new IOException("Unexpected resume file: " + path);
            } else Files.createDirectory(target);
            report.put("status", "INCOMPLETE");
            publishJson(target.resolve(MARKER), report);
        }
        // Cooperative OS lock only: MC 1.7 session.lock is a timestamp file, not a held OS lock.
        // The reviewed-offline-copy plan is an explicit operator assertion that this is a detached stopped copy.
        Path session = source.resolve("session.lock");
        try (FileChannel channel = Files.exists(session) ? FileChannel.open(session, StandardOpenOption.READ) : null;
            FileLock lock = channel == null ? null : channel.tryLock(0L, Long.MAX_VALUE, true)) {
            if (channel != null && lock == null) throw new IOException("Source world is in use");
            for (String relative : before.keySet()) {
                if (Thread.currentThread()
                    .isInterrupted()) throw new IOException("Migration cancelled; output remains INCOMPLETE");
                Path input = source.resolve(relative), output = target.resolve(relative);
                if (!hash(input).equals(before.get(relative))) throw new IOException("Source changed: " + relative);
                if (relative.equals("session.lock")) {
                    if (!dry) copyAtomic(output, Files.readAllBytes(input));
                    continue;
                }
                if (relative.endsWith(".mca")) region(input, output, relative, context, dry);
                else if (relative.endsWith(".dat") || relative.endsWith(".dat_old") || relative.endsWith(".nbt")) {
                    byte[] raw = Files.readAllBytes(input);
                    boolean gzip = raw.length > 2 && (raw[0] & 255) == 31 && (raw[1] & 255) == 139;
                    NBTTagCompound root = decode(raw, gzip);
                    rewrite(root, relative, context, 0);
                    if (!dry) {
                        byte[] encoded = encode(root, gzip);
                        if (!root.equals(decode(encoded, gzip)))
                            throw new IOException("NBT round-trip mismatch: " + relative);
                        copyAtomic(output, encoded);
                    }
                } else throw new IOException("Unsupported file/encoding: " + relative + "; complete migration blocked");
            }
            if (!inventory(source).equals(before) || !hash(planFile).equals(planHash))
                throw new IOException("Source/plan changed during conversion");
            validated(plan, planFile.getParent()); // Also recheck mapping, registry, artifact and occupancy evidence.
            if (context.fluids != null) MaterialIdFluidCellCopy.finish(context, report);
            report.put("operations", context.operations);
            report.put("targetQuantities", context.quantities);
            if (dry) {
                if (Files.exists(reviewFile)) throw new IOException("Review already exists; choose a new review file");
                publishJson(reviewFile, report);
            } else {
                Map<String, String> outputFiles = inventory(target);
                outputFiles.remove(MARKER);
                report.put("outputFiles", outputFiles);
                report.put("status", "COPY_CONVERTED_REQUIRES_NATIVE_REOPEN");
                publishJson(target.resolve(MARKER), report);
                if (context.fluids != null) MaterialIdFluidCellCopy.publishAdmission(target, reviewFile, context);
            }
        }
        System.out.println(
            "Material copy status=" + report.get("status")
                + ", files="
                + before.size()
                + ", operations="
                + context.operations.size()
                + ", worldReady=false");
    }

    static Context validated(Plan plan, Path base) throws Exception {
        if ("gtsr.material-copy/v2".equals(plan.schema)) return MaterialIdFluidCellCopy.validated(plan, base);
        if (!"gtsr.material-copy/v1".equals(plan.schema) || !"reviewed-offline-copy".equals(plan.mode))
            throw new IOException("Unsupported plan schema/mode");
        if (plan.sourceGt == null || plan.sourceGtsr == null
            || plan.targetGt == null
            || plan.targetGtsr == null
            || plan.rules == null
            || plan.rules.isEmpty()) throw new IOException("Missing version/rule evidence");
        evidence(base, plan.gtArtifact, plan.gtArtifactSha256);
        evidence(base, plan.sourceGtsrArtifact, plan.sourceGtsrArtifactSha256);
        evidence(base, plan.targetGtsrArtifact, plan.targetGtsrArtifactSha256);
        if (!"5.09.54.277".equals(plan.sourceGt) || !"5.09.54.277".equals(plan.targetGt)
            || !"88a0ac96a01e11fa04f738784722ecf27d4c72488973ff41eeb1399a167801e7".equals(plan.gtArtifactSha256))
            throw new IOException(
                "Only the audited exact GT .277 item codec is supported; other source/target environments need separate codec evidence");
        Path oldFile = evidence(base, plan.oldMapping, plan.oldMappingSha256);
        Path newFile = evidence(base, plan.newMapping, plan.newMappingSha256);
        Path registryFile = evidence(base, plan.registryEvidence, plan.registryEvidenceSha256);
        Path occupiedFile = evidence(base, plan.occupancyEvidence, plan.occupancyEvidenceSha256);
        Properties oldIds = properties(oldFile), newIds = properties(newFile), registry = properties(registryFile),
            occupied = properties(occupiedFile);
        if (!oldIds.stringPropertyNames()
            .equals(newIds.stringPropertyNames())) throw new IOException("Mapping key sets differ");
        Map<Integer, String> registryNames = new HashMap<>();
        for (String id : registry.stringPropertyNames())
            registryNames.put(Integer.parseInt(id), registry.getProperty(id));
        java.util.Set<String> represented = new java.util.HashSet<>();
        java.util.Set<String> identities = new java.util.HashSet<>();
        java.util.Set<Integer> allocated = new java.util.HashSet<>();
        java.util.Set<Integer> sourceIds = new java.util.HashSet<>();
        for (String key : oldIds.stringPropertyNames()) {
            int id = Integer.parseInt(oldIds.getProperty(key));
            if (id < 0 || id >= 1000 || !sourceIds.add(id))
                throw new IOException("Invalid/duplicate source material ID: " + key);
        }
        for (int nativeId : NATIVE_277_IDS) if (!occupied.containsKey(Integer.toString(nativeId)))
            throw new IOException("Incomplete .277 occupancy evidence; missing native slot " + nativeId);
        for (String key : newIds.stringPropertyNames()) {
            int id = Integer.parseInt(newIds.getProperty(key));
            if (id < 0 || id >= 1000 || !allocated.add(id))
                throw new IOException("Invalid/duplicate target material ID: " + key);
            if (occupied.containsKey(Integer.toString(id))) throw new IOException(
                "Target slot occupied: " + key + "=" + id + " owner=" + occupied.getProperty(Integer.toString(id)));
        }
        for (Rule rule : plan.rules) {
            if (rule.oldId < 0 || rule.oldId >= 1000
                || rule.newId < 0
                || rule.newId >= 1000
                || rule.oldId == rule.newId
                || rule.prefixIndex < 0
                || rule.prefixIndex >= 32
                || rule.prefix == null
                || rule.prefix.isEmpty()
                || !"gregtech:gt.metaitem.01".equals(rule.registryName))
                throw new IOException("Unsupported GT identity rule");
            String[] prefixes = { "dustTiny", "dustSmall", "dust", "dustImpure", "dustPure", "crushed",
                "crushedPurified", "crushedCentrifuged", "gem", "nugget", "", "ingot", "ingotHot", "", "", "", "",
                "plate", "plateDouble", "plateTriple", "plateQuadruple", "plateQuintuple", "plateDense", "stick",
                "lens", "round", "bolt", "screw", "ring", "foil", "cell", "cellPlasma" };
            if (prefixes[rule.prefixIndex].isEmpty() || !prefixes[rule.prefixIndex].equals(rule.prefix))
                throw new IOException("Exact .277 prefix index/name mismatch");
            if (!Integer.toString(rule.oldId)
                .equals(oldIds.getProperty(rule.key))
                || !Integer.toString(rule.newId)
                    .equals(newIds.getProperty(rule.key)))
                throw new IOException("Rule does not match complete mapping: " + rule.key);
            if (!rule.registryName.equals(registryNames.get(rule.numericItemId)))
                throw new IOException("Numeric registry identity evidence mismatch: " + rule.registryName);
            if (!identities.add(rule.registryName + ":" + rule.prefixIndex + ":" + rule.oldId))
                throw new IOException("Ambiguous source rule");
            represented.add(rule.key);
        }
        for (String key : oldIds.stringPropertyNames()) if (!oldIds.getProperty(key)
            .equals(newIds.getProperty(key)) && !represented.contains(key))
            throw new IOException("Changed mapping has no item/prefix rules: " + key);
        return new Context(plan, registryNames);
    }

    static void rewrite(NBTBase tag, String path, Context context, int depth) throws IOException {
        if (context.fluids != null) {
            MaterialIdFluidCellCopy.rewrite(tag, path, context, depth);
            return;
        }
        if (depth > 64 || ++context.nodes > 4_000_000)
            throw new IOException("NBT depth/node budget exceeded at " + path);
        if (tag instanceof NBTTagCompound) {
            NBTTagCompound compound = (NBTTagCompound) tag;
            String stringId = compound.hasKey("id", 8) ? compound.getString("id") : "";
            String lowerId = stringId.toLowerCase(java.util.Locale.ROOT);
            if (lowerId.contains("appliedenergistics") || lowerId.startsWith("ae2") || lowerId.startsWith("ae:"))
                throw new IOException("Unsupported AE container/encoding at " + path);
            if (compound.hasKey("id") && !compound.hasKey("Damage") && !compound.hasKey("Count")) {
                // Native inventory carriers have a reviewed Items list; machine delegates need separate adapters.
                if (!compound.hasKey("id", 8) || !Arrays.asList("Chest", "Trap", "Dropper", "Furnace", "Hopper")
                    .contains(stringId))
                    throw new IOException("Unsupported entity/tile/container identity at " + path + ": " + stringId);
                List<String> fields = Arrays
                    .asList("id", "x", "y", "z", "Items", "CustomName", "BurnTime", "CookTime", "TransferCooldown");
                for (String field : compound.func_150296_c()) if (!fields.contains(field))
                    throw new IOException("Unreviewed native-container extension at " + path + "." + field);
                if (!compound.hasKey("Items", 9) || compound.getTagList("Items", 10)
                    .tagCount() != ((NBTTagList) compound.getTag("Items")).tagCount())
                    throw new IOException("Unknown native-container item list at " + path);
            }
            if (compound.hasKey("Damage") || compound.hasKey("Count")) {
                if (!compound.hasKey("id") || !compound.hasKey("Damage", 2) || !compound.hasKey("Count", 1))
                    throw new IOException("Unknown item encoding at " + path);
                String registryName;
                if (compound.hasKey("id", 2)) registryName = context.registry.get(compound.getShort("id") & 65535);
                else if (compound.hasKey("id", 8)) registryName = stringId;
                else throw new IOException("Unknown item ID encoding at " + path);
                if (registryName == null) throw new IOException("Missing numeric registry evidence at " + path);
                String lowerRegistry = registryName.toLowerCase(java.util.Locale.ROOT);
                if (lowerRegistry.contains("appliedenergistics") || lowerRegistry.startsWith("ae2:")
                    || lowerRegistry.startsWith("ae:")) throw new IOException("Unverified AE item storage at " + path);
                int damage = compound.getShort("Damage") & 65535;
                Rule selected = null;
                for (Rule rule : context.plan.rules) {
                    if (!rule.registryName.equals(registryName) || damage != rule.prefixIndex * 1000 + rule.oldId)
                        continue;
                    if (selected != null) throw new IOException("Ambiguous item transform at " + path);
                    selected = rule;
                }
                if (selected != null) {
                    NBTTagCompound before = (NBTTagCompound) compound.copy();
                    int next = selected.prefixIndex * 1000 + selected.newId;
                    compound.setShort("Damage", (short) next);
                    NBTTagCompound conserved = (NBTTagCompound) compound.copy();
                    conserved.setShort("Damage", (short) damage);
                    if (!before.equals(conserved)) throw new IOException("Non-Damage NBT changed at " + path);
                    int count = compound.getByte("Count") & 255;
                    context.quantities.merge(
                        selected.key + "/" + selected.registryName + "/" + selected.prefix,
                        (long) count,
                        Long::sum);
                    context.operations.add(
                        path + ": "
                            + selected.registryName
                            + " prefix="
                            + selected.prefix
                            + " Damage="
                            + damage
                            + "->"
                            + next
                            + " Count="
                            + count);
                } else if (registryName.startsWith("gregtech:gt.metaitem.") && damage < 32000) {
                    for (Rule rule : context.plan.rules) if (damage % 1000 == rule.oldId)
                        throw new IOException("Changed material appears in an unreviewed GT item/prefix at " + path);
                }
            }
            for (String key : new java.util.TreeSet<>(compound.func_150296_c())) {
                NBTBase child = compound.getTag(key);
                // MC chunk section arrays contain block/light data, never item identities.
                if (child.getId() == 7 || child.getId() == 11) {
                    boolean section = path.matches(".*\\.Level\\.Sections\\[[0-9]+\\]");
                    boolean structural = section && Arrays.asList("Blocks", "Data", "Add", "BlockLight", "SkyLight")
                        .contains(key) || path.endsWith(".Level")
                            && Arrays.asList("Biomes", "HeightMap")
                                .contains(key);
                    if (!structural) throw new IOException("Unsupported opaque/AE/custom array at " + path + "." + key);
                } else rewrite(child, path + "." + key, context, depth + 1);
            }
        } else if (tag instanceof NBTTagList) {
            NBTTagList list = (NBTTagList) tag;
            if (list.func_150303_d() == 10) for (int i = 0; i < list.tagCount(); i++)
                rewrite(list.getCompoundTagAt(i), path + "[" + i + "]", context, depth + 1);
            else if (list.func_150303_d() == 9 || list.func_150303_d() == 7 || list.func_150303_d() == 11)
                throw new IOException("Unsupported nested/opaque list at " + path);
        }
    }

    static void region(Path input, Path output, String path, Context context, boolean dry) throws Exception {
        long size = Files.size(input);
        if (size < 8192 || size % 4096 != 0 || size > MAX_FILE)
            throw new IOException("Malformed/oversized region: " + path);
        Map<Integer, NBTTagCompound> changed = new LinkedHashMap<>();
        try (RandomAccessFile region = new RandomAccessFile(input.toFile(), "r")) {
            java.util.BitSet used = new java.util.BitSet();
            used.set(0, 2);
            for (int index = 0; index < 1024; index++) {
                region.seek(index * 4L);
                int location = region.readInt(), start = location >>> 8, sectors = location & 255;
                if (location == 0) continue;
                if (start < 2 || sectors == 0
                    || (long) (start + sectors) * 4096 > size
                    || used.nextSetBit(start) >= 0 && used.nextSetBit(start) < start + sectors)
                    throw new IOException("Invalid/overlapping region sector at " + path + "#" + index);
                used.set(start, start + sectors);
                region.seek(start * 4096L);
                int length = region.readInt();
                int compression = region.readUnsignedByte();
                if (length <= 1 || length > sectors * 4096 - 4 || compression != 1 && compression != 2)
                    throw new IOException("Unsupported region compression/length at " + path + "#" + index);
                byte[] payload = new byte[length - 1];
                region.readFully(payload);
                NBTTagCompound root;
                try (DataInputStream decoded = new DataInputStream(
                    compression == 1 ? new GZIPInputStream(new ByteArrayInputStream(payload))
                        : new InflaterInputStream(new ByteArrayInputStream(payload)))) {
                    root = CompressedStreamTools.func_152456_a(decoded, new NBTSizeTracker(MAX_NBT));
                    if (decoded.read() != -1) throw new IOException("Trailing chunk data: " + path);
                }
                int previous = context.operations.size();
                rewrite(root, path + "#" + index, context, 0);
                if (context.operations.size() != previous) changed.put(index, root);
            }
        }
        if (dry) return;
        // RegionFile opens read/write; use it ONLY on a new temporary output copy, never the source.
        Files.createDirectories(output.getParent());
        Path scratch = Files.createTempFile(output.getParent(), ".gtsr-region-", ".tmp");
        try {
            Files.write(scratch, Files.readAllBytes(input));
            RegionFile nativeRegion = new RegionFile(scratch.toFile());
            try {
                for (Map.Entry<Integer, NBTTagCompound> entry : changed.entrySet()) {
                    int x = entry.getKey() & 31, z = entry.getKey() >>> 5;
                    try (DataOutputStream out = nativeRegion.getChunkDataOutputStream(x, z)) {
                        CompressedStreamTools.write(entry.getValue(), out);
                    }
                    try (DataInputStream in = nativeRegion.getChunkDataInputStream(x, z)) {
                        if (in == null || !entry.getValue()
                            .equals(CompressedStreamTools.func_152456_a(in, new NBTSizeTracker(MAX_NBT))))
                            throw new IOException("Native region round-trip mismatch");
                    }
                }
            } finally {
                nativeRegion.close();
            }
            copyAtomic(output, Files.readAllBytes(scratch));
        } finally {
            Files.deleteIfExists(scratch);
        }
    }

    static NBTTagCompound decode(byte[] bytes, boolean gzip) throws IOException {
        if (bytes.length > MAX_FILE) throw new IOException("NBT file size exceeded");
        try (DataInputStream input = new DataInputStream(
            gzip ? new GZIPInputStream(new ByteArrayInputStream(bytes)) : new ByteArrayInputStream(bytes))) {
            NBTTagCompound result = CompressedStreamTools.func_152456_a(input, new NBTSizeTracker(MAX_NBT));
            if (input.read() != -1) throw new IOException("Trailing/unsupported NBT data");
            return result;
        }
    }

    static byte[] encode(NBTTagCompound root, boolean gzip) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        if (gzip) CompressedStreamTools.writeCompressed(root, bytes);
        else try (DataOutputStream output = new DataOutputStream(bytes)) {
            CompressedStreamTools.write(root, output);
        }
        return bytes.toByteArray();
    }

    static Map<String, String> inventory(Path root) throws IOException {
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Source/output directory missing or symlink");
        Map<String, String> result = new TreeMap<>();
        try (java.util.stream.Stream<Path> files = Files.walk(root)) {
            java.util.Iterator<Path> iterator = files.iterator();
            while (iterator.hasNext()) {
                Path file = iterator.next();
                if (Files.isSymbolicLink(file) || !file.toRealPath()
                    .startsWith(root.toRealPath())) throw new IOException("Symbolic-link escape rejected: " + file);
                if (Files.isDirectory(file)) continue;
                if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE)
                    throw new IOException("Unsupported/oversized file: " + file);
                if (result.size() >= 100000) throw new IOException("File count budget exceeded");
                result.put(
                    root.relativize(file)
                        .toString(),
                    hash(file));
            }
        }
        return result;
    }

    static void rejectOverlap(Path source, Path target) throws IOException {
        if (!source.toRealPath()
            .equals(source) || target.startsWith(source) || source.startsWith(target))
            throw new IOException("Source/target overlap or source alias rejected");
        Path existing = target;
        while (existing != null && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) existing = existing.getParent();
        if (existing == null || !existing.toRealPath()
            .equals(existing)) throw new IOException("Target alias/symlink rejected");
    }

    static Properties properties(Path file) throws IOException {
        Properties properties = new Properties() {

            @Override
            public synchronized Object put(Object key, Object value) {
                if (containsKey(key)) throw new IllegalArgumentException("Duplicate evidence key: " + key);
                return super.put(key, value);
            }
        };
        try (java.io.InputStream input = Files.newInputStream(file)) {
            properties.load(input);
        }
        for (String key : new ArrayList<>(properties.stringPropertyNames()))
            if (key.startsWith("__gtsr.")) properties.remove(key);
        return properties;
    }

    static Path evidence(Path base, String relative, String expected) throws IOException {
        if (relative == null || expected == null || !expected.matches("[a-f0-9]{64}"))
            throw new IOException("Missing reviewed evidence path/hash");
        Path file = base.resolve(relative)
            .normalize();
        if (Files.isSymbolicLink(file) || !hash(file).equals(expected))
            throw new IOException("Evidence fingerprint mismatch: " + file);
        return file;
    }

    static String hash(Path path) throws IOException {
        return MaterialIdPreflight.sha256(Files.readAllBytes(path));
    }

    static void copyAtomic(Path path, byte[] bytes) throws IOException {
        Files.createDirectories(path.getParent());
        if (Files.isSymbolicLink(path)) throw new IOException("Output symlink rejected");
        AtomicFilePublisher.publishAtomic(path, bytes);
        if (!Arrays.equals(Files.readAllBytes(path), bytes)) throw new IOException("Published copy mismatch");
    }

    static void publishJson(Path path, Object report) throws IOException {
        copyAtomic(
            path,
            JSON.toJson(report)
                .getBytes(StandardCharsets.UTF_8));
    }
}
