package org.kettingpowered.mixinextras;

import org.objectweb.asm.tree.FieldNode;

@FunctionalInterface
interface IFieldTransformer {
    int transform(InjectionInfo info, FieldNode field);
}
