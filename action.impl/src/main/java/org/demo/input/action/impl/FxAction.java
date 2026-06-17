package org.demo.input.action.impl;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ObservableBooleanValue;
import org.demo.input.action.Action;
import org.demo.input.action.exceptions.ActionDisabledException;

final class FxAction<ActionType extends Enum<ActionType>> implements Action<ActionType> {

    private final ActionType actionType;
    private final Runnable executor;
    private final BooleanProperty enabled = new SimpleBooleanProperty(this, "enabled", true);

    FxAction(ActionType actionType, Runnable executor, ObservableBooleanValue enabledBinding) {
        this.actionType = actionType;
        this.executor = executor;

        if (enabledBinding != null) {
            enabled.bind(enabledBinding);
        }
    }

    @Override
    public ActionType getActionType() {
        return actionType;
    }

    @Override
    public ReadOnlyBooleanProperty enabledProperty() {
        return enabled;
    }

    @Override
    public void execute() {
        if (!enabled.get()) throw new ActionDisabledException(actionType.name());
        executor.run();
    }
}