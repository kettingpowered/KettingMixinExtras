package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Replaces a constructor and changes the descriptor of an instance field.
/// The replacement constructor must take exactly one argument whose descriptor matches
/// {@code fieldDescriptor}; its body will assign that argument directly to the field.
/// The transformation runs after the mixin has been applied, so any {@code @Shadow} for the
/// field must use the field's original descriptor. Cast the shadow value to the replacement
/// type where needed; field accesses in the transformed class are updated to the new descriptor.
///
/// Descriptors use JVM format, for example {@code (Ljava/lang/Object;)V} for a constructor
/// and {@code Ljava/lang/Object;} for a field.
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.TYPE)
public @interface TransformConstructor {
    String fieldName();
    String fieldDescriptor();
    String constructorDescriptor();
    String replacementConstructorDescriptor();
}
