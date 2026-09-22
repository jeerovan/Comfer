package com.jeerovan.buildlogic;

import org.junit.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class FrameworkCompatibilityVisitorTest {
    private List<String> visit(int opcode, String owner, String name, String descriptor) {
        List<String> calls = new ArrayList<>();
        ClassVisitor sink = new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int o, String c, String m, String desc, boolean i) {
                        calls.add(o + " " + c + "." + m + desc);
                    }
                };
            }
        };
        MethodVisitor method = FrameworkCompatibilityTransform.visitor(sink)
                .visitMethod(Opcodes.ACC_STATIC, "caller", "()V", null, null);
        method.visitMethodInsn(opcode, owner, name, descriptor, false);
        return calls;
    }

    @Test public void staticOverlayCallUsesBridge() {
        assertEquals(List.of(Opcodes.INVOKESTATIC + " " + FrameworkCompatibilityTransform.BRIDGE + ".systemOverlays()I"),
                visit(Opcodes.INVOKESTATIC, "android/view/WindowInsets$Type", "systemOverlays", "()I"));
    }

    @Test public void sensitivityReceiverAndBooleanBecomeStaticArguments() {
        assertEquals(List.of(Opcodes.INVOKESTATIC + " " + FrameworkCompatibilityTransform.BRIDGE
                        + ".setAccessibilityDataSensitive(Landroid/view/accessibility/AccessibilityEvent;Z)V"),
                visit(Opcodes.INVOKEVIRTUAL, "android/view/accessibility/AccessibilityEvent", "setAccessibilityDataSensitive", "(Z)V"));
    }

    @Test public void unrelatedApisAndOverloadsRemainUnchanged() {
        String[][] calls = {
                {"android/view/WindowInsets$Type", "statusBars", "()I"},
                {"example/Type", "systemOverlays", "()I"},
                {"android/view/WindowInsets$Type", "systemOverlays", "(I)I"},
                {"android/view/accessibility/AccessibilityEvent", "setPassword", "(Z)V"},
                {"android/view/accessibility/AccessibilityEvent", "setAccessibilityDataSensitive", "(I)V"},
        };
        for (String[] call : calls) {
            assertEquals(List.of(Opcodes.INVOKEVIRTUAL + " " + call[0] + "." + call[1] + call[2]),
                    visit(Opcodes.INVOKEVIRTUAL, call[0], call[1], call[2]));
        }
    }

    @Test public void bridgeIsExcludedButDependenciesAndInlineAppCallersAreCovered() {
        assertFalse(FrameworkCompatibilityTransform.shouldInstrument("com.jeerovan.comfer.compat.FrameworkCompatibility"));
        assertTrue(FrameworkCompatibilityTransform.shouldInstrument("androidx.core.view.WindowInsetsCompat$TypeImpl34"));
        assertTrue(FrameworkCompatibilityTransform.shouldInstrument("com.jeerovan.comfer.GestureShortcutScreenKt"));
    }
}
