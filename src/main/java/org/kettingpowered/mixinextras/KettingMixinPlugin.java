package org.kettingpowered.mixinextras;

import org.kettingpowered.mixinextras.annotations.*;
import org.kettingpowered.mixinextras.injectionPoints.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.logging.ILogger;
import org.spongepowered.asm.logging.Level;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Constants;

import java.util.*;
import java.util.stream.Stream;

public class KettingMixinPlugin implements IMixinConfigPlugin {

    public static final ILogger LOGGER = MixinService.getService().getLogger("KettingMixinExtras");
    public static final boolean DEBUG = MixinEnvironment.getDefaultEnvironment().getOption(MixinEnvironment.Option.DEBUG_VERBOSE);

    private final TransformerRegistry preTransformerRegistry = new TransformerRegistry();
    private final TransformerRegistry postTransformerRegistry = new TransformerRegistry();

    public static void log(String message, Object... params) {
        LOGGER.log(DEBUG?Level.INFO:Level.DEBUG, message, params);
    }
    public KettingMixinPlugin(){}

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("Loading KettingMixin plugin");
        InjectionPoint.register(AfterInvokeC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeFieldAccessC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeInvokeC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeNewC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeStringInvokeC.class, "org.kettingpowered.mixinextras");

        addTransformers();
    }

    private void addTransformers() {
        preTransformerRegistry.addClassTransformer(TransformSuperclass.class, (info, clazz) -> {
            final Map<String, Object> annotationValues = info.annotationValues();
            final String newSuperclass = (String) annotationValues.get("superclass");
            final String methodName = (String) annotationValues.get("methodName");
            final List<String> methodDescriptorsToRemove = (List<String>) annotationValues.get("methodDescriptorsToRemove");

            String oldSuperclass = clazz.superName;
            if (!oldSuperclass.equals(newSuperclass)) {
                for (String methodDescriptor : methodDescriptorsToRemove) {
                    boolean removed = clazz.methods.removeIf(method ->
                            method.name.equals(methodName) && method.desc.equals(methodDescriptor));
                    if (!removed) {
                        throw new IllegalStateException("Could not find method " + methodName + methodDescriptor
                                + " to remove from class " + clazz.name);
                    }
                }

                for (MethodNode method : clazz.methods) {
                    for (AbstractInsnNode instruction : method.instructions) {
                        if (instruction instanceof MethodInsnNode methodInsn
                                && methodInsn.owner.equals(oldSuperclass)
                                && methodInsn.name.equals(Constants.CTOR)) {
                            methodInsn.owner = newSuperclass;
                        }
                    }
                }

                if (clazz.signature != null) {
                    clazz.signature = clazz.signature
                            .replace("L" + oldSuperclass + "<", "L" + newSuperclass + "<")
                            .replace("L" + oldSuperclass + ";", "L" + newSuperclass + ";");
                }
                clazz.superName = newSuperclass;
            }
        });

        postTransformerRegistry.addClassTransformer(TransformSuperclass.class, (info, clazz) -> {
            final Map<String, Object> annotationValues = info.annotationValues();
            final String methodName = (String) annotationValues.get("methodName");
            final String bridgeDescriptor = Optional.ofNullable((String) annotationValues.get("bridgeDescriptor")).orElse("");
            final String bridgeTargetDescriptor = Optional.ofNullable((String) annotationValues.get("bridgeTargetDescriptor")).orElse("");
            if (bridgeDescriptor.isBlank()) return;

            MethodNode bridgeTarget = clazz.methods.stream()
                    .filter(method -> method.name.equals(methodName) && method.desc.equals(bridgeTargetDescriptor))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find bridge target "
                            + methodName + bridgeTargetDescriptor + " in class " + clazz.name));
            if (clazz.methods.stream().noneMatch(method ->
                    method.name.equals(methodName) && method.desc.equals(bridgeDescriptor))) {
                clazz.methods.add(createBridgeMethod(clazz.name, methodName, bridgeDescriptor, bridgeTarget.desc));
            }
        });

        postTransformerRegistry.addClassTransformer(TransformConstructor.class, (info, clazz) -> {
            final Map<String, Object> annotationValues = info.annotationValues();
            final String fieldName = (String) annotationValues.get("fieldName");
            final String fieldDescriptor = (String) annotationValues.get("fieldDescriptor");
            final String constructorDescriptor = (String) annotationValues.get("constructorDescriptor");
            final String replacementConstructorDescriptor = (String) annotationValues.get("replacementConstructorDescriptor");

            FieldNode field = clazz.fields.stream()
                    .filter(candidate -> candidate.name.equals(fieldName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Could not find field " + fieldName + " in class " + clazz.name));
            if (field.desc.equals(fieldDescriptor)
                    && clazz.methods.stream().anyMatch(candidate -> candidate.name.equals(Constants.CTOR)
                    && candidate.desc.equals(replacementConstructorDescriptor))) {
                return;
            }
            if ((field.access & Opcodes.ACC_STATIC) != 0) {
                throw new IllegalStateException("Cannot assign an instance constructor argument to static field " + clazz.name + "." + fieldName);
            }

            MethodNode constructor = clazz.methods.stream()
                    .filter(candidate -> candidate.name.equals(Constants.CTOR) && candidate.desc.equals(constructorDescriptor))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Could not find constructor " + constructorDescriptor
                                    + " in class " + clazz.name
                                    + "; found: " + clazz.methods.stream()
                                    .filter(candidate -> candidate.name.equals(Constants.CTOR))
                                    .map(candidate -> candidate.desc)
                                    .toList()));
            if (clazz.methods.stream().anyMatch(candidate -> candidate != constructor
                    && candidate.name.equals(Constants.CTOR)
                    && candidate.desc.equals(replacementConstructorDescriptor))) {
                throw new IllegalStateException("Constructor " + replacementConstructorDescriptor + " already exists in class " + clazz.name);
            }

            MethodInsnNode superConstructorCall = null;
            for (AbstractInsnNode instruction : constructor.instructions) {
                if (instruction instanceof MethodInsnNode methodInsn
                        && methodInsn.getOpcode() == Opcodes.INVOKESPECIAL
                        && methodInsn.owner.equals(clazz.superName)
                        && methodInsn.name.equals(Constants.CTOR)) {
                    superConstructorCall = methodInsn;
                    break;
                }
            }
            if (superConstructorCall == null) {
                throw new IllegalStateException("Could not find the superclass constructor call in " + clazz.name + constructorDescriptor);
            }

            Type replacementType = Type.getMethodType(replacementConstructorDescriptor);
            Type[] argumentTypes = replacementType.getArgumentTypes();
            Type replacementFieldType = Type.getType(fieldDescriptor);
            if (!replacementType.getReturnType().equals(Type.VOID_TYPE)
                    || argumentTypes.length != 1
                    || !argumentTypes[0].getDescriptor().equals(fieldDescriptor)
                    || replacementFieldType.getSort() == Type.METHOD
                    || replacementFieldType.getSort() == Type.VOID) {
                throw new IllegalArgumentException("Replacement constructor must take exactly one argument matching field descriptor "
                        + fieldDescriptor + " and return void");
            }

            String oldFieldDescriptor = field.desc;
            field.desc = fieldDescriptor;
            constructor.desc = replacementConstructorDescriptor;
            constructor.signature = null;
            AbstractInsnNode remainingInstruction = superConstructorCall.getNext();
            while (remainingInstruction != null) {
                AbstractInsnNode next = remainingInstruction.getNext();
                constructor.instructions.remove(remainingInstruction);
                remainingInstruction = next;
            }
            constructor.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
            constructor.instructions.add(new VarInsnNode(argumentTypes[0].getOpcode(Opcodes.ILOAD), 1));
            constructor.instructions.add(new FieldInsnNode(Opcodes.PUTFIELD, clazz.name, fieldName, fieldDescriptor));
            constructor.instructions.add(new InsnNode(Opcodes.RETURN));
            constructor.tryCatchBlocks.clear();
            constructor.localVariables = null;
            constructor.visibleLocalVariableAnnotations = null;
            constructor.invisibleLocalVariableAnnotations = null;
            constructor.visibleParameterAnnotations = null;
            constructor.invisibleParameterAnnotations = null;
            constructor.maxStack = Math.max(constructor.maxStack, 1 + argumentTypes[0].getSize());
            constructor.maxLocals = Math.max(constructor.maxLocals, 1 + argumentTypes[0].getSize());

            for (MethodNode method : clazz.methods) {
                for (AbstractInsnNode instruction : method.instructions) {
                    if (instruction instanceof FieldInsnNode fieldInsn
                            && fieldInsn.owner.equals(clazz.name)
                            && fieldInsn.name.equals(fieldName)
                            && fieldInsn.desc.equals(oldFieldDescriptor)) {
                        fieldInsn.desc = fieldDescriptor;
                    } else if (instruction instanceof MethodInsnNode methodInsn
                            && methodInsn.owner.equals(clazz.name)
                            && methodInsn.name.equals(Constants.CTOR)
                            && methodInsn.desc.equals(constructorDescriptor)) {
                        methodInsn.desc = replacementConstructorDescriptor;
                    }
                }
            }
        });

        postTransformerRegistry.addClassTransformer(TransformMethod.class, (info, clazz) -> {
            final Map<String, Object> annotationValues = info.annotationValues();
            final String method = (String) annotationValues.get("method");
            final String desc = (String) annotationValues.get("desc");
            final List<Type> toApply = (List<Type>) annotationValues.get("toApply");

            Stream<MethodNode> filter = clazz.methods.stream()
                    .filter(methodNode -> methodNode.name.equals(method));

            if (desc != null && !desc.isBlank())
                filter = filter.filter(methodNode -> methodNode.desc.equals(desc));

            final MethodNode m = filter.findFirst()
                    .orElseThrow(() -> new RuntimeException("Could not find method " + method + " with desc " + desc + " in class " + clazz.name));

            if (m.invisibleAnnotations == null) m.invisibleAnnotations = new ArrayList<>();

            toApply.forEach(t -> {
                AnnotationNode newAnnotation = new AnnotationNode(t.getDescriptor());
                m.invisibleAnnotations.add(newAnnotation);
            });
        });

        postTransformerRegistry.add(DelegateConstructor.class, (info, method) -> {
            final String name = Optional.ofNullable(info.annotationValues().get("clazz")).map(v -> ((Type)v).getInternalName()).orElse(info.targetClass().name);

            for(var new_method:info.targetClass().methods){
                AnnotationNode node = Annotations.getInvisible(new_method, NewConstructor.class);
                if (node == null) continue;
                for(int i = 0; i < new_method.instructions.size(); i++) {
                    if (new_method.instructions.get(i) instanceof MethodInsnNode call) {
                        if (call.owner.equals(info.targetClass().name) && call.name.equals(method.name) && call.desc.equals(method.desc)){
                            //Rewriting just this should be fine, since the arguments should be setup properly already.
                            call.setOpcode(Opcodes.INVOKESPECIAL);
                            call.owner = name;
                            call.name = Constants.CTOR;
                            if (!method.desc.endsWith(")V")) {
                                call.desc = method.desc.substring(0, method.desc.lastIndexOf(')')+1) + "V";
                            }
                        }
                    }
                }
            }
            info.targetClass().methods.remove(method);
            return -1;
        }, null);

        postTransformerRegistry.add(NewConstructor.class, (info, method) -> {
            method.name = Constants.CTOR;
            method.access &= ~Opcodes.ACC_STATIC;
            method.access &= ~Opcodes.ACC_ABSTRACT;
            method.access &= ~Opcodes.ACC_SYNCHRONIZED;
            if (!method.desc.endsWith(")V")) {
                method.desc = method.desc.substring(0, method.desc.lastIndexOf(')')+1) + "V";
            }
            return 0;
        }, null);

        preTransformerRegistry.add(Public.class,
                (info, method) -> {
                    method.access |= ~Opcodes.ACC_PRIVATE;
                    method.access &= ~Opcodes.ACC_PROTECTED;
                    method.access &= ~Opcodes.ACC_PUBLIC;
                    return 0;
                },
                (info, field) -> {
                    field.access |= Opcodes.ACC_PRIVATE;
                    field.access &= ~Opcodes.ACC_PROTECTED;
                    field.access &= ~Opcodes.ACC_PUBLIC;
                    return 0;
                }
        );

        postTransformerRegistry.add(Public.class,
                (info, method) -> {
                    method.access &= ~Opcodes.ACC_PRIVATE;
                    method.access &= ~Opcodes.ACC_PROTECTED;
                    method.access |= Opcodes.ACC_PUBLIC;
                    return 0;
                },
                (info, field) -> {
                    field.access &= ~Opcodes.ACC_PRIVATE;
                    field.access &= ~Opcodes.ACC_PROTECTED;
                    field.access |= Opcodes.ACC_PUBLIC;
                    return 0;
                }
        );

        postTransformerRegistry.add(MakeFinal.class,
                null,
                (info, field) -> {
                    field.access |= Opcodes.ACC_FINAL;
                    return 0;
                }
        );

        postTransformerRegistry.add(MakeSynchronized.class,
                (info, method) -> {
                    method.access |= Opcodes.ACC_SYNCHRONIZED;
                    return 0;
                },
                null
        );

        postTransformerRegistry.add(StubConstructor.class, (info, method) -> {
            final String name = Optional.ofNullable(info.annotationValues().get("clazz")).map(v -> ((Type)v).getInternalName()).orElse(info.targetClass().name);
            method.instructions.clear();
            method.instructions.add(newCall(method, name));
            return 0;
        }, null);
    }

    private static InsnList newCall(MethodNode method, String name) {
        InsnList list = new InsnList();
        list.clear();
        list.add(new TypeInsnNode(Opcodes.NEW, name));
        MethodInsnNode invokeSpecialNode = new MethodInsnNode(Opcodes.INVOKESPECIAL, name, Constants.CTOR, method.desc, false);
        final InsnNode ret;
        if (!method.desc.endsWith(")V")) {
            list.add(new InsnNode(Opcodes.DUP));
            invokeSpecialNode.desc = method.desc.substring(0, method.desc.lastIndexOf(')')+1) + "V";
            ret = new InsnNode(Opcodes.ARETURN);
        } else {
            ret = new InsnNode(Opcodes.RETURN);
        }
        Type[] types = Type.getMethodType(method.desc).getArgumentTypes();
        method.maxStack = types.length + 2;
        int varIndex = (method.access&Opcodes.ACC_STATIC) == 0 ? 1 : 0;
        for(int i = 0; i < types.length; i++){
            switch (types[i].getSort()) {
                case Type.BOOLEAN:
                case Type.CHAR:
                case Type.BYTE:
                case Type.SHORT:
                case Type.INT:
                    list.add(new VarInsnNode(Opcodes.ILOAD, varIndex++));
                    break;
                case Type.FLOAT:
                    list.add(new VarInsnNode(Opcodes.FLOAD, varIndex++));
                    break;
                case Type.LONG:
                    //longs take 2 stackframes
                    method.maxStack += 1;
                    list.add(new VarInsnNode(Opcodes.LLOAD, varIndex++));
                    varIndex++;
                    break;
                case Type.DOUBLE:
                    //doubles take 2 stackframes
                    method.maxStack += 1;
                    list.add(new VarInsnNode(Opcodes.DLOAD, varIndex++));
                    varIndex++;
                    break;
                case Type.OBJECT:
                case Type.ARRAY:
                    list.add(new VarInsnNode(Opcodes.ALOAD, varIndex++));
                    break;
            }
        }
        list.add(invokeSpecialNode);
        list.add(ret);
        return list;
    }

    private static MethodNode createBridgeMethod(String owner, String name, String bridgeDescriptor, String targetDescriptor) {
        Type bridgeType = Type.getMethodType(bridgeDescriptor);
        Type targetType = Type.getMethodType(targetDescriptor);
        Type[] bridgeArguments = bridgeType.getArgumentTypes();
        Type[] targetArguments = targetType.getArgumentTypes();
        if (bridgeArguments.length != targetArguments.length
                || bridgeType.getReturnType().getSort() != Type.VOID
                || targetType.getReturnType().getSort() != Type.VOID) {
            throw new IllegalArgumentException("Bridge and target must have the same argument count and return void");
        }

        MethodNode bridge = new MethodNode(Opcodes.ACC_PROTECTED | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                name, bridgeDescriptor, null, null);
        bridge.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        int localIndex = 1;
        int stackSize = 1;
        for (int i = 0; i < bridgeArguments.length; i++) {
            Type bridgeArgument = bridgeArguments[i];
            Type targetArgument = targetArguments[i];
            if (!bridgeArgument.equals(targetArgument)
                    && (bridgeArgument.getSort() != Type.OBJECT && bridgeArgument.getSort() != Type.ARRAY
                    || targetArgument.getSort() != Type.OBJECT && targetArgument.getSort() != Type.ARRAY)) {
                throw new IllegalArgumentException("Bridge argument " + i + " cannot convert "
                        + bridgeArgument + " to " + targetArgument);
            }

            bridge.instructions.add(new VarInsnNode(bridgeArgument.getOpcode(Opcodes.ILOAD), localIndex));
            if (!bridgeArgument.equals(targetArgument)) {
                String castType = targetArgument.getSort() == Type.OBJECT
                        ? targetArgument.getInternalName()
                        : targetArgument.getDescriptor();
                bridge.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, castType));
            }
            localIndex += bridgeArgument.getSize();
            stackSize += targetArgument.getSize();
        }
        bridge.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, owner, name, targetDescriptor, false));
        bridge.instructions.add(new InsnNode(Opcodes.RETURN));
        bridge.maxStack = stackSize;
        bridge.maxLocals = localIndex;
        return bridge;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        preTransformerRegistry.apply(targetClass, mixinInfo);
    }
    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        postTransformerRegistry.apply(targetClass, mixinInfo);
    }

    //<editor-fold desc="Unused overrides">
    @Override public String getRefMapperConfig() {
        return null;
    }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }
    @Override public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}
    @Override public List<String> getMixins() {
        return null;
    }
    //</editor-fold>
}