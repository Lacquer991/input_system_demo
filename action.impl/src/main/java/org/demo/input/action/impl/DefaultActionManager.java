package org.demo.input.action.impl;

import org.demo.input.action.Action;
import org.demo.input.action.ActionLayer;
import org.demo.input.action.ActionManager;
import org.demo.input.action.LayerHandle;
import org.demo.input.action.exceptions.ActionNotFoundException;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

final class DefaultActionManager<ActionType extends Enum<ActionType>>
        implements ActionManager<ActionType> {

    private final Deque<ActionLayer<ActionType>> layers = new ArrayDeque<>();
    private final List<DefaultActionManager<ActionType>> children = new ArrayList<>();
    private final DefaultActionManager<ActionType> parent;

    DefaultActionManager(DefaultActionManager<ActionType> parent) {
        this.parent = parent;
    }

    @Override
    public LayerHandle pushLayer(ActionLayer<ActionType> layer) {
        layers.push(layer);

        return new DefaultLayerHandle<>(layers, layer);
    }

    @Override
    public Action<ActionType> getAction(ActionType type) {
        var fromChildren = searchChildren(type);
        if (fromChildren.isPresent()) return fromChildren.get();

        var fromSelf = searchSelf(type);
        if (fromSelf.isPresent()) return fromSelf.get();

        if (parent != null) return parent.getAction(type);

        throw new ActionNotFoundException(type.name());
    }

    private Optional<Action<ActionType>> searchDown(ActionType type) {
        var fromChildren = searchChildren(type);
        if (fromChildren.isPresent()) return fromChildren;
        return searchSelf(type);
    }

    private Optional<Action<ActionType>> searchChildren(ActionType type) {
        for (var child : children) {
            if (!child.layers.isEmpty()) {
                var result = child.searchDown(type);
                if (result.isPresent()) return result;
            }
        }
        return Optional.empty();
    }

    private Optional<Action<ActionType>> searchSelf(ActionType type) {
        for (ActionLayer<ActionType> layer : layers) {
            var action = layer.findAction(type);
            if (action.isPresent()) return action;
            if (layer.isExclusive()) throw new ActionNotFoundException(type.name());
        }
        return Optional.empty();
    }

    @Override
    public ActionManager<ActionType> createChild() {
        var child = new DefaultActionManager<>(this);
        children.add(child);
        return child;
    }
}