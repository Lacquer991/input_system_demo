package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.reactivestreams.Publisher;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;

class TapProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {

    private final Binding.Tap<ActionType> binding;

    TapProcessor(Binding.Tap<ActionType> binding) {
        this.binding = binding;
    }


    @Override
    public Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Enum<?> key = binding.getKey();
        Duration tapDuration = binding.getDuration();

        return TapDetector.detect(events, key)
                .filter(g -> !g.interrupted())
                .filter(g -> !g.upAt().isBefore(g.downAt()) && Duration.between(g.downAt(), g.upAt()).compareTo(tapDuration) <= 0)
                .map(g -> binding.getActionType());
    }

}
