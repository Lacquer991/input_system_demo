package org.demo.input.binding.impl.processor;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.time.Instant;

class TapProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {

    private final Binding.Tap<ActionType> binding;

    TapProcessor(Binding.Tap<ActionType> binding) {
        this.binding = binding;
    }


    @Override
    public Publisher<ActionCandidate<ActionType>> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Enum<?> key = binding.getKey();
        Duration tapDuration = binding.getDuration();

        Flux<KeyInputEvent<KeyType>> shared = Flux.from(events).share();

        Flux<KeyInputEvent<KeyType>> downTaps = shared.filter(e ->
                e.getEventType() == KeyInputEventType.KEY_DOWN && e.getKeyType().equals(key));

        Flux<KeyInputEvent<KeyType>> upTaps = shared.filter(e ->
                e.getEventType() == KeyInputEventType.KEY_UP && e.getKeyType().equals(key));

        Flux<KeyInputEvent<KeyType>> otherTaps = shared.filter(k ->
                !k.getKeyType().equals(key));

        return downTaps.switchMap(down ->
                upTaps.next()
                        .takeUntilOther(otherTaps.next())
                        .filter(up -> isWithin(down.getTimestamp(), up.getTimestamp(), tapDuration))
                        .map(up -> ActionCandidate.tap(binding.getActionType(), key))
                        .flux()
        );
    }

    private boolean isWithin(Instant downTimestamp, Instant upTimestamp, Duration tapDuration) {
        return !upTimestamp.isBefore(downTimestamp) && Duration.between(downTimestamp, upTimestamp).compareTo(tapDuration) <= 0;
    }

    @Override
    public ActionType getActionType() {
        return binding.getActionType();
    }
}
