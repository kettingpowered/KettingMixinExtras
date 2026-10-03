package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Changes a target class's superclass and removes the method implementation that is no longer
/// compatible with the new superclass. Add the replacement method normally in the mixin; do not
/// mark it {@code @Overwrite} when its descriptor does not exist in the original target.
///
/// This transformation changes the runtime class hierarchy. Mixin's exported/debug class files may
/// still show the original superclass because Mixin can restore the class signature from its
/// cached metadata; use the loaded class's superclass to determine whether the transformation applied.
///
/// Class names and method descriptors use JVM internal format.
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface TransformSuperclass {
    String superclass();
    String methodName();
    String[] methodDescriptorsToRemove();
    String bridgeDescriptor() default "";
    String bridgeTargetDescriptor() default "";
}
