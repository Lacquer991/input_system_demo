package org.demo.input.action.impl;

import javafx.beans.value.ObservableBooleanValue;
import org.demo.input.action.Action;
import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.demo.input.action.spi.ActionFactory;
import org.reactivestreams.Publisher;

import java.util.function.Consumer;

public final class DefaultActionFactory implements ActionFactory {

    @Override
    public <ActionType extends Enum<ActionType>> Action<ActionType> createAction(ActionType type, Runnable executor, ObservableBooleanValue enabled) {
        return new FxAction<>(type, executor, enabled);
    }

    @Override
    public <ActionType extends Enum<ActionType>> ActionManager<ActionType> createManager() {
        return new DefaultActionManager<>();
    }

    @Override
    public <ActionType extends Enum<ActionType>> ActionDispatcher createDispatcher(Publisher<ActionType> publisher, ActionManager<ActionType> manager, boolean marshalToFx, Consumer<Throwable> onError) {
        return new DefaultActionDispatcher<>(publisher, manager, marshalToFx, onError);
    }
}