package org.demo.input.binding.impl.processor;

import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.Binding;
import org.demo.input.binding.BindingService;
import org.demo.input.binding.impl.ActionCandidate;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class PublisherService<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> implements ActionPublisher<ActionType> {

    private final KeyInputSource<KeyType> inputSource;

    private final BindingService<ActionType> bindingService;

    private final Scheduler scheduler;

    private final Publisher<ActionType> actions;

    public PublisherService(KeyInputSource<KeyType> inputSource, BindingService<ActionType> bindingService, Scheduler scheduler) {
        this.inputSource = inputSource;
        this.bindingService = bindingService;
        this.scheduler = scheduler;

        Flux<KeyInputEvent<KeyType>> inputEvents = Flux.from(inputSource.getEventPublisher())
                .publishOn(scheduler)
                .share();

        Flux<List<Binding<ActionType>>> bindings = Flux.from(bindingService.getBindingsPublisher())
                .publishOn(scheduler);

        this.actions = bindings.switchMap(currentBindings -> process(inputEvents, currentBindings))
                .share();
    }

    private Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> inputEvents, List<Binding<ActionType>> bindings) {
        List<ActionProcessor<ActionType, KeyType>> processors = bindings.stream()
                .map(ActionProcessorFactory::<ActionType, KeyType>create)
                .toList();

        Flux<ActionCandidate<ActionType>> candidates = Flux.merge(
                processors.stream()
                        .map(p -> Flux.from(p.process(inputEvents, scheduler)))
                        .toList()
        ).share();

        Map<Enum<?>, Duration> doubleTapIntervals = bindings.stream()
                .filter(b -> b.getBindingType() == Binding.BindingType.DOUBLE_TAP)
                .map(b -> (Binding.DoubleTap<ActionType>) b)
                .collect(Collectors.toMap(
                        Binding.DoubleTap::getKey,
                        Binding.DoubleTap::getInterval,
                        (a, b) -> a
                ));

        Flux<ActionCandidate<ActionType>> taps = candidates.filter(c -> c.bindingType() == Binding.BindingType.TAP);

        Flux<ActionCandidate<ActionType>> notTaps = candidates.filter(c -> c.bindingType() != Binding.BindingType.TAP);

        Flux<ActionCandidate<ActionType>> resolvedTaps = taps.groupBy(ActionCandidate::key)
                .flatMap(group -> {
                    Enum<?> key = group.key();
                    Duration interval = doubleTapIntervals.get(key);

                    if (interval == null) {
                        return group;
                    }

                    Flux<ActionCandidate<ActionType>> doubleTapSignal = candidates
                            .filter(c -> c.bindingType() == Binding.BindingType.DOUBLE_TAP && key.equals(c.key()));

                    Flux<ActionCandidate<ActionType>> higherPrioritySignal = candidates
                            .filter(c -> c.bindingType() != Binding.BindingType.TAP && isInKeys(c, key));

                    return group.switchMap(tap ->
                            Mono.delay(interval, scheduler)
                                    .takeUntilOther(doubleTapSignal.next())
                                    .takeUntilOther(higherPrioritySignal.next())
                                    .thenReturn(tap)
                                    .flux()
                    );
                });

        return Flux.merge(notTaps, resolvedTaps)
                .map(ActionCandidate::actionType);
    }


    private boolean isInKeys(ActionCandidate<ActionType> c, Enum<?> key) {
        if (c.key() != null) {
            return key.equals(c.key());
        }
        if (c.keys() != null) {
            return c.keys().contains(key);
        }
        return false;
    }

    @Override
    public Publisher<ActionType> getActionPublisher() {
        return actions;
    }
}
