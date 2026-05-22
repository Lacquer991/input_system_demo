package org.demo.input.action.exceptions;

public class ActionNotFoundException extends RuntimeException {

    public ActionNotFoundException(String actionName) {
        super("Action not found: " + actionName);
    }
}