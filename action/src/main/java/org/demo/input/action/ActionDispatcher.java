package org.demo.input.action;

import org.demo.input.action.spi.ActionFactory;
import org.reactivestreams.Publisher;

import java.util.function.Consumer;

public interface ActionDispatcher extends AutoCloseable {

    @Override
    void close();

    static <ActionType extends Enum<ActionType>> ActionDispatcher bind(
            Publisher<ActionType> publisher,
            ActionManager<ActionType> manager,
            boolean marshalToFx,
            Consumer<Throwable> onError) {

        return ActionFactory.getInstance().createDispatcher(publisher, manager, marshalToFx, onError);
    }
}