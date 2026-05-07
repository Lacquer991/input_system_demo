package org.demo.input.action;

public interface ActionDispatcher<ActionType extends Enum<ActionType>> extends AutoCloseable {
    @Override
    void close();
}
