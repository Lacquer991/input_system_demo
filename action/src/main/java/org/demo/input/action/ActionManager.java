package org.demo.input.action;

import org.demo.input.action.spi.ActionManagerFactory;

import java.util.ServiceLoader;


public interface ActionManager<ActionType extends Enum<ActionType>> {

    LayerHandle pushLayer(ActionLayer<ActionType> layer);

    Action<ActionType> getAction(ActionType type);

    default void execute(ActionType type) {
        getAction(type).execute();
    }

    static <ActionType extends Enum<ActionType>> ActionManager<ActionType> create() {
        return ServiceLoader.load(ActionManagerFactory.class)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No ActionManagerFactory found on classpath"))
                .create();
    }
}