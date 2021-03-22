import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.30"
}

group = "me.ikvarxt"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.squareup.retrofit2", "retrofit", "2.9.0")
    implementation("com.squareup.retrofit2", "converter-gson", "2.9.0")
    implementation("com.squareup.okhttp3", "okhttp", "4.9.0")
    implementation("com.google.code.gson", "gson", "2.8.6")

    // https://mvnrepository.com/artifact/org.mnode.ical4j/ical4j
    implementation("org.mnode.ical4j", "ical4j",  "3.0.21")

    testImplementation(kotlin("test-junit"))
}

tasks.test {
    useJUnit()
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}