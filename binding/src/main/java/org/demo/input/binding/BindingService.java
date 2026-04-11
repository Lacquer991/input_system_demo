package org.demo.input.binding;

import org.reactivestreams.Publisher;

import java.util.List;

public interface BindingService<ActionType extends Enum<ActionType>> {

    void setBindings(Iterable<? extends Binding<ActionType>> bindings);

    Publisher<List<Binding<ActionType>>> getBindingsPublisher();
}
