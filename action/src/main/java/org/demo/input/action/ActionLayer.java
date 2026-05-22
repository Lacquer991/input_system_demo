package org.demo.input.action;

import java.util.Map;
import java.util.Optional;


@FunctionalInterface
public interface ActionLayer<ActionType extends Enum<ActionType>> {
    
    Optional<? extends Action<ActionType>> findAction(ActionType type);

    static <ActionType extends Enum<ActionType>> ActionLayer<ActionType> of(Map<ActionType, Action<ActionType>> actions) {

        var copy = Map.copyOf(actions);
        return type -> Optional.ofNullable(copy.get(type));
    }
}