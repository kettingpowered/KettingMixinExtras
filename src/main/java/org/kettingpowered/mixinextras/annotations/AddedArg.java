package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.*;

/// Describes one argument that a {@link ContextOverload} adds to the original method.
/// {@code index} is the position of the argument in the overload's parameter list.
@Retention(RetentionPolicy.RUNTIME)
@Target({})
public @interface AddedArg {
    String name();
    int index();
    Class<?> type();
}
