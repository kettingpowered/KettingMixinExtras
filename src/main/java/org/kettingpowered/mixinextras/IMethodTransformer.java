package org.kettingpowered.mixinextras;

import org.objectweb.asm.tree.MethodNode;

@FunctionalInterface
interface IMethodTransformer {
    int transform(InjectionInfo info, MethodNode method);
}
