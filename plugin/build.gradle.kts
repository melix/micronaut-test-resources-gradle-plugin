plugins {
    `java-gradle-plugin`
}

repositories {
    mavenCentral()
    mavenLocal()
}

dependencies {
    implementation("io.micronaut.test:micronaut-test-resources-classpath:1.0.0-SNAPSHOT")
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
