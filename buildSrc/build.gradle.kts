plugins { `java-library` }

repositories { google(); mavenCentral() }

dependencies {
    implementation("org.ow2.asm:asm:9.9")
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // Gradle's ProjectBuilder fixture injects its synthetic interfaces on modern JDKs.
    jvmArgs("--add-opens=java.base/java.lang=ALL-UNNAMED")
    inputs.file("../app/src/main/java/com/jeerovan/comfer/compat/FrameworkCompatibility.java")
}
