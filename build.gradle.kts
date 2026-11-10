
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

tasks.named("check") {
    dependsOn(verifyRigMeshContracts)
    dependsOn(verifyEchoRosterContracts)
    dependsOn(verifyCriticalJsonContracts)
    dependsOn(verifyRemasterJsonContracts)
    dependsOn(verifyWeaponModelContracts)
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
