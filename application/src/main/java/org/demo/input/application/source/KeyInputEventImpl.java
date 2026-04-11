package org.demo.input.application.source;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;

public class KeyInputEventImpl implements KeyInputEvent<KeyType> {
    private final KeyType keyType;
    private final KeyInputEventType eventType;

    public KeyInputEventImpl(KeyType keyType, KeyInputEventType eventType) {
        this.keyType = keyType;
        this.eventType = eventType;
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
    public String toString() {
        return "KeyInputEventImpl{" +
                "keyType=" + keyType +
                ", eventType=" + eventType +
                '}';
    }
}
