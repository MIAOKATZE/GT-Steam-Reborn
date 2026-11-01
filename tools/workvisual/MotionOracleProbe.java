import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.miaokatze.gtsr.common.machinevisual.MachineVisualDefinition;
import com.miaokatze.miaokatzecore.api.animation.Sampler;

/** Pure production sampler probe. No Forge launch, world, client, GL or manual phase injection. */
public final class MotionOracleProbe {

    private static String digest(byte[] bytes) throws Exception {
        StringBuilder result = new StringBuilder();
        for (byte value : MessageDigest.getInstance("SHA-256").digest(bytes)) {
            result.append(String.format("%02x", value & 255));
        }
        return result.toString();
    }

    private static JsonObject codeSource(Class<?> type, String expected) throws Exception {
        File actual = new File(type.getProtectionDomain().getCodeSource().getLocation().toURI()).getCanonicalFile();
        if (!actual.equals(new File(expected).getCanonicalFile()) || !actual.isFile()) {
            throw new IllegalStateException(type.getName() + " did not load from the explicit candidate JAR: " + actual);
        }
        JsonObject record = new JsonObject();
        record.addProperty("class", type.getName());
        record.addProperty("file", actual.getName());
        record.addProperty("sha256", digest(Files.readAllBytes(actual.toPath())));
        return record;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) throw new IllegalArgumentException("requests.json output.json candidate-dev.jar core-dev.jar");
        JsonArray origins = new JsonArray();
        origins.add(codeSource(MachineVisualDefinition.class, args[2]));
        origins.add(codeSource(Sampler.class, args[3]));
        byte[] definitionBytes;
        try (InputStream stream = MachineVisualDefinition.class.getResourceAsStream("/assets/gtsr/machinevisual/motions.json")) {
            if (stream == null) throw new IllegalStateException("Candidate definitions missing");
            definitionBytes = com.google.common.io.ByteStreams.toByteArray(stream);
        }
        JsonObject requests;
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(Paths.get(args[0])), StandardCharsets.UTF_8)) {
            requests = new JsonParser().parse(reader).getAsJsonObject();
        }
        if (requests.get("schema").getAsInt() != 1 || requests.getAsJsonArray("cases").size() == 0) {
            throw new IllegalArgumentException("Nonempty schema1 scalar requests required");
        }
        JsonArray cases = new JsonArray();
        for (JsonElement item : requests.getAsJsonArray("cases")) {
            JsonObject request = item.getAsJsonObject();
            MachineVisualDefinition definition = MachineVisualDefinition.get(request.get("definition").getAsString());
            MachineVisualDefinition.Group selected = null;
            for (MachineVisualDefinition.Group group : definition.groups) {
                if (group.id.equals(request.get("group").getAsString())) selected = group;
            }
            if (selected == null) throw new IllegalArgumentException("Unknown requested group");
            long milliseconds = request.get("timeMs").getAsLong();
            double value = selected.sample(milliseconds);
            if (!Double.isFinite(value) || value != selected.sample(milliseconds)) {
                throw new IllegalStateException("Non-finite or impure repeated sample");
            }
            JsonObject record = new JsonObject();
            record.addProperty("id", request.get("id").getAsString());
            JsonArray values = new JsonArray();
            values.add(new com.google.gson.JsonPrimitive(value));
            record.add("values", values);
            cases.add(record);
        }
        JsonObject result = new JsonObject();
        result.addProperty("schema", 1);
        result.addProperty("status", "PASS");
        result.addProperty("kind", "production_sampler_probe");
        result.addProperty("definitionSha256", digest(definitionBytes));
        result.add("codeSources", origins);
        result.add("cases", cases);
        result.addProperty("boundary", "Actual candidate MachineVisualDefinition.Group.sample -> locked Core Sampler; pure Java numeric/repeatability only, no game or GL acceptance.");
        Files.write(Paths.get(args[1]), (new GsonBuilder().setPrettyPrinting().create().toJson(result) + "\n").getBytes(StandardCharsets.UTF_8));
        System.out.println("PASS production scalar cases=" + cases.size() + "; game=NOT_RUN");
    }
}
