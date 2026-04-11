package org.demo.input.binding;

import org.reactivestreams.Publisher;

public interface ActionPublisher<ActionType extends Enum<ActionType>> {

    Publisher<ActionType> getActionPublisher();
}
