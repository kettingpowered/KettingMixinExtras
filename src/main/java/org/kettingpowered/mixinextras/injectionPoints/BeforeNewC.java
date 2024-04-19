package org.kettingpowered.mixinextras.injectionPoints;

import org.spongepowered.asm.mixin.injection.IInjectionPointContext;
import org.spongepowered.asm.mixin.injection.InjectionPoint;
import org.spongepowered.asm.mixin.injection.points.BeforeNew;
import org.spongepowered.asm.mixin.injection.struct.InjectionPointData;

@InjectionPoint.AtCode("NEW_C")
public class BeforeNewC extends BeforeNew {
    public BeforeNewC(InjectionPointData data) {
        super(data);
    }
    public RestrictTargetLevel getTargetRestriction(IInjectionPointContext context) {
        return RestrictTargetLevel.CONSTRUCTORS_AFTER_DELEGATE;
    }
}
