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

class DoubleTapProcessor<ActionType extends Enum<ActionType>> {

    private final class KeyState {
        Instant downAt;
        Instant firstUpAt;
        Instant secondDownAt;
        boolean waitingSecondUp;
        Disposable timer;
        ActionType readyAction;

        boolean isPending() {
            return firstUpAt != null || waitingSecondUp;
        }

        void cancelTimer() {
            if (timer != null) { timer.dispose(); timer = null; }
        }

        void clearPending() {
            cancelTimer();
            firstUpAt = null;
            secondDownAt = null;
            waitingSecondUp = false;
        }
    }

    private final Scheduler scheduler;
    private final Runnable onStateChanged;
    private final Map<Enum<?>, Binding.DoubleTap<ActionType>> bindings = new HashMap<>();
    private final Map<Enum<?>, ActionType> conflictingTapActions = new HashMap<>();
    private final Map<Enum<?>, KeyState> states = new HashMap<>();

    DoubleTapProcessor(Scheduler scheduler, Runnable onStateChanged) {
        this.scheduler = scheduler;
        this.onStateChanged = onStateChanged;
    }

    void setBindings(List<Binding<ActionType>> bindings) {
        dispose();
        this.bindings.clear();
        this.conflictingTapActions.clear();

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
                if (tapAction != null) conflictingTapActions.put(dt.getKey(), tapAction);
            }
        }
    }

    Optional<ActionType> update(KeyInputEvent<?> event) {
        Enum<?> key = event.getKeyType();
        Binding.DoubleTap<ActionType> dt = bindings.get(key);
        if (dt == null) return Optional.empty();

        KeyState state = states.computeIfAbsent(key, k -> new KeyState());

        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            if (state.firstUpAt != null && !state.waitingSecondUp) {
                Duration gap = Duration.between(state.firstUpAt, event.getTimestamp());
                if (!gap.isNegative() && gap.compareTo(dt.getInterval()) <= 0) {
                    state.cancelTimer();
                    state.waitingSecondUp = true;
                    state.secondDownAt = event.getTimestamp();
                }
            }
            state.downAt = event.getTimestamp();
            return Optional.empty();
        }

        if (state.downAt == null) return Optional.empty();

        Duration pressDuration = Duration.between(state.downAt, event.getTimestamp());
        state.downAt = null;
        boolean validPress = pressDuration.compareTo(dt.getDuration()) <= 0;

        if (state.waitingSecondUp) {
            Duration interval = Duration.between(state.firstUpAt, state.secondDownAt);
            boolean validInterval = !interval.isNegative() && interval.compareTo(dt.getInterval()) <= 0;
            state.clearPending();
            if (validInterval && validPress) {
                return Optional.of(dt.getActionType());
            }

            ActionType fallback = conflictingTapActions.get(key);
            if (fallback != null) {
                state.readyAction = fallback;
                onStateChanged.run();
            }

            return Optional.empty();
        }

        if (validPress) {
            state.firstUpAt = event.getTimestamp();
            state.cancelTimer();
            state.timer = scheduler.schedule(() -> {
                state.firstUpAt = null;
                state.readyAction = conflictingTapActions.get(key);
                onStateChanged.run();
            }, dt.getInterval().toMillis(), TimeUnit.MILLISECONDS);
        }

        return Optional.empty();
    }

    Optional<ActionType> poll(Enum<?> key) {
        KeyState state = states.get(key);
        if (state == null || state.readyAction == null) return Optional.empty();
        ActionType action = state.readyAction;
        state.readyAction = null;
        return Optional.of(action);
    }

    boolean isPending(Enum<?> key) {
        KeyState state = states.get(key);
        return state != null && state.isPending();
    }

    Set<Enum<?>> boundKeys() {
        return bindings.keySet();
    }

    void dispose() {
        states.values().forEach(KeyState::cancelTimer);
        states.clear();
    }
}