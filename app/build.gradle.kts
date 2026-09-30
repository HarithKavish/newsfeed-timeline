plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

android {
    namespace = "com.harithkavish.newsfeed"
    compileSdk = 34

    // Without this the output is "app-debug.apk" for every module in every
    // project, which is useless the moment it leaves the build directory and
    // lands in a downloads folder next to three other app-debug.apk files.
    base.archivesName = "newsfeed-timeline"

    defaultConfig {
        applicationId = "com.harithkavish.newsfeed"
        // 26 is the floor for the launcher-overlay protocol in practice: every
        // launcher that offers a -1 screen provider setting targets 8.0+.
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // The one deployment knob. Points at the Timeline news worker -- the
        // same engine timeline.harithkavish.com reads from. Overridable per
        // build type so a local `wrangler dev` can be pointed at instead.
        buildConfigField(
            "String",
            "NEWS_API_BASE",
            "\"https://timeline-news.harithkavish40.workers.dev\"",
        )
    }

    buildFeatures {
        buildConfig = true
        viewBinding = false
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            // A debug build installed alongside a release one would register a
            // second overlay provider with the same label, which is confusing
            // to pick between in a launcher's feed-provider list.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources {
            excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "DebugProbesKt.bin", "**/*.kotlin_metadata")
        }
    }
}

dependencies {
    implementation(libs.androidx.recyclerview)
}
