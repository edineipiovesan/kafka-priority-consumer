import org.gradle.kotlin.dsl.version
import com.adarshr.gradle.testlogger.TestLoggerExtension
import com.adarshr.gradle.testlogger.TestLoggerPlugin
import com.adarshr.gradle.testlogger.theme.ThemeType.MOCHA
import org.gradle.api.logging.LogLevel.LIFECYCLE
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.0.5"
    id("io.spring.dependency-management") version "1.1.0"
    id("com.adarshr.test-logger") version "3.2.0"
    kotlin("jvm") version "1.8.20"
    kotlin("plugin.spring") version "1.8.20"

    id("io.github.janbarari.gradle-analytics-plugin") version "1.0.1"
}

group = "com.github.edn"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17

repositories {
    mavenCentral()
    maven(url = uri("https://packages.confluent.io/maven/"))
    maven(url = uri("https://repo.spring.io/milestone"))
}

extra["springCloudVersion"] = "2022.0.2"

dependencies {
    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.0-RC")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")

    // Spring
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    compileOnly("org.springframework.boot:spring-boot-configuration-processor")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    // Monitoring
    implementation("io.micrometer:micrometer-registry-prometheus:1.11.2")

    // Jackson support for Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Avro serialization
    implementation("org.apache.avro:avro:1.11.1")
    implementation("io.confluent:kafka-avro-serializer:7.3.3")
    implementation("io.confluent:kafka-streams-avro-serde:7.3.3")

    // Distributed rate limit
    // implementation("com.bucket4j:bucket4j-core:8.3.0")
    // implementation("com.bucket4j:bucket4j-redis:8.3.0")
    // implementation("io.lettuce:lettuce-core:6.2.4.RELEASE")

    // AWS
    implementation(platform("software.amazon.awssdk:bom:2.20.56"))
    implementation("software.amazon.awssdk:dynamodb-enhanced")
    implementation("software.amazon.awssdk:netty-nio-client")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test") {
        exclude("org.junit.vintage:junit-vintage-engine")
    }
    testImplementation("org.springframework.kafka:spring-kafka-test")

    testImplementation("io.kotest:kotest-assertions-core-jvm:4.2.0")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.6.2")
    testCompileOnly("org.testcontainers:testcontainers:1.15.2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
    }
}

/**
 * Apache AVRO settings
 */
buildscript {
    dependencies {
        classpath("org.apache.avro:avro-tools:1.11.2")
    }
}

val avroGen by tasks.register("generateAvroJavaClasses") {
    val sourceAvroFiles = fileTree("src/main/resources/avro") { include("**/*.avsc") }
    val generatedJavaDir = File("$rootDir/src/main/java")

    inputs.files(sourceAvroFiles)
    outputs.dir(generatedJavaDir)

    doLast {
        sourceAvroFiles.forEach { avroFile ->
            val schema = org.apache.avro.Schema.Parser().parse(avroFile)
            val compiler = org.apache.avro.compiler.specific.SpecificCompiler(schema)
            compiler.setFieldVisibility(org.apache.avro.compiler.specific.SpecificCompiler.FieldVisibility.PRIVATE)
            compiler.setOutputCharacterEncoding("UTF-8")
            compiler.setStringType(org.apache.avro.generic.GenericData.StringType.CharSequence)
            compiler.compileToDestination(avroFile, generatedJavaDir)
        }
    }
}

tasks.named("clean", Delete::class) {
    delete("src/main/java")
}

tasks.withType<JavaCompile> {
    source(avroGen)
}

/**
 * Compiler tasks
 */
tasks.withType<KotlinCompile> {
    dependsOn(avroGen)
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=all-compatibility")
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

/**
 * Test console output
 */
plugins.withType<TestLoggerPlugin> {
    configure<TestLoggerExtension> {
        theme = MOCHA
        showExceptions = true
        showStackTraces = false
        showFullStackTraces = false
        showCauses = true
        slowThreshold = 5000
        showSummary = true
        showSimpleNames = true
        showPassed = true
        showSkipped = true
        showFailed = true
        showStandardStreams = false
        showPassedStandardStreams = true
        showSkippedStandardStreams = true
        showFailedStandardStreams = true
        logLevel = LIFECYCLE
    }
}


//
gradleAnalyticsPlugin {
    enabled = true // Optional: By default it's True.

    database {
        local = sqlite {
            path = "/build/"
            name = "localdb"
        }
    }

    trackingTasks = setOf(
        // Add your requested tasks to be analyzed, Example:
        ":app:assembleDebug",
        ":jar",
        ":assemble"
    )

    trackingBranches = setOf(
        // requested tasks only analyzed in the branches you add here, Example:
        "main",
        "master",
        "develop"
    )

    // Optional: Exclude modules that are not necessary like test or demo modules
    excludeModules = setOf()

    trackAllBranchesEnabled = false // Optional: Default is False.
}