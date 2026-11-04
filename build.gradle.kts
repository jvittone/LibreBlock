plugins {
    id("com.android.application") version "9.4.1" apply false
    // AGP 9 intègre Kotlin ; cette ligne fixe seulement la version de Kotlin utilisée.
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
