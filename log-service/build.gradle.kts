import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val junitVersion = "5.10.0"
val jacocoVersion = "0.8.12"
val javaVersion = "21"

plugins {
    kotlin("jvm") version "2.0.0"
    kotlin("plugin.spring") version "2.0.0"
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
    jacoco
}

group = "com.github.logboard.log"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot Starters
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-cache")

    // Database
    implementation("org.postgresql:postgresql:42.6.0")
    implementation("org.liquibase:liquibase-core:4.24.0")

    // Cache
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Logger
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.0")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")

    // Kotest
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
}

tasks {
    compileKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion))
        }
    }

    compileTestKotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.fromTarget(javaVersion))
        }
    }

    test {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
    }

    bootJar {
        enabled = true
    }

    jar {
        enabled = false
    }

    register<Test>("integrationTest") {
        description = "Runs integration tests."
        group = "verification"

        testClassesDirs = sourceSets["integrationTest"].output.classesDirs
        classpath = sourceSets["integrationTest"].runtimeClasspath
        shouldRunAfter("test")

        useJUnitPlatform()

        reports {
            junitXml.required.set(true)
            html.required.set(true)
        }
    }
}

sourceSets {
    create("integrationTest") {
        kotlin.srcDir("src/integration-test/kotlin")
        resources.srcDir("src/integration-test/resources")
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += sourceSets.main.get().output
    }
}

configurations {
    getByName("integrationTestImplementation") {
        extendsFrom(getByName("testImplementation"))
    }
    getByName("integrationTestRuntimeOnly") {
        extendsFrom(getByName("testRuntimeOnly"))
    }
}

jacoco {
    toolVersion = jacocoVersion
}

val jacocoExclusions = listOf(
    "**/Main*",
    "**/config/**",
    "**/repository/ElasticsearchLogRepository*",
    "**/repository/ClickHouseLogRepository*",
    "**/repository/SharedApiKeyRepository*",
    "**/repository/SharedProjectMemberRepository*"
)

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    classDirectories.setFrom(
        files(classDirectories.files.map {
            fileTree(it) { exclude(jacocoExclusions) }
        })
    )
    violationRules {
        rule {
            limit {
                minimum = "0.65".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
