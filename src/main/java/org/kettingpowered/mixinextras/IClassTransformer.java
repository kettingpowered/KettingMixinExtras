package org.kettingpowered.mixinextras;

import org.objectweb.asm.tree.ClassNode;

@FunctionalInterface
interface IClassTransformer {
    void transform(InjectionInfo info, ClassNode field);
}
