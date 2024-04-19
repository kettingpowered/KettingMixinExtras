package org.kettingpowered.mixinextras.injectionPoints;

import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.AfterInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

@InjectionPoint.AtCode("INVOKE_ASSIGN_C")
public class AfterInvokeC extends AfterInvoke {
    public AfterInvokeC(InjectionPointData data) {
        super(data);
    }

    public RestrictTargetLevel getTargetRestriction(IInjectionPointContext context) {
        return RestrictTargetLevel.CONSTRUCTORS_AFTER_DELEGATE;
    }
}
