package org.demo.input.action.impl;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.demo.input.action.Action;
import org.demo.input.action.exceptions.ActionDisabledException;

class FxAction<ActionType extends Enum<ActionType>> implements Action<ActionType> {

    private final ActionType actionType;

    private final BooleanProperty enabled = new SimpleBooleanProperty(true);

    private final Runnable executor;

    public FxAction(ActionType actionType, Runnable executor) {
        this.actionType = actionType;
        this.executor = executor;
    }

    public BooleanProperty enabledWritable() {
        return enabled;
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
    public void execute() throws ActionDisabledException {
        if (!enabled.get()) {
            throw new ActionDisabledException(actionType.name());
        }
        executor.run();
    }
}
