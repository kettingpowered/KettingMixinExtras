package org.kettingpowered.mixinextras;

import org.kettingpowered.mixinextras.annotations.DelegateConstructor;
import org.kettingpowered.mixinextras.annotations.NewConstructor;
import org.kettingpowered.mixinextras.annotations.Public;
import org.kettingpowered.mixinextras.annotations.StubConstructor;
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
        int varIndex = 0;
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

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        postTransformerRegistry.apply(targetClass, mixinInfo);
    }
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        preTransformerRegistry.apply(targetClass, mixinInfo);
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