package org.demo.input.application.source;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.demo.input.source.KeyInputSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Sinks;

import java.time.Instant;

public class KeyInputSourceImpl implements KeyInputSource<KeyType> {

    private final Sinks.Many<KeyInputEvent<KeyType>> sink = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public Publisher<KeyInputEvent<KeyType>> getEventPublisher() {
        return sink.asFlux();
    }

    public void publish(KeyType keyType, KeyInputEventType eventType, Instant timestamp) {
        sink.tryEmitNext(new KeyInputEventImpl(keyType, eventType, timestamp));
    }
}
