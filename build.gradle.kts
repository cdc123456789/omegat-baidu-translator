plugins {
    java
    // groovy
    // kotlin("jvm") version "1.3.72"
    distribution
    id("org.omegat.gradle") version "2.0.0-rc2"
}

version = "1.0.0"

omegat {
    version = "6.1.1"
    pluginClass = "org.cdc.PluginMain"
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.1")
    implementation("com.fasterxml.jackson.core:jackson-core:2.21.1")

    testImplementation("junit:junit:4.13.1")
    packIntoJar("net.bytebuddy:byte-buddy:1.14.17")
    implementation("org.netbeans.api:org-openide-awt:RELEASE130")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    // OmegaT 6.1.1 is compiled for Java 11 and may be run with a JRE 11.
    options.release.set(11)
}

distributions {
    main {
        contents {
            from(tasks["jar"], "README.md", "COPYING", "CHANGELOG.md")
        }
    }
}
