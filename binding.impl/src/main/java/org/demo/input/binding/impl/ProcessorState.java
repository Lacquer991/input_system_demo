package org.demo.input.binding.impl;

import java.util.Set;

record ProcessorState<ActionType extends Enum<ActionType>>(
        Phase phase,
        ActionType action,
        Set<Enum<?>> keys,
        long readyAtMillis
) {

    enum Phase { WAITING, ACTIVE, READY }

    ProcessorState {
        keys = Set.copyOf(keys);
    }

    boolean isReady() {
        return phase == Phase.READY;
    }

    ProcessorState<ActionType> ready() {
        return new ProcessorState<>(Phase.READY, action, keys, readyAtMillis);
    }
}
