package org.kettingpowered.mixinextras;

import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.util.Annotations;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

public final class TransformerRegistry {

    private final Map<Class<? extends Annotation>, IMethodTransformer> methodTransformers = new HashMap<>();
    private final Map<Class<? extends Annotation>, IFieldTransformer> fieldTransformers = new HashMap<>();

    public void add(Class<? extends Annotation> annotation, IMethodTransformer methodTransformer, IFieldTransformer fieldTransformer) {
        if (methodTransformer != null) methodTransformers.put(annotation, methodTransformer);
        if (fieldTransformer != null) fieldTransformers.put(annotation, fieldTransformer);
    }

    public void apply(ClassNode targetClass) {
        targetClass.methods.forEach(m -> applyMethod(targetClass, m));
        targetClass.fields.forEach(f -> applyField(targetClass, f));
    }

    private void applyMethod(ClassNode targetClass, MethodNode method) {
        if (method.invisibleAnnotations == null) return;
        methodTransformers.forEach((ann, transformer) -> {
            if (Annotations.getInvisible(method, ann) == null) return;
            transformer.transform(targetClass, method);
        });
    }

    private void applyField(ClassNode targetClass, FieldNode field) {
        if (field.invisibleAnnotations == null) return;
        fieldTransformers.forEach((ann, transformer) -> {
            if (Annotations.getInvisible(field, ann) == null) return;
            transformer.transform(targetClass, field);
        });
    }
}
