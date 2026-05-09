package org.kettingpowered.mixinextras;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Map;

@FunctionalInterface
interface IMethodTransformer {
    int transform(ClassNode targetClass, IMixinInfo mixinInfo, MethodNode method, @NotNull Map<String, Object> annotation);
}
