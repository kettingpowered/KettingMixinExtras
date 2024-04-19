package org.kettingpowered.mixinextras;

import org.kettingpowered.mixinextras.injectionPoints.*;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.util.Annotations;

import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Consumer;

public class ExampleMixinPlugin implements IMixinConfigPlugin {
    @Override
    public void onLoad(String mixinPackage) {
        InjectionPoint.register(AfterInvokeC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeFieldAccessC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeInvokeC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeNewC.class, "org.kettingpowered.mixinextras");
        InjectionPoint.register(BeforeStringInvokeC.class, "org.kettingpowered.mixinextras");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        AnnotationNode changeSuperClass = Annotations.getInvisible(targetClass, ChangeSuperClass.class);
        if (changeSuperClass != null) {
            String oldOwner = targetClass.superName;
            targetClass.superName = Annotations.<Type>getValue(changeSuperClass).getInternalName();
            transformCalls(targetClass, call -> {
                if (call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals(oldOwner)) {
                    call.owner = targetClass.superName;
                }
            });
        }
        for (ListIterator<MethodNode> it = targetClass.methods.listIterator(); it.hasNext(); ) {
            MethodNode method = it.next();
            if (Annotations.getInvisible(method, SuperConstructorStub.class) != null) {
                it.remove();
                transformCalls(targetClass, call -> {
                    if (call.name.equals(method.name) && call.desc.equals(method.desc)) {
                        call.setOpcode(Opcodes.INVOKESPECIAL);
                        call.name = "<init>";
                        call.owner = targetClass.superName;
                    }
                });
                continue;
            }
            if (Annotations.getInvisible(method, SelfConstructorStub.class) != null) {
                it.remove();
                transformCalls(targetClass, call -> {
                    if (call.name.equals(method.name) && call.desc.equals(method.desc)) {
                        call.setOpcode(Opcodes.INVOKESPECIAL);
                        call.name = "<init>";
                        call.owner = targetClass.name;
                    }
                });
                continue;
            }
            if (Annotations.getInvisible(method, Public.class) != null) {
                method.access |= Opcodes.ACC_PUBLIC;
                method.access &= ~(Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);
            }
            if (Annotations.getInvisible(method, NewConstructor.class) != null) {
                method.name = "<init>";
            }
        }
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
}