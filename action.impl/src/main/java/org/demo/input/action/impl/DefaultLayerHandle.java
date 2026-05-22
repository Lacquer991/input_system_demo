package org.demo.input.action.impl;

import org.demo.input.action.ActionLayer;
import org.demo.input.action.LayerHandle;

import java.util.Deque;

final class DefaultLayerHandle<ActionType extends Enum<ActionType>> implements LayerHandle {

    private final Deque<ActionLayer<ActionType>> stack;

    private final ActionLayer<ActionType> layer;

    private volatile boolean active = true;

    DefaultLayerHandle(Deque<ActionLayer<ActionType>> stack, ActionLayer<ActionType> layer) {
        this.stack = stack;
        this.layer = layer;
    }

    @Override
    public void close() {
        if (active) {
            active = false;
            stack.remove(layer);
        }
    }

    @Override
    public boolean isActive() {
        return active;
    }
}