package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.util.HashSet;
import java.util.Set;

class ChordProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {
    private final Binding.Chord<ActionType> binding;

    ChordProcessor(Binding.Chord<ActionType> binding) {
        this.binding = binding;
    }


    @Override
    public Publisher<ActionCandidate<ActionType>> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Set<Enum<?>> requiredKeys = binding.getKeys();

        Flux<KeyInputEvent<KeyType>> relevant = Flux.from(events)
                .filter(e -> requiredKeys.contains(e.getKeyType()));

        record State(Set<Enum<?>> pressed, boolean active, KeyInputEvent<?> lastKey) {
        }

        Flux<State> states = relevant.scan(
                new State(Set.of(), false, null),
                (st, e) -> {
                    Set<Enum<?>> next = new HashSet<>(st.pressed());

                    if (e.getEventType() == KeyInputEventType.KEY_DOWN) next.add(e.getKeyType());
                    if (e.getEventType() == KeyInputEventType.KEY_UP) next.remove(e.getKeyType());

                    boolean active = next.containsAll(requiredKeys);

                    return new State(Set.copyOf(next), active, e);
                }
        ).skip(1);

        return states
                .filter(s -> s.lastKey() != null && s.lastKey().getEventType() == KeyInputEventType.KEY_DOWN)
                .filter(State::active)
                .map(s -> ActionCandidate.chord(binding.getActionType(), Set.copyOf(requiredKeys)));
    }
}
