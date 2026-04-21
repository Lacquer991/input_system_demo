package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.util.NoSuchElementException;
import java.util.Set;

class ChordOrHoldProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>>
        implements ActionProcessor<ActionType, KeyType> {

    private final Binding.Chord<ActionType> chordBinding;

    private final Binding.Hold<ActionType> holdBinding;

    private final Set<Enum<?>> observedKeys;

    private final boolean exactMatch;

    ChordOrHoldProcessor(Binding.Chord<ActionType> chordBinding, Binding.Hold<ActionType> holdBinding, Set<Enum<?>> observedKeys, boolean exactMatch) {
        this.chordBinding = chordBinding;
        this.holdBinding = holdBinding;
        this.observedKeys = Set.copyOf(observedKeys);
        this.exactMatch = exactMatch;
    }


    @Override
    public Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Set<Enum<?>> requiredKeys = Set.copyOf(chordBinding.getKeys());

        var signals = ComboDetector.detect(events, requiredKeys, observedKeys, exactMatch);

        return signals.activated()
                .filter(e -> e.getEventType() == KeyInputEventType.KEY_DOWN)
                .switchMap(__ -> {
                    Mono<ActionType> chordMono =
                            signals.deactivated().next()
                                    .filter(e -> e.getEventType() == KeyInputEventType.KEY_UP
                                                 && requiredKeys.contains(e.getKeyType()))
                                    .map(e -> chordBinding.getActionType());

                    Mono<ActionType> holdMono =
                            Mono.delay(holdBinding.getDuration(), scheduler)
                                    .takeUntilOther(signals.deactivated().next())
                                    .map(t -> holdBinding.getActionType());

                    return Mono.firstWithValue(chordMono, holdMono)
                            .onErrorResume(NoSuchElementException.class, e -> Mono.empty())
                            .flux();

                });
    }
}
