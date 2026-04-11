package org.demo.input.binding;

import java.time.Duration;
import java.util.Set;

public interface Binding<ActionType extends Enum<ActionType>> {

    enum BindingType {
        TAP,
        HOLD,
        DOUBLE_TAP,
        CHORD
    }

    interface Tap<ActionType extends Enum<ActionType>> extends Binding<ActionType> {

        Enum<?> getKey();

        Duration getDuration();

        @Override
        default BindingType getBindingType() {
            return BindingType.TAP;
        }
    }

    interface DoubleTap<ActionType extends Enum<ActionType>> extends Binding<ActionType> {

        Enum<?> getKey();

        Duration getDuration();

        @Override
        default BindingType getBindingType() {
            return BindingType.DOUBLE_TAP;
        }
    }

    interface Hold<ActionType extends Enum<ActionType>> extends Binding<ActionType> {

        Set<Enum<?>> getKeys();

        Duration getDuration();

        @Override
        default BindingType getBindingType() {
            return BindingType.HOLD;
        }
    }

    interface Chord<ActionType extends Enum<ActionType>> extends Binding<ActionType> {

        Set<Enum<?>> getKeys();

        @Override
        default BindingType getBindingType() {
            return BindingType.CHORD;
        }
    }

    ActionType getActionType();

    BindingType getBindingType();
}
