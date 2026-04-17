package org.demo.input.binding.impl;


import org.demo.input.binding.Binding;

import java.util.Set;

record ActionCandidate<ActionType extends Enum<ActionType>>(

        ActionType actionType,

        Binding.BindingType bindingType,

        Enum<?> key,

        Set<Enum<?>> keys
) {

    public static <ActionType extends Enum<ActionType>> ActionCandidate<ActionType> tap(ActionType actionType, Enum<?> key) {
        return new ActionCandidate<>(actionType, Binding.BindingType.TAP, key, null);
    }

    public static <ActionType extends Enum<ActionType>> ActionCandidate<ActionType> doubleTap(ActionType actionType, Enum<?> key) {
        return new ActionCandidate<>(actionType, Binding.BindingType.DOUBLE_TAP, key, null);
    }

    public static <ActionType extends Enum<ActionType>> ActionCandidate<ActionType> hold(ActionType actionType, Set<Enum<?>> keys) {
        return new ActionCandidate<>(actionType, Binding.BindingType.HOLD, null, keys);
    }

    public static <ActionType extends Enum<ActionType>> ActionCandidate<ActionType> chord(ActionType actionType, Set<Enum<?>> keys) {
        return new ActionCandidate<>(actionType, Binding.BindingType.CHORD, null, keys);
    }
}
