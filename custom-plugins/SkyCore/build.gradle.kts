// SkyCore'u Gradle ile derlemek icin (gelistiriciler):  gradle build  ->  build/libs/SkyCore-<surum>.jar
// Not: Hazir jar server/plugins/ icinde. Bu dosya kurulum ortaminda calistirilmadi; orada eklenti gercek
// Paper 26.1.2 API kaynagina karsi javac ile derlendi ve MockBukkit testleri calistirildi.
plugins {
    java
}

group = "net.skysurvival"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://jitpack.io") // VaultAPI
    maven("https://repo.codemc.io/repository/maven-public/") // AuthMe
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    compileOnly("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    compileOnly("fr.xephi:authme:5.6.0-SNAPSHOT") { isTransitive = false }

    // MockBukkit henuz 26.x'i desteklemiyor; testler Paper 1.21.11 API'siyle calisir
    // (SkyCore'un kullandigi API iki surumde de ayni).
    testImplementation("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:4.116.3")
    testImplementation("com.github.MilkBowl:VaultAPI:1.7.1") { isTransitive = false }
    testImplementation(platform("org.junit:junit-bom:6.0.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    // Paper 26.1 API'si Java 25 ile derlendigi icin JDK 25 gerekir; cikti Java 21+ ile calisir.
    toolchain.languageVersion = JavaLanguageVersion.of(25)
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.test {
    useJUnitPlatform()
}
