// SPDX-License-Identifier: AGPL-3.0-only
// Additional terms: see AGPL-ADDITIONAL-TERMS.md in the project root.

package com.miaokatze.gtsr.loader.recipes;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import com.google.common.io.ByteStreams;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import cpw.mods.fml.common.registry.GameRegistry;
import gregtech.api.enums.ItemList;
import gtPlusPlus.xmod.gregtech.api.enums.GregtechItemList;

/** Reviewed registration evidence bound to exact code fingerprints; never a full mod initialization proof. */
public final class CenterIngredientRegistrationContracts {

    private static final String RECIPES = "com/miaokatze/gtsr/loader/recipes/OreIntegrationCenterRecipes";
    private static final String GT_ITEMS = "gregtech/api/enums/ItemList";
    private static final String GTPP_ITEMS = "gtPlusPlus/xmod/gregtech/api/enums/GregtechItemList";
    private static final String GTSR_ITEMS = "com/miaokatze/gtsr/common/api/enums/GTSRItemList";
    private static final String MATERIALS = "gregtech/api/enums/Materials";
    private static final String PREFIXES = "gregtech/api/enums/OrePrefixes";
    private static final String STACK = "Lnet/minecraft/item/ItemStack;";
    private static final String ORE_DESC = "(L" + PREFIXES + ";L" + MATERIALS + ";I)" + STACK;
    private static final Set<String> ENUM_OWNERS = new HashSet<>(Arrays.asList(GT_ITEMS, GTPP_ITEMS, GTSR_ITEMS));
    private static final Set<String> SUPPORT = new HashSet<>(
        Arrays.asList(GT_ITEMS + ".Circuit_Integrated", GT_ITEMS + ".Tool_DataStick", GT_ITEMS + ".Tool_DataOrb"));

    private final JsonObject report = new JsonObject();
    private final JsonArray negatives = new JsonArray();
    private int checks;

    private CenterIngredientRegistrationContracts() {}

    public static int verify() {
        CenterIngredientRegistrationContracts contract = new CenterIngredientRegistrationContracts();
        return contract.run();
    }

    private int run() {
        report.addProperty("verification", "reviewed registration evidence and exact code fingerprints");
        report.addProperty("gameStarted", false);
        report.addProperty("fullModInitialization", false);
        report.addProperty(
            "boundary",
            "Producer and lifecycle semantics are reviewed manifest evidence, not inferred from GETSTATIC/set presence. "
                + "Fixture hasBeenSet values do not establish ingredient availability. "
                + "Fingerprints cover class resources and local source content, not transformed runtime classes.");
        report.add("negativeControls", negatives);
        try {
            JsonObject manifest;
            try (InputStream input = resource("orecenter/ingredient-registration-audit.json").openStream();
                InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                manifest = new JsonParser().parse(reader)
                    .getAsJsonObject();
            }
            Snapshot snapshot = snapshot(manifest);
            gate(snapshot, read(snapshot.production), snapshot.providers, snapshot.sources);
            report.addProperty(
                "gtVersion",
                manifest.get("gtVersion")
                    .getAsString());
            report.addProperty("gtJar", snapshot.jar.toString());
            report.addProperty("gtJarSha256", snapshot.jarHash);
            report.addProperty("directEnums", 76);
            report.addProperty("supportEnums", 3);
            report.addProperty("orePairs", 29);
            report.addProperty("fluidInputs", 2);
            report.addProperty("providerClasses", snapshot.providers.size());
            report.addProperty("sourceFiles", snapshot.sources.size());
            report.addProperty("sourceHashFormat", "utf8-lf");
            JsonObject selectedProviders = new JsonObject();
            for (Map.Entry<String, byte[]> entry : snapshot.providers.entrySet())
                selectedProviders.addProperty(entry.getKey(), sha256(entry.getValue()));
            report.add("selectedProviderSha256", selectedProviders);
            legacyPlaceholderNegative(snapshot);
            fingerprintNegatives(snapshot);
            report.addProperty("status", "PASS");
            report.addProperty("checks", checks);
            writeReport();
            System.out.println(
                "PASS center ingredient reviewed-registration evidence: " + checks
                    + " checks; exact .277 code fingerprints; fixture state is not registration proof; "
                    + "full_mod_initialization=false; game_started=false");
            return checks;
        } catch (Exception | AssertionError failure) {
            report.addProperty("status", "FAIL");
            report.addProperty("checks", checks);
            report.addProperty("failure", failure.toString());
            try {
                writeReport();
            } catch (Exception reportFailure) {
                failure.addSuppressed(reportFailure);
            }
            if (failure instanceof AssertionError) throw (AssertionError) failure;
            throw new AssertionError("Ingredient registration evidence gate failed", failure);
        }
    }

    private Snapshot snapshot(JsonObject manifest) throws Exception {
        Snapshot result = new Snapshot(manifest);
        URL itemResource = ItemList.class.getResource("/" + GT_ITEMS + ".class");
        check(
            itemResource != null && itemResource.openConnection() instanceof JarURLConnection,
            "native GT ItemList must come from a jar, not a fixture directory");
        JarURLConnection connection = (JarURLConnection) itemResource.openConnection();
        check(
            "file".equals(
                connection.getJarFileURL()
                    .getProtocol()),
            "native GT jar must be a local artifact");
        result.jar = Paths.get(
            connection.getJarFileURL()
                .toURI())
            .toAbsolutePath()
            .normalize();
        check(Files.isRegularFile(result.jar), "native GT artifact exists");
        try (InputStream input = Files.newInputStream(result.jar)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) digest.update(buffer, 0, count);
            result.jarHash = hex(digest.digest());
        }
        for (Map.Entry<String, JsonElement> entry : manifest.getAsJsonObject("providerClassSha256")
            .entrySet()) {
            URL provider = resource(entry.getKey() + ".class");
            if (!entry.getKey()
                .startsWith("com/miaokatze/gtsr/")) {
                check(
                    provider.openConnection() instanceof JarURLConnection,
                    "reviewed upstream provider must come from the native GT jar: " + entry.getKey());
                JarURLConnection providerConnection = (JarURLConnection) provider.openConnection();
                check(
                    result.jar.equals(
                        Paths.get(
                            providerConnection.getJarFileURL()
                                .toURI())
                            .toAbsolutePath()
                            .normalize()),
                    "provider and native GT artifact identities agree: " + entry.getKey());
            }
            try (InputStream input = provider.openStream()) {
                result.providers.put(entry.getKey(), ByteStreams.toByteArray(input));
            }
        }
        Path root = Paths.get("")
            .toAbsolutePath()
            .normalize();
        for (Map.Entry<String, JsonElement> entry : manifest.getAsJsonObject("sourceSha256")
            .entrySet()) {
            Path source = root.resolve(entry.getKey())
                .normalize();
            check(
                !Paths.get(entry.getKey())
                    .isAbsolute() && source.startsWith(root),
                "reviewed source path stays in project root: " + entry.getKey());
            result.sources.put(entry.getKey(), Files.readAllBytes(source));
        }
        try (InputStream input = resource(RECIPES + ".class").openStream()) {
            result.production = ByteStreams.toByteArray(input);
        }
        return result;
    }

    private void gate(Snapshot snapshot, ClassNode production, Map<String, byte[]> providers,
        Map<String, byte[]> sources) throws Exception {
        JsonObject manifest = snapshot.manifest;
        check(
            manifest.get("schema")
                .getAsInt() == 1,
            "supported reviewed-registration schema");
        check(
            "5.09.54.277".equals(
                manifest.get("gtVersion")
                    .getAsString()),
            "reviewed main platform is exact GT .277");
        check(
            "utf8-lf".equals(
                manifest.get("sourceHashFormat")
                    .getAsString()),
            "reviewed local sources use normalized UTF-8 LF content hashes");
        check(
            snapshot.jarHash.equals(
                manifest.get("gtArtifactSha256")
                    .getAsString()),
            "reviewed GT artifact fingerprint changed");
        JsonObject providerHashes = manifest.getAsJsonObject("providerClassSha256");
        check(
            !providerHashes.entrySet()
                .isEmpty(),
            "reviewed provider fingerprint set is nonempty");
        for (Map.Entry<String, JsonElement> entry : providerHashes.entrySet()) {
            byte[] bytes = providers.get(entry.getKey());
            check(bytes != null, "reviewed provider bytes are present: " + entry.getKey());
            String actual = sha256(bytes);
            boolean allowed = false;
            for (JsonElement hash : entry.getValue()
                .getAsJsonArray()) allowed |= actual.equals(hash.getAsString());
            check(allowed, "reviewed provider code fingerprint changed: " + entry.getKey());
        }
        JsonObject sourceHashes = manifest.getAsJsonObject("sourceSha256");
        check(
            !sourceHashes.entrySet()
                .isEmpty(),
            "reviewed local lifecycle/source fingerprint set is nonempty");
        for (Map.Entry<String, JsonElement> entry : sourceHashes.entrySet()) {
            byte[] bytes = sources.get(entry.getKey());
            check(
                bytes != null && sourceSha256(bytes).equals(
                    entry.getValue()
                        .getAsString()),
                "reviewed source/lifecycle fingerprint changed: " + entry.getKey());
        }
        dependencies(manifest, production);
        for (JsonElement value : manifest.getAsJsonArray("enums")) {
            JsonObject entry = value.getAsJsonObject();
            String provider = text(entry, "providerClass"), selected = text(entry, "providerMethod");
            byte[] bytes = providers.get(provider);
            if (bytes == null) {
                check(
                    provider.startsWith("com/miaokatze/gtsr/")
                        && sourceHashes.has("src/main/java/" + provider + ".java"),
                    "enum provider has reviewed code identity: " + provider);
                try (InputStream input = resource(provider + ".class").openStream()) {
                    bytes = ByteStreams.toByteArray(input);
                }
            }
            ClassNode node = read(bytes);
            check(
                node.name.equals(provider),
                "reviewed provider resource has the expected class identity: " + provider);
            boolean field = false, writer = false, methodFound = false;
            String write = text(entry, "write");
            check(
                Arrays.asList("set", "register", "setAndRegister")
                    .contains(write),
                "recognized reviewed write/helper");
            for (MethodNode method : node.methods) {
                if (!(method.name.equals(selected) || (method.name + method.desc).equals(selected))) continue;
                methodFound = true;
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (instruction instanceof FieldInsnNode) {
                        FieldInsnNode reference = (FieldInsnNode) instruction;
                        field |= reference.getOpcode() == Opcodes.GETSTATIC
                            && reference.owner.equals(text(entry, "owner"))
                            && reference.name.equals(text(entry, "field"));
                    } else if (instruction instanceof MethodInsnNode)
                        writer |= ((MethodInsnNode) instruction).name.equals(write);
                }
            }
            check(
                methodFound && field && writer,
                "reviewed provider structure changed: " + provider + "#" + selected + " -> " + enumKey(entry));
            text(entry, "producer");
            text(entry, "lifecycle");
        }
    }

    private void dependencies(JsonObject manifest, ClassNode production) {
        Set<String> expected = new TreeSet<>(), support = new TreeSet<>(), ores = new TreeSet<>(),
            fluids = new TreeSet<>();
        for (JsonElement value : manifest.getAsJsonArray("enums")) {
            JsonObject entry = value.getAsJsonObject();
            String role = text(entry, "role");
            check(ENUM_OWNERS.contains(text(entry, "owner")), "reviewed dependency uses a supported enum owner");
            check("direct".equals(role) || "support".equals(role), "recognized enum dependency role");
            check(("direct".equals(role) ? expected : support).add(enumKey(entry)), "unique reviewed enum dependency");
        }
        check(
            expected.size() == 76 && support.equals(SUPPORT),
            "reviewed scope is 76 direct and three support entries");
        for (JsonElement value : manifest.getAsJsonArray("ores")) {
            JsonObject entry = value.getAsJsonObject();
            check(ores.add(text(entry, "prefix") + "/" + text(entry, "material")), "unique reviewed ore pair");
            text(entry, "evidence");
        }
        for (JsonElement value : manifest.getAsJsonArray("fluids")) {
            JsonObject entry = value.getAsJsonObject();
            check(
                fluids.add(
                    text(entry, "material") + "/"
                        + text(entry, "getter")
                        + "/"
                        + entry.get("amount")
                            .getAsLong()),
                "unique reviewed fluid input");
            text(entry, "evidence");
        }
        check(ores.size() == 29 && fluids.size() == 2, "reviewed scope includes 29 ore pairs and two fluid inputs");
        Set<String> actual = new TreeSet<>(), actualOres = new TreeSet<>(), actualFluids = new TreeSet<>();
        for (MethodNode method : production.methods) {
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof FieldInsnNode) {
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (field.getOpcode() == Opcodes.GETSTATIC && ENUM_OWNERS.contains(field.owner))
                        actual.add(field.owner + "." + field.name);
                } else if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    if (call.owner.equals(RECIPES) && call.name.equals("ore")) {
                        check(call.desc.equals(ORE_DESC), "recognized production ore helper descriptor");
                        AbstractInsnNode amount = previous(call), material = previous(amount),
                            prefix = previous(material);
                        number(amount);
                        actualOres.add(staticField(prefix, PREFIXES) + "/" + staticField(material, MATERIALS));
                    } else if (call.owner.equals(MATERIALS)
                        && (call.name.equals("getMolten") || call.name.equals("getFluid"))) {
                            check(
                                call.desc.equals("(J)Lnet/minecraftforge/fluids/FluidStack;")
                                    || call.desc.equals("(I)Lnet/minecraftforge/fluids/FluidStack;"),
                                "recognized production fluid getter descriptor");
                            AbstractInsnNode amount = previous(call);
                            actualFluids
                                .add(staticField(previous(amount), MATERIALS) + "/" + call.name + "/" + number(amount));
                        }
                }
            }
        }
        equal(expected, actual, "direct enum dependencies");
        equal(ores, actualOres, "production ore prefix/material pairs");
        equal(fluids, actualFluids, "production fluid material/getter/amount inputs");
    }

    private void legacyPlaceholderNegative(Snapshot snapshot) throws Exception {
        GregtechItemList obsolete = GregtechItemList.Industrial_Electromagnetic_Separator;
        Field stack = GregtechItemList.class.getDeclaredField("mStack");
        Field unset = GregtechItemList.class.getDeclaredField("mHasNotBeenSet");
        stack.setAccessible(true);
        unset.setAccessible(true);
        Object oldStack = stack.get(obsolete);
        boolean oldUnset = unset.getBoolean(obsolete);
        try {
            Method factory = PortableWeaponRecipeContracts.class.getDeclaredMethod("item", String.class);
            factory.setAccessible(true);
            Item item = (Item) factory.invoke(null, "gtsr_ingredient_registration_contract:obsolete_separator");
            check(
                GameRegistry.findUniqueIdentifierFor(item) != null,
                "negative control uses a genuinely registered fixture Item");
            obsolete.set(new ItemStack(item, 1));
            check(obsolete.hasBeenSet(), "obsolete enum placeholder is set before the negative dependency audit");
            ClassNode changed = read(snapshot.production);
            int mutations = 0;
            for (MethodNode method : changed.methods) {
                for (AbstractInsnNode instruction : method.instructions.toArray()) {
                    if (!(instruction instanceof FieldInsnNode)) continue;
                    FieldInsnNode field = (FieldInsnNode) instruction;
                    if (!field.owner.equals(GT_ITEMS)
                        || !field.name.equals("Machine_Multi_IndustrialElectromagneticSeparator")) continue;
                    AbstractInsnNode amount = next(field), invocation = next(amount);
                    check(
                        number(amount) == 2 && invocation instanceof MethodInsnNode,
                        "negative control targets the actual two-controller production ingredient");
                    MethodInsnNode call = (MethodInsnNode) invocation;
                    check(
                        call.owner.equals(RECIPES) && call.name.equals("item")
                            && call.desc.equals("(L" + GT_ITEMS + ";I)" + STACK),
                        "negative control preserves the item-helper call shape");
                    field.owner = GTPP_ITEMS;
                    field.name = "Industrial_Electromagnetic_Separator";
                    field.desc = "L" + GTPP_ITEMS + ";";
                    call.desc = "(L" + GTPP_ITEMS + ";I)" + STACK;
                    mutations++;
                }
            }
            check(mutations == 1, "negative control changes exactly one actual separator dependency");
            rejected(
                "legacy enum remains rejected even when placeholder-set",
                "direct enum dependencies",
                () -> gate(snapshot, changed, snapshot.providers, snapshot.sources));
        } finally {
            stack.set(obsolete, oldStack);
            unset.setBoolean(obsolete, oldUnset);
        }
    }

    private void fingerprintNegatives(Snapshot snapshot) throws Exception {
        String provider = snapshot.providers.keySet()
            .iterator()
            .next();
        Map<String, byte[]> providers = new LinkedHashMap<>(snapshot.providers);
        providers.put(provider, altered(providers.get(provider)));
        rejected(
            "changed provider bytes require fresh review",
            "reviewed provider code fingerprint changed",
            () -> gate(snapshot, read(snapshot.production), providers, snapshot.sources));
        String source = snapshot.sources.keySet()
            .stream()
            .filter(path -> path.contains("CommonProxy"))
            .findFirst()
            .orElse(
                snapshot.sources.keySet()
                    .iterator()
                    .next());
        Map<String, byte[]> sources = new LinkedHashMap<>(snapshot.sources);
        sources.put(source, altered(sources.get(source)));
        rejected(
            "changed local lifecycle/source bytes require fresh review",
            "reviewed source/lifecycle fingerprint changed",
            () -> gate(snapshot, read(snapshot.production), snapshot.providers, sources));
    }

    private void rejected(String name, String reason, CheckedCall operation) throws Exception {
        AssertionError rejected = null;
        try {
            operation.run();
        } catch (AssertionError expected) {
            rejected = expected;
        }
        check(
            rejected != null && rejected.getMessage() != null
                && rejected.getMessage()
                    .contains(reason),
            "negative control must fail for its intended reason: " + name);
        JsonObject entry = new JsonObject();
        entry.addProperty("name", name);
        entry.addProperty("result", "rejected");
        entry.addProperty("reason", rejected.getMessage());
        negatives.add(entry);
    }

    private void equal(Set<String> expected, Set<String> actual, String description) {
        Set<String> missing = new TreeSet<>(expected), added = new TreeSet<>(actual);
        missing.removeAll(actual);
        added.removeAll(expected);
        check(expected.equals(actual), description + " differ; missing=" + missing + "; unreviewed=" + added);
    }

    private static String staticField(AbstractInsnNode instruction, String owner) {
        if (!(instruction instanceof FieldInsnNode))
            throw new AssertionError("Unrecognized direct registration ingredient parameter");
        FieldInsnNode field = (FieldInsnNode) instruction;
        if (field.getOpcode() != Opcodes.GETSTATIC || !field.owner.equals(owner))
            throw new AssertionError("Unrecognized direct ingredient field owner: " + field.owner + "." + field.name);
        return field.name;
    }

    private static long number(AbstractInsnNode instruction) {
        if (instruction == null) throw new AssertionError("Missing direct ingredient amount");
        if (instruction.getOpcode() >= Opcodes.ICONST_M1 && instruction.getOpcode() <= Opcodes.ICONST_5)
            return instruction.getOpcode() - Opcodes.ICONST_0;
        if (instruction.getOpcode() == Opcodes.LCONST_0 || instruction.getOpcode() == Opcodes.LCONST_1)
            return instruction.getOpcode() - Opcodes.LCONST_0;
        if (instruction instanceof IntInsnNode
            && (instruction.getOpcode() == Opcodes.BIPUSH || instruction.getOpcode() == Opcodes.SIPUSH))
            return ((IntInsnNode) instruction).operand;
        if (instruction instanceof LdcInsnNode) {
            Object value = ((LdcInsnNode) instruction).cst;
            if (value instanceof Integer || value instanceof Long) return ((Number) value).longValue();
        }
        throw new AssertionError("Unrecognized nonconstant ingredient amount opcode: " + instruction.getOpcode());
    }

    private static AbstractInsnNode previous(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do instruction = instruction.getPrevious(); while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static AbstractInsnNode next(AbstractInsnNode instruction) {
        if (instruction == null) return null;
        do instruction = instruction.getNext(); while (instruction != null && instruction.getOpcode() < 0);
        return instruction;
    }

    private static String enumKey(JsonObject entry) {
        return text(entry, "owner") + "." + text(entry, "field");
    }

    private static String text(JsonObject entry, String key) {
        String value = entry.get(key)
            .getAsString();
        if (value.trim()
            .isEmpty()) throw new AssertionError("Missing reviewed evidence: " + key);
        return value;
    }

    private static URL resource(String path) {
        URL result = CenterIngredientRegistrationContracts.class.getClassLoader()
            .getResource(path);
        if (result == null) throw new AssertionError("Missing reviewed registration resource: " + path);
        return result;
    }

    private static ClassNode read(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return node;
    }

    private static byte[] altered(byte[] bytes) {
        byte[] result = Arrays.copyOf(bytes, bytes.length + 1);
        result[bytes.length] = '\n';
        return result;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return hex(
            MessageDigest.getInstance("SHA-256")
                .digest(bytes));
    }

    private static String sourceSha256(byte[] bytes) throws Exception {
        return sha256(
            new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) {
            result.append(Character.forDigit((value >>> 4) & 15, 16));
            result.append(Character.forDigit(value & 15, 16));
        }
        return result.toString();
    }

    private void writeReport() throws Exception {
        Path output = Paths.get("build/reports/orecenter/ingredient-registration.json");
        Files.createDirectories(output.getParent());
        Files.write(
            output,
            (new GsonBuilder().setPrettyPrinting()
                .create()
                .toJson(report) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private interface CheckedCall {

        void run() throws Exception;
    }

    private static final class Snapshot {

        final JsonObject manifest;
        final Map<String, byte[]> providers = new LinkedHashMap<>(), sources = new LinkedHashMap<>();
        Path jar;
        String jarHash;
        byte[] production;

        Snapshot(JsonObject manifest) {
            this.manifest = manifest;
        }
    }
}
