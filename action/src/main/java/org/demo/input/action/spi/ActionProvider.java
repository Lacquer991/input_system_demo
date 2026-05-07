package org.demo.input.action.spi;

import org.demo.input.action.Action;
import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.reactivestreams.Publisher;

import java.util.Map;
import java.util.function.Consumer;

public interface ActionProvider {

    <ActionType extends Enum<ActionType>> ActionManager<ActionType> createManager();

    <ActionType extends Enum<ActionType>> Action<ActionType> createAction(ActionType actionType, Runnable executor);

    <ActionType extends Enum<ActionType>> ActionManager.Layer<ActionType> createEnumLayer(Map<ActionType, Action<ActionType>> actions);

    <ActionType extends Enum<ActionType>> ActionDispatcher createDispatcher(Publisher<ActionType> triggers, ActionManager<ActionType> manager,
                                                                            boolean marshalToFx, Consumer<Throwable> onError);
}
