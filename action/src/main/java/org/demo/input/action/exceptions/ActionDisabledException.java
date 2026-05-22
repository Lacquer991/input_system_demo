package org.demo.input.action.exceptions;

public class ActionDisabledException extends RuntimeException {

    public ActionDisabledException(String actionName) {
        super("Action is disabled: " + actionName);
    }
}