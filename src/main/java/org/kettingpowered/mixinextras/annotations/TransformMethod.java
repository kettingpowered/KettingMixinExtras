package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.*;

@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE})
public @interface TransformMethod {
    String method();
    String desc() default "";
    Class<? extends Annotation>[] toApply();
}
