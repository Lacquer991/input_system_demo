package org.demo.input.action;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.demo.input.action.spi.ActionFactory;

public interface Action<ActionType extends Enum<ActionType>> {

    ActionType getActionType();

    ReadOnlyBooleanProperty enabledProperty();

    default boolean isEnabled() {
        return enabledProperty().get();
    }

    void execute();

    static <ActionType extends Enum<ActionType>> Action<ActionType> of(ActionType type, Runnable executor) {
        return ActionFactory.getInstance().createAction(type, executor, null);
    }

    static <ActionType extends Enum<ActionType>> Action<ActionType> of(ActionType type, Runnable executor, ObservableBooleanValue enabled) {
        return ActionFactory.getInstance().createAction(type, executor, enabled);
    }
}