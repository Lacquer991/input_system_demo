package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.binding.BindingService;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;

class BindingServiceImpl<ActionType extends Enum<ActionType>> implements BindingService<ActionType> {

    private final Sinks.Many<List<Binding<ActionType>>> sink;
    private volatile List<Binding<ActionType>> currentBindings;

    public BindingServiceImpl() {
        sink = Sinks.many().replay().latest();
        currentBindings = new ArrayList<>();
    }

    @Override
    public void setBindings(Iterable<? extends Binding<ActionType>> bindings) {
        List<Binding<ActionType>> newBindings = new ArrayList<>();
        bindings.forEach(newBindings::add);

        currentBindings = List.copyOf(newBindings);
        sink.tryEmitNext(currentBindings);
    }

    @Override
    public Publisher<List<Binding<ActionType>>> getBindingsPublisher() {
        return sink.asFlux();
    }
}
