package org.demo.input.action.impl;

import javafx.beans.property.SimpleBooleanProperty;
import org.demo.input.action.Action;
import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionLayer;
import org.demo.input.action.ActionManager;
import org.demo.input.action.exceptions.ActionNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ActionDispatcherTest {

    enum ActionEn {SAVE, SAVE_AS}

    @BeforeAll
    static void startFx() {
        try {
            javafx.application.Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
    }

    private ActionManager<ActionEn> manager() {
        return new DefaultActionManagerFactory().create();
    }

    private Sinks.Many<ActionEn> sink() {
        return Sinks.many().unicast().onBackpressureBuffer();
    }

    @Test
    void executes_onEmit() {
        List<ActionEn> executed = new ArrayList<>();
        ActionManager<ActionEn> manager = manager();
        manager.pushLayer(ActionLayer.of(Map.of(
                ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> executed.add(ActionEn.SAVE)),
                ActionEn.SAVE_AS, Action.of(ActionEn.SAVE_AS, () -> executed.add(ActionEn.SAVE_AS))
        )));

        var sink = sink();
        ActionDispatcher dispatcher = ActionDispatcher.bind(sink.asFlux(), manager, false, e -> {
        });

        sink.tryEmitNext(ActionEn.SAVE);
        sink.tryEmitNext(ActionEn.SAVE_AS);
        sink.tryEmitNext(ActionEn.SAVE);

        assertEquals(List.of(ActionEn.SAVE, ActionEn.SAVE_AS, ActionEn.SAVE), executed);
        dispatcher.close();
    }

    @Test
    void close_stopsExecution() {
        List<ActionEn> executed = new ArrayList<ActionEn>();
        ActionManager<ActionEn> manager = manager();
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> executed.add(ActionEn.SAVE)))));

        var sink = sink();
        ActionDispatcher dispatcher = ActionDispatcher.bind(sink.asFlux(), manager, false, e -> {
        });

        sink.tryEmitNext(ActionEn.SAVE);
        dispatcher.close();
        sink.tryEmitNext(ActionEn.SAVE);

        assertEquals(1, executed.size());
    }

    @Test
    void notFound_toErrorHandler() {
        var error = new AtomicReference<Throwable>();
        var sink = sink();
        ActionDispatcher dispatcher = ActionDispatcher.bind(sink.asFlux(), manager(), false, error::set);

        sink.tryEmitNext(ActionEn.SAVE);

        assertInstanceOf(ActionNotFoundException.class, error.get());
        dispatcher.close();
    }

    @Test
    void disabled_toErrorHandler() {
        var error = new AtomicReference<Throwable>();
        ActionManager<ActionEn> manager = manager();
        var enabled = new SimpleBooleanProperty(false);
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> {
        }, enabled))));

        var sink = sink();
        ActionDispatcher dispatcher = ActionDispatcher.bind(sink.asFlux(), manager, false, error::set);

        sink.tryEmitNext(ActionEn.SAVE);

        assertNotNull(error.get());
        dispatcher.close();
    }

    @Test
    void publisherError_toErrorHandler() {
        var error = new AtomicReference<Throwable>();
        var cause = new RuntimeException("upstream");
        ActionDispatcher dispatcher = ActionDispatcher.bind(Flux.error(cause), manager(), false, error::set);

        assertSame(cause, error.get());
        dispatcher.close();
    }
}