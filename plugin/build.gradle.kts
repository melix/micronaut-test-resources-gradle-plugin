plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    implementation("io.micronaut.testresources:micronaut-test-resources-classpath:1.0.0-SNAPSHOT")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

gradlePlugin {
    val testResourcesPlugin by plugins.creating {
        id = "io.micronaut.testresources"
        implementationClass = "io.micronaut.gradle.testresources.MicronautTestResourcesGradlePlugin"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
