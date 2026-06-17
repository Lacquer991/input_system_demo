package org.demo.input.action;

import javafx.scene.Node;
import javafx.scene.Scene;
import org.demo.input.action.spi.ActionFactory;

public interface ActionManager<ActionType extends Enum<ActionType>> {


    LayerHandle register(Node node, ActionLayer<ActionType> layer);

    LayerHandle pushTransient(ActionLayer<ActionType> layer);

    void bindScene(Scene scene);

    Action<ActionType> getAction(ActionType type);

    default void execute(ActionType type) {
        getAction(type).execute();
    }

    static <ActionType extends Enum<ActionType>> ActionManager<ActionType> create() {
        return ActionFactory.getInstance().createManager();
    }
}