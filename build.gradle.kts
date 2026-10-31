
plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Compatibility contracts are executable mains, run through tools/compat/gtnl/contracts.init.gradle.
tasks.withType<Test>().configureEach {
    failOnNoDiscoveredTests = false
}
