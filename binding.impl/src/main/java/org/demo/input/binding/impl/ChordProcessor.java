package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;

import java.util.Set;

class ChordProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {

    private final Binding.Chord<ActionType> binding;

    private final Set<Enum<?>> observedKeys;

    private final boolean exactMatch;

    private final Duration confirmDelay;


    ChordProcessor(Binding.Chord<ActionType> binding, Set<Enum<?>> observedKeys, boolean exactMatch, Duration confirmDelay) {
        this.binding = binding;
        this.observedKeys = Set.copyOf(observedKeys);
        this.exactMatch = exactMatch;
        this.confirmDelay = confirmDelay;
    }


    @Override
    public Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Set<Enum<?>> requiredKeys = Set.copyOf(binding.getKeys());

        var signals = ComboDetector.detect(events, requiredKeys, observedKeys, exactMatch);

        Flux<KeyInputEvent<KeyType>> activatedOnDown = signals.activated()
                .filter(e -> e.getEventType() == KeyInputEventType.KEY_DOWN);

        if (confirmDelay == null || confirmDelay.isZero() || confirmDelay.isNegative()) {
            return activatedOnDown.map(e -> binding.getActionType());
        }

        return signals.activated()
                .filter(e -> e.getEventType() == KeyInputEventType.KEY_DOWN)
                .switchMap(__ ->
                        Mono.delay(confirmDelay, scheduler)
                                .takeUntilOther(signals.deactivated().next())
                                .map(t -> binding.getActionType())
                                .flux()
                );
    }
}
