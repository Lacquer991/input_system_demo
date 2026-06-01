package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.scheduler.Scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

class InputEngine<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> {

    private final Consumer<ActionType> emit;

    private final Set<Enum<?>> pressed = new HashSet<>();

    private final TapProcessor<ActionType> tapProcessor;
    private final DoubleTapProcessor<ActionType> doubleTapProcessor;
    private final ComboProcessor<ActionType> comboProcessor;

    InputEngine(Consumer<ActionType> emit, Scheduler scheduler) {
        this.emit = emit;
        this.tapProcessor = new TapProcessor<>();
        this.doubleTapProcessor = new DoubleTapProcessor<>(scheduler, emit);
        this.comboProcessor = new ComboProcessor<>(scheduler, emit);
    }

    void setBindings(List<Binding<ActionType>> bindings) {
        pressed.clear();
        tapProcessor.setBindings(bindings);
        doubleTapProcessor.setBindings(bindings);
        comboProcessor.setBindings(bindings);
    }

    void onEvent(KeyInputEvent<KeyType> event) {
        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            pressed.add(event.getKeyType());
        } else {
            pressed.remove(event.getKeyType());
        }

        Optional<ActionType> fromDoubleTap = doubleTapProcessor.onEvent(event, pressed);
        Optional<ActionType> fromCombo = comboProcessor.onEvent(event, pressed);
        Optional<ActionType> fromTap = tapProcessor.onEvent(event, pressed);


        Optional<ActionType> result = fromDoubleTap.or(() -> fromCombo)
                .or(() -> doubleTapProcessor.isPending(event.getKeyType()) ? Optional.empty() : fromTap);

        result.ifPresent(emit);
    }

    void dispose() {
        tapProcessor.dispose();
        doubleTapProcessor.dispose();
        comboProcessor.dispose();
    }
}