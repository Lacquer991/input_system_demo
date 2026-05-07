package org.demo.input.binding.impl;

import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.BindingService;
import org.demo.input.binding.spi.BindingImplProvider;
import org.demo.input.source.KeyInputSource;
import reactor.core.scheduler.Scheduler;

public final class DefaultBindingImplProvider implements BindingImplProvider {

    public DefaultBindingImplProvider() {}

    @Override
    public <ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>>
    ActionPublisher<ActionType> createPublisher(KeyInputSource<KeyType> source, BindingService<ActionType> bindingService, Scheduler scheduler) {
        return new PublisherService<>(source, bindingService, scheduler);
    }

    @Override
    public <ActionType extends Enum<ActionType>> BindingService<ActionType> createBindingService() {
        return new DefaultBindingService<>();
    }
}
