package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

class DoubleTapProcessor<ActionType extends Enum<ActionType>>
        implements BindingProcessor<ActionType> {

    private static final class KeyState {
        Instant downAt;
        Instant firstUpAt;
        Instant secondDownAt;
        boolean waitingSecondUp;
        Disposable timer;

        void cancelTimer() {
            if (timer != null) {
                timer.dispose();
                timer = null;
            }
        }

        void clearPending() {
            cancelTimer();
            firstUpAt = null;
            secondDownAt = null;
            waitingSecondUp = false;
        }

        boolean isPending() {
            return firstUpAt != null || waitingSecondUp;
        }
    }

    private final Scheduler scheduler;
    private final Consumer<ActionType> emit;

    private final Map<Enum<?>, Binding.DoubleTap<ActionType>> bindings = new HashMap<>();
    private final Map<Enum<?>, ActionType> pendingTapActions = new HashMap<>();
    private final Map<Enum<?>, KeyState> states = new HashMap<>();

    DoubleTapProcessor(Scheduler scheduler, Consumer<ActionType> emit) {
        this.scheduler = scheduler;
        this.emit = emit;
    }

    @Override
    public void setBindings(List<Binding<ActionType>> bindings) {
        dispose();
        this.bindings.clear();
        this.pendingTapActions.clear();

        Map<Enum<?>, ActionType> taps = new HashMap<>();
        for (Binding<ActionType> b : bindings) {
            if (b instanceof Binding.Tap<ActionType> tap) {
                taps.put(tap.getKey(), tap.getActionType());
            }
        }

        for (Binding<ActionType> b : bindings) {
            if (b instanceof Binding.DoubleTap<ActionType> dt) {
                this.bindings.put(dt.getKey(), dt);
                ActionType tapAction = taps.get(dt.getKey());
                if (tapAction != null) {
                    pendingTapActions.put(dt.getKey(), tapAction);
                }
            }
        }
    }

    @Override
    public Optional<ActionType> onEvent(KeyInputEvent<?> event, Set<Enum<?>> pressed) {
        Enum<?> key = event.getKeyType();
        if (!bindings.containsKey(key)) return Optional.empty();

        Binding.DoubleTap<ActionType> dt = bindings.get(key);
        KeyState state = states.computeIfAbsent(key, k -> new KeyState());

        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            return onDown(key, event.getTimestamp(), dt, state);
        } else {
            return onUp(key, event.getTimestamp(), dt, state);
        }
    }

    private Optional<ActionType> onDown(Enum<?> key, Instant ts,
                                        Binding.DoubleTap<ActionType> dt, KeyState state) {
        if (state.firstUpAt != null && !state.waitingSecondUp) {
            Duration gap = Duration.between(state.firstUpAt, ts);
            if (!gap.isNegative() && gap.compareTo(dt.getInterval()) <= 0) {
                state.waitingSecondUp = true;
                state.secondDownAt = ts;
                state.cancelTimer();
            }
        }
        state.downAt = ts;
        return Optional.empty();
    }

    private Optional<ActionType> onUp(Enum<?> key, Instant ts,
                                      Binding.DoubleTap<ActionType> dt, KeyState state) {
        if (state.downAt == null) return Optional.empty();

        Duration duration = Duration.between(state.downAt, ts);
        state.downAt = null;
        boolean validDTap = duration.compareTo(dt.getDuration()) <= 0;

        if (state.waitingSecondUp) {
            Duration interval = Duration.between(state.firstUpAt, state.secondDownAt);
            boolean validInterval = !interval.isNegative() && interval.compareTo(dt.getInterval()) <= 0;
            state.clearPending();
            if (validInterval && validDTap) {
                return Optional.of(dt.getActionType());
            }
            return Optional.empty();
        }

        if (validDTap) {
            state.firstUpAt = ts;
            state.cancelTimer();
            state.timer = scheduler.schedule(() -> {
                state.firstUpAt = null;
                ActionType tapAction = pendingTapActions.get(key);
                if (tapAction != null) emit.accept(tapAction);
            }, dt.getInterval().toMillis(), TimeUnit.MILLISECONDS);
        }
        return Optional.empty();
    }

    boolean isPending(Enum<?> key) {
        KeyState state = states.get(key);
        return state != null && state.isPending();
    }

    @Override
    public void dispose() {
        states.values().forEach(KeyState::cancelTimer);
        states.clear();
    }
}