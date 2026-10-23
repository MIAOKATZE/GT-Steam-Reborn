import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Read real 78-machine tooltip builders and their concise profiles without a game or keyboard/GL access. */
public final class MachineTooltipProfileRegression {
    public static void main(String[] args) throws Exception {
        Issue37RegistrationRegression.launch("MachineTooltipProfileRuntime", args[0]);
    }
}

class MachineTooltipProfileRuntime {
    @SuppressWarnings("unchecked")
    private static void language(Path path) throws Exception {
        Map<String, String> values = new HashMap<>();
        for (String row : Files.readAllLines(path)) {
            int equals = row.indexOf('=');
            if (equals > 0 && !row.startsWith("#")) values.put(row.substring(0, equals), row.substring(equals + 1));
        }
        for (Field field : net.minecraft.util.StatCollector.class.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !field.getType().getSimpleName().equals("StringTranslate")) {
                continue;
            }
            field.setAccessible(true);
            Object translator = field.get(null);
            for (Field translations : translator.getClass().getDeclaredFields()) {
                if (Map.class.isAssignableFrom(translations.getType())) {
                    translations.setAccessible(true);
                    ((Map<String, String>) translations.get(translator)).putAll(values);
                }
            }
        }
    }

    public static void run(String version) throws Exception {
        Issue37Runtime.bootstrap(version);
        Path root = Path.of(System.getProperty("user.dir"));
        while (!Files.exists(root.resolve("src/main/resources/assets/gtsr/lang/en_US.lang"))) root = root.getParent();
        language(root.resolve("src/main/resources/assets/gtsr/lang/"
            + System.getProperty("gtsr.tooltip.locale", "en_US") + ".lang"));
        com.miaokatze.gtsr.loader.MachineLoader.initMachines();
        Method summary = Class.forName("com.miaokatze.gtsr.client.tooltip.GTSRMachineTooltipProfiles")
            .getDeclaredMethod("summary", com.miaokatze.gtsr.common.api.enums.GTSRItemList.class,
                List.class, List.class, List.class);
        summary.setAccessible(true);
        int machines = 0, shortProfiles = 0;
        List<String> failures = new ArrayList<>();
        for (var entry : com.miaokatze.gtsr.common.api.enums.GTSRItemList.values()) {
            if (!entry.hasBeenSet() || !(entry.getItem() instanceof gregtech.common.blocks.ItemMachines)) continue;
            var machine = gregtech.api.GregTechAPI.METATILEENTITIES[entry.getMeta()];
            if (machine == null || !machine.getClass().getName().startsWith("com.miaokatze.gtsr.")) continue;
            machines++;
            String[] primary = machine instanceof gregtech.api.interfaces.ISecondaryDescribable s
                ? s.getPrimaryDescription() : machine.getDescription();
            String[] structure = machine instanceof gregtech.api.interfaces.ISecondaryDescribable s
                ? s.getSecondaryDescription() : new String[0];
            List<String> additional = new ArrayList<>();
            machine.addAdditionalTooltipInformation(entry.get(1), additional);
            @SuppressWarnings("unchecked")
            List<String> rows = (List<String>) summary.invoke(null, entry, Arrays.asList(primary),
                Arrays.asList(structure), additional);
            if (rows.isEmpty()) failures.add(entry.name() + ": empty profile");
            if (machine instanceof gregtech.api.interfaces.ISecondaryDescribable && rows.size() < 2) {
                failures.add(entry.name() + ": no actual parameter rows selected");
            }
            if (rows.stream().anyMatch(row -> row.contains("gtsr.tooltip.machine."))) {
                failures.add(entry.name() + ": untranslated summary");
            }
            if (rows.size() <= 7) shortProfiles++;
            System.out.println("PROFILE " + entry.name() + " overview=" + rows.size() + " primary=" + primary.length
                + " structure=" + structure.length + " additional=" + additional.size());
            for (String row : rows) {
                System.out.println("  " + net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(row));
            }
        }
        System.out.println("RESULT profiles machines=" + machines + " atMost7Rows=" + shortProfiles
            + " failures=" + failures);
        if (machines != 78 || !failures.isEmpty()) throw new AssertionError("profile coverage: " + failures);
    }
}
