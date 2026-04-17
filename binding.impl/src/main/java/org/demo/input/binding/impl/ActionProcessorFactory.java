package org.demo.input.binding.impl;


import org.demo.input.binding.Binding;

final class ActionProcessorFactory {

    private ActionProcessorFactory() {
    }

    public static <ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>>
    ActionProcessor<ActionType, KeyType> create(Binding<ActionType> binding) {
        return switch (binding.getBindingType()) {
            case TAP -> new TapProcessor<>((Binding.Tap<ActionType>) binding);

            case HOLD -> new HoldProcessor<>((Binding.Hold<ActionType>) binding);

            case DOUBLE_TAP -> new DoubleTapProcessor<>((Binding.DoubleTap<ActionType>) binding);

            case CHORD -> new ChordProcessor<>((Binding.Chord<ActionType>) binding);
        };
    }
}
