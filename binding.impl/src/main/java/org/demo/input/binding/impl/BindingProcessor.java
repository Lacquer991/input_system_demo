package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;

import java.util.Optional;
import java.util.Set;

interface BindingProcessor<ActionType extends Enum<ActionType>> {

    void update(KeyInputEvent<?> event, Set<Enum<?>> pressedKeys, long nowMillis);

    Optional<ProcessorState<ActionType>> getCurrentState();

    void reset();

    default void dispose() {
        reset();
    }
}
