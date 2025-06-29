group = "com.moshy.drugcalc.commontest"
version = "0.0.1"

plugins {
    id("kotlin-jvm-conventions")
}

dependencies {
    implementation(platform(libs.junit.bom))
    implementation(libs.junit.jupiter)
}
