package org.demo.input.application;

import org.demo.input.application.source.KeyInputSourceImpl;
import org.demo.input.application.source.KeyType;
import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.BindingService;
import org.demo.input.binding.BindingServiceLocator;
import org.demo.input.binding.Bindings;
import org.demo.input.binding.spi.BindingImplProvider;
import org.demo.input.source.KeyInputEventType;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

public class Application {
    private static final Duration HOLD_DURATION = Duration.ofMillis(500);
    private static final Duration TAP_DURATION = Duration.ofMillis(300);
    private static final Duration DOUBLE_TAP_DURATION = Duration.ofMillis(300);
    private static final Duration DOUBLE_TAP_INTERVAL = Duration.ofMillis(300);


    public void start() {

        Scheduler loop = Schedulers.newSingle("input-loop");
        BindingImplProvider provider = BindingServiceLocator.getBindingImplProvider();
        KeyInputSourceImpl inputSource = new KeyInputSourceImpl();
        BindingService<ActionType> bindingService = provider.createBindingService();
        ActionPublisher<ActionType> actionPublisher = provider.createPublisher(inputSource, bindingService, loop);

        Disposable events = null;
        Disposable actions = null;

        try {
            events = Flux.from(inputSource.getEventPublisher())
                    .subscribe(event -> System.err.println("Event: " + event));
            actions = Flux.from(actionPublisher.getActionPublisher())
                    .subscribe(action -> System.err.println("Action: " + action));

            bindingService.setBindings(List.of(
                    Bindings.createChordBinding(ActionType.SAVE, EnumSet.of(KeyType.CTRL, KeyType.S)),
                    Bindings.createHoldBinding(ActionType.SAVE_AS, EnumSet.of(KeyType.CTRL, KeyType.S), HOLD_DURATION),
                    Bindings.createTapBinding(ActionType.OPEN_MAP, KeyType.M, TAP_DURATION),
                    Bindings.createTapBinding(ActionType.SELECT_HOME_POINT, KeyType.H, TAP_DURATION),
                    Bindings.createDouleTapBinding(ActionType.GO_TO_HOME_POINT, KeyType.H, DOUBLE_TAP_DURATION, DOUBLE_TAP_INTERVAL)
            ));

            System.err.println("======CHORD CTRL+S ");
            Instant t0 = Instant.now();
            inputSource.publish(KeyType.CTRL, KeyInputEventType.KEY_DOWN, t0);
            inputSource.publish(KeyType.S, KeyInputEventType.KEY_DOWN, t0.plusMillis(10));
            inputSource.publish(KeyType.S, KeyInputEventType.KEY_UP, t0.plusMillis(20));
            inputSource.publish(KeyType.CTRL, KeyInputEventType.KEY_UP, t0.plusMillis(30));

            sleep(600);

            System.err.println("======HOLD CTRL+S (500ms) ");
            Instant t1 = Instant.now();
            inputSource.publish(KeyType.CTRL, KeyInputEventType.KEY_DOWN, t1);
            inputSource.publish(KeyType.S, KeyInputEventType.KEY_DOWN, t1.plusMillis(10));
            sleep(600);
            inputSource.publish(KeyType.S, KeyInputEventType.KEY_UP, Instant.now());
            inputSource.publish(KeyType.CTRL, KeyInputEventType.KEY_UP, Instant.now());

            System.err.println("======TAP M ");
            Instant t2 = Instant.now();
            inputSource.publish(KeyType.M, KeyInputEventType.KEY_DOWN, t2);
            inputSource.publish(KeyType.M, KeyInputEventType.KEY_UP, t2.plusMillis(50));

            sleep(600);

            System.err.println("======DOUBLE TAP H ");
            Instant t3 = Instant.now();
            inputSource.publish(KeyType.H, KeyInputEventType.KEY_DOWN, t3);
            inputSource.publish(KeyType.H, KeyInputEventType.KEY_UP, t3.plusMillis(50));
            inputSource.publish(KeyType.H, KeyInputEventType.KEY_DOWN, t3.plusMillis(150));
            inputSource.publish(KeyType.H, KeyInputEventType.KEY_UP, t3.plusMillis(200));

            sleep(1000);

        } finally {
            if (events != null) events.dispose();
            if (actions != null) actions.dispose();
            loop.dispose();
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    public static void main(String[] args) {
        new Application().start();

    }
}
