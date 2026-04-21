package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.time.Instant;

final class TapDetector {

    private TapDetector() {
    }

    record TapGesture(Enum<?> key, Instant downAt, Instant upAt, boolean interrupted) {
    }

    private record State(Instant downAt, boolean interrupted, TapGesture produced) {
    }

    static <KeyType extends Enum<KeyType>> Flux<TapGesture> detect(Publisher<KeyInputEvent<KeyType>> events, Enum<?> key) {

        Flux<KeyInputEvent<KeyType>> shared = Flux.from(events);


        return shared
                .scan(new State(null, false, null), (State st, KeyInputEvent<KeyType> e) -> {
                    boolean isOurKey = e.getKeyType() == key;

                    if (!isOurKey && st.downAt() != null) {
                        return new State(st.downAt(), true, null);
                    }

                    if (isOurKey && e.getEventType() == KeyInputEventType.KEY_DOWN) {
                        if (st.downAt() != null) return new State(st.downAt(), st.interrupted(), null);
                        return new State(e.getTimestamp(), false, null);
                    }

                    if (isOurKey && e.getEventType() == KeyInputEventType.KEY_UP) {
                        if (st.downAt() == null) return new State(null, false, null);
                        TapGesture g = new TapGesture(key, st.downAt(), e.getTimestamp(), st.interrupted());
                        return new State(null, false, g);
                    }

                    return new State(st.downAt(), st.interrupted(), null);
                })
                .skip(1)
                .handle((st, sink) -> {
                    if (st.produced() != null) sink.next(st.produced());

                });
    }
}
