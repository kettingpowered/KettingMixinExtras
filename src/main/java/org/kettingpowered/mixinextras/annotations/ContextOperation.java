package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.*;

/// Marks the Operation parameter of a {@code @WrapOperation} handler, so that calling it with one extra value
/// supplies the named {@link ContextOverload} argument of the wrapped method or constructor.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ContextOperation {
    String value();
}
