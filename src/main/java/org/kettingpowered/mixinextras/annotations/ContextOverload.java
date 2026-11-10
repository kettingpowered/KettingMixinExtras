package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.*;

/// Adds an overload of a target method or constructor that takes extra arguments.
/// The overload runs the original body, and handlers can read the extra arguments with {@link ContextArg}.
///
/// {@code method} is the name followed by the JVM descriptor, for example {@code add(ID)I}.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Repeatable(ContextOverload.Container.class)
public @interface ContextOverload {
    String method();
    AddedArg[] args();
    int access() default -1;

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @interface Container {
        ContextOverload[] value();
    }
}
