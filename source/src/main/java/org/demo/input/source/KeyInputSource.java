package org.demo.input.source;

import org.reactivestreams.Publisher;

public interface KeyInputSource<KeyType extends Enum<KeyType>> {

    Publisher<KeyInputEvent<KeyType>> getEventPublisher();
}
