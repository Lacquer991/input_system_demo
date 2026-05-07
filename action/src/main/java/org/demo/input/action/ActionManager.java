package org.demo.input.action;


import org.demo.input.action.exceptions.ActionDisabledException;
import org.demo.input.action.exceptions.ActionNotFoundException;

import java.util.Optional;

public interface ActionManager<ActionType extends Enum<ActionType>> {

    interface Layer<ActionType extends Enum<ActionType>> {

        Optional<? extends Action<ActionType>> findAction(ActionType actionType);
    }

    Action<ActionType> getAction(ActionType actionType) throws ActionNotFoundException;

    void push(Layer<ActionType> layer);

    Optional<Layer<ActionType>> pop();

    Optional<Layer<ActionType>> peek();

    void clear();

    default void execute(ActionType actionType) throws ActionDisabledException {
        getAction(actionType).execute();
    }

}
