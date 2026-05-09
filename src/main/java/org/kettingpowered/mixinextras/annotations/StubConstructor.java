package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/// This annotation replaces the body of a function to return a new value of clazz with a call to the constructor,
///  who's arguments matches the arguments of the function call.
///
/// Additionally, it expects the return Value to be either equal to:
/// - void (which is kind-of pointless, since you are constructing a class and instantly discarding it),
/// - clazz or a superclass of clazz.
///
/// If the return Value is something bogus, you will still get an instance of clazz.
/// The typing in our java source-code will just be wrong (which will sooner or later lead to issues)!
///
/// Also, it's recommended (but not required), that the function tagged with this annotation is static,
/// so that you can make use this function (in the source-code) in static contexts.
@SuppressWarnings("unused")
@Retention(RetentionPolicy.CLASS)
public @interface StubConstructor {
    Class<?> clazz();
}
