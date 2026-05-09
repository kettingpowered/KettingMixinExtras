package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/// Marks a Method or Field as Public, regardless of what it actually is.
///
/// Also allows you to bypass rules about statics ín mixins not being able to be public.
@Retention(RetentionPolicy.CLASS)
public @interface Public {
}
