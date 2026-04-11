package org.demo.input.application.source;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;

import java.time.Instant;

public class KeyInputEventImpl implements KeyInputEvent<KeyType> {
    private final KeyType keyType;
    private final KeyInputEventType eventType;
    private final Instant timestamp;

    public KeyInputEventImpl(KeyType keyType, KeyInputEventType eventType, Instant timestamp) {
        this.keyType = keyType;
        this.eventType = eventType;
        this.timestamp = timestamp;
    }

    @Override
    public KeyType getKeyType() {
        return keyType;
    }

    @Override
    public KeyInputEventType getEventType() {
        return eventType;
    }

    @Override
    public Instant getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "KeyInputEventImpl{" +
                "keyType=" + keyType +
                ", eventType=" + eventType +
                '}';
    }
}
