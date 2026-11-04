import java.util.Properties

plugins {
    java
    id("edu.sc.seis.launch4j") version "3.0.5"
}

launch4j {
    mainClassName = "view.UrnaView"
    outfile = "Urna-Eletronica.exe"
    headerType = "gui"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.code.gson:gson:2.14.0")
    testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

val firebaseRuntimeConfigDir = layout.buildDirectory.dir("generated/firebase-runtime-config")

val generateFirebaseRuntimeConfig = tasks.register("generateFirebaseRuntimeConfig") {
    val configFile = firebaseRuntimeConfigDir.map { it.file("firebase.properties") }
    outputs.file(configFile)
    outputs.upToDateWhen { false }
    outputs.cacheIf("Contains Firebase runtime configuration") { false }

    doLast {
        val properties = Properties()
        properties.setProperty("apiKey", providers.environmentVariable("FIREBASE_API_KEY").orElse("").get())
        properties.setProperty("databaseUrl", providers.environmentVariable("FIREBASE_DATABASE_URL").orElse("").get())
        val output = configFile.get().asFile
        output.parentFile.mkdirs()
        output.outputStream().use { stream -> properties.store(stream, "Firebase runtime configuration") }
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("Urna/src"))
            exclude("test/**")
        }
        resources {
            srcDir(firebaseRuntimeConfigDir)
        }
    }
    test {
        java {
            setSrcDirs(listOf("Urna/src/test/java"))
        }
    }
}

tasks.processResources {
    dependsOn(generateFirebaseRuntimeConfig)
}
