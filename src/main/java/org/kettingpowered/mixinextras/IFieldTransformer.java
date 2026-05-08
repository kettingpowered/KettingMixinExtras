package org.kettingpowered.mixinextras;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

@FunctionalInterface
public interface IFieldTransformer {
    void transform(ClassNode targetClass, FieldNode method);
}
