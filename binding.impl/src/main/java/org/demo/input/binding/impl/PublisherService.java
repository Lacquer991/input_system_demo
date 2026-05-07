package org.demo.input.binding.impl;

import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.BindingService;
import org.demo.input.source.KeyInputSource;

import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;

class PublisherService<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>>
        implements ActionPublisher<ActionType>, AutoCloseable {

    private final Sinks.Many<ActionType> actions = Sinks.many().multicast().onBackpressureBuffer();

    private final InputEngine<ActionType, KeyType> engine;

    private final Disposable inputSourceSub;

    private final Disposable bindingsSub;

    public PublisherService(KeyInputSource<KeyType> inputSource, BindingService<ActionType> bindingService, Scheduler scheduler) {
        this.engine = new InputEngine<>(actions::tryEmitNext, scheduler);

        this.bindingsSub = Flux.from(bindingService.getBindingsPublisher())
                .publishOn(scheduler)
                .subscribe(engine::setBindings, actions::tryEmitError);

        this.inputSourceSub = Flux.from(inputSource.getEventPublisher())
                .publishOn(scheduler)
                .subscribe(engine::onEvent, actions::tryEmitError);
    }


    @Override
    public Publisher<ActionType> getActionPublisher() {
        return actions.asFlux();
    }

    @Override
    public void close() {
        inputSourceSub.dispose();
        bindingsSub.dispose();
        engine.dispose();
    }
}


