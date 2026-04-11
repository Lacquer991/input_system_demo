package org.demo.input.source;

public interface KeyInputEvent<KeyType extends Enum<KeyType>> {

    KeyType getKeyType();

    KeyInputEventType getEventType();
}
