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

class DoubleTapProcessor<ActionType extends Enum<ActionType>> implements BindingProcessor<ActionType> {

    private final Map<Enum<?>, Binding.DoubleTap<ActionType>> bindings = new HashMap<>();
    private final Map<Enum<?>, Instant> startedAt = new HashMap<>();
    private ProcessorState<ActionType> currentState;
    private Instant firstUpAt;

    void setBindings(List<Binding<ActionType>> bindings) {
        reset();
        this.bindings.clear();
        for (Binding<ActionType> binding : bindings) {
            if (binding instanceof Binding.DoubleTap<ActionType> doubleTap) {
                this.bindings.put(doubleTap.getKey(), doubleTap);
            }
        }
    }

    @Override
    public void update(KeyInputEvent<?> event, Set<Enum<?>> pressedKeys, long nowMillis) {
        Enum<?> key = event.getKeyType();
        Binding.DoubleTap<ActionType> binding = bindings.get(key);
        if (binding == null) return;

        expire(nowMillis);

        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            startedAt.put(key, event.getTimestamp());
            if (isWaitingFor(key)) {
                Duration interval = Duration.between(firstUpAt, event.getTimestamp());
                if (!interval.isNegative() && interval.compareTo(binding.getInterval()) <= 0) {
                    currentState = new ProcessorState<>(ProcessorState.Phase.ACTIVE,
                            binding.getActionType(), Set.of(key), currentState.readyAtMillis());
                } else {
                    currentState = null;
                    firstUpAt = null;
                }
            }
            return;
        }

        Instant downAt = startedAt.remove(key);
        if (downAt == null) return;

        Duration pressDuration = Duration.between(downAt, event.getTimestamp());
        boolean validPress = !pressDuration.isNegative() && pressDuration.compareTo(binding.getDuration()) <= 0;

        if (isSecondPressFor(key)) {
            currentState = validPress ? new ProcessorState<>(ProcessorState.Phase.READY,
                    binding.getActionType(), Set.of(key), nowMillis) : null;
            firstUpAt = null;
        } else if (validPress) {
            firstUpAt = event.getTimestamp();
            currentState = new ProcessorState<>(ProcessorState.Phase.WAITING,
                    binding.getActionType(), Set.of(key), nowMillis + binding.getInterval().toMillis());
        }
    }

    void expire(long nowMillis) {
        if (currentState != null && currentState.phase() == ProcessorState.Phase.WAITING && nowMillis >= currentState.readyAtMillis()) {
            currentState = null;
            firstUpAt = null;
        }
    }

    private boolean isWaitingFor(Enum<?> key) {
        return currentState != null && currentState.phase() == ProcessorState.Phase.WAITING && currentState.keys().contains(key);
    }

    private boolean isSecondPressFor(Enum<?> key) {
        return currentState != null && currentState.phase() == ProcessorState.Phase.ACTIVE && currentState.keys().contains(key);
    }

    @Override
    public Optional<ProcessorState<ActionType>> getCurrentState() {
        return Optional.ofNullable(currentState);
    }

    @Override
    public void reset() {
        startedAt.clear();
        currentState = null;
        firstUpAt = null;
    }
}
