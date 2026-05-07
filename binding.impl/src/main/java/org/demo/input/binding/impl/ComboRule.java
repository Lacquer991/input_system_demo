package org.demo.input.binding.impl;

import java.time.Duration;
import java.util.Set;

record ComboRule<ActionType extends Enum<ActionType>>(
        Set<Enum<?>> requiredKeys,
        Set<Enum<?>> observedKeys,
        Set<Enum<?>> blockers,
        boolean exactMatch,
        Duration chordDelay,
        ActionType chordAction,
        ActionType holdAction,
        Duration holdDuration
) {
}
