package org.demo.input.action;

import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;


public interface Action<ActionType extends Enum<ActionType>> {

    ActionType getActionType();

    ReadOnlyBooleanProperty enabledProperty();

    default boolean isEnabled() {
        return enabledProperty().get();
    }

    void execute();


    static <ActionType extends Enum<ActionType>> Action<ActionType> of(ActionType actionType, Runnable executor) {
        return new FxAction<>(actionType, executor, null);
    }
    
    static <ActionType extends Enum<ActionType>> Action<ActionType> of(ActionType actionType, Runnable executor,
                                            ObservableBooleanValue enabled) {
        return new FxAction<>(actionType, executor, enabled);
    }
}