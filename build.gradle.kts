plugins {
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.serialization") version "2.0.21"
}

group = "setups"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.7.3")
    implementation("com.charleskorn.kaml:kaml:0.61.0")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.11.0")

    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

tasks.test {
    useJUnitPlatform()
    // Tests that check the real dataset need to know where the repo root is.
    systemProperty("project.root", rootDir.absolutePath)
}

// Builds the static site into build/site.
val generate by tasks.registering(JavaExec::class) {
    group = "site"
    description = "Generates the static site into build/site"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("setups.MainKt")
    args(
        file("data").absolutePath,
        file("site-static").absolutePath,
        layout.buildDirectory.dir("site").get().asFile.absolutePath,
    )
    // Base path the site is served from (only the 404 page uses it). Set by the deploy workflow.
    val siteBase = providers.environmentVariable("SITE_BASE").orElse("/")
    environment("SITE_BASE", siteBase.get())
    inputs.property("siteBase", siteBase)
    inputs.dir("data")
    inputs.dir("site-static")
    outputs.dir(layout.buildDirectory.dir("site"))
}

// One-off: converts the raw .txt dumps in raw/ into draft YAML under build/import.
// Review build/import/REVIEW.md, then copy the YAML into data/setups.
val importTxt by tasks.registering(JavaExec::class) {
    group = "site"
    description = "Converts raw/*.txt into draft YAML under build/import"
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("setups.importer.TxtImporterKt")
    args(
        file("raw").absolutePath,
        layout.buildDirectory.dir("import").get().asFile.absolutePath,
    )
}
