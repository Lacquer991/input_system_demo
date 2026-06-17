package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class TapProcessor<ActionType extends Enum<ActionType>> {

    private final class KeyState {
        Instant downAt;
        boolean interrupted;
        ActionType readyAction;
    }

    private final Map<Enum<?>, Binding.Tap<ActionType>> bindings = new HashMap<>();
    private final Map<Enum<?>, KeyState> states = new HashMap<>();

    void setBindings(List<Binding<ActionType>> bindings) {
        states.clear();
        this.bindings.clear();
        for (Binding<ActionType> b : bindings) {
            if (b instanceof Binding.Tap<ActionType> tap) {
                this.bindings.put(tap.getKey(), tap);
            }
        }
    }

    void update(KeyInputEvent<?> event) {
        Enum<?> key = event.getKeyType();

        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            for (var entry : states.entrySet()) {
                if (!entry.getKey().equals(key) && entry.getValue().downAt != null) {
                    entry.getValue().interrupted = true;
                }
            }
            if (bindings.containsKey(key)) {
                KeyState s = states.computeIfAbsent(key, k -> new KeyState());
                s.downAt = event.getTimestamp();
                s.interrupted = false;
                s.readyAction = null;
            }
            return;
        }

        KeyState state = states.get(key);
        if (state == null || state.downAt == null || state.interrupted) {
            if (state != null) {
                state.downAt = null;
                state.interrupted = false;
            }
            return;
        }

        Binding.Tap<ActionType> tap = bindings.get(key);
        if (tap == null) return;

        Duration duration = Duration.between(state.downAt, event.getTimestamp());
        state.downAt = null;
        if (duration.compareTo(tap.getDuration()) <= 0) {
            state.readyAction = tap.getActionType();
        }
    }

    Optional<ActionType> poll(Enum<?> key) {
        KeyState state = states.get(key);
        if (state == null || state.readyAction == null) return Optional.empty();
        ActionType action = state.readyAction;
        state.readyAction = null;
        return Optional.of(action);
    }

    void consume(Enum<?> key) {
        KeyState state = states.get(key);
        if (state != null) {
            state.downAt = null;
            state.readyAction = null;
            state.interrupted = false;
        }
    }

    Set<Enum<?>> boundKeys() {
        return bindings.keySet();
    }

    void dispose() {
        states.clear();
    }
}