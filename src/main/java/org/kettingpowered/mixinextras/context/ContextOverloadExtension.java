package org.kettingpowered.mixinextras.context;

import com.llamalad7.mixinextras.MixinExtrasBootstrap;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.utils.MixinInternals;
import org.kettingpowered.mixinextras.annotations.ContextArg;
import org.kettingpowered.mixinextras.annotations.ContextOperation;
import org.kettingpowered.mixinextras.annotations.ContextOverload;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.signature.SignatureWriter;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.struct.InjectionInfo;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;
import org.spongepowered.asm.service.MixinService;
import org.spongepowered.asm.util.Annotations;
import org.spongepowered.asm.util.Bytecode;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

import static org.kettingpowered.mixinextras.context.ContextBytecode.*;

public final class ContextOverloadExtension implements IExtension {

    private static final String CONTEXT_ARG = Type.getDescriptor(ContextArg.class);
    private static final String CONTEXT_OPERATION = Type.getDescriptor(ContextOperation.class);
    private static final Type OPERATION = Type.getType(Operation.class);
    private static final ContextDeclarations DECLARATIONS = new ContextDeclarations();
    private static final Method ADD_METHOD = classInfoAdder();
    private static boolean registered;

    private final ContextOverloadExtension planner;
    private final Map<ClassNode, ClassPlan> plans = new WeakHashMap<>();
    private final Set<ClassNode> prepared = Collections.newSetFromMap(new WeakHashMap<>());

    private ContextOverloadExtension(ContextOverloadExtension planner) {
        this.planner = planner;
    }

    public static synchronized void register() {
        if (registered) return;

        MixinExtrasBootstrap.init();
        ContextOverloadExtension planner = new ContextOverloadExtension(null);
        MixinInternals.registerExtension(planner, true);
        MixinInternals.registerExtension(new ContextOverloadExtension(planner));
        registered = true;
    }

    public static void registerMixin(String targetClassName, String mixinClassName) {
        ClassNode mixin = readClass(mixinClassName);
        DECLARATIONS.register(targetClassName.replace('.', '/'), mixin.name, declarations(mixin));
    }

    @Override
    public boolean checkActive(MixinEnvironment environment) {
        return true;
    }

    @Override
    public void preApply(ITargetClassContext context) {
        if (planner == null) plan(context);
    }

    @Override
    public void postApply(ITargetClassContext context) {
        if (planner != null) planner.complete(context);
    }

    @Override
    public void export(MixinEnvironment environment, String name, boolean force, ClassNode node) {
    }

    private void plan(ITargetClassContext context) {
        ClassNode target = context.getClassNode();
        ClassPlan plan = new ClassPlan(target);
        var mixins = MixinInternals.getMixinsFor(context);
        for (var mixin : mixins) {
            DECLARATIONS.register(target.name, mixin.getRight().name, declarations(mixin.getRight()));
        }

        for (var mixin : mixins) {
            for (AnnotationNode declaration : declarations(mixin.getRight())) {
                require(mixin.getLeft().getTargetClasses().size() == 1, "@ContextOverload requires one target class: " + mixin.getRight().name);
                plan.declare(declaration);
            }
        }
        for (MethodPlan method : plan.methods.values()) {
            require(method.arguments.size() == method.contract.parameters().size(), "@ContextOverload declarations differ from registered arguments: " + target.name + '.' + method.key);
        }

        for (var mixin : mixins) {
            ClassNode node = mixin.getRight();
            if (!prepared.add(node)) continue;

            for (MethodNode handler : List.copyOf(node.methods)) {
                if (!hasContextParameters(handler)) continue;

                require(mixin.getLeft().getTargetClasses().size() == 1, "@ContextArg and @ContextOperation require one target class: " + node.name);
                plan.bridge(mixin.getLeft(), node, handler);
            }
        }

        if (!plan.methods.isEmpty()) plans.put(target, plan);
    }

    private void complete(ITargetClassContext context) {
        ClassPlan plan = plans.remove(context.getClassNode());
        if (plan == null) return;

        for (MethodPlan method : plan.methods.values()) {
            if (method.original.name.equals("<init>")) method.completeConstructor(context.getClassInfo());
            else method.completeMethod(context.getClassInfo());
        }
    }

    private static final class ClassPlan {

        private final ClassNode target;
        private final Map<String, MethodPlan> methods = new LinkedHashMap<>();
        private final Set<String> signatures = new HashSet<>();

        private ClassPlan(ClassNode target) {
            this.target = target;
            for (MethodNode method : target.methods) {
                signatures.add(method.name + method.desc);
            }
        }

        private void declare(AnnotationNode declaration) {
            String selector = Annotations.getValue(declaration, "method");
            MethodNode original = findMethod(target, selector);
            require((target.access & Opcodes.ACC_INTERFACE) == 0 && !original.name.equals("<clinit>") && (original.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0,
                "@ContextOverload requires a concrete class method or constructor: " + target.name + '.' + selector);

            MethodPlan method = methods.computeIfAbsent(selector, key -> new MethodPlan(target, original));
            List<AnnotationNode> added = Annotations.getValue(declaration, "args");
            Type[] originalTypes = Type.getArgumentTypes(original.desc);
            Type[] types = new Type[originalTypes.length + added.size()];
            Map<Integer, Argument> additions = new LinkedHashMap<>();
            for (AnnotationNode node : added) {
                String name = Annotations.getValue(node, "name");
                Integer index = Annotations.getValue(node, "index");
                ContextDeclarations.Parameter parameter = method.contract.parameters().get(name);
                require(index != null && index >= 0 && index < types.length && types[index] == null, "Invalid or duplicate @AddedArg index " + index + ": " + target.name + '.' + selector);
                require(additions.values().stream().noneMatch(argument -> argument.name.equals(name)), "Duplicate @AddedArg name " + name + ": " + target.name + '.' + selector);

                Argument argument = new Argument(name, parameter.type(), parameter.slot());
                method.arguments.put(name, argument);
                types[index] = argument.type;
                additions.put(index, argument);
            }

            int originalIndex = 0;
            for (int index = 0; index < types.length; index++) {
                if (types[index] == null) types[index] = originalTypes[originalIndex++];
            }
            String descriptor = Type.getMethodDescriptor(Type.getReturnType(original.desc), types);
            require(signatures.add(original.name + descriptor), "Context overload already exists: " + target.name + '.' + original.name + descriptor);

            int access = Annotations.<Integer>getValue(declaration, "access", -1);
            if (access == -1) access = original.access & ~(Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_VARARGS);
            require((access & Opcodes.ACC_STATIC) == (original.access & Opcodes.ACC_STATIC), "@ContextOverload access must keep the static flag of the original: " + target.name + '.' + selector);
            method.overloads.add(new Overload(descriptor, access, additions));
        }

        private void bridge(IMixinInfo info, ClassNode mixin, MethodNode handler) {
            AnnotationNode injector = InjectionInfo.getInjectorAnnotation(info, handler);
            require(injector != null, "@ContextArg and @ContextOperation require an injection handler: " + mixin.name + '.' + handler.name);
            require((handler.access & Opcodes.ACC_ABSTRACT) == 0, "Context handler must have a body: " + mixin.name + '.' + handler.name);

            AnnotationNode originalInjector = Annotations.getValue(injector, "original", injector);
            List<String> selectors = Annotations.getValue(originalInjector, "method");
            require(selectors != null && selectors.size() == 1, "Context handler requires one method selector: " + mixin.name + '.' + handler.name);
            String selector = selectors.get(0);
            List<MethodNode> matches = target.methods.stream().filter(method -> (method.name + method.desc).equals(selector) || method.name.equals(selector)).toList();
            require(matches.size() == 1, "Context handler " + mixin.name + '.' + handler.name + " requires an unambiguous method in " + target.name + ": " + selector);
            MethodNode targetMethod = matches.get(0);
            MethodPlan plan = methods.get(targetMethod.name + targetMethod.desc);
            require((targetMethod.access & Opcodes.ACC_STATIC) == 0 || (handler.access & Opcodes.ACC_STATIC) != 0, "A static target requires a static context handler: " + mixin.name + '.' + handler.name);

            Type[] parameters = Type.getArgumentTypes(handler.desc);
            List<Type> retained = new ArrayList<>();
            List<Integer> retainedIndices = new ArrayList<>();
            Map<Integer, AnnotationNode> contexts = new HashMap<>();
            Map<Integer, OperationBinding> operations = new HashMap<>();
            for (int index = 0; index < parameters.length; index++) {
                AnnotationNode context = parameterAnnotation(handler, index, CONTEXT_ARG);
                AnnotationNode operation = parameterAnnotation(handler, index, CONTEXT_OPERATION);
                require(context == null || operation == null, "@ContextArg and @ContextOperation cannot share a parameter: " + mixin.name + '.' + handler.name);
                if (context == null) {
                    if (operation != null) {
                        require(parameters[index].equals(OPERATION) && operations.isEmpty(), "@ContextOperation requires the wrapped Operation parameter: " + mixin.name + '.' + handler.name);
                        OperationBinding binding = operationBinding(originalInjector, operation, mixin.name + '.' + handler.name);
                        require(retained.size() == binding.arity(), "@ContextOperation must annotate the original Operation parameter: " + mixin.name + '.' + handler.name);
                        operations.put(index, binding);
                    }
                    retained.add(parameters[index]);
                    retainedIndices.add(index);
                    continue;
                }

                String name = Annotations.getValue(context);
                Argument argument = plan == null ? null : plan.arguments.get(name);
                require(argument != null, "Unknown context argument " + name + " in " + mixin.name + '.' + handler.name);
                boolean present = Annotations.getValue(context, "present", Boolean.FALSE);
                require(parameters[index].equals(present ? Type.BOOLEAN_TYPE : argument.type), "Context argument type mismatch for " + name + " in " + mixin.name + '.' + handler.name);
                require(handler.visibleParameterAnnotations[index].size() == 1, "@ContextArg cannot be combined with another parameter annotation: " + mixin.name + '.' + handler.name);
                contexts.put(index, context);
            }

            String name = handler.name + "$ketting$context";
            require(mixin.methods.stream().noneMatch(method -> method.name.equals(name)), "Context handler bridge name already exists: " + mixin.name + '.' + name);
            ClassInfo.Method implementation = ClassInfo.forName(mixin.name).findMethod(handler, ClassInfo.INCLUDE_ALL);
            require(implementation != null, "Context handler metadata was not found: " + mixin.name + '.' + handler.name);
            handler.name = implementation.getName();
            implementation.setUnique(true);

            int access = handler.access | Opcodes.ACC_SYNTHETIC;
            if ((targetMethod.access & Opcodes.ACC_STATIC) == 0 && !targetMethod.name.equals("<init>")) access &= ~Opcodes.ACC_STATIC;
            String descriptor = Type.getMethodDescriptor(Type.getReturnType(handler.desc), retained.toArray(Type[]::new));
            MethodNode bridge = new MethodNode(Opcodes.ASM9, access, name, descriptor, bridgeSignature(handler.signature, contexts.keySet()), handler.exceptions.toArray(String[]::new));
            bridge.visibleAnnotations = new ArrayList<>(handler.visibleAnnotations);
            bridge.invisibleAnnotations = handler.invisibleAnnotations == null ? null : new ArrayList<>(handler.invisibleAnnotations);
            bridge.visibleParameterAnnotations = selectAnnotations(handler.visibleParameterAnnotations, retainedIndices);
            bridge.invisibleParameterAnnotations = selectAnnotations(handler.invisibleParameterAnnotations, retainedIndices);
            if (injector != originalInjector) {
                List<AnnotationNode> sugars = Annotations.getValue(injector, "sugars");
                require(sugars != null && sugars.size() == parameters.length, "Unsupported injector wrapper on context handler: " + mixin.name + '.' + handler.name);
                Annotations.setValue(injector, "sugars", retainedIndices.stream().map(sugars::get).toList());
                Annotations.setValue(injector, "signature", bridge.signature == null ? "" : bridge.signature);
            }

            int[] localSlots = slots(bridge);
            InsnList code = bridge.instructions;
            if ((handler.access & Opcodes.ACC_STATIC) == 0) code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            int retainedIndex = 0;
            for (int index = 0; index < parameters.length; index++) {
                AnnotationNode context = contexts.get(index);
                if (context == null) {
                    code.add(new VarInsnNode(parameters[index].getOpcode(Opcodes.ILOAD), localSlots[retainedIndex++]));
                    OperationBinding binding = operations.get(index);
                    if (binding != null) binding.emit(code);
                    continue;
                }

                Argument argument = plan.arguments.get(Annotations.<String>getValue(context));
                boolean present = Annotations.getValue(context, "present", Boolean.FALSE);
                identity(code, target.name, plan.key);
                receiver(code, bridge);
                code.add(new LdcInsnNode(argument.slot));
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, present ? "supplied" : "value", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;I)" + (present ? "Z" : "Ljava/lang/Object;"), false));
                if (!present) unbox(code, argument.type);
            }
            invoke(code, mixin.name, handler);
            code.add(new InsnNode(Type.getReturnType(handler.desc).getOpcode(Opcodes.IRETURN)));
            bridge.maxLocals = argumentSize(bridge);
            bridge.maxStack = argumentSize(handler) + (operations.isEmpty() ? 4 : 10);

            handler.visibleAnnotations.remove(injector);
            Annotations.setVisible(handler, Unique.class);
            mixin.methods.add(bridge);
            addMethod(ClassInfo.forName(mixin.name), bridge);
        }

        private OperationBinding operationBinding(AnnotationNode injector, AnnotationNode annotation, String handler) {
            require(injector.desc.equals(Type.getDescriptor(WrapOperation.class)), "@ContextOperation requires @WrapOperation: " + handler);
            List<AnnotationNode> points = Annotations.getValue(injector, "at");
            require(points != null && points.size() == 1, "@ContextOperation requires one @At: " + handler);
            String kind = Annotations.getValue(points.get(0));
            String reference = Annotations.getValue(points.get(0), "target");
            boolean constructor = "NEW".equals(kind);
            require(constructor || "INVOKE".equals(kind), "@ContextOperation requires an INVOKE or NEW target: " + handler);

            String owner;
            String selector;
            if (constructor) {
                require(reference != null && reference.startsWith("(") && Type.getReturnType(reference).getSort() == Type.OBJECT, "@ContextOperation NEW requires a target like (Largs;)Lowner;: " + handler);
                owner = Type.getReturnType(reference).getInternalName();
                selector = "<init>" + Type.getMethodDescriptor(Type.VOID_TYPE, Type.getArgumentTypes(reference));
            } else {
                int separator = reference == null ? -1 : reference.indexOf(';');
                require(reference != null && reference.startsWith("L") && separator > 1, "@ContextOperation INVOKE requires a target like Lowner;name(Largs;)V: " + handler);
                owner = reference.substring(1, separator);
                selector = reference.substring(separator + 1);
            }

            MethodNode original = findMethod(owner.equals(target.name) ? target : readClass(owner), selector);
            ContextDeclarations.Contract contract = DECLARATIONS.resolve(owner, selector);
            String name = Annotations.getValue(annotation);
            ContextDeclarations.Parameter argument = contract.parameters().get(name);
            require(argument != null, "Unknown @ContextOperation argument " + name + " for " + reference + " in " + handler);
            return new OperationBinding(contract, argument, !constructor && (original.access & Opcodes.ACC_STATIC) == 0);
        }
    }

    private record OperationBinding(ContextDeclarations.Contract contract, ContextDeclarations.Parameter argument, boolean hasReceiver) {

        private int arity() {
            return Type.getArgumentTypes(contract.key().substring(contract.key().indexOf('('))).length + (hasReceiver ? 1 : 0);
        }

        private void emit(InsnList code) {
            identity(code, contract.owner(), contract.key());
            code.add(new LdcInsnNode(contract.parameters().size()));
            code.add(new LdcInsnNode(argument.slot()));
            classLiteral(code, argument.type());
            code.add(new LdcInsnNode(arity()));
            code.add(new InsnNode(hasReceiver ? Opcodes.ICONST_1 : Opcodes.ICONST_0));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, Type.getInternalName(ContextOperations.class), "bind",
                '(' + OPERATION.getDescriptor() + "Ljava/lang/Class;Ljava/lang/String;IILjava/lang/Class;IZ)" + OPERATION.getDescriptor(), false));
        }
    }

    private static final class MethodPlan {

        private final ClassNode owner;
        private final MethodNode original;
        private final String key;
        private final ContextDeclarations.Contract contract;
        private final Map<String, Argument> arguments = new LinkedHashMap<>();
        private final List<Overload> overloads = new ArrayList<>();

        private MethodPlan(ClassNode owner, MethodNode original) {
            this.owner = owner;
            this.original = original;
            this.key = original.name + original.desc;
            this.contract = DECLARATIONS.resolve(owner.name, key);
        }

        private void completeMethod(ClassInfo classInfo) {
            for (Overload overload : overloads) {
                MethodNode generated = newOverload(overload);
                int valuesLocal = argumentSize(generated);
                generated.maxLocals = valuesLocal + 1;

                InsnList entry = new InsnList();
                collectArguments(entry, generated, overload, valuesLocal);
                identity(entry, owner.name, key);
                receiver(entry, original);
                entry.add(new VarInsnNode(Opcodes.ALOAD, valuesLocal));
                entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "offer", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)" + CALL, false));

                if ((original.access & Opcodes.ACC_STATIC) == 0) generated.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
                loadOriginalArguments(generated.instructions, generated, overload);
                invoke(generated.instructions, owner.name, original);
                generated.instructions.add(new InsnNode(Type.getReturnType(generated.desc).getOpcode(Opcodes.IRETURN)));
                guard(generated, entry, null, CALL, "finish", null);
                generated.maxStack = Math.max(generated.maxStack, argumentSize(generated) + 4);

                owner.methods.add(generated);
                addMethod(classInfo, generated);
            }

            MethodNode target = findMethod(owner, key);
            InsnList entry = new InsnList();
            identity(entry, owner.name, key);
            receiver(entry, target);
            entry.add(new LdcInsnNode(arguments.size()));
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "enter", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;I)" + FRAME, false));
            openFrame(target, entry, null);
        }

        private void completeConstructor(ClassInfo classInfo) {
            MethodNode target = findMethod(owner, key);
            Type[] originalTypes = Type.getArgumentTypes(target.desc);
            Type[] sharedTypes = Arrays.copyOf(originalTypes, originalTypes.length + 1);
            sharedTypes[originalTypes.length] = CONSTRUCTOR_ARGUMENTS;
            String descriptor = Type.getMethodDescriptor(Type.VOID_TYPE, sharedTypes);

            MethodNode shared = new MethodNode(Opcodes.ASM9, Opcodes.ACC_PRIVATE | Opcodes.ACC_SYNTHETIC, "<init>", descriptor, null, target.exceptions.toArray(String[]::new));
            target.accept(shared);
            shared.parameters = null;
            shared.visibleParameterAnnotations = null;
            shared.invisibleParameterAnnotations = null;
            for (AbstractInsnNode instruction : shared.instructions.toArray()) {
                if (instruction instanceof FrameNode) shared.instructions.remove(instruction);
            }

            int contextParameter = argumentSize(target);
            int contextLocal = Math.max(target.maxLocals, contextParameter + 1);
            InsnList preserve = new InsnList();
            preserve.add(new VarInsnNode(Opcodes.ALOAD, contextParameter));
            preserve.add(new VarInsnNode(Opcodes.ASTORE, contextLocal));
            shared.instructions.insert(preserve);
            shared.maxLocals = contextLocal + 1;

            Bytecode.DelegateInitialiser delegate = Bytecode.findDelegateInit(shared, owner.superName, owner.name);
            require(delegate.isPresent, "Context constructor has no this() or super() call: " + owner.name + '.' + key);
            InsnList entry = new InsnList();
            identity(entry, owner.name, key);
            entry.add(new VarInsnNode(Opcodes.ALOAD, 0));
            entry.add(new LdcInsnNode(arguments.size()));
            entry.add(new VarInsnNode(Opcodes.ALOAD, contextLocal));
            entry.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "enterConstructor", "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;I" + CONSTRUCTOR_ARGUMENTS.getDescriptor() + ')' + FRAME, false));
            openFrame(shared, entry, delegate.insn);
            owner.methods.add(shared);
            addMethod(classInfo, shared);

            for (Overload overload : overloads) {
                MethodNode generated = newOverload(overload);
                delegateConstructor(generated, descriptor, overload);
                owner.methods.add(generated);
                addMethod(classInfo, generated);
            }

            target.instructions.clear();
            target.tryCatchBlocks.clear();
            target.localVariables = null;
            target.visibleLocalVariableAnnotations = null;
            target.invisibleLocalVariableAnnotations = null;
            delegateConstructor(target, descriptor, null);
        }

        private MethodNode newOverload(Overload overload) {
            require(owner.methods.stream().noneMatch(method -> method.name.equals(original.name) && method.desc.equals(overload.descriptor)),
                "Context overload was added by another transformation: " + owner.name + '.' + original.name + overload.descriptor);
            return new MethodNode(Opcodes.ASM9, overload.access, original.name, overload.descriptor, null, original.exceptions.toArray(String[]::new));
        }

        private void openFrame(MethodNode method, InsnList entry, AbstractInsnNode after) {
            int frameLocal = Math.max(method.maxLocals, argumentSize(method));
            InsnList defaults = new InsnList();
            for (Argument argument : arguments.values()) {
                defaults.add(new VarInsnNode(Opcodes.ALOAD, frameLocal));
                defaults.add(new LdcInsnNode(argument.slot));
                defaultValue(defaults, argument.type);
                defaults.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "setDefault", '(' + FRAME + "ILjava/lang/Object;)V", false));
            }
            guard(method, entry, defaults, FRAME, "exit", after);
        }

        private void delegateConstructor(MethodNode method, String sharedDescriptor, Overload overload) {
            int valuesLocal = argumentSize(method);
            method.maxLocals = valuesLocal + 1;
            InsnList code = method.instructions;
            if (overload == null) {
                identity(code, owner.name, key);
                code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "takeConstructorArguments", "(Ljava/lang/Class;Ljava/lang/String;)[Ljava/lang/Object;", false));
                code.add(new VarInsnNode(Opcodes.ASTORE, valuesLocal));
            } else {
                collectArguments(code, method, overload, valuesLocal);
            }

            code.add(new VarInsnNode(Opcodes.ALOAD, 0));
            loadOriginalArguments(code, method, overload);
            code.add(new TypeInsnNode(Opcodes.NEW, CONSTRUCTOR_ARGUMENTS.getInternalName()));
            code.add(new InsnNode(Opcodes.DUP));
            code.add(new VarInsnNode(Opcodes.ALOAD, valuesLocal));
            code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, CONSTRUCTOR_ARGUMENTS.getInternalName(), "<init>", "([Ljava/lang/Object;)V", false));
            code.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, owner.name, "<init>", sharedDescriptor, false));
            code.add(new InsnNode(Opcodes.RETURN));
            method.maxStack = argumentSize(method) + 4;
        }

        private void collectArguments(InsnList code, MethodNode method, Overload overload, int valuesLocal) {
            Type[] parameters = Type.getArgumentTypes(method.desc);
            int[] localSlots = slots(method);
            code.add(new LdcInsnNode(arguments.size()));
            code.add(new MethodInsnNode(Opcodes.INVOKESTATIC, RUNTIME, "arguments", "(I)[Ljava/lang/Object;", false));
            code.add(new VarInsnNode(Opcodes.ASTORE, valuesLocal));
            for (var addition : overload.additions.entrySet()) {
                int index = addition.getKey();
                code.add(new VarInsnNode(Opcodes.ALOAD, valuesLocal));
                code.add(new LdcInsnNode(addition.getValue().slot));
                code.add(new VarInsnNode(parameters[index].getOpcode(Opcodes.ILOAD), localSlots[index]));
                box(code, parameters[index]);
                code.add(new InsnNode(Opcodes.AASTORE));
            }
        }

        private void loadOriginalArguments(InsnList code, MethodNode method, Overload overload) {
            Type[] parameters = Type.getArgumentTypes(method.desc);
            int[] localSlots = slots(method);
            for (int index = 0; index < parameters.length; index++) {
                if (overload != null && overload.additions.containsKey(index)) continue;
                code.add(new VarInsnNode(parameters[index].getOpcode(Opcodes.ILOAD), localSlots[index]));
            }
        }
    }

    private record Argument(String name, Type type, int slot) {
    }

    private record Overload(String descriptor, int access, Map<Integer, Argument> additions) {
    }

    private static List<AnnotationNode> declarations(ClassNode mixin) {
        AnnotationNode single = Annotations.getVisible(mixin, ContextOverload.class);
        if (single != null) return List.of(single);

        AnnotationNode repeated = Annotations.getVisible(mixin, ContextOverload.Container.class);
        return repeated == null ? List.of() : Annotations.getValue(repeated);
    }

    private static boolean hasContextParameters(MethodNode method) {
        for (int index = 0; index < Type.getArgumentTypes(method.desc).length; index++) {
            if (parameterAnnotation(method, index, CONTEXT_ARG) != null || parameterAnnotation(method, index, CONTEXT_OPERATION) != null) return true;
        }
        return false;
    }

    private static AnnotationNode parameterAnnotation(MethodNode method, int index, String descriptor) {
        if (method.visibleParameterAnnotations == null || index >= method.visibleParameterAnnotations.length || method.visibleParameterAnnotations[index] == null) return null;
        return method.visibleParameterAnnotations[index].stream().filter(annotation -> annotation.desc.equals(descriptor)).findFirst().orElse(null);
    }

    @SuppressWarnings("unchecked")
    private static List<AnnotationNode>[] selectAnnotations(List<AnnotationNode>[] source, List<Integer> indices) {
        if (source == null) return null;

        List<AnnotationNode>[] selected = new List[indices.size()];
        for (int index = 0; index < indices.size(); index++) {
            List<AnnotationNode> annotations = indices.get(index) < source.length ? source[indices.get(index)] : null;
            if (annotations == null) continue;
            selected[index] = new ArrayList<>(annotations);
            selected[index].removeIf(annotation -> annotation.desc.equals(CONTEXT_OPERATION));
        }
        return selected;
    }

    private static String bridgeSignature(String original, Set<Integer> removed) {
        if (original == null) return null;

        SignatureWriter writer = new SignatureWriter() {

            private int index;

            @Override
            public SignatureVisitor visitParameterType() {
                return removed.contains(index++) ? new SignatureVisitor(Opcodes.ASM9) {
                } : super.visitParameterType();
            }
        };
        new SignatureReader(original).accept(writer);
        return writer.toString();
    }

    private static MethodNode findMethod(ClassNode owner, String selector) {
        require(selector != null && selector.indexOf('(') > 0, "An exact method name and descriptor are required: " + owner.name + '.' + selector);
        return owner.methods.stream().filter(method -> (method.name + method.desc).equals(selector)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Method not found: " + owner.name + '.' + selector));
    }

    private static ClassNode readClass(String name) {
        try {
            return MixinService.getService().getBytecodeProvider().getClassNode(name);
        } catch (ClassNotFoundException | IOException exception) {
            throw new IllegalArgumentException("Cannot read class " + name, exception);
        }
    }

    private static Method classInfoAdder() {
        try {
            Method method = ClassInfo.class.getDeclaredMethod("addMethod", MethodNode.class);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static void addMethod(ClassInfo owner, MethodNode method) {
        try {
            ADD_METHOD.invoke(owner, method);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Cannot register generated method " + method.name + method.desc, exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
