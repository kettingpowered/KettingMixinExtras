package org.kettingpowered.mixinextras;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.util.Annotations;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TransformerRegistry {
    private final Map<Class<? extends Annotation>, IMethodTransformer> methodTransformers = new HashMap<>();
    private final Map<Class<? extends Annotation>, IFieldTransformer> fieldTransformers = new HashMap<>();

    public void add(Class<? extends Annotation> annotation, IMethodTransformer methodTransformer, IFieldTransformer fieldTransformer) {
        if (methodTransformer != null) methodTransformers.put(annotation, methodTransformer);
        if (fieldTransformer != null) fieldTransformers.put(annotation, fieldTransformer);
    }

    void apply(ClassNode targetClass) {
        targetClass.methods.forEach(m -> applyMethod(targetClass, m));
        targetClass.fields.forEach(f -> applyField(targetClass, f));
    }

    private void applyMethod(ClassNode targetClass, MethodNode method) {
        if (method == null || method.invisibleAnnotations == null) return;
        methodTransformers.forEach((ann, transformer) -> {
            AnnotationNode annotationNode = Annotations.getInvisible(method, ann);
            if (annotationNode == null) return;
            KettingMixinPlugin.log("Applying transformation to: {}:{}", targetClass.name, method.name);
            transformer.transform(targetClass, method, listToMap(ann, annotationNode.values));
        });
    }

    private void applyField(ClassNode targetClass, FieldNode field) {
        if (field == null || field.invisibleAnnotations == null) return;
        fieldTransformers.forEach((ann, transformer) -> {
            AnnotationNode annotationNode = Annotations.getInvisible(field, ann);
            if (annotationNode == null) return;
            KettingMixinPlugin.log("Applying transformation to field: {}.{}", targetClass.name, field.name);
            transformer.transform(targetClass, field, listToMap(ann, annotationNode.values));
        });
    }

    @Contract("_, _ -> !null")
    private static @NotNull Map<String, Object> listToMap(@NotNull Class<? extends Annotation> clazz, @Nullable List<Object> object){
        var map = new HashMap<String, Object>();
        if (object == null) return map;
        var iter = object.listIterator();
        while (iter.hasNext()) {
            Object key = iter.next();
            Object value = iter.hasNext() ? iter.next() : null;
            if (key instanceof String stringKey) map.put(stringKey, value);
            else KettingMixinPlugin.log("The key of Annotation {} is not a String (is instead {})? Ignoring value", clazz.getCanonicalName(), key.getClass().getCanonicalName());
        }
        return map;
    }
}
