import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// AGP 9 内置 Kotlin 支持：无需 org.jetbrains.kotlin.android 插件。
// Kotlin jvmTarget 自动跟随 compileOptions.targetCompatibility（Java 17）。

// 发布签名（M5）：密钥库与口令在项目根的 keystore/ 与 keystore.properties（不进版本库，务必自行备份）。
// 调试版与正式版共用同一密钥 —— 两者可以互相覆盖安装，真机调试不丢数据；换机/换密钥会导致必须卸载重装。
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.jianji.ime"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.jianji.ime"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.2.0"
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    // JVM 单元测试：引擎排序/整句的回归测试台（testDebugUnitTest），跑真实 dict.bin
    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    testLogging {
        showStandardStreams = true
        events("passed", "failed", "skipped")
    }
}
