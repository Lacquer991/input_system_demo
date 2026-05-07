package org.demo.input.action;

import javafx.beans.property.ReadOnlyBooleanProperty;
import org.demo.input.action.exceptions.ActionDisabledException;

public interface Action<ActionType extends Enum<ActionType>> {

    ActionType getActionType();

    ReadOnlyBooleanProperty enabledProperty();

    default boolean isEnabled() {
        return enabledProperty().get();
    }

    void execute() throws ActionDisabledException;
}
