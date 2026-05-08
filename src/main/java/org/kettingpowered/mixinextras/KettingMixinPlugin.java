package org.kettingpowered.mixinextras;

import org.kettingpowered.mixinextras.annotations.NewConstructor;
import org.kettingpowered.mixinextras.annotations.Public;
import org.kettingpowered.mixinextras.annotations.SelfConstructorStub;
import org.kettingpowered.mixinextras.annotations.SuperConstructorStub;
import org.kettingpowered.mixinextras.injectionPoints.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.InjectionPoint;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class KettingMixinPlugin implements IMixinConfigPlugin {

    private final TransformerRegistry transformerRegistry = new TransformerRegistry();

    @Override
    public void onLoad(String mixinPackage) {
        InjectionPoint.register(AfterInvokeC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeFieldAccessC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeInvokeC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeNewC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeStringInvokeC.class, "org.kettingpowered.mixinextras");

        addTransformers();
    }

    private void addTransformers() {
        transformerRegistry.add(NewConstructor.class, (targetClass, method) -> {
            method.name = "<init>";
        }, null);

        transformerRegistry.add(Public.class,
                (targetClass, method) -> {
                    method.access &= ~Opcodes.ACC_PRIVATE;
                    method.access &= ~Opcodes.ACC_PROTECTED;
                    method.access |= Opcodes.ACC_PUBLIC;
                },
                (targetClass, field) -> {
                    field.access &= ~Opcodes.ACC_PRIVATE;
                    field.access &= ~Opcodes.ACC_PROTECTED;
                    field.access |= Opcodes.ACC_PUBLIC;
                }
        );

        transformerRegistry.add(SelfConstructorStub.class, (targetClass, method) -> {
            transformCalls(targetClass, call -> {
                if (call.name.equals(method.name) && call.desc.equals(method.desc)) {
                    call.setOpcode(Opcodes.INVOKESPECIAL);
                    call.name = "<init>";
                    call.owner = targetClass.name;
                }
            });
        }, null);

        transformerRegistry.add(SuperConstructorStub.class, (targetClass, method) -> {
            transformCalls(targetClass, call -> {
                if (call.name.equals(method.name) && call.desc.equals(method.desc)) {
                    call.setOpcode(Opcodes.INVOKESPECIAL);
                    call.name = "<init>";
                    call.owner = targetClass.superName;
                }
            });
        }, null);
    }

    private void transformCalls(ClassNode classNode, Consumer<MethodInsnNode> consumer) {
        for (MethodNode method : classNode.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) {
                    consumer.accept(call);
                }
            }
        }
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