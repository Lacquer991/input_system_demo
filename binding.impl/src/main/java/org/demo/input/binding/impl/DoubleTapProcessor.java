package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;


import java.time.Duration;

class DoubleTapProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionProcessor<ActionType, KeyType> {

    private final Binding.DoubleTap<ActionType> binding;

    DoubleTapProcessor(Binding.DoubleTap<ActionType> binding) {
        this.binding = binding;
    }

    @Override
    public Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {
        Enum<?> key = binding.getKey();
        Duration tapDuration = binding.getDuration();
        Duration interval = binding.getInterval();

        Flux<TapDetector.TapGesture> taps = TapDetector.detect(events, key)
                .filter(g -> !g.interrupted())
                .filter(g -> Duration.between(g.downAt(), g.upAt()).compareTo(tapDuration) <= 0);

        return Flux.defer(() -> {
            final class State {
                TapDetector.TapGesture first;
            }
            State st = new State();

            return taps.handle((tap, sink) -> {
                if (st.first == null) {
                    st.first = tap;
                    return;
                }
                Duration dt = Duration.between(st.first.upAt(), tap.downAt());
                if (!dt.isNegative() && dt.compareTo(interval) <= 0) {
                    sink.next(binding.getActionType());
                    st.first = null;
                } else {
                    st.first = tap;
                }
            });
        });
    }
}
