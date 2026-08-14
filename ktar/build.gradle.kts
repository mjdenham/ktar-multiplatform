import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKmpLibrary)
    alias(libs.plugins.vanniktechPublish)
}

group = "io.github.mjdenham"
version = providers.gradleProperty("VERSION_NAME").get()

kotlin {
    // Published library: require explicit visibility and return types on the public API so that
    // nothing becomes part of the API surface by accident.
    explicitApi()

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
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// The test suite is compiled for every target, which type checks it against native as well as the
// JVM, but it is only run on the JVM and Android host. The tests read fixture archives from the
// repository's testFiles folder by relative path, and that folder is not reachable from inside the
// iOS simulator's sandbox. Running them there would need the fixtures embedded in the test binary.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    enabled = false
}

mavenPublishing {
    publishToMavenCentral()
    signAllPublications()

    // group and version come from the project, set above from VERSION_NAME
    coordinates(artifactId = "ktar")

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
