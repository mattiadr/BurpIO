plugins {
    kotlin("jvm") version "2.3.10"
    id("com.gradleup.shadow") version "9.4.1"
}

group = "com.mattiadr"
version = "1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    compileOnly("net.portswigger.burp.extensions:montoya-api:2026.4")
}

kotlin {
    jvmToolchain(21)
}

tasks {
    shadowJar {
        archiveClassifier = ""
        archiveVersion = ""
    }
}
