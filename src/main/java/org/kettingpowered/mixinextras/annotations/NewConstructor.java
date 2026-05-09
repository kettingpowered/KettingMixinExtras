package org.kettingpowered.mixinextras.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/// This annotation changes a function's name to be a constructor.
/// Because of JVM rules, the function should return VOID and should NOT be static!
/// Trying to call the function, WILL NOT WORK.
///
/// If you need access to the constructor created by this function, you sadly will need to
/// create a new static function in the Mixin with identical parameters and the current class as a return value
/// Then tag it with {@link StubConstructor}, which will populate the function's body (a simple {@code return null; } is enough)
///
///
/// If the return Value is something bogus, you will still get an instance of clazz.
/// The typing in our java source-code will just be wrong (which will sooner or later lead to issues)!
///
/// Aside from the function parameters and return-value you are free to do anything else (e.g. access modifiers or making the method static)
/// If you want a Constructor to delegate to another constructor, use {@link DelegateConstructor}.
///
/// {@link StubConstructor} is only for obtaining new instances of inaccessible constructors!
@Retention(RetentionPolicy.CLASS)
public @interface NewConstructor {
}
