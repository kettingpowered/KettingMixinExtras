package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.*;

/// Passes an argument added by {@link ContextOverload} to an injection handler parameter.
/// With {@code present = true} the parameter must be a boolean and tells whether the caller supplied the argument.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ContextArg {
    String value();
    boolean present() default false;
}
