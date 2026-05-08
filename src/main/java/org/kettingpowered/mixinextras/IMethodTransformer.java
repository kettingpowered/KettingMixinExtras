package org.kettingpowered.mixinextras;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

@FunctionalInterface
public interface IMethodTransformer {
    void transform(ClassNode targetClass, MethodNode method);
}
