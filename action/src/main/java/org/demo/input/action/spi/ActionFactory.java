package org.demo.input.action.spi;

import javafx.beans.value.ObservableBooleanValue;
import org.demo.input.action.Action;
import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.reactivestreams.Publisher;

import java.util.ServiceLoader;
import java.util.function.Consumer;

public interface ActionFactory {

    <ActionType extends Enum<ActionType>> Action<ActionType> createAction(ActionType type, Runnable executor, ObservableBooleanValue enabled);

    <ActionType extends Enum<ActionType>> ActionManager<ActionType> createManager();

    <ActionType extends Enum<ActionType>> ActionDispatcher createDispatcher(Publisher<ActionType> publisher, ActionManager<ActionType> manager, boolean marshalToFx, Consumer<Throwable> onError);

    ActionFactory INSTANCE = loadFactory();

    static ActionFactory getInstance() {
        return INSTANCE;
    }

    private static ActionFactory loadFactory() {
        return ServiceLoader.load(ActionFactory.class).findFirst().orElseThrow(() -> new IllegalStateException("No ActionFactory found on classpath"));
    }
}