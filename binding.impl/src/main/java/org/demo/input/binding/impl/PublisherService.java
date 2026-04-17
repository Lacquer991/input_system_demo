package org.demo.input.binding.impl;

import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.Binding;
import org.demo.input.binding.BindingService;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputSource;

import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

class PublisherService<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionPublisher<ActionType> {

    private final Scheduler scheduler;

    private final Publisher<ActionType> actions;

    public PublisherService(KeyInputSource<KeyType> inputSource, BindingService<ActionType> bindingService, Scheduler scheduler) {
        this.scheduler = scheduler;

        Flux<KeyInputEvent<KeyType>> inputEvents = Flux.from(inputSource.getEventPublisher())
                .publishOn(scheduler).publish().autoConnect(1);

        Flux<List<Binding<ActionType>>> bindings = Flux.from(bindingService.getBindingsPublisher());

        this.actions = bindings.switchMap(currentBindings -> process(inputEvents, currentBindings))
                .publishOn(scheduler)
                .share();
    }
// удаленные процессоры перестают обрабатывать ввод
    private Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> inputEvents, List<Binding<ActionType>> bindings) {

        List<ActionProcessor<ActionType, KeyType>> processors = new java.util.ArrayList<>();

        var tapsByKey = bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.TAP)
                .map(b -> (Binding.Tap<ActionType>) b)
                .collect(Collectors.toMap(Binding.Tap::getKey, b -> b, (a, b) -> a));

        var doubleTapsByKey = bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.DOUBLE_TAP)
                .map(b -> (Binding.DoubleTap<ActionType>) b)
                .collect(Collectors.toMap(Binding.DoubleTap::getKey, b -> b, (a, b) -> a));


        bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.CHORD || b.getBindingType() == Binding.BindingType.HOLD)
                .map(ActionProcessorFactory::<ActionType, KeyType>create)
                .forEach(processors::add);

        Set<Enum<?>> keys = new HashSet<>();
        keys.addAll(tapsByKey.keySet());
        keys.addAll(doubleTapsByKey.keySet());

        for (Enum<?> key : keys) {
            Binding.Tap<ActionType> tap = tapsByKey.get(key);
            Binding.DoubleTap<ActionType> dTap = doubleTapsByKey.get(key);

            if (tap != null && dTap != null) {
                processors.add(new TapOrDoubleTapProcessor<>(tap, dTap));
            } else if (tap != null) {
                processors.add(new TapProcessor<>(tap));
            } else {
                processors.add(new DoubleTapProcessor<>(dTap));
            }
        }

        Flux<ActionCandidate<ActionType>> candidates = Flux.merge(
                processors.stream()
                        .map(p -> Flux.from(p.process(inputEvents, scheduler)))
                        .toList()
        );

        return candidates.map(ActionCandidate::actionType);
    }

    @Override
    public Publisher<ActionType> getActionPublisher() {
        return actions;
    }
}
