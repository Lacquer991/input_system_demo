package org.demo.input.binding.impl.processor;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

class HoldProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {

    private final Binding.Hold<ActionType> binding;

    HoldProcessor(Binding.Hold<ActionType> binding) {
        this.binding = binding;
    }


    @Override
    public Publisher<ActionCandidate<ActionType>> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Set<Enum<?>> requiredKeys = binding.getKeys();
        Duration holdDuration = binding.getDuration();

        Flux<KeyInputEvent<KeyType>> relevant = Flux.from(events)
                .filter(e -> requiredKeys.contains(e.getKeyType()));

        record State(Set<Enum<?>> pressed, boolean active) {
        }

        Flux<State> states = relevant.scan(new State(Set.of(), false),
                (s, e) -> {
                    Set<Enum<?>> next = new HashSet<>(s.pressed());

                    if (e.getEventType() == KeyInputEventType.KEY_DOWN) next.add(e.getKeyType());
                    if (e.getEventType() == KeyInputEventType.KEY_UP) next.remove(e.getKeyType());

                    boolean active = next.containsAll(requiredKeys);

                    return new State(Set.copyOf(next), active);
                }).skip(1).share();

        Flux<State> activated = states.buffer(2, 1)
                .filter(buffer -> buffer.size() == 2)
                .filter(buffer -> !buffer.get(0).active() && buffer.get(1).active())
                .map(buffer -> buffer.get(1));

        Flux<State> deactivated = states.filter(s -> !s.active());

        return activated.switchMap(act -> Mono.delay(holdDuration, scheduler)
                .takeUntilOther(deactivated.next())
                .thenReturn(ActionCandidate.hold(binding.getActionType(), Set.copyOf(requiredKeys)))
                .flux()
        );
    }

    @Override
    public ActionType getActionType() {
        return binding.getActionType();
    }
}
