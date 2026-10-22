plugins {
    java
    id("org.springframework.boot") version "4.1.2"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "jp.shirabete"
version = "0.0.1-SNAPSHOT"
description = "質問から引用付きレポートを作成するナレッジベース API"

java {
    sourceCompatibility = JavaVersion.VERSION_25
    targetCompatibility = JavaVersion.VERSION_25
}

tasks.withType<JavaCompile> {
    options.release = 25
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.azure:azure-ai-agents:2.8.0")
    implementation("com.azure:azure-identity:1.18.7")
    implementation("com.azure:azure-core-http-jdk-httpclient:1.2.0")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.commonmark:commonmark:0.31.0")
    implementation("org.jsoup:jsoup:1.23.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName = "shirabete-matomeru-kun-backend-${project.version}.jar"
}
