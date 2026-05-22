package org.demo.input.action.impl;

import org.demo.input.action.Action;
import org.demo.input.action.ActionLayer;
import org.demo.input.action.ActionManager;
import org.demo.input.action.LayerHandle;
import org.demo.input.action.exceptions.ActionNotFoundException;

import java.util.ArrayDeque;
import java.util.Deque;

final class DefaultActionManager<ActionType extends Enum<ActionType>>
        implements ActionManager<ActionType> {

    private final Deque<ActionLayer<ActionType>> layers = new ArrayDeque<>();

    @Override
    public LayerHandle pushLayer(ActionLayer<ActionType> layer) {
        layers.push(layer);

        return new DefaultLayerHandle<>(layers, layer);
    }

    @Override
    public Action<ActionType> getAction(ActionType type) {
        for (ActionLayer<ActionType> layer : layers) {
            var action = layer.findAction(type);
            if (action.isPresent()) {
                return action.get();
            }
        }
        throw new ActionNotFoundException(type.name());
    }
}