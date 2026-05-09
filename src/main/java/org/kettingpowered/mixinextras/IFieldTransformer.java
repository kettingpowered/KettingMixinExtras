package org.kettingpowered.mixinextras;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Map;

@FunctionalInterface
interface IFieldTransformer {
    int transform(ClassNode targetClass, IMixinInfo mixinInfo, FieldNode method, @NotNull Map<String, Object> annotation);
}
