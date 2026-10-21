plugins {
    alias(libs.plugins.android.application) apply false
    // AGP 9 ya trae Kotlin integrado; declararlo acá solo fija la versión del compilador.
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}
