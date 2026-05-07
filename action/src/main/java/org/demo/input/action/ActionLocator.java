package org.demo.input.action;

import org.demo.input.action.spi.ActionProvider;

import java.util.ServiceLoader;

public final class ActionLocator {

    private static volatile ActionProvider cached;

    private ActionLocator() {
    }

    public static ActionProvider getActionProvider() {
        ActionProvider actionProvider = cached;
        if (actionProvider != null) {
            return actionProvider;
        }
        return cached = ServiceLoader.load(ActionProvider.class).findFirst()
                .orElseThrow(() -> new IllegalStateException("No ActionProvider found on classpath"));
    }
}
