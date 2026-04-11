package org.demo.input.application.source;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.demo.input.source.KeyInputSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Sinks;

public class TestKeyInputSource implements KeyInputSource<KeyType> {
    private final Sinks.Many<KeyInputEvent<KeyType>> sink = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public Publisher<KeyInputEvent<KeyType>> getEventPublisher() {
        return sink.asFlux();
    }

    public void publish(KeyType keyType, KeyInputEventType eventType) {

    }
}
