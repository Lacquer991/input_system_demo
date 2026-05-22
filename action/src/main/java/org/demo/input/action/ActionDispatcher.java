package org.demo.input.action;

import org.demo.input.action.spi.ActionDispatcherFactory;
import org.reactivestreams.Publisher;

import java.util.ServiceLoader;
import java.util.function.Consumer;

public interface ActionDispatcher extends AutoCloseable {

    @Override
    void close();

    static <ActionType extends Enum<ActionType>> ActionDispatcher bind(
            Publisher<ActionType> publisher, ActionManager<ActionType> manager, boolean marshalToFx, Consumer<Throwable> onError) {

        return ServiceLoader.load(ActionDispatcherFactory.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ActionDispatcherFactory found on classpath"))
                .create(publisher, manager, marshalToFx, onError);
    }
}