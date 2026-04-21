package org.demo.input.binding.impl;


import org.demo.input.source.KeyInputEvent;
import org.reactivestreams.Publisher;
import reactor.core.scheduler.Scheduler;

interface ActionProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> {

    Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler);
}
