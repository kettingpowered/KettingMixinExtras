package org.kettingpowered.mixinextras.injectionPoints;

import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeInvoke;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

@InjectionPoint.AtCode("INVOKE_C")
public class BeforeInvokeC extends BeforeInvoke {
    public BeforeInvokeC(InjectionPointData data) {
        super(data);
    }
    public RestrictTargetLevel getTargetRestriction(IInjectionPointContext context) {
        return RestrictTargetLevel.CONSTRUCTORS_AFTER_DELEGATE;
    }
}
