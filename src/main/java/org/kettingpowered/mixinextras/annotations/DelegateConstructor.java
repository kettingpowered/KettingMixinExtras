package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/// This annotation exists to allow a {@link NewConstructor} to delegate properly to another constructor.
/// The function should NOT be static and should have the same parameters and return value as the desired Constructor.
///
/// The function tagged with this annotation will be REMOVED ENTIRELY.
/// Calls to the removed function inside a method tagged with {@link NewConstructor} will be rewritten to be constructor calls.
@SuppressWarnings("unused")
@Retention(RetentionPolicy.CLASS)
public @interface DelegateConstructor {
    Class<?> clazz();
}
