import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val junitVersion = "5.10.0"
val jacocoVersion = "0.8.12"
val javaVersion = "21"

plugins {
    kotlin("jvm") version "2.0.0"
    jacoco
}

group = "com.github.frubyl.logboard.core"
version = "0.0.1-SNAPSHOT"

repositories {
    mavenCentral()
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
}

jacoco {
    toolVersion = jacocoVersion
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}
