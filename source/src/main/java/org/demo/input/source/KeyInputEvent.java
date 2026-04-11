package org.demo.input.source;

import java.time.Instant;

public interface KeyInputEvent<KeyType extends Enum<KeyType>> {

    KeyType getKeyType();

    KeyInputEventType getEventType();

    Instant getTimestamp();
}
