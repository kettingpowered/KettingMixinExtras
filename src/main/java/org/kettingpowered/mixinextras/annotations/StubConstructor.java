package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@SuppressWarnings("unused")
@Retention(RetentionPolicy.CLASS)
public @interface StubConstructor {
    Class<?> clazz();
}
