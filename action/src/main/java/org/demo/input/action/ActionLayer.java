package org.demo.input.action;

import java.util.Map;
import java.util.Optional;

public interface ActionLayer<ActionType extends Enum<ActionType>> {

    Optional<Action<ActionType>> findAction(ActionType type);

    default boolean isExclusive() {
        return false;
    }

    static <ActionType extends Enum<ActionType>> ActionLayer<ActionType> of(Map<ActionType, Action<ActionType>> actions) {
        return of(actions, false);
    }

    static <ActionType extends Enum<ActionType>> ActionLayer<ActionType> of(Map<ActionType, Action<ActionType>> actions, boolean exclusive) {
        Map<ActionType, Action<ActionType>> copy = Map.copyOf(actions);

        return new ActionLayer<>() {
            @Override
            public Optional<Action<ActionType>> findAction(ActionType type) {
                return Optional.ofNullable(copy.get(type));
            }

            @Override
            public boolean isExclusive() {
                return exclusive;
            }
        };
    }
}