package org.kettingpowered.mixinextras.context;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import java.lang.invoke.MethodType;
import java.util.Arrays;

public final class ContextOperations {

    private ContextOperations() {
    }

    public static <R> Operation<R> bind(Operation<R> original, Class<?> owner, String method, int size, int slot, Class<?> type, int arity, boolean hasReceiver) {
        return new Bound<>(original, owner, method, size, slot, type, arity, hasReceiver);
    }

    private record Bound<R>(Operation<R> original, Class<?> owner, String method, int size, int slot, Class<?> type, int arity, boolean hasReceiver) implements Operation<R> {

        @Override
        public R call(Object... args) {
            if (args.length == arity) return original.call(args);
            if (args.length != arity + 1) {
                throw new IllegalArgumentException("@ContextOperation for " + owner.getName() + '.' + method + " expects " + arity + " arguments, optionally followed by one context value; got " + args.length);
            }

            Object value = args[arity];
            if (value == null ? type.isPrimitive() : !MethodType.methodType(type).wrap().returnType().isInstance(value)) {
                throw new IllegalArgumentException("@ContextOperation for " + owner.getName() + '.' + method + " requires a context value of type " + type.getTypeName());
            }

            Object[] values = ContextRuntime.arguments(size);
            values[slot] = value;
            ContextRuntime.Call call = ContextRuntime.offer(owner, method, hasReceiver ? args[0] : null, values);
            try {
                return original.call(Arrays.copyOf(args, arity));
            } finally {
                ContextRuntime.finish(call);
            }
        }
    }
}
