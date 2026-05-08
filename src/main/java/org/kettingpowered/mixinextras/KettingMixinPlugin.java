package org.kettingpowered.mixinextras;

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

import java.util.List;
import java.util.Optional;
import java.util.Set;

public class KettingMixinPlugin implements IMixinConfigPlugin {

    public static final ILogger LOGGER = MixinService.getService().getLogger("KettingMixinExtras");
    public static final boolean DEBUG = MixinEnvironment.getDefaultEnvironment().getOption(MixinEnvironment.Option.DEBUG_VERBOSE);

    private final TransformerRegistry transformerRegistry = new TransformerRegistry();

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
        transformerRegistry.add(NewConstructor.class, (targetClass, method, _a) -> {
            method.name = "<init>";
        }, null);

        transformerRegistry.add(Public.class,
                (targetClass, method, _a) -> {
                    method.access &= ~Opcodes.ACC_PRIVATE;
                    method.access &= ~Opcodes.ACC_PROTECTED;
                    method.access |= Opcodes.ACC_PUBLIC;
                },
                (targetClass, field, _a) -> {
                    field.access &= ~Opcodes.ACC_PRIVATE;
                    field.access &= ~Opcodes.ACC_PROTECTED;
                    field.access |= Opcodes.ACC_PUBLIC;
                }
        );

        transformerRegistry.add(StubConstructor.class, (targetClass, annotated_method, annotation) -> {
            for (MethodNode method : targetClass.methods) {
                for (int i = 0; i < method.instructions.size(); i++) {
                    AbstractInsnNode isn = method.instructions.get(i);
                    if (isn instanceof MethodInsnNode call) {
                        if (call.name.equals(method.name) && call.desc.equals(method.desc)) {
                            final String name = Optional.ofNullable(annotation.get("clazz")).map(v -> ((Type)v).getInternalName()).orElse(targetClass.name);
                            InsnList list = new InsnList();
                            list.add(new TypeInsnNode(Opcodes.NEW, name));
                            list.add(new InsnNode(Opcodes.DUP));
                            method.instructions.insertBefore(call, list);
                            call.setOpcode(Opcodes.INVOKESPECIAL);
                            call.name = "<init>";
                            call.owner = name;
                        }
                    }
                }
            }
        }, null);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        transformerRegistry.apply(targetClass);
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
    @Override public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}
    //</editor-fold>
}