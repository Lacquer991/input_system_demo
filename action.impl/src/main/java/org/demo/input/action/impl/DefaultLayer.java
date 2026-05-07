package org.demo.input.action.impl;

import org.demo.input.action.Action;
import org.demo.input.action.ActionManager;

import java.util.Map;
import java.util.Optional;

class DefaultLayer<ActionType extends Enum<ActionType>> implements ActionManager.Layer<ActionType> {

    private final Map<ActionType, Action<ActionType>> actions;

    public DefaultLayer(Map<ActionType, Action<ActionType>> actions) {
        this.actions = Map.copyOf(actions);
    }

    @Override
    public Optional<? extends Action<ActionType>> findAction(ActionType actionType) {
        return Optional.ofNullable(actions.get(actionType));
    }
}
