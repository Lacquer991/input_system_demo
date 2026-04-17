package org.demo.input.application.source;

import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.demo.input.source.KeyInputSource;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public final class JavaFxKeyInputSource implements KeyInputSource<KeyType> {

    private final Sinks.Many<KeyInputEvent<KeyType>> sink =
            Sinks.many().multicast().onBackpressureBuffer();

    private final Publisher<KeyInputEvent<KeyType>> publisher = sink.asFlux();

    private final Scene scene;

    private final Set<KeyCode> pressed = new HashSet<>();

    private final EventHandler<KeyEvent> pressedHandler = this::onPressed;
    private final EventHandler<KeyEvent> releasedHandler = this::onReleased;

    public JavaFxKeyInputSource(Scene scene) {
        this.scene = scene;
        scene.addEventFilter(KeyEvent.KEY_PRESSED, pressedHandler);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, releasedHandler);
    }

    @Override
    public Publisher<KeyInputEvent<KeyType>> getEventPublisher() {
        return publisher;
    }

    private void onPressed(KeyEvent e) {
        if (!pressed.add(e.getCode())) {
            return;
        }

        KeyType key = map(e.getCode());
        if (key == null) return;

        sink.tryEmitNext(new KeyInputEventImpl(key, KeyInputEventType.KEY_DOWN, Instant.now()));
    }

    private void onReleased(KeyEvent e) {
        pressed.remove(e.getCode());

        KeyType key = map(e.getCode());
        if (key == null) return;

        sink.tryEmitNext(new KeyInputEventImpl(key, KeyInputEventType.KEY_UP, Instant.now()));
    }

    private KeyType map(KeyCode code) {
        return switch (code) {
            case W -> KeyType.W;
            case A -> KeyType.A;
            case S -> KeyType.S;
            case D -> KeyType.D;
            case Q -> KeyType.Q;
            case E -> KeyType.E;
            case SPACE -> KeyType.SPACE;
            case CONTROL -> KeyType.CTRL;
            case F -> KeyType.F;
            case Z -> KeyType.Z;
            case X -> KeyType.X;
            case C -> KeyType.C;
            case M -> KeyType.M;
            case H -> KeyType.H;
            default -> null;
        };
    }

    public void stop() {
        scene.removeEventFilter(KeyEvent.KEY_PRESSED, pressedHandler);
        scene.removeEventFilter(KeyEvent.KEY_RELEASED, releasedHandler);
        sink.tryEmitComplete();
    }
}