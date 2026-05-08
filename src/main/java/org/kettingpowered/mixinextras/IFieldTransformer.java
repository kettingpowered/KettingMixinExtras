package org.kettingpowered.mixinextras;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.Map;

@FunctionalInterface
interface IFieldTransformer {
    void transform(ClassNode targetClass, FieldNode method, @NotNull Map<String, Object> annotation);
}
