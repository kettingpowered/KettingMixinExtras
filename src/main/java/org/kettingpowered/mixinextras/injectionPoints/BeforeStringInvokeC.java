package org.kettingpowered.mixinextras.injectionPoints;

import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeStringInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

@InjectionPoint.AtCode("INVOKE_STRING_C")
public class BeforeStringInvokeC extends BeforeStringInvoke {
    public BeforeStringInvokeC(InjectionPointData data) {
        super(data);
    }
    public RestrictTargetLevel getTargetRestriction(IInjectionPointContext context) {
        return RestrictTargetLevel.CONSTRUCTORS_AFTER_DELEGATE;
    }
}
