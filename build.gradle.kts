
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

abstract class RuntimeArchiveExec {
    @get:javax.inject.Inject
    abstract val execOperations: org.gradle.process.ExecOperations
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
    java.include("com/miaokatze/gtsr/common/critical/CriticalJsonData.java")
    java.include("com/miaokatze/gtsr/tools/CriticalJsonCompiler.java")
    java.include("com/miaokatze/gtsr/tools/RemasterJsonCompiler.java")
    java.include("com/miaokatze/gtsr/tools/CompactRuntimeJar.java")
}

tasks.named<JavaCompile>(assetCompiler.compileJavaTaskName) {
    options.release.set(8)
}

// Forge remains the OBJ parser and face renderer; the compiler needs its mapped classes, not main outputs.
// The Java 8 tool does not use Jabel. Its compiler-plugin service must not auto-load from the game classpath.
val mappedWeaponClasspath = sourceSets["main"].compileClasspath.filter { !it.name.startsWith("jabel-") }
val weaponAssetCompiler = sourceSets.create("weaponAssetCompiler") {
    java.setSrcDirs(listOf("src/main/java", "tools/model-compiler/java"))
    java.include("com/miaokatze/gtsr/client/weapons/WeaponModelData.java")
    java.include("com/miaokatze/gtsr/client/weapons/ForgeWeaponModel.java")
    java.include("com/miaokatze/gtsr/tools/WeaponModelCompiler.java")
    compileClasspath += mappedWeaponClasspath
    runtimeClasspath += mappedWeaponClasspath
}
tasks.named<JavaCompile>(weaponAssetCompiler.compileJavaTaskName) {
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

val criticalModels = fileTree("src/main/resources") {
    include("assets/gtsr/critical/models/*.geometry.json")
}
val compiledCriticalModels = layout.buildDirectory.dir("generated/critical-json")
val compileCriticalModels = tasks.register<JavaExec>("compileCriticalModels") {
    group = "build"
    description = "Compile canonical and compatibility critical models with exact JSON token/order roundtrips."
    dependsOn(assetCompiler.classesTaskName)
    classpath = assetCompiler.runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.tools.CriticalJsonCompiler")
    maxHeapSize = "512m"
    inputs.files(criticalModels).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(compiledCriticalModels)
    args(file("src/main/resources").absolutePath, compiledCriticalModels.get().asFile.absolutePath)
}

val remasterSources = fileTree("src/main/resources") {
    include("assets/gtsr/remaster/prefabs/**/*.json.gz")
}
val compiledRemasterJson = layout.buildDirectory.dir("generated/remaster-json")
val compileRemasterJson = tasks.register<JavaExec>("compileRemasterJson") {
    group = "build"
    description = "Compile all prefab headers and slices with exact decompressed-byte roundtrips."
    dependsOn(assetCompiler.classesTaskName)
    classpath = assetCompiler.runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.tools.RemasterJsonCompiler")
    maxHeapSize = "256m"
    inputs.files(remasterSources).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(compiledRemasterJson)
    args(file("src/main/resources").absolutePath, compiledRemasterJson.get().asFile.absolutePath)
}

val weaponSources = fileTree("src/main/resources") {
    include("assets/gtsr/models/weapons/*.obj")
}
val compiledWeaponModels = layout.buildDirectory.dir("generated/weapon-models")
val compileWeaponModels = tasks.register<JavaExec>("compileWeaponModels") {
    group = "build"
    description = "Compile exact Forge-parsed weapon geometry while retaining native face rendering."
    dependsOn(weaponAssetCompiler.classesTaskName)
    classpath = weaponAssetCompiler.runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.tools.WeaponModelCompiler")
    maxHeapSize = "256m"
    inputs.files(weaponSources).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(compiledWeaponModels)
    args(file("src/main/resources").absolutePath, compiledWeaponModels.get().asFile.absolutePath)
}

tasks.named<ProcessResources>("processResources") {
    dependsOn(compileRigMeshes, compileCriticalModels, compileRemasterJson, compileWeaponModels)
    exclude("assets/gtsr/liminal/*/models/*.obj")
    exclude("assets/gtsr/critical/models/*.geometry.json")
    exclude("assets/gtsr/remaster/prefabs/**/*.json.gz")
    exclude("assets/gtsr/models/weapons/*.obj")
    exclude(retiredModelPaths)
    exclude(inactiveSoundPaths)
    exclude("assets/gtsr/sounds/inactive-sounds.txt")
    from(compiledRigMeshes)
    from(compiledCriticalModels)
    from(compiledRemasterJson)
    from(compiledWeaponModels)
}

// Author resources remain original OBJ/JSON files; only runtime resources use compiled representations.
// Keep them explicitly available in sources archives even when the convention plugin uses processed resources.
tasks.matching { it.name == "sourcesJar" }.configureEach {
    (this as Jar).from("src/main/resources") {
        include("assets/gtsr/liminal/*/models/*.obj")
        include("assets/gtsr/critical/models/*.geometry.json")
        include("assets/gtsr/remaster/prefabs/**/*.json.gz")
        include("assets/gtsr/models/weapons/*.obj")
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

val verifyCriticalJsonContracts = tasks.register<JavaExec>("verifyCriticalJsonContracts") {
    group = "verification"
    description = "Verify strict lossless decoding and the actual binary-only critical resource loader."
    dependsOn(tasks.named("testClasses"), compileCriticalModels)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.common.critical.CriticalJsonDataContracts")
    maxHeapSize = "256m"
    args(file("src/main/resources").absolutePath, compiledCriticalModels.get().asFile.absolutePath)
    inputs.files(criticalModels).withPathSensitivity(PathSensitivity.RELATIVE)
}

val verifyRemasterJsonContracts = tasks.register<JavaExec>("verifyRemasterJsonContracts") {
    group = "verification"
    description = "Verify actual prefab/entrance/slice loading, full data identity and bounded lazy caches."
    dependsOn(tasks.named("testClasses"), compileRemasterJson)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.common.dimension.prosperity.remaster.RemasterCatalogContracts")
    maxHeapSize = "256m"
    args(
        file("src/main/resources").absolutePath,
        compiledRemasterJson.get().asFile.absolutePath,
        layout.buildDirectory.dir("tmp/remaster-contracts").get().asFile.absolutePath,
    )
}

val verifyWeaponModelContracts = tasks.register<JavaExec>("verifyWeaponModelContracts") {
    group = "verification"
    description = "Compare native Forge draw events, float bits and resource override behavior."
    dependsOn(tasks.named("testClasses"), compileWeaponModels)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.client.weapons.WeaponModelContracts")
    maxHeapSize = "256m"
    args(file("src/main/resources").absolutePath)
}

val verifyBossCombatContracts = tasks.register<JavaExec>("verifyBossCombatContracts") {
    group = "verification"
    description = "Verify boss damage scope, real health attributes, potion lifecycle and shared cast timing."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath
    mainClass.set("com.miaokatze.gtsr.common.dimension.prosperity.encounter.BossCombatContracts")
    maxHeapSize = "256m"
}

// GT's public entry classes link these APIs even though this fixture never runs their mod lifecycle.
val portableRecipeContractRuntime = configurations.create("portableRecipeContractRuntime") {
    isTransitive = false
}
dependencies {
    add(portableRecipeContractRuntime.name, "com.falsepattern:chunkapi-mc1.7.10:0.8.3:dev")
    add(portableRecipeContractRuntime.name, "com.falsepattern:endlessids-mc1.7.10:1.7.3:dev")
    add(portableRecipeContractRuntime.name, "com.falsepattern:falsepatternlib-mc1.7.10:1.12.0:dev")
    // Same Java-17 multi-release shim used by tools/tests/turbine_restart/run.py.
    add(portableRecipeContractRuntime.name, "xyz.wagyourtail.jvmdowngrader:jvmdowngrader-java-api:1.3.5:downgraded-8")
}
val verifyPortableWeaponRecipeContracts = tasks.register<JavaExec>("verifyPortableWeaponRecipeContracts") {
    group = "verification"
    description = "Verify portable recipes and schematic/cache isolation with the native GT .265 backend."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath +
        sourceSets["main"].compileClasspath + portableRecipeContractRuntime
    mainClass.set("com.miaokatze.gtsr.loader.recipes.PortableWeaponRecipeContracts")
    maxHeapSize = "512m"
    systemProperty("java.awt.headless", "true")
    val contractDirectory = layout.buildDirectory.dir("portable-recipe-contract").get().asFile
    workingDir(contractDirectory)
    doFirst { contractDirectory.mkdirs() }
}

val verifyTurbineCasingLightContracts = tasks.register<JavaExec>("verifyTurbineCasingLightContracts") {
    group = "verification"
    description = "Verify native light reuse and world boundaries within a single turbine render."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("com.miaokatze.gtsr.client.machinevisual.MachineVisualRenderAccessContracts")
    maxHeapSize = "256m"
}

val verifyTurbineMeshContracts = tasks.register<JavaExec>("verifyTurbineMeshContracts") {
    group = "verification"
    description = "Check native turbine mesh reuse and retirement without creating a GL context."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("com.miaokatze.gtsr.client.machinevisual.MachineVisualMeshCacheContracts")
    maxHeapSize = "256m"
}

val verifyMachineDecorationContracts = tasks.register<JavaExec>("verifyMachineDecorationContracts") {
    group = "verification"
    description = "Verify client animation admission and geometry without querying the world for clearance."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("com.miaokatze.gtsr.client.machinevisual.MachineDecorationContracts")
    maxHeapSize = "256m"
}

val verifyProsperityClientIdentityContracts = tasks.register<JavaExec>("verifyProsperityClientIdentityContracts") {
    group = "verification"
    description = "Verify server-login recipient identity and queued-client lifecycle without a game."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("com.miaokatze.gtsr.client.network.ClientRecipientIdentityContracts")
    maxHeapSize = "256m"
    systemProperty("java.awt.headless", "true")
}
val verifyProsperityLoginMixinContracts = tasks.register<JavaExec>("verifyProsperityLoginMixinContracts") {
    group = "verification"
    description = "Apply the client login Mixins to actual Forge 1.7.10 classes without a game."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("com.miaokatze.gtsr.client.network.LoginIdentityMixinContracts")
    args(layout.buildDirectory.dir("prosperity-login-mixin-contract/mcp").get().asFile.absolutePath, "mcp")
    val mappings = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.GenSrgMappingsTask>("generateForgeSrgMappings")
    systemProperty("mixin.env.remapRefMap", "true")
    systemProperty("mixin.env.refMapRemappingFile", mappings.get().srgToMcp.get().asFile.absolutePath)
    maxHeapSize = "256m"
    systemProperty("java.awt.headless", "true")
}
// Use the same official Forge-patched classes and production MCP-to-SRG toolchain, never hand-renamed fixtures.
val loginIdentityMinecraft = tasks.register<Jar>("loginIdentityMinecraft") {
    dependsOn(tasks.named("patchedMcClasses"))
    from(sourceSets["patchedMc"].output)
    include("net/minecraft/client/network/NetHandlerLoginClient.class")
    include("net/minecraft/network/login/server/S02PacketLoginSuccess.class")
    archiveFileName.set("forge-1614-login-mcp.jar")
    destinationDirectory.set(layout.buildDirectory.dir("prosperity-login-mixin-contract/input"))
}
val loginSrgMinecraft = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfLoginIdentityMinecraft") {
    archiveFileName.set("forge-1614-login-srg.jar")
}
val loginRuntimeJar = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfJar")
val verifyProsperityLoginSrgMixinContracts = tasks.register<JavaExec>("verifyProsperityLoginSrgMixinContracts") {
    group = "verification"
    description = "Apply the login Mixins and production refmap to official Forge 1614 classes reobfuscated to SRG."
    dependsOn(tasks.named("testClasses"), loginSrgMinecraft, loginRuntimeJar)
    classpath = files(loginSrgMinecraft.flatMap { it.archiveFile }, loginRuntimeJar.flatMap { it.archiveFile }) +
        sourceSets["test"].runtimeClasspath + sourceSets["main"].compileClasspath
    mainClass.set("com.miaokatze.gtsr.client.network.LoginIdentityMixinContracts")
    args(layout.buildDirectory.dir("prosperity-login-mixin-contract/srg").get().asFile.absolutePath, "srg",
        loginRuntimeJar.get().archiveFile.get().asFile.absolutePath)
    maxHeapSize = "256m"
    systemProperty("java.awt.headless", "true")
}

tasks.named("check") {
    dependsOn(verifyBossCombatContracts)
    dependsOn(verifyRigMeshContracts)
    dependsOn(verifyEchoRosterContracts)
    dependsOn(verifyCriticalJsonContracts)
    dependsOn(verifyRemasterJsonContracts)
    dependsOn(verifyWeaponModelContracts)
    dependsOn(verifyPortableWeaponRecipeContracts)
    dependsOn(verifyTurbineCasingLightContracts)
    dependsOn(verifyTurbineMeshContracts)
    dependsOn(verifyMachineDecorationContracts)
    dependsOn(verifyProsperityClientIdentityContracts)
    dependsOn(verifyProsperityLoginMixinContracts)
    dependsOn(verifyProsperityLoginSrgMixinContracts)
}

// Gradle writes nonempty parent directories too. Rewrite only runtime archives through standard Java ZIP APIs.
// Keep the two directory entries used by the manifest convention and the existing texture diagnostic.
tasks.named<Jar>("jar") {
    dependsOn(assetCompiler.classesTaskName)
    val runner = objects.newInstance<RuntimeArchiveExec>()
    val toolClasspath = assetCompiler.runtimeClasspath
    val producedArchive = archiveFile
    val ownedBuild = layout.buildDirectory
    inputs.files(toolClasspath).withPathSensitivity(PathSensitivity.RELATIVE)
    doLast {
        runner.execOperations.javaexec {
            classpath = toolClasspath
            mainClass.set("com.miaokatze.gtsr.tools.CompactRuntimeJar")
            maxHeapSize = "256m"
            args(producedArchive.get().asFile.absolutePath, ownedBuild.get().asFile.absolutePath)
        }
    }
}

tasks.named("reobfJar") {
    dependsOn(assetCompiler.classesTaskName)
    val runner = objects.newInstance<RuntimeArchiveExec>()
    val toolClasspath = assetCompiler.runtimeClasspath
    val producedArchives = outputs.files
    val ownedBuild = layout.buildDirectory
    inputs.files(toolClasspath).withPathSensitivity(PathSensitivity.RELATIVE)
    doLast {
        producedArchives.files.filter { it.isFile && it.extension == "jar" }.forEach { archive ->
            runner.execOperations.javaexec {
                classpath = toolClasspath
                mainClass.set("com.miaokatze.gtsr.tools.CompactRuntimeJar")
                maxHeapSize = "256m"
                args(archive.absolutePath, ownedBuild.get().asFile.absolutePath)
            }
        }
    }
}

// A complete replacement variant, never an addon to the ordinary GTSR jar.
val testlabSourceSet = sourceSets.create("testlab") {
    compileClasspath += sourceSets.main.get().output + sourceSets.main.get().compileClasspath
    runtimeClasspath += output + compileClasspath + sourceSets.main.get().runtimeClasspath
}
configurations.named(testlabSourceSet.implementationConfigurationName) { extendsFrom(configurations.implementation.get()) }
configurations.named(testlabSourceSet.compileOnlyConfigurationName) { extendsFrom(configurations.compileOnly.get()) }
configurations.named(testlabSourceSet.annotationProcessorConfigurationName) { extendsFrom(configurations.annotationProcessor.get()) }
sourceSets.test {
    compileClasspath += testlabSourceSet.output
    runtimeClasspath += testlabSourceSet.output
}
val testlabJar = tasks.register<Jar>("testlabJar") {
    group = "build"
    description = "Complete opt-in testlab replacement; do not install alongside the ordinary jar."
    archiveClassifier.set("testlab-dev")
    // Reuse every production CopySpec, including convention-added injected tags and mixin source sets.
    with(tasks.jar.get())
    from(testlabSourceSet.output)
    manifest.from(tasks.jar.get().manifest)
    manifest.attributes("Miaokatze-Testlab-Variant" to "replacement")
}
// RFG 2.x creates and fully configures this task through its reobf<JarTaskName> rule.
tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfTestlabJar") {
    archiveClassifier.set("testlab")
    referenceClasspath.from(testlabSourceSet.compileClasspath)
    val productionReobf = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfJar")
    extraSrgFiles.from(productionReobf.map { it.extraSrgFiles })
    extraSrgEntries.addAll(productionReobf.flatMap { it.extraSrgEntries })
}

val verifyTestLabContracts = tasks.register<JavaExec>("verifyTestLabContracts") {
    group = "verification"
    description = "Verify disabled implementation linkage, catalog selection, and independent report-write retry."
    dependsOn(tasks.named("testClasses"))
    classpath = files(layout.buildDirectory.dir("classes/java/patchedMc")) + sourceSets["test"].runtimeClasspath + sourceSets.main.get().compileClasspath
    mainClass.set("com.miaokatze.gtsr.common.dimension.testlab.TestLabContracts")
    args(layout.buildDirectory.file("reports/testlab/cases.json").get().asFile.absolutePath)
    maxHeapSize = "512m"
}
tasks.named("check") { dependsOn(verifyTestLabContracts) }

// Bind reports to the checkout that produced the loaded archive, including intentional local deletions.
val testlabSourceCommit = providers.exec { commandLine("git", "rev-parse", "HEAD") }
    .standardOutput.asText.map { it.trim() }
val testlabSourceDirty = providers.exec { commandLine("git", "status", "--porcelain", "--untracked-files=no") }
    .standardOutput.asText.map { it.isNotBlank().toString() }
tasks.withType<Jar>().configureEach {
    manifest.attributes(
        "Miaokatze-Source-Commit" to testlabSourceCommit.get(),
        "Miaokatze-Source-Dirty" to testlabSourceDirty.get(),
    )
}

// Package and verify the project-specific code and independent-asset licenses.
apply(from = "gradle/licensing.gradle.kts")
