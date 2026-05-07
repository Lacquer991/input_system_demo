package org.demo.input.action.impl;

import org.demo.input.action.Action;
import org.demo.input.action.ActionManager;
import org.demo.input.action.exceptions.ActionNotFoundException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

class DefaultActionManager<ActionType extends Enum<ActionType>> implements ActionManager<ActionType> {

    private final Deque<Layer<ActionType>> layers = new ArrayDeque<>();

    @Override
    public Action<ActionType> getAction(ActionType actionType) {
        for (Layer<ActionType> layer : layers) {
            var action = layer.findAction(actionType);
            if (action.isPresent()) {
                return action.get();
            }
        }
        throw new ActionNotFoundException(actionType.name());
    }

    @Override
    public void push(Layer<ActionType> layer) {
        layers.push(layer);
    }

    @Override
    public Optional<Layer<ActionType>> pop() {
        return layers.isEmpty() ? Optional.empty() : Optional.of(layers.pop());
    }

    @Override
    public Optional<Layer<ActionType>> peek() {
        return layers.isEmpty() ? Optional.empty() : Optional.of(layers.peek());
    }

    @Override
    public void clear() {
        layers.clear();
    }
}
