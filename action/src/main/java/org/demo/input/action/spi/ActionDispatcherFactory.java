package org.demo.input.action.spi;

import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.reactivestreams.Publisher;

import java.util.function.Consumer;

public interface ActionDispatcherFactory {
    <ActionType extends Enum<ActionType>> ActionDispatcher create(Publisher<ActionType> publisher,
                                                ActionManager<ActionType> manager,
                                                boolean marshalToFx,
                                                Consumer<Throwable> onError);
}