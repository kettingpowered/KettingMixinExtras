package org.kettingpowered.mixinextras;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Map;

record InjectionInfo(ClassNode targetClass, IMixinInfo mixinInfo, @NotNull Map<String, Object> annotationValues) {}
