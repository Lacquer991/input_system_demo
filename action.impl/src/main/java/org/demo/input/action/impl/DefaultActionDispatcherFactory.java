package org.demo.input.action.impl;

import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.demo.input.action.spi.ActionDispatcherFactory;
import org.reactivestreams.Publisher;

import java.util.function.Consumer;

public final class DefaultActionDispatcherFactory implements ActionDispatcherFactory {

    @Override
    public <ActionType extends Enum<ActionType>> ActionDispatcher create(
            Publisher<ActionType> publisher, ActionManager<ActionType> manager, boolean marshalToFx, Consumer<Throwable> onError) {

        return new DefaultActionDispatcher<>(publisher, manager, marshalToFx, onError);
    }
}