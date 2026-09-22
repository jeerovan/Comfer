package com.jeerovan.buildlogic;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Guards two API-34 calls missing on the runtimes reported by Crashlytics.
 * Dependency and app bytecode are covered because Kotlin/R8 can inline callers.
 */
public final class FrameworkCompatibilityTransform {
    private FrameworkCompatibilityTransform() {}
    static final String BRIDGE = "com/jeerovan/comfer/compat/FrameworkCompatibility";

    public static boolean shouldInstrument(String name) {
        return !name.equals(BRIDGE.replace('/', '.'));
    }

    public static ClassVisitor visitor(ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9,
                        super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override public void visitMethodInsn(int opcode, String owner, String method,
                            String desc, boolean isInterface) {
                        if (opcode == Opcodes.INVOKESTATIC && !isInterface
                                && owner.equals("android/view/WindowInsets$Type")
                                && method.equals("systemOverlays") && desc.equals("()I")) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, method, desc, false);
                        } else if (opcode == Opcodes.INVOKEVIRTUAL && !isInterface
                                && owner.equals("android/view/accessibility/AccessibilityEvent")
                                && method.equals("setAccessibilityDataSensitive") && desc.equals("(Z)V")) {
                            // Same operand stack: the former receiver becomes argument zero.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, BRIDGE, method,
                                    "(Landroid/view/accessibility/AccessibilityEvent;Z)V", false);
                        } else {
                            super.visitMethodInsn(opcode, owner, method, desc, isInterface);
                        }
                    }
                };
            }
        };
    }
}
