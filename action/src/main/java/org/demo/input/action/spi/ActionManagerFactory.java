package org.demo.input.action.spi;

import org.demo.input.action.ActionManager;

public interface ActionManagerFactory {
    <ActionType extends Enum<ActionType>> ActionManager<ActionType> create();
}
 