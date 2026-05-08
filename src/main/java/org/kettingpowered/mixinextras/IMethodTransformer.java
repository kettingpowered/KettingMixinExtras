package org.kettingpowered.mixinextras;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.Map;

@FunctionalInterface
interface IMethodTransformer {
    void transform(ClassNode targetClass, MethodNode method, @NotNull Map<String, Object> annotation);
}
