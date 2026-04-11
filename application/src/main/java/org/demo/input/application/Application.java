package org.demo.input.application;

import org.demo.input.application.source.KeyInputSourceImpl;
import org.demo.input.application.source.KeyType;
import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.BindingService;
import org.demo.input.binding.Bindings;
import org.demo.input.source.KeyInputEventType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class Application {
    private static final Duration HOLD_DURATION = Duration.ofMillis(500);
    private static final Duration TAP_DURATION = Duration.ofMillis(300);
    private static final Duration DEOUBLE_TAP_DURATION = Duration.ofMillis(300);

    private final KeyInputSourceImpl inputSource = new KeyInputSourceImpl();

    private final BindingService<ActionType> bindingService; // TODO...
    private final ActionPublisher<ActionType> actionPublisher; // TODO...

    public void start() {
        Flux.from(inputSource.getEventPublisher())
                .subscribe(event -> System.err.println("Event: " + event));
        Flux.from(actionPublisher.getActionPublisher())
                .subscribe(action -> System.err.println("Action: " + action));

        bindingService.setBindings(List.of(
                Bindings.createChordBinding(ActionType.SAVE, EnumSet.of(KeyType.CTRL, KeyType.S)),
                Bindings.createHoldBinding(ActionType.SAVE_AS, EnumSet.of(KeyType.CTRL, KeyType.S), HOLD_DURATION),
                Bindings.createTapBinding(ActionType.OPEN_MAP, KeyType.M, TAP_DURATION),
                Bindings.createTapBinding(ActionType.SELECT_HOME_POINT, KeyType.H, TAP_DURATION),
                Bindings.createDouleTapBinding(ActionType.GO_TO_HOME_POINT, KeyType.H, DEOUBLE_TAP_DURATION)
        ));

        inputSource.publish(KeyType.CTRL, KeyInputEventType.KEY_DOWN);
        inputSource.publish(KeyType.S, KeyInputEventType.KEY_DOWN);
    }

    public static void main(String[] args) {
        new Application().start();

    }
}
