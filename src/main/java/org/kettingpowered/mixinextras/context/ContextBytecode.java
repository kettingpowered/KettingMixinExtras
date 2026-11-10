package org.kettingpowered.mixinextras.context;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;

final class ContextBytecode {

    static final String RUNTIME = Type.getInternalName(ContextRuntime.class);
    static final String CALL = Type.getDescriptor(ContextRuntime.Call.class);
    static final String FRAME = Type.getDescriptor(ContextRuntime.Frame.class);
    static final Type CONSTRUCTOR_ARGUMENTS = Type.getType(ContextRuntime.ConstructorArguments.class);

    private ContextBytecode() {
    }

    static int[] slots(MethodNode method) {
        Type[] arguments = Type.getArgumentTypes(method.desc);
        int[] result = new int[arguments.length];
        int slot = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (int index = 0; index < arguments.length; index++) {
            result[index] = slot;
            slot += arguments[index].getSize();
        }
        return result;
    }

    static int argumentSize(MethodNode method) {
        int size = (method.access & Opcodes.ACC_STATIC) != 0 ? 0 : 1;
        for (Type type : Type.getArgumentTypes(method.desc)) {
            size += type.getSize();
        }
        return size;
    }

    static void identity(InsnList code, String owner, String method) {
        code.add(new LdcInsnNode(Type.getObjectType(owner)));
        code.add(new LdcInsnNode(method));
    }

    static void receiver(InsnList code, MethodNode method) {
        code.add((method.access & Opcodes.ACC_STATIC) != 0 ? new InsnNode(Opcodes.ACONST_NULL) : new VarInsnNode(Opcodes.ALOAD, 0));
    }

    static void invoke(InsnList code, String owner, MethodNode method) {
        int opcode = (method.access & Opcodes.ACC_STATIC) != 0 ? Opcodes.INVOKESTATIC
            : (method.access & Opcodes.ACC_PRIVATE) != 0 ? Opcodes.INVOKESPECIAL : Opcodes.INVOKEVIRTUAL;
        code.add(new MethodInsnNode(opcode, owner, method.name, method.desc, false));
    }

    static void box(InsnList code, Type type) {
        String wrapper = wrapper(type);
        if (wrapper != null) {
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, wrapper, "valueOf", '(' + type.getDescriptor() + ")L" + wrapper + ';', false));
        }
    }

    static void unbox(InsnList code, Type type) {
        String wrapper = wrapper(type);
        if (wrapper == null) {
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, type.getInternalName()));
        } else {
            code.add(new TypeInsnNode(Opcodes.CHECKCAST, wrapper));
            code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, wrapper, type.getClassName() + "Value", "()" + type.getDescriptor(), false));
        }
    }

    static void classLiteral(InsnList code, Type type) {
        String wrapper = wrapper(type);
        code.add(wrapper == null ? new LdcInsnNode(type) : new FieldInsnNode(Opcodes.GETSTATIC, wrapper, "TYPE", "Ljava/lang/Class;"));
    }

    static void defaultValue(InsnList code, Type type) {
        switch (type.getSort()) {
            case Type.LONG -> code.add(new InsnNode(Opcodes.LCONST_0));
            case Type.FLOAT -> code.add(new InsnNode(Opcodes.FCONST_0));
            case Type.DOUBLE -> code.add(new InsnNode(Opcodes.DCONST_0));
            case Type.OBJECT, Type.ARRAY -> code.add(new InsnNode(Opcodes.ACONST_NULL));
            default -> code.add(new InsnNode(Opcodes.ICONST_0));
        }
        box(code, type);
    }

    static void guard(MethodNode method, InsnList entry, InsnList initialize, String scope, String cleanup, AbstractInsnNode after) {
        int local = Math.max(method.maxLocals, argumentSize(method));
        method.maxLocals = local + 1;
        entry.add(new VarInsnNode(Opcodes.ASTORE, local));
        LabelNode start = new LabelNode();
        entry.add(start);
        if (initialize != null) entry.add(initialize);
        if (after == null) method.instructions.insert(entry);
        else method.instructions.insert(after, entry);

        Type result = Type.getReturnType(method.desc);
        int resultLocal = method.maxLocals;
        method.maxLocals += result.getSize();
        int errorLocal = method.maxLocals++;
        LabelNode end = new LabelNode();
        LabelNode failure = new LabelNode();
        LabelNode success = new LabelNode();
        for (AbstractInsnNode instruction : method.instructions.toArray()) {
            int opcode = instruction.getOpcode();
            if (opcode < Opcodes.IRETURN || opcode > Opcodes.RETURN) continue;

            InsnList exit = new InsnList();
            if (result.getSort() != Type.VOID) exit.add(new VarInsnNode(result.getOpcode(Opcodes.ISTORE), resultLocal));
            exit.add(new JumpInsnNode(Opcodes.GOTO, success));
            method.instructions.insertBefore(instruction, exit);
            method.instructions.remove(instruction);
        }

        method.instructions.add(end);
        method.instructions.add(failure);
        method.instructions.add(new VarInsnNode(Opcodes.ASTORE, errorLocal));
        close(method.instructions, local, scope, cleanup);
        method.instructions.add(new VarInsnNode(Opcodes.ALOAD, errorLocal));
        method.instructions.add(new InsnNode(Opcodes.ATHROW));
        method.instructions.add(success);
        close(method.instructions, local, scope, cleanup);
        if (result.getSort() != Type.VOID) method.instructions.add(new VarInsnNode(result.getOpcode(Opcodes.ILOAD), resultLocal));
        method.instructions.add(new InsnNode(result.getOpcode(Opcodes.IRETURN)));

        if (method.tryCatchBlocks == null) method.tryCatchBlocks = new ArrayList<>();
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, failure, null));
        method.maxStack = Math.max(method.maxStack, 8);
    }

    private static void close(InsnList code, int local, String scope, String cleanup) {
        code.add(new VarInsnNode(Opcodes.ALOAD, local));
        code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, cleanup, '(' + scope + ")V", false));
    }

    private static String wrapper(Type type) {
        return switch (type.getSort()) {
            case Type.BOOLEAN -> "java/lang/Boolean";
            case Type.BYTE -> "java/lang/Byte";
            case Type.CHAR -> "java/lang/Character";
            case Type.SHORT -> "java/lang/Short";
            case Type.INT -> "java/lang/Integer";
            case Type.FLOAT -> "java/lang/Float";
            case Type.LONG -> "java/lang/Long";
            case Type.DOUBLE -> "java/lang/Double";
            default -> null;
        };
    }
}
