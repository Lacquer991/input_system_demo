package org.demo.input.binding.impl;

import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.Binding;
import org.demo.input.binding.BindingService;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputSource;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

class PublisherService<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionPublisher<ActionType> {

    private final Scheduler scheduler;

    private final Publisher<ActionType> actions;

    public PublisherService(KeyInputSource<KeyType> inputSource, BindingService<ActionType> bindingService, Scheduler scheduler) {
        this.scheduler = scheduler;

        Flux<KeyInputEvent<KeyType>> inputEvents = Flux.from(inputSource.getEventPublisher())
                .publishOn(scheduler).publish().autoConnect(1);

        Flux<List<Binding<ActionType>>> bindings = Flux.from(bindingService.getBindingsPublisher());

        this.actions = bindings.switchMap(currentBindings -> process(inputEvents, currentBindings))
                .publishOn(scheduler)
                .share();
    }

    private Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> inputEvents, List<Binding<ActionType>> bindings) {

        List<ActionProcessor<ActionType, KeyType>> processors = new java.util.ArrayList<>();

        var tapsByKey = bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.TAP)
                .map(b -> (Binding.Tap<ActionType>) b)
                .collect(Collectors.toMap(Binding.Tap::getKey, b -> b, (a, b) -> a));

        var doubleTapsByKey = bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.DOUBLE_TAP)
                .map(b -> (Binding.DoubleTap<ActionType>) b)
                .collect(Collectors.toMap(Binding.DoubleTap::getKey, b -> b, (a, b) -> a));

        Set<Enum<?>> keys = new HashSet<>();
        keys.addAll(tapsByKey.keySet());
        keys.addAll(doubleTapsByKey.keySet());

        var chordsBySet = bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.CHORD)
                .map(b -> (Binding.Chord<ActionType>) b)
                .collect(Collectors.toMap(
                        c -> Set.copyOf(c.getKeys()),
                        c -> c,
                        (a, b) -> a
                ));

        var holdsBySet = bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.HOLD)
                .map(b -> (Binding.Hold<ActionType>) b)
                .collect(Collectors.toMap(
                        h -> Set.copyOf(h.getKeys()),
                        h -> h,
                        (a, b) -> a
                ));

        Set<Set<Enum<?>>> comboKeySets = new HashSet<>();
        comboKeySets.addAll(chordsBySet.keySet());
        comboKeySets.addAll(holdsBySet.keySet());

        List<Set<Enum<?>>> comboSets = comboKeySets.stream()
                .map(Set::copyOf)
                .toList();

        Map<Set<Enum<?>>, Set<Enum<?>>> blockersBySet = new HashMap<>();

        for (Set<Enum<?>> base : comboSets) {
            Set<Enum<?>> blockers = new HashSet<>();
            for (Set<Enum<?>> other : comboSets) {
                if (other.size() > base.size() && other.containsAll(base)) {
                    for (Enum<?> k : other) if (!base.contains(k)) blockers.add(k);
                }
            }
            blockersBySet.put(base, Set.copyOf(blockers));
        }

        Duration subsetDelay = Duration.ofMillis(100);

        for (Set<Enum<?>> keySet : comboKeySets) {
            Binding.Chord<ActionType> chord = chordsBySet.get(keySet);
            Binding.Hold<ActionType> hold = holdsBySet.get(keySet);

            Set<Enum<?>> blockers = blockersBySet.getOrDefault(keySet, Set.of());
            Set<Enum<?>> observed = new HashSet<>(keySet);
            observed.addAll(blockers);

            boolean exactMatch = !blockers.isEmpty();
            Duration chordDelay = exactMatch ? subsetDelay : Duration.ZERO;

            if (chord != null && hold != null) {
                processors.add(new ChordOrHoldProcessor<>(chord, hold, observed, exactMatch));
            } else if (chord != null) {
                processors.add(new ChordProcessor<>(chord, observed, exactMatch, chordDelay));
            } else {
                processors.add(new HoldProcessor<>(hold, observed, exactMatch));
            }
        }


        for (Enum<?> key : keys) {
            Binding.Tap<ActionType> tap = tapsByKey.get(key);
            Binding.DoubleTap<ActionType> dTap = doubleTapsByKey.get(key);

            if (tap != null && dTap != null) {
                processors.add(new TapOrDoubleTapProcessor<>(tap, dTap));
            } else if (tap != null) {
                processors.add(new TapProcessor<>(tap));
            } else {
                processors.add(new DoubleTapProcessor<>(dTap));
            }
        }

        return Flux.merge(
                processors.stream()
                        .map(p -> Flux.from(p.process(inputEvents, scheduler)))
                        .toList()
        );
    }

    @Override
    public Publisher<ActionType> getActionPublisher() {
        return actions;
    }
}
