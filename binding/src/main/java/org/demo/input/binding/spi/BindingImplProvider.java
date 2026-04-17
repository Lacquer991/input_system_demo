package org.demo.input.binding.spi;

import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.BindingService;
import org.demo.input.source.KeyInputSource;
import reactor.core.scheduler.Scheduler;

public interface BindingImplProvider {

    <ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>>
    ActionPublisher<ActionType> createPublisher(KeyInputSource<KeyType> source, BindingService<ActionType> bindingService, Scheduler scheduler);

    <ActionType extends Enum<ActionType>> BindingService<ActionType> createBindingService();
}
