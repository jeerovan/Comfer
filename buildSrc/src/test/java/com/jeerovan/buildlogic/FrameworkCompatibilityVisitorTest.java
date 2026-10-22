package com.jeerovan.buildlogic;

import org.junit.Test;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class FrameworkCompatibilityVisitorTest {
    private List<String> visitField(int opcode, String owner, String name, String descriptor) {
        List<String> operations = new ArrayList<>();
        ClassVisitor sink = new ClassVisitor(Opcodes.ASM9) {
            @Override public MethodVisitor visitMethod(int a, String n, String d, String s, String[] e) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override public void visitMethodInsn(int o, String c, String m, String desc, boolean i) {
                        operations.add(o + " " + c + "." + m + desc);
                    }
                    @Override public void visitFieldInsn(int o, String c, String f, String desc) {
                        operations.add(o + " " + c + "." + f + desc);
                    }
                };
            }
        };
        FrameworkCompatibilityTransform.visitor(sink)
                .visitMethod(Opcodes.ACC_STATIC, "caller", "()V", null, null)
                .visitFieldInsn(opcode, owner, name, descriptor);
        return operations;
    }

    @Test public void scrollDirectionFieldReadUsesBridgeWithoutChangingStackType() {
        String owner = "android/view/accessibility/AccessibilityNodeInfo$AccessibilityAction";
        String descriptor = "L" + owner + ";";
        assertEquals(List.of(Opcodes.INVOKESTATIC + " " + FrameworkCompatibilityTransform.BRIDGE
                        + ".getActionScrollInDirection()" + descriptor),
                visitField(Opcodes.GETSTATIC, owner, "ACTION_SCROLL_IN_DIRECTION", descriptor));
        for (int opcode : new int[]{Opcodes.GETFIELD, Opcodes.PUTSTATIC}) {
            assertEquals(List.of(opcode + " " + owner + ".ACTION_SCROLL_IN_DIRECTION" + descriptor),
                    visitField(opcode, owner, "ACTION_SCROLL_IN_DIRECTION", descriptor));
        }
        for (String[] field : new String[][]{
                {owner, "ACTION_CLICK", descriptor},
                {"example/Action", "ACTION_SCROLL_IN_DIRECTION", descriptor},
                {owner, "ACTION_SCROLL_IN_DIRECTION", "I"}}) {
            assertEquals(List.of(Opcodes.GETSTATIC + " " + field[0] + "." + field[1] + field[2]),
                    visitField(Opcodes.GETSTATIC, field[0], field[1], field[2]));
        }
    }

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

    @Test public void nodeSensitivityReceiverAndBooleanBecomeStaticArguments() {
        assertEquals(List.of(Opcodes.INVOKESTATIC + " " + FrameworkCompatibilityTransform.BRIDGE
                        + ".setAccessibilityDataSensitive(Landroid/view/accessibility/AccessibilityNodeInfo;Z)V"),
                visit(Opcodes.INVOKEVIRTUAL, "android/view/accessibility/AccessibilityNodeInfo", "setAccessibilityDataSensitive", "(Z)V"));
    }

    @Test public void accessibilityToolRequestUsesExactReceiverBridge() {
        assertEquals(List.of(Opcodes.INVOKESTATIC + " " + FrameworkCompatibilityTransform.BRIDGE
                        + ".isRequestFromAccessibilityTool(Landroid/view/accessibility/AccessibilityManager;)Z"),
                visit(Opcodes.INVOKEVIRTUAL, "android/view/accessibility/AccessibilityManager",
                        "isRequestFromAccessibilityTool", "()Z"));
    }

    @Test public void unrelatedApisAndOverloadsRemainUnchanged() {
        String[][] calls = {
                {"android/view/WindowInsets$Type", "statusBars", "()I"},
                {"example/Type", "systemOverlays", "()I"},
                {"android/view/WindowInsets$Type", "systemOverlays", "(I)I"},
                {"android/view/accessibility/AccessibilityEvent", "setPassword", "(Z)V"},
                {"android/view/accessibility/AccessibilityEvent", "setAccessibilityDataSensitive", "(I)V"},
                {"android/view/accessibility/AccessibilityNodeInfo", "setPassword", "(Z)V"},
                {"android/view/accessibility/AccessibilityNodeInfo", "setAccessibilityDataSensitive", "(I)V"},
                {"example/Manager", "isRequestFromAccessibilityTool", "()Z"},
                {"android/view/accessibility/AccessibilityManager", "isRequestFromAccessibilityTool", "(I)Z"},
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
