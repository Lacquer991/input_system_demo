package org.demo.input.action;

import java.util.Map;
import java.util.Optional;


@FunctionalInterface
public interface ActionLayer<ActionType extends Enum<ActionType>> {

    Optional<? extends Action<ActionType>> findAction(ActionType type);

    static <ActionType extends Enum<ActionType>> ActionLayer<ActionType> of(Map<ActionType, Action<ActionType>> actions) {
        return of(actions, false);
    }
    
    static <ActionType extends Enum<ActionType>> ActionLayer<ActionType> of(Map<ActionType, Action<ActionType>> actions, boolean exclusive) {
        var copy = Map.copyOf(actions);
        return new ActionLayer<>() {
            @Override
            public Optional<? extends Action<ActionType>> findAction(ActionType type) {
                return Optional.ofNullable(copy.get(type));
            }
            @Override
            public boolean isExclusive() {
                return exclusive;
            }
        };
    }

    default boolean isExclusive() {
        return false;
    }
}