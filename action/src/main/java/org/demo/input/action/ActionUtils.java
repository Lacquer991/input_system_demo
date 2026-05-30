package org.demo.input.action;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.CheckBox;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;


public final class ActionUtils {

    private static final String ACTION_KEY = "action_is_bind";


    private ActionUtils() {
    }

    public static <ActionType extends Enum<ActionType>> Button createButton(Action<ActionType> action, String text) {
        Button button = new Button(text);
        configure(action, button);
        return button;
    }

    public static <ActionType extends Enum<ActionType>> ToggleButton createToggleButton(Action<ActionType> action, String text) {
        ToggleButton button = new ToggleButton(text);
        configure(action, button);
        return button;
    }

    public static <ActionType extends Enum<ActionType>> CheckBox createCheckBox(Action<ActionType> action, String text) {
        CheckBox checkBox = new CheckBox(text);
        configure(action, checkBox);
        return checkBox;
    }

    public static <ActionType extends Enum<ActionType>> MenuItem createMenuItem(Action<ActionType> action, String text) {
        MenuItem item = new MenuItem(text);
        item.disableProperty().bind(action.enabledProperty().not());
        item.setOnAction(e -> action.execute());
        item.getProperties().put(ACTION_KEY, action);
        return item;
    }

    public static <ActionType extends Enum<ActionType>> ButtonBase configure(Action<ActionType> action, ButtonBase button) {
        unconfigure(button);
        button.disableProperty().bind(action.enabledProperty().not());
        button.setOnAction(e -> action.execute());
        button.getProperties().put(ACTION_KEY, action);
        return button;
    }

    public static void unconfigure(ButtonBase button) {
        if (button.getProperties().containsKey(ACTION_KEY)) {
            button.disableProperty().unbind();
            button.setOnAction(null);
            button.getProperties().remove(ACTION_KEY);
        }
    }

    public static void unconfigure(MenuItem item) {
        if (item.getProperties().containsKey(ACTION_KEY)) {
            item.disableProperty().unbind();
            item.setOnAction(null);
            item.getProperties().remove(ACTION_KEY);
        }
    }

    public static <ActionType extends Enum<ActionType>> Action<ActionType> getAction(ButtonBase button) {
        return (Action<ActionType>) button.getProperties().get(ACTION_KEY);
    }

    public static boolean isBind(ButtonBase button) {
        return button.getProperties().containsKey(ACTION_KEY);
    }
}