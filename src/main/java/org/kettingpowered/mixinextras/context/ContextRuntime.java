package org.kettingpowered.mixinextras.context;

import java.util.Arrays;

public final class ContextRuntime {

    private static final Object ABSENT = new Object();
    private static final ThreadLocal<State> STATE = ThreadLocal.withInitial(State::new);

    private ContextRuntime() {
    }

    public static Object[] arguments(int size) {
        Object[] values = new Object[size];
        Arrays.fill(values, ABSENT);
        return values;
    }

    public static Call offer(Class<?> owner, String method, Object receiver, Object[] values) {
        State state = STATE.get();
        Call previous = state.pending;
        if (previous != null && previous.active == 0 && previous.matches(owner, method, receiver)) {
            for (int index = 0; index < values.length; index++) {
                if (values[index] == ABSENT) values[index] = previous.values[index];
            }
        }

        Call call = new Call(state, previous, owner, method, receiver, values);
        state.pending = call;
        return call;
    }

    public static void finish(Call call) {
        call.state.pending = call.previous;
    }

    public static Frame enter(Class<?> owner, String method, Object receiver, int size) {
        State state = STATE.get();
        Call pending = state.pending;
        Call binding = pending != null && pending.active == 0 && pending.matches(owner, method, receiver) ? pending : null;
        Object[] values = binding == null ? arguments(size) : binding.values.clone();
        if (binding != null) binding.active++;

        Frame frame = new Frame(state, state.current, owner, method, receiver, values, binding);
        state.current = frame;
        return frame;
    }

    public static Object[] takeConstructorArguments(Class<?> owner, String method) {
        Call pending = STATE.get().pending;
        if (pending == null || pending.active != 0 || !pending.matches(owner, method, null)) return null;

        pending.active++;
        return pending.values;
    }

    public static Frame enterConstructor(Class<?> owner, String method, Object receiver, int size, ConstructorArguments arguments) {
        State state = STATE.get();
        Object[] values = arguments.values == null ? arguments(size) : arguments.values.clone();

        Frame frame = new Frame(state, state.current, owner, method, receiver, values, null);
        state.current = frame;
        return frame;
    }

    public static void exit(Frame frame) {
        frame.state.current = frame.previous;
        if (frame.binding != null) frame.binding.active--;
    }

    public static void setDefault(Frame frame, int index, Object value) {
        if (!frame.supplied[index]) frame.values[index] = value;
    }

    public static Object value(Class<?> owner, String method, Object receiver, int index) {
        return current(owner, method, receiver).values[index];
    }

    public static boolean supplied(Class<?> owner, String method, Object receiver, int index) {
        return current(owner, method, receiver).supplied[index];
    }

    private static Frame current(Class<?> owner, String method, Object receiver) {
        for (Frame frame = STATE.get().current; frame != null; frame = frame.previous) {
            if (frame.owner != owner || !frame.method.equals(method)) continue;
            if (frame.receiver == receiver || receiver == null && method.startsWith("<init>")) return frame;
        }
        throw new IllegalStateException("No active context for " + owner.getName() + '.' + method);
    }

    private static final class State {

        private Call pending;
        private Frame current;
    }

    public static final class Call {

        private final State state;
        private final Call previous;
        private final Class<?> owner;
        private final String method;
        private final Object receiver;
        private final Object[] values;
        private int active;

        private Call(State state, Call previous, Class<?> owner, String method, Object receiver, Object[] values) {
            this.state = state;
            this.previous = previous;
            this.owner = owner;
            this.method = method;
            this.receiver = receiver;
            this.values = values;
        }

        private boolean matches(Class<?> owner, String method, Object receiver) {
            return this.owner == owner && this.method.equals(method) && this.receiver == receiver;
        }
    }

    public static final class Frame {

        private final State state;
        private final Frame previous;
        private final Class<?> owner;
        private final String method;
        private final Object receiver;
        private final Object[] values;
        private final boolean[] supplied;
        private final Call binding;

        private Frame(State state, Frame previous, Class<?> owner, String method, Object receiver, Object[] values, Call binding) {
            this.state = state;
            this.previous = previous;
            this.owner = owner;
            this.method = method;
            this.receiver = receiver;
            this.values = values;
            this.supplied = new boolean[values.length];
            this.binding = binding;
            for (int index = 0; index < values.length; index++) {
                this.supplied[index] = values[index] != ABSENT;
            }
        }
    }

    public static final class ConstructorArguments {

        private final Object[] values;

        public ConstructorArguments(Object[] values) {
            this.values = values;
        }
    }
}
