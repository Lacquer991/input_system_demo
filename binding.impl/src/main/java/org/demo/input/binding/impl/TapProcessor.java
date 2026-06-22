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

class TapProcessor<ActionType extends Enum<ActionType>> implements BindingProcessor<ActionType> {

    private final Map<Enum<?>, Binding.Tap<ActionType>> bindings = new HashMap<>();
    private Enum<?> activeKey;
    private Instant startedAt;
    private ProcessorState<ActionType> currentState;

    void setBindings(List<Binding<ActionType>> bindings) {
        reset();
        this.bindings.clear();
        for (Binding<ActionType> binding : bindings) {
            if (binding instanceof Binding.Tap<ActionType> tap) {
                this.bindings.put(tap.getKey(), tap);
            }
        }
    }

    @Override
    public void update(KeyInputEvent<?> event, Set<Enum<?>> pressedKeys, long nowMillis) {
        Enum<?> key = event.getKeyType();

        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            if (activeKey != null && !activeKey.equals(key)) clearActivePress();
            if (bindings.containsKey(key) && pressedKeys.equals(Set.of(key))) {
                activeKey = key;
                startedAt = event.getTimestamp();
            }
            return;
        }

        if (!key.equals(activeKey)) return;

        Instant downAt = startedAt;
        clearActivePress();
        Binding.Tap<ActionType> tap = bindings.get(key);
        if (downAt == null || tap == null) return;

        Duration duration = Duration.between(downAt, event.getTimestamp());
        if (!duration.isNegative() && duration.compareTo(tap.getDuration()) <= 0 && currentState == null) {
            currentState = new ProcessorState<>(ProcessorState.Phase.READY,
                    tap.getActionType(), Set.of(key), nowMillis);
        }
    }

    @Override
    public Optional<ProcessorState<ActionType>> getCurrentState() {
        return Optional.ofNullable(currentState);
    }

    @Override
    public void reset() {
        clearActivePress();
        currentState = null;
    }

    private void clearActivePress() {
        activeKey = null;
        startedAt = null;
    }
}
