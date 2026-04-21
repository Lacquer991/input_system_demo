package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;


import java.util.Set;

class HoldProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {

    private final Binding.Hold<ActionType> binding;

    private final Set<Enum<?>> observedKeys;

    private final boolean exactMatch;

    HoldProcessor(Binding.Hold<ActionType> binding, Set<Enum<?>> observedKeys, boolean exactMatch) {
        this.binding = binding;
        this.observedKeys = Set.copyOf(observedKeys);
        this.exactMatch = exactMatch;
    }


    @Override
    public Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Set<Enum<?>> requiredKeys = Set.copyOf(binding.getKeys());

        var signals = ComboDetector.detect(events, requiredKeys, observedKeys, exactMatch);

        return signals.activated()
                .filter(e -> e.getEventType() == KeyInputEventType.KEY_DOWN)
                .switchMap(__ ->
                        Mono.delay(binding.getDuration(), scheduler)
                                .takeUntilOther(signals.deactivated().next())
                                .map(t -> binding.getActionType())
                                .flux()
                );
    }
}
