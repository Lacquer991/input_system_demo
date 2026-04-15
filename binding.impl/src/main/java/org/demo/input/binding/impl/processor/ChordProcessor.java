package org.demo.input.binding.impl.processor;

import org.demo.input.binding.Binding;
import org.demo.input.binding.impl.ActionCandidate;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
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

        Duration delay = Duration.ofMillis(200);

        Flux<KeyInputEvent<KeyType>> keyEvents = Flux.from(events).share();
        record State(Set<Enum<?>> pressed, boolean active, KeyInputEvent<?> lastKey) {
        }

        Flux<State> states = keyEvents.scan(
                new State(Set.of(), false, null),
                (st, e) -> {
                    Set<Enum<?>> next = new HashSet<>(st.pressed());

                    if (e.getEventType() == KeyInputEventType.KEY_DOWN) next.add(e.getKeyType());
                    if (e.getEventType() == KeyInputEventType.KEY_UP) next.remove(e.getKeyType());

                    boolean active = next.containsAll(requiredKeys);

                    return new State(Set.copyOf(next), active, e);
                }
        ).skip(1).share();

        Flux<State> activated = states.buffer(2, 1)
                .filter(b -> b.size() == 2 && !b.get(0).active() && b.get(1).active())
                .map(b -> b.get(1));

        Flux<State> deactivated = states.filter(s -> !s.active()).share();

        return activated.switchMap(act ->
                Mono.delay(delay, scheduler)
                        .takeUntilOther(deactivated.next())
                        .takeUntilOther(keyEvents
                                .filter(e -> e.getEventType() == KeyInputEventType.KEY_DOWN)
                                .filter(e -> !requiredKeys.contains(e.getKeyType()))
                                .next()
                        )
                        .map(t -> ActionCandidate.chord(binding.getActionType(), Set.copyOf(requiredKeys)))
                        .flux()
        );
    }
}
