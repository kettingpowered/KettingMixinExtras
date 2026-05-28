package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a Field as final
@Retention(RetentionPolicy.CLASS)
@Target(ElementType.FIELD)
public @interface MakeFinal {
}
