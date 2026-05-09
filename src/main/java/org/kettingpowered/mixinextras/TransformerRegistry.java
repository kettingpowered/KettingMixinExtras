package org.kettingpowered.mixinextras;

import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.util.Annotations;

import java.lang.annotation.Annotation;
import java.util.HashMap;
import java.util.Map;

final class TransformerRegistry {
    private final Map<Class<? extends Annotation>, IMethodTransformer> methodTransformers = new HashMap<>();
    private final Map<Class<? extends Annotation>, IFieldTransformer> fieldTransformers = new HashMap<>();

    public void add(Class<? extends Annotation> annotation, IMethodTransformer methodTransformer, IFieldTransformer fieldTransformer) {
        if (methodTransformer != null) methodTransformers.put(annotation, methodTransformer);
        if (fieldTransformer != null) fieldTransformers.put(annotation, fieldTransformer);
    }

    void apply(ClassNode targetClass, IMixinInfo mixinInfo) {
        for(int i = 0; i < targetClass.methods.size(); i++)
            i += applyMethod(targetClass, mixinInfo, targetClass.methods.get(i));
        for(int i = 0; i < targetClass.fields.size(); i++)
            i += applyField(targetClass, mixinInfo, targetClass.fields.get(i));
    }

    private int applyMethod(ClassNode targetClass, IMixinInfo mixinInfo, MethodNode method) {
        if (method == null || method.invisibleAnnotations == null) return 0;
        return methodTransformers.entrySet().stream().mapToInt(kv -> {
            AnnotationNode annotationNode = Annotations.getInvisible(method, kv.getKey());
            if (annotationNode == null) return 0;
            KettingMixinPlugin.log("Applying transformation to: {}:{}", targetClass.name, method.name);
            InjectionInfo info = new InjectionInfo(targetClass, mixinInfo, annotationNode);
            return kv.getValue().transform(info, method);
        }).sum();
    }

    private int applyField(ClassNode targetClass, IMixinInfo mixinInfo, FieldNode field) {
        if (field == null || field.invisibleAnnotations == null) return 0;
        return fieldTransformers.entrySet().stream().mapToInt(kv -> {
            AnnotationNode annotationNode = Annotations.getInvisible(field, kv.getKey());
            if (annotationNode == null) return 0;
            KettingMixinPlugin.log("Applying transformation to field: {}.{}", targetClass.name, field.name);
            InjectionInfo info = new InjectionInfo(targetClass, mixinInfo, annotationNode);
            return kv.getValue().transform(info, field);
        }).sum();
    }
}
