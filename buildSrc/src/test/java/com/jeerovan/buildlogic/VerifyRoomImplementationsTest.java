package com.jeerovan.buildlogic;

import org.gradle.api.GradleException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.Assert.*;

public class VerifyRoomImplementationsTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    @Test public void generatedSourceWithoutCompiledImplementationFails() throws Exception {
        var root = temp.newFolder();
        var project = ProjectBuilder.builder().withProjectDir(root).build();
        var task = project.getTasks().create("verifyRoom", VerifyRoomImplementations.class);
        var directory = project.getLayout().getProjectDirectory().dir("generated");
        directory.getAsFile().mkdirs();
        Files.writeString(directory.file("ExampleDatabase_Impl.kt").getAsFile().toPath(), "// generated source only");
        task.getImplementationNames().set(List.of("example.ExampleDatabase_Impl"));
        task.getDirectories().set(List.of(directory));
        task.getJars().set(List.of());
        try { task.verify(); fail("Uncompiled generated code must fail packaging"); }
        catch (GradleException error) { assertTrue(error.getMessage().contains("example.ExampleDatabase_Impl")); }
    }

    @Test public void implementationsMayBeSplitAcrossCompiledDirectoriesAndJars() throws Exception {
        var root = temp.newFolder();
        var project = ProjectBuilder.builder().withProjectDir(root).build();
        var task = project.getTasks().create("verifyRoom", VerifyRoomImplementations.class);
        var directory = project.getLayout().getProjectDirectory().dir("classes");
        var clazz = directory.file("example/First_Impl.class").getAsFile().toPath();
        Files.createDirectories(clazz.getParent()); Files.write(clazz, new byte[]{1});
        var jar = project.getLayout().getProjectDirectory().file("generated.jar");
        try (var zip = new ZipOutputStream(Files.newOutputStream(jar.getAsFile().toPath()))) {
            zip.putNextEntry(new ZipEntry("example/Second_Impl.class")); zip.write(1); zip.closeEntry();
        }
        task.getImplementationNames().set(List.of("example.First_Impl", "example.Second_Impl"));
        task.getDirectories().set(List.of(directory)); task.getJars().set(List.of(jar));
        task.verify();
        task.getImplementationNames().add("example.Missing_Impl");
        try { task.verify(); fail("A partially complete set must fail packaging"); }
        catch (GradleException error) { assertTrue(error.getMessage().contains("example.Missing_Impl")); }
    }
}
