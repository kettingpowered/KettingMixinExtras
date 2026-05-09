package org.kettingpowered.mixinextras;

import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

record InjectionInfo(ClassNode targetClass, IMixinInfo mixinInfo, AnnotationNode annotation) {

    @NotNull Map<String, Object> annotationValues() {
        if (annotation == null || annotation.values == null || annotation.values.isEmpty()) return Collections.emptyMap();
        Map<String, Object> annotationValues = new HashMap<>(annotation.values.size() / 2);
        for (int i = 0; i + 1 < annotation.values.size(); i += 2) {
            Object key = annotation.values.get(i);
            Object value = annotation.values.get(i + 1);

            if (key instanceof String stringKey)
                annotationValues.put(stringKey, value);
            else
                KettingMixinPlugin.LOGGER.warn("Annotation key at index {} is not a String in {}", i, annotation.desc);
        }
        return Map.copyOf(annotationValues);
    }
}
