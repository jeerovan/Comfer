package com.jeerovan.buildlogic;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

/** Execute the actual app bridge against complete and incomplete framework fixtures.
 * Compile against the complete API first, then remove methods at runtime, just
 * as the reported devices differ from the compile SDK.
 */
public class FrameworkCompatibilityRuntimeTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private Path source(Path root, String name, String body) throws Exception {
        Path file = root.resolve(name); Files.createDirectories(file.getParent());
        Files.writeString(file, body); return file;
    }

    private void compile(Path output, Path... sources) {
        List<String> args = new ArrayList<>(List.of("-d", output.toString(), "-classpath", output.toString()));
        for (Path source : sources) args.add(source.toString());
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, args.toArray(new String[0])));
    }

    private URLClassLoader runtime(String overlayBody, String eventBody) throws Exception {
        Path root = temp.newFolder().toPath();
        Path type = source(root, "android/view/WindowInsets.java",
                "package android.view; public class WindowInsets { public static class Type { public static int systemOverlays() { return 256; } } }");
        Path event = source(root, "android/view/accessibility/AccessibilityEvent.java",
                "package android.view.accessibility; public class AccessibilityEvent { public boolean sensitive; public void setAccessibilityDataSensitive(boolean value) { sensitive=value; } }");
        Path keep = source(root, "androidx/annotation/Keep.java", "package androidx.annotation; public @interface Keep {}");
        Path lint = source(root, "android/annotation/SuppressLint.java", "package android.annotation; public @interface SuppressLint { String[] value(); }");
        compile(root, type, event, keep, lint,
                Path.of("../app/src/main/java/com/jeerovan/comfer/compat/FrameworkCompatibility.java"));
        source(root, "android/view/WindowInsets.java",
                "package android.view; public class WindowInsets { public static class Type { " + overlayBody + " } }");
        source(root, "android/view/accessibility/AccessibilityEvent.java",
                "package android.view.accessibility; public class AccessibilityEvent { public boolean sensitive; " + eventBody + " }");
        compile(root, type, event);
        return new URLClassLoader(new java.net.URL[]{root.toUri().toURL()}, null);
    }

    @Test public void missingMethodsFallBackWithoutLinkageCrash() throws Exception {
        try (URLClassLoader loader = runtime("", "")) {
            Class<?> bridge = loader.loadClass("com.jeerovan.comfer.compat.FrameworkCompatibility");
            Class<?> event = loader.loadClass("android.view.accessibility.AccessibilityEvent");
            for (int i = 0; i < 2; i++) {
                assertEquals(0, bridge.getMethod("systemOverlays").invoke(null));
                bridge.getMethod("setAccessibilityDataSensitive", event, boolean.class)
                        .invoke(null, event.getConstructor().newInstance(), true);
            }
        }
    }

    @Test public void availableMethodsRetainValuesAndBothSensitivityStates() throws Exception {
        try (URLClassLoader loader = runtime("public static int systemOverlays() { return 512; }",
                "public void setAccessibilityDataSensitive(boolean value) { sensitive=value; }")) {
            Class<?> bridge = loader.loadClass("com.jeerovan.comfer.compat.FrameworkCompatibility");
            Class<?> event = loader.loadClass("android.view.accessibility.AccessibilityEvent");
            Object instance = event.getConstructor().newInstance();
            assertEquals(512, bridge.getMethod("systemOverlays").invoke(null));
            for (boolean sensitive : new boolean[]{true, false}) {
                bridge.getMethod("setAccessibilityDataSensitive", event, boolean.class).invoke(null, instance, sensitive);
                assertEquals(sensitive, event.getField("sensitive").get(instance));
            }
        }
    }

    @Test public void unrelatedFrameworkFailuresPropagate() throws Exception {
        try (URLClassLoader loader = runtime("public static int systemOverlays() { throw new SecurityException(\"denied\"); }",
                "public void setAccessibilityDataSensitive(boolean value) { throw new IllegalStateException(\"sealed\"); }")) {
            Class<?> bridge = loader.loadClass("com.jeerovan.comfer.compat.FrameworkCompatibility");
            Class<?> event = loader.loadClass("android.view.accessibility.AccessibilityEvent");
            try { bridge.getMethod("systemOverlays").invoke(null); fail(); }
            catch (InvocationTargetException error) { assertTrue(error.getCause() instanceof SecurityException); }
            try { bridge.getMethod("setAccessibilityDataSensitive", event, boolean.class)
                    .invoke(null, event.getConstructor().newInstance(), true); fail(); }
            catch (InvocationTargetException error) { assertTrue(error.getCause() instanceof IllegalStateException); }
        }
    }
}
