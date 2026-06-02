package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a Method as synchronized
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.METHOD})
public @interface MakeSynchronized {
}
