package org.kettingpowered.mixinextras.injectionPoints;

import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeFieldAccess;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

@InjectionPoint.AtCode("FIELD_C")
public class BeforeFieldAccessC extends BeforeFieldAccess {
    public BeforeFieldAccessC(InjectionPointData data) {
        super(data);
    }
    public RestrictTargetLevel getTargetRestriction(IInjectionPointContext context) {
        return RestrictTargetLevel.CONSTRUCTORS_AFTER_DELEGATE;
    }
}
