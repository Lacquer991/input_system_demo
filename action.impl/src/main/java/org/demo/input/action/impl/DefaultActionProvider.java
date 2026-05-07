package org.demo.input.action.impl;

import org.demo.input.action.Action;
import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.demo.input.action.spi.ActionProvider;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.function.Consumer;

public final class DefaultActionProvider implements ActionProvider {

    @Override
    public <ActionType extends Enum<ActionType>> ActionManager<ActionType> createManager() {
        return new DefaultActionManager<>();
    }

    @Override
    public <ActionType extends Enum<ActionType>> Action<ActionType> createAction(ActionType actionType, Runnable executor) {
        return new FxAction<>(actionType, executor);
    }

    @Override
    public <ActionType extends Enum<ActionType>> ActionManager.Layer<ActionType> createEnumLayer(Map<ActionType, Action<ActionType>> actions) {
        return new DefaultLayer<>(actions);
    }

    @Override
    public <ActionType extends Enum<ActionType>> ActionDispatcher<ActionType> createDispatcher(Publisher<ActionType> triggers, ActionManager<ActionType> manager, boolean marshalToFx, Consumer<Throwable> onError) {
        return new DefaultActionDispatcher<>(triggers, manager, marshalToFx, onError);
    }
}
