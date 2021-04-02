/*
 * This file was generated by the Gradle 'init' task.
 */
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    `maven-publish`
    kotlin("jvm") version "1.4.32"
}

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo1.maven.org/maven2/")
    }

    maven {
        url = uri("https://kotlin.bintray.com/kotlinx")
    }

    maven {
        url = uri("https://jitpack.io")
    }

    maven {
        url = uri("http://jsch.sf.net/maven2/")
    }

    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.32")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-common:1.4.32")
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.4.32")
    implementation("com.microsoft.azure.functions:azure-functions-java-library:1.4.2")
    implementation("com.azure:azure-core:1.14.1")
    implementation("com.azure:azure-core-http-netty:1.9.0")
    implementation("com.azure:azure-storage-blob:12.10.2")
    implementation("com.azure:azure-storage-queue:12.8.0")
    implementation("com.azure:azure-security-keyvault-secrets:4.2.6")
    implementation("com.azure:azure-identity:1.2.4")
    implementation("org.apache.logging.log4j:log4j-api:[2.13.2,)")
    implementation("org.apache.logging.log4j:log4j-core:[2.13.2,)")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:[2.13.2,)")
    implementation("org.apache.logging.log4j:log4j-api-kotlin:1.0.0")
    implementation("com.github.doyaaaaaken:kotlin-csv-jvm:0.15.1")
    implementation("tech.tablesaw:tablesaw-core:0.38.2")
    implementation("com.github.ajalt.clikt:clikt-jvm:3.1.0")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.12.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.12.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.12.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.12.2")
    implementation("com.github.javafaker:javafaker:1.0.2")
    implementation("ca.uhn.hapi:hapi-base:2.3")
    implementation("ca.uhn.hapi:hapi-structures-v251:2.3")
    implementation("com.googlecode.libphonenumber:libphonenumber:8.12.20")
    implementation("org.thymeleaf:thymeleaf:3.0.12.RELEASE")
    implementation("com.sendgrid:sendgrid-java:4.7.2")
    implementation("com.okta.jwt:okta-jwt-verifier:0.5.0")
    implementation("com.github.kittinunf.fuel:fuel:2.3.1")
    implementation("com.github.kittinunf.fuel:fuel-json:2.3.1")
    implementation("org.json:json:20210307")
    implementation("com.hierynomus:sshj:0.30.0")
    implementation("org.bouncycastle:bcprov-jdk15on:1.68")
    implementation("com.jcraft:jsch:0.1.55")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("org.apache.commons:commons-text:1.9")
    implementation("commons-codec:commons-codec:1.15")
    implementation("commons-io:commons-io:2.8.0")
    implementation("org.postgresql:postgresql:42.2.19")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("org.flywaydb:flyway-core:7.3.0")
    implementation("org.jooq:jooq:3.14.8")
    implementation("org.jooq:jooq-kotlin:3.14.8")
    implementation("org.yaml:snakeyaml:1.27")
    runtimeOnly("com.okta.jwt:okta-jwt-verifier-impl:0.5.0")
    runtimeOnly("com.github.kittinunf.fuel:fuel-jackson:2.3.1")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5:1.4.32")
    testImplementation("com.github.KennethWussmann:mock-fuel:1.3.0")
    testImplementation("io.mockk:mockk:1.11.0")
}

group = "gov.cdc.prime"
version = "0.1-SNAPSHOT"
description = "prime-router"
java.sourceCompatibility = JavaVersion.VERSION_11
java.targetCompatibility = JavaVersion.VERSION_11

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.test {
    useJUnit()
}
