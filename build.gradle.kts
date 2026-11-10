
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Compatibility contracts are executable mains, run through tools/compat/gtnl/contracts.init.gradle.
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}

// Build tools use only the same pure Java 8 codec as runtime, without Minecraft or external executables.
val assetCompiler = sourceSets.create("assetCompiler") {
    java.setSrcDirs(listOf("src/main/java", "tools/model-compiler/java"))
    java.include("com/miaokatze/gtsr/client/encounter/RigMeshData.java")
    java.include("com/miaokatze/gtsr/tools/RigMeshCompiler.java")
}

tasks.named<JavaCompile>(assetCompiler.compileJavaTaskName) {
    options.release.set(8)
}

val encounterModels = fileTree("src/main/resources") {
    include("assets/gtsr/liminal/*/models/*.obj")
}
val activeModelManifest = layout.projectDirectory.file("src/main/resources/assets/gtsr/liminal/active-models.txt")
val inactiveSoundManifest = layout.projectDirectory.file("src/main/resources/assets/gtsr/sounds/inactive-sounds.txt")
fun manifestLines(text: String): List<String> = text.lineSequence()
    .map { it.substringBefore('#').trim() }.filter { it.isNotEmpty() }.toList()
val activeModelCodes = manifestLines(providers.fileContents(activeModelManifest).asText.get()).toSet()
val inactiveSoundPaths = manifestLines(providers.fileContents(inactiveSoundManifest).asText.get())
require(activeModelCodes.isNotEmpty() && activeModelCodes.all { it.matches(Regex("[a-z0-9_-]+")) }) {
    "Invalid active encounter model manifest"
}
val retiredModelPaths = encounterModels.files.map { it.parentFile.parentFile.name }.toSet()
    .minus(activeModelCodes).map { "assets/gtsr/liminal/$it/**" }
require(inactiveSoundPaths.all { it.startsWith("assets/gtsr/sounds/combat/") && it.endsWith(".ogg") && !it.contains("..") }) {
    "Invalid retired sound path"
}
val compiledRigMeshes = layout.buildDirectory.dir("generated/rigmeshes")
val compileRigMeshes = tasks.register<JavaExec>("compileRigMeshes") {
    group = "build"
    description = "Compile and roundtrip all full precision encounter meshes."
    dependsOn(assetCompiler.classesTaskName)
    classpath = assetCompiler.runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.tools.RigMeshCompiler")
    inputs.files(encounterModels).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(activeModelManifest)
    inputs.dir("src/main/resources/assets/gtsr/textures/blocks")
    inputs.dir("src/main/resources/assets/gtsr/textures/items/lore")
    inputs.files(
        "src/main/resources/assets/gtsr/textures/items/spacetime_anchor_beacon.png",
        "src/main/resources/assets/gtsr/textures/items/spacetime_anchor_beacon_needle.png",
        "src/main/resources/assets/gtsr/textures/items/AbyssalObsessionBucket.png",
    )
    outputs.dir(compiledRigMeshes)
    args(file("src/main/resources").absolutePath, compiledRigMeshes.get().asFile.absolutePath)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(compileRigMeshes)
    exclude("assets/gtsr/liminal/*/models/*.obj")
    exclude(retiredModelPaths)
    exclude(inactiveSoundPaths)
    exclude("assets/gtsr/sounds/inactive-sounds.txt")
    from(compiledRigMeshes)
}

// Source resources remain original OBJ files; only processed runtime resources substitute .rigmesh files.
// Keep them explicitly available in sources archives even when the convention plugin uses processed resources.
tasks.matching { it.name == "sourcesJar" }.configureEach {
    (this as Jar).from("src/main/resources") {
        include("assets/gtsr/liminal/*/models/*.obj")
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
}

val verifyRigMeshContracts = tasks.register<JavaExec>("verifyRigMeshContracts") {
    group = "verification"
    description = "Verify mesh parsing, compiled input defenses and all 40 independent legacy golden models."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.client.encounter.RigMeshDataContracts")
    maxHeapSize = "512m"
    args(
        file("src/main/resources/assets/gtsr/liminal").absolutePath,
        file("src/test/resources/encounter/rigmesh-golden.tsv").absolutePath,
    )
    inputs.files(encounterModels).withPathSensitivity(PathSensitivity.RELATIVE)
    inputs.file(activeModelManifest)
    inputs.file("src/test/resources/encounter/rigmesh-golden.tsv")
}

val verifyEchoRosterContracts = tasks.register<JavaExec>("verifyEchoRosterContracts") {
    group = "verification"
    description = "Verify retired monster mappings, gameplay dependencies and the runtime sound closure."
    dependsOn(tasks.named("testClasses"))
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.common.dimension.prosperity.echo.EchoRosterContracts")
    maxHeapSize = "512m"
    args(file("src/main/resources").absolutePath)
}

tasks.named("check") {
    dependsOn(verifyRigMeshContracts)
    dependsOn(verifyEchoRosterContracts)
}
