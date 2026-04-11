package org.demo.input.binding.impl.processor;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;
import reactor.core.scheduler.Scheduler;


import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

class DoubleTapProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {

    private final Binding.DoubleTap<ActionType> binding;

    DoubleTapProcessor(Binding.DoubleTap<ActionType> binding) {
        this.binding = binding;
    }

    private record TapAction(Instant down, Instant up) {
    }

    @Override
    public Publisher<ActionCandidate<ActionType>> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Enum<?> key = binding.getKey();
        Duration tapDuration = binding.getDuration();
        Duration interval = binding.getInterval();

        Flux<KeyInputEvent<KeyType>> keyEvents = Flux.from(events)
                .filter(e -> e.getKeyType().equals(key))
                .share();

        record TapState(Instant lastDown, TapAction produced) {
        }

        Flux<TapAction> taps = keyEvents
                .scan(new TapState(null, null), (TapState st, KeyInputEvent<KeyType> e) -> {

                    if (e.getEventType() == KeyInputEventType.KEY_DOWN) {
                        return new TapState(e.getTimestamp(), null);
                    }

                    if (e.getEventType() == KeyInputEventType.KEY_UP && st.lastDown() != null) {
                        Instant up = e.getTimestamp();
                        Duration pressed = Duration.between(st.lastDown(), up);

                        if (!pressed.isNegative() && pressed.compareTo(tapDuration) <= 0) {
                            return new TapState(null, new TapAction(st.lastDown(), up));
                        }

                        return new TapState(null, null);
                    }

                    return new TapState(st.lastDown(), null);
                })
                .map(TapState::produced)
                .filter(Objects::nonNull);

        return Flux.defer(() -> {
            final class DoubleTapState {
                TapAction first;
            }
            DoubleTapState st = new DoubleTapState();

            return taps.handle((TapAction tap, SynchronousSink<ActionCandidate<ActionType>> sink) -> {
                if (st.first == null) {
                    st.first = tap;
                    return;
                }

                Duration currentInterval = Duration.between(st.first.up(), tap.down());

                if (!currentInterval.isNegative() && currentInterval.compareTo(interval) <= 0) {
                    sink.next(ActionCandidate.doubleTap(binding.getActionType(), key));
                    st.first = null;
                } else {
                    st.first = tap;
                }
            });
        });
    }

    @Override
    public ActionType getActionType() {
        return binding.getActionType();
    }
}
