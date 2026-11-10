package com.miaokatze.gtsr.common.dimension.prosperity.echo;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.miaokatze.gtsr.common.dimension.prosperity.lore.LoreSources;

/** Pure Java admission/asset contract; real entity NBT migration is checked separately in a native world. */
public final class EchoRosterContracts {

    private static int checks;

    private EchoRosterContracts() {}

    public static void main(String[] args) throws IOException {
        if (args.length != 1) throw new IllegalArgumentException("Expected main resources root");
        Path resources = Paths.get(args[0]);
        String[] wireCodes = { "dr-01", "dr-03", "dr-05", "dr-07", "dr-10", "dr-11", "dr-12", "dr-15", "dr-20", "di-02",
            "di-05", "di-08", "di-10", "di-13", "dc-02", "dc-08", "do-01", "di-01", "di-03", "di-04", "di-06", "di-07",
            "di-09", "di-11", "di-12", "di-14", "di-15", "do-02", "dr-02", "dr-04", "dr-06", "dr-08", "dr-09", "dr-13",
            "dr-14", "dr-16", "dr-17", "dr-18", "dr-19" };
        String[][] retired = { { "dr-02", "dr-04" }, { "dr-05", "dr-04" }, { "dr-06", "dr-04" }, { "dr-07", "dr-04" },
            { "dr-08", "dr-04" }, { "dr-10", "dr-09" }, { "dr-13", "dr-14" }, { "dr-16", "dr-04" },
            { "dr-19", "dr-20" }, { "di-03", "di-12" }, { "di-06", "di-11" }, { "di-09", "di-04" },
            { "di-14", "di-11" }, { "di-15", "di-04" } };
        String[] olderDiTargets = { "di-04", "di-04", "di-05", "di-12", "di-08", "di-10", "di-11", "di-13", "di-04",
            "di-12" };
        Map<String, String> expected = new LinkedHashMap<>();
        for (String[] pair : retired) expected.put(pair[0], pair[1]);
        check(EchoKind.values().length == wireCodes.length, "Released wire table length");
        Set<String> active = new LinkedHashSet<>();
        int retiredCount = 0;
        for (int i = 0; i < wireCodes.length; i++) {
            EchoKind raw = EchoKind.values()[i], canonical = raw.canonical();
            check(raw.ordinal() == i && raw.code.equals(wireCodes[i]), "Released wire slot " + i);
            String target = expected.containsKey(raw.code) ? expected.get(raw.code) : raw.code;
            check(canonical.code.equals(target), "Canonical map " + raw.code);
            check(canonical.canonical() == canonical && !canonical.isRetired(), "No alias chain/cycle " + raw.code);
            check(EchoKind.byCode(raw.code) == canonical, "Code admission " + raw.code);
            check(EchoKind.byCode(raw.code.toUpperCase(java.util.Locale.ROOT)) == canonical, "Case admission");
            check(EchoKind.byWireId(i) == canonical, "Wire admission " + raw.code);
            check(raw.isRetired() == expected.containsKey(raw.code), "Retirement marker");
            if (raw.isRetired()) {
                retiredCount++;
                check(
                    raw.code.substring(0, 2)
                        .equals(canonical.code.substring(0, 2)),
                    "DR/DI category retained");
                check(
                    LoreSources.bossRelic(raw)
                        .isEmpty()
                        && LoreSources.bossRelic(canonical)
                            .isEmpty(),
                    "Retirement cannot introduce a witness reward");
            }
            active.add(canonical.code);
        }
        check(retiredCount == 14, "Fourteen retired identities");
        check(count(active, "dr-") == 11 && count(active, "di-") == 10, "35 to 21 minor roster");
        check(EchoKind.byWireId(-1) == EchoKind.DR01 && EchoKind.byWireId(39) == EchoKind.DR01, "Bad wire fallback");
        for (int i = 0; i < olderDiTargets.length; i++)
            check(EchoKind.byCode("di-" + (16 + i)).code.equals(olderDiTargets[i]), "Earlier DI alias " + i);
        try {
            EchoKind.byCode("dr-99");
            throw new AssertionError("Unknown species was accepted");
        } catch (IllegalArgumentException expectedFailure) {
            checks++;
        }
        String[] bosses = { "di-02", "di-05", "di-08", "di-10", "di-13", "dc-02", "dc-08" };
        String[] relics = { "patina_seal", "webbed_shuttle", "cracked_sighting_lens", "bridge_pass", "resonant_clapper",
            "foundry_heart_fragment", "hive_memory_knot" };
        for (int i = 0; i < bosses.length; i++) {
            EchoKind boss = EchoKind.byCode(bosses[i]);
            check(boss.code.equals(bosses[i]) && !boss.isRetired(), "Witness boss stays active");
            check(
                LoreSources.bossRelic(boss)
                    .equals(relics[i]),
                "Witness identity unchanged");
        }
        for (String required : Arrays
            .asList("dr-01", "dr-12", "dr-20", "dr-04", "dr-14", "dr-18", "dr-09", "di-07", "do-01", "do-02"))
            check(active.contains(required), "Night/summon/story consumer " + required);
        Set<String> models = lines(resources.resolve("assets/gtsr/liminal/active-models.txt"));
        active.add("dc-10");
        check(models.size() == 26 && models.equals(active), "Exact active model closure");
        for (String code : models) {
            Path base = resources.resolve("assets/gtsr/liminal/" + code);
            check(Files.isRegularFile(base.resolve("models/" + code + ".obj")), "Source mesh retained " + code);
            check(Files.isRegularFile(base.resolve("models/rig_animation.json")), "Active rig " + code);
            check(Files.isRegularFile(base.resolve("textures/model/" + code + ".png")), "Active PNG " + code);
        }
        for (String[] pair : retired) {
            Path base = resources.resolve("assets/gtsr/liminal/" + pair[0]);
            check(Files.isRegularFile(base.resolve("models/" + pair[0] + ".obj")), "Retired source stays authored");
            check(
                Files.isRegularFile(base.resolve("textures/model/" + pair[0] + ".png")),
                "Retired PNG remains intact");
        }
        String sounds = text(resources.resolve("assets/gtsr/sounds.json"));
        Set<String> inactive = lines(resources.resolve("assets/gtsr/sounds/inactive-sounds.txt"));
        check(inactive.size() == 56, "Exact inactive OGG set size");
        Set<String> expectedInactive = new HashSet<>();
        for (String[] pair : retired) for (String event : Arrays.asList("attack", "death", "hurt", "idle")) {
            String key = "entity." + pair[0] + "." + event;
            Pattern alias = Pattern.compile(
                "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*\\{[^}]*?\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"",
                Pattern.DOTALL);
            Matcher found = alias.matcher(sounds);
            check(found.find(), "Retired event key remains " + key);
            check(
                found.group(1)
                    .equals("gtsr:combat/entity_" + pair[1] + "_" + event),
                "Sound event alias " + key);
            expectedInactive.add("assets/gtsr/sounds/combat/entity_" + pair[0] + "_" + event + ".ogg");
        }
        check(inactive.equals(expectedInactive), "Exact inactive OGG paths");
        Set<String> referenced = new HashSet<>();
        // Sound payloads may be shorthand strings or objects with a name field.
        Matcher names = Pattern.compile("\\\"((?:gtsr:)?(?:combat|music|weapons)/[^\\\"]+)\\\"")
            .matcher(sounds);
        while (names.find()) referenced.add(
            "assets/gtsr/sounds/" + names.group(1)
                .replaceFirst("^gtsr:", "") + ".ogg");
        check(referenced.size() == 154, "Active sound payload closure");
        for (String name : referenced) {
            check(!inactive.contains(name), "No event consumes retired OGG " + name);
            check(Files.isRegularFile(resources.resolve(name)), "Referenced OGG exists " + name);
        }
        for (String name : inactive) check(Files.isRegularFile(resources.resolve(name)), "Retired author OGG retained");
        System.out.println(
            "ECHO_ROSTER PASS checks=" + checks + " wireSlots=39 activeMinor=21 models=26 soundAliases=56 ogg=154");
    }

    private static long count(Set<String> codes, String prefix) {
        long count = 0;
        for (String code : codes) if (code.startsWith(prefix)) count++;
        return count;
    }

    private static Set<String> lines(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        Set<String> result = new LinkedHashSet<>();
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            check(result.add(line), "Duplicate manifest entry " + line);
        }
        return result;
    }

    private static String text(Path path) throws IOException {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
