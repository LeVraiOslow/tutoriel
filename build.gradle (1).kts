plugins {
    java
}

group = "fr.ivalona"
version = "1.0.0"

val paperVer = providers.gradleProperty("paperVersion").get()
val javaTarget = providers.gradleProperty("targetJava").get().toInt()

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io")
    maven("https://repo.extendedclip.com/releases/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperVer")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1")
    compileOnly("me.clip:placeholderapi:2.11.6")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.release.set(javaTarget)
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}
