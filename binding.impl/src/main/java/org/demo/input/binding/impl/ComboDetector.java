package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;

import java.util.HashSet;
import java.util.Set;

final class ComboDetector {

    private ComboDetector() {
    }

    record Signals<KeyType extends Enum<KeyType>>(

            Flux<KeyInputEvent<KeyType>> activated,

            Flux<KeyInputEvent<KeyType>> deactivated
    ) {
    }

    record State<KeyType extends Enum<KeyType>>(Set<Enum<?>> pressed, boolean active, KeyInputEvent<KeyType> last) {
    }


    static <KeyType extends Enum<KeyType>> Signals<KeyType> detect(
            Publisher<KeyInputEvent<KeyType>> events, Set<Enum<?>> requiredKeys,
            Set<Enum<?>> observedKeys, boolean exactMatch) {

        Set<Enum<?>> observed = Set.copyOf(observedKeys);

        Flux<KeyInputEvent<KeyType>> keyEvents = Flux.from(events)
                .filter(e -> observed.contains(e.getKeyType()))
                .share();

        Flux<State<KeyType>> states = keyEvents
                .scan(new State<KeyType>(Set.of(), false, null), (st, e) -> {
                    Set<Enum<?>> next = new HashSet<>(st.pressed());

                    if (e.getEventType() == KeyInputEventType.KEY_DOWN) {
                        next.add(e.getKeyType());
                    }
                    if (e.getEventType() == KeyInputEventType.KEY_UP) {
                        next.remove(e.getKeyType());
                    }

                    boolean active = exactMatch
                            ? (next.containsAll(requiredKeys) && next.size() == requiredKeys.size())
                            : next.containsAll(requiredKeys);

                    return new State<>(Set.copyOf(next), active, e);
                })
                .skip(1)
                .share();

        Flux<KeyInputEvent<KeyType>> activated = states.buffer(2, 1)
                .filter(b -> b.size() == 2 && !b.get(0).active() && b.get(1).active())
                .map(b -> b.get(1).last());

        Flux<KeyInputEvent<KeyType>> deactivated = states
                .filter(s -> !s.active())
                .map(State::last);

        return new Signals<>(activated, deactivated);
    }
}
