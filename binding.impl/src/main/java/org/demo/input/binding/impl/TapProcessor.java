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

class TapProcessor<ActionType extends Enum<ActionType>>
        implements BindingProcessor<ActionType> {

    private static final class KeyState {
        Instant downAt;
        boolean interrupted;
    }

    private final Map<Enum<?>, Binding.Tap<ActionType>> bindings = new HashMap<>();
    private final Map<Enum<?>, KeyState> states = new HashMap<>();

    @Override
    public void setBindings(List<Binding<ActionType>> bindings) {
        this.bindings.clear();
        states.clear();
        for (Binding<ActionType> b : bindings) {
            if (b instanceof Binding.Tap<ActionType> tap) {
                this.bindings.put(tap.getKey(), tap);
            }
        }
    }

    @Override
    public Optional<ActionType> onEvent(KeyInputEvent<?> event, Set<Enum<?>> pressed) {
        Enum<?> key = event.getKeyType();

        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            for (var entry : states.entrySet()) {
                if (!entry.getKey().equals(key) && entry.getValue().downAt != null) {
                    entry.getValue().interrupted = true;
                }
            }
        }

        if (!bindings.containsKey(key)) return Optional.empty();

        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            KeyState state = states.computeIfAbsent(key, k -> new KeyState());
            state.downAt = event.getTimestamp();
            state.interrupted = false;
            return Optional.empty();
        }

        KeyState state = states.get(key);
        if (state == null || state.downAt == null) return Optional.empty();

        Instant down = state.downAt;
        state.downAt = null;

        if (state.interrupted) {
            state.interrupted = false;
            return Optional.empty();
        }

        Binding.Tap<ActionType> tap = bindings.get(key);
        Duration duration = Duration.between(down, event.getTimestamp());
        if (duration.compareTo(tap.getDuration()) <= 0) {
            return Optional.of(tap.getActionType());
        }
        return Optional.empty();
    }

    @Override
    public void dispose() {
        states.clear();
    }
}