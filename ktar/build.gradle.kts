import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    id("com.vanniktech.maven.publish") version "0.30.0"
}

group = "io.github.mjdenham"

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    androidLibrary {
        namespace = "org.martin.ktar"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }

        withHostTestBuilder {}
    }

    val xcf = XCFramework()
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "ktar"
            xcf.add(this)
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.okio)
        }
        val androidHostTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

mavenPublishing {
    publishToMavenCentral(com.vanniktech.maven.publish.SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    coordinates("io.github.mjdenham", "ktar", "0.1.1")

    pom {
        name.set("ktar")
        description.set("Kotlin Multiplatform library for reading and writing tar and tar.gz archives.")
        inceptionYear.set("2024")
        url.set("https://github.com/mjdenham/ktar-multiplatform")
        licenses {
            license {
                name.set("GNU Lesser General Public License, version 2.1")
                url.set("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt")
                distribution.set("https://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt")
            }
        }
        developers {
            developer {
                id.set("mjdenham")
                name.set("Martin Denham")
                url.set("https://github.com/mjdenham")
            }
        }
        scm {
            url.set("https://github.com/mjdenham/ktar-multiplatform")
            connection.set("scm:git:git://github.com/mjdenham/ktar-multiplatform.git")
            developerConnection.set("scm:git:ssh://git@github.com/mjdenham/ktar-multiplatform.git")
        }
    }
}
