import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias (libs.plugins.composeMultiplatform)
    alias (libs.plugins.kotlinMultiplatform)
    alias (libs.plugins.composeCompiler)
    alias (libs.plugins.kotlinx.serialization)
}

val debugManagerAppVersion = "2.6.5"

group = "com.stephen"
version = debugManagerAppVersion

kotlin {
    // Desktop-only JVM target. Compose Multiplatform 1.11+ dropped macosX64;
    // macOS/Windows/Linux packages are produced via compose.desktop (JVM), not K/N frameworks.
    jvm("desktop")

    sourceSets {
        val desktopMain by getting

        commonMain.dependencies {
            implementation(libs.kotlin.coroutines)
            implementation(libs.kotlin.serialization)
            implementation(libs.navigation.compose)
            implementation(libs.koin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(libs.datastore)
            implementation(libs.datastore.preferences)
            implementation(libs.ktor.core)
            implementation(libs.ktor.websockets)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.ktor.client.logging)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.compose.markdown )
            implementation(libs.coil.compose)
            implementation(libs.log4j.slf4j2)
            implementation(libs.log4j.core)
        }
        desktopMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.stephen.debugmanager.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "DebugManager"
            packageVersion = debugManagerAppVersion
            description = "DebugManager"
            copyright = "C 2024 Stephen. All rights reserved."
            vendor = "Stephen Zhan"
            licenseFile.set(project.file("../LICENSE"))
            modules("java.instrument", "jdk.unsupported")
            // 配置打包保留文件，不同平台下区分
            appResourcesRootDir.set(project.layout.projectDirectory.dir("../resources"))
            windows {
                includeAllModules = true
                packageVersion = debugManagerAppVersion
                msiPackageVersion = debugManagerAppVersion
                exePackageVersion = debugManagerAppVersion
                menu = true
                shortcut = true
                // 可自行选择安装目录
                dirChooser = true
                // 可单独为当前用户安装，不需要管理员权限
                perUserInstall = true
                // 设置图标
                iconFile.set(project.file("../launcher/logo.ico"))
                upgradeUuid = "888888-8888-8888-8888-888888888888"
            }
            linux {
                packageVersion = debugManagerAppVersion
                debPackageVersion = debugManagerAppVersion
                rpmPackageVersion = debugManagerAppVersion
                iconFile.set(project.file("../launcher/logo.png"))
            }
            macOS {
                bundleID = "com.stephen.debugmanager"
                includeAllModules = true
                packageVersion = debugManagerAppVersion
                dmgPackageVersion = debugManagerAppVersion
                pkgPackageVersion = debugManagerAppVersion
                // 显示在菜单栏、“关于”菜单项、停靠栏等中的应用程序名称
                dockName = "DebugManager"
                packageBuildVersion = debugManagerAppVersion
                dmgPackageBuildVersion = debugManagerAppVersion
                pkgPackageBuildVersion = debugManagerAppVersion
                // 设置图标
                iconFile.set(project.file("../launcher/logo.icns"))
                entitlementsFile.set(project.file("../default.entitlements"))
            }
        }
    }
}