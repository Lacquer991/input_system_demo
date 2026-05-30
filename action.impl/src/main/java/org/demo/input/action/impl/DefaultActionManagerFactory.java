package org.demo.input.action.impl;

import org.demo.input.action.ActionManager;
import org.demo.input.action.spi.ActionManagerFactory;

public final class DefaultActionManagerFactory implements ActionManagerFactory {

    @Override
    public <ActionType extends Enum<ActionType>> ActionManager<ActionType> create() {
        return new DefaultActionManager<>(null);
    }
}