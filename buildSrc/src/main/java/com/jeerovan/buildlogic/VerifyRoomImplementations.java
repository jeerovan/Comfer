package com.jeerovan.buildlogic;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.Directory;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.zip.ZipFile;

/** Fail packaging when KSP ran but its generated database implementations were not compiled. */
public abstract class VerifyRoomImplementations extends DefaultTask {
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<RegularFile> getJars();
    @InputFiles @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ListProperty<Directory> getDirectories();
    @Input public abstract ListProperty<String> getImplementationNames();

    @TaskAction public void verify() throws IOException {
        var missing = new LinkedHashSet<>(getImplementationNames().get());
        for (Directory directory : getDirectories().get()) {
            missing.removeIf(name -> directory.file(name.replace('.', '/') + ".class").getAsFile().isFile());
        }
        for (RegularFile jar : getJars().get()) {
            try (var zip = new ZipFile(jar.getAsFile())) {
                missing.removeIf(name -> zip.getEntry(name.replace('.', '/') + ".class") != null);
            }
        }
        if (!missing.isEmpty()) throw new GradleException(
                "Room implementations missing from compiled classes: " + missing
                + ". Check KSP generated-source wiring before packaging this build.");
        getLogger().lifecycle("Verified all {} generated Room implementations", getImplementationNames().get().size());
    }
}
