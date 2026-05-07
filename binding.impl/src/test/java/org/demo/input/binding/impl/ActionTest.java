package org.demo.input.binding.impl;

import org.demo.input.binding.Bindings;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ActionTest {

    enum Key { CTRL, S, Q, M, H, X }
    enum Action { SAVE, SAVE_AS, DELETE, OPEN_MAP, SELECT_HOME, GO_HOME }

    private final VirtualTimeScheduler vts = VirtualTimeScheduler.create();

    @AfterEach
    void tearDown() {
        vts.dispose();
    }

    private static KeyInputEvent<Key> ev(Key key, KeyInputEventType type, long ms) {
        Instant t = Instant.EPOCH.plusMillis(ms);
        return new KeyInputEvent<>() {
            @Override public Key getKeyType() { return key; }
            @Override public KeyInputEventType getEventType() { return type; }
            @Override public Instant getTimestamp() { return t; }
            @Override public String toString() { return "Ev(" + key + "," + type + "," + ms + ")"; }
        };
    }

    @Test
    void tap_ok() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(Bindings.createTapBinding(Action.OPEN_MAP, Key.M, Duration.ofMillis(300))));

        e.onEvent(ev(Key.M, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.M, KeyInputEventType.KEY_UP, 50));

        assertEquals(List.of(Action.OPEN_MAP), out);
    }

    @Test
    void tap_interrupted() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(Bindings.createTapBinding(Action.OPEN_MAP, Key.M, Duration.ofMillis(300))));

        e.onEvent(ev(Key.M, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 10));
        e.onEvent(ev(Key.M, KeyInputEventType.KEY_UP, 50));

        assertTrue(out.isEmpty());
    }

    @Test
    void doubleTap_ok() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createTapBinding(Action.SELECT_HOME, Key.H, Duration.ofMillis(300)),
                Bindings.createDouleTapBinding(Action.GO_HOME, Key.H, Duration.ofMillis(200), Duration.ofMillis(300))
        ));

        e.onEvent(ev(Key.H, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.H, KeyInputEventType.KEY_UP, 50));
        e.onEvent(ev(Key.H, KeyInputEventType.KEY_DOWN, 150));
        e.onEvent(ev(Key.H, KeyInputEventType.KEY_UP, 180));

        vts.advanceTimeBy(Duration.ofMillis(1000));

        assertEquals(List.of(Action.GO_HOME), out);
    }

    @Test
    void doubleTap_timeout() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createTapBinding(Action.SELECT_HOME, Key.H, Duration.ofMillis(300)),
                Bindings.createDouleTapBinding(Action.GO_HOME, Key.H, Duration.ofMillis(200), Duration.ofMillis(300))
        ));

        e.onEvent(ev(Key.H, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.H, KeyInputEventType.KEY_UP, 50));

        vts.advanceTimeBy(Duration.ofMillis(300));

        assertEquals(List.of(Action.SELECT_HOME), out);
    }

    @Test
    void dt_bad_second() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createTapBinding(Action.SELECT_HOME, Key.H, Duration.ofMillis(300)),
                Bindings.createDouleTapBinding(Action.GO_HOME, Key.H, Duration.ofMillis(200), Duration.ofMillis(300))
        ));

        e.onEvent(ev(Key.H, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.H, KeyInputEventType.KEY_UP, 50));

        e.onEvent(ev(Key.H, KeyInputEventType.KEY_DOWN, 150));
        e.onEvent(ev(Key.H, KeyInputEventType.KEY_UP, 500));

        vts.advanceTimeBy(Duration.ofMillis(1000));

        assertEquals(List.of(Action.SELECT_HOME), out);
    }

    @Test
    void hold_threshold() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createHoldBinding(Action.SAVE_AS, EnumSet.of(Key.CTRL, Key.S), Duration.ofMillis(500))
        ));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 10));

        vts.advanceTimeBy(Duration.ofMillis(300));

        e.onEvent(ev(Key.S, KeyInputEventType.KEY_UP, 310));
        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_UP, 320));

        vts.advanceTimeBy(Duration.ofMillis(1000));

        assertTrue(out.isEmpty());

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 2000));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 2010));

        vts.advanceTimeBy(Duration.ofMillis(499));

        assertTrue(out.isEmpty());

        vts.advanceTimeBy(Duration.ofMillis(1));

        assertEquals(List.of(Action.SAVE_AS), out);
    }

    @Test
    void hold_once() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createHoldBinding(Action.SAVE_AS, EnumSet.of(Key.CTRL, Key.S), Duration.ofMillis(500))
        ));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 10));

        vts.advanceTimeBy(Duration.ofMillis(2000));

        assertEquals(List.of(Action.SAVE_AS), out);
    }

    @Test
    void chord_ok() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createChordBinding(Action.SAVE, EnumSet.of(Key.CTRL, Key.S))
        ));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 10));

        assertEquals(List.of(Action.SAVE), out);
    }

    @Test
    void chord_order() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createChordBinding(Action.SAVE, EnumSet.of(Key.CTRL, Key.S))
        ));

        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 10));

        assertEquals(List.of(Action.SAVE), out);
    }

    @Test
    void chord_delay() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createChordBinding(Action.SAVE, EnumSet.of(Key.CTRL, Key.S)),
                Bindings.createHoldBinding(Action.DELETE, EnumSet.of(Key.CTRL, Key.S, Key.Q), Duration.ofMillis(500))
        ));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 10));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_UP, 20));
        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_UP, 30));

        vts.advanceTimeBy(Duration.ofMillis(100));

        assertEquals(List.of(Action.SAVE), out);

        out.clear();

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 1000));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 1010));
        e.onEvent(ev(Key.Q, KeyInputEventType.KEY_DOWN, 1050));
        vts.advanceTimeBy(Duration.ofMillis(200));

        assertTrue(out.isEmpty());
    }

    @Test
    void chord_hold() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createChordBinding(Action.SAVE, EnumSet.of(Key.CTRL, Key.S)),
                Bindings.createHoldBinding(Action.SAVE_AS, EnumSet.of(Key.CTRL, Key.S), Duration.ofMillis(500))
        ));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 10));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_UP, 50));
        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_UP, 60));
        vts.advanceTimeBy(Duration.ofMillis(1000));

        assertEquals(List.of(Action.SAVE), out);

        out.clear();

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 2000));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 2010));
        vts.advanceTimeBy(Duration.ofMillis(500));

        assertEquals(List.of(Action.SAVE_AS), out);
    }

    @Test
    void subset_superset() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createHoldBinding(Action.SAVE_AS, EnumSet.of(Key.CTRL, Key.S), Duration.ofMillis(500)),
                Bindings.createHoldBinding(Action.DELETE, EnumSet.of(Key.CTRL, Key.S, Key.Q), Duration.ofMillis(500))
        ));

        e.onEvent(ev(Key.M, KeyInputEventType.KEY_DOWN, 0));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 10));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 20));
        e.onEvent(ev(Key.Q, KeyInputEventType.KEY_DOWN, 30));

        vts.advanceTimeBy(Duration.ofMillis(500));

        assertEquals(List.of(Action.DELETE), out);
    }

    @Test
    void blocker_up() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createChordBinding(Action.SAVE, EnumSet.of(Key.CTRL, Key.S)),
                Bindings.createHoldBinding(Action.SAVE_AS, EnumSet.of(Key.CTRL, Key.S), Duration.ofMillis(500)),
                Bindings.createHoldBinding(Action.DELETE, EnumSet.of(Key.CTRL, Key.S, Key.Q), Duration.ofMillis(500))
        ));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 10));
        e.onEvent(ev(Key.Q, KeyInputEventType.KEY_DOWN, 20));

        vts.advanceTimeBy(Duration.ofMillis(500));

        assertEquals(List.of(Action.DELETE), out);

        out.clear();

        e.onEvent(ev(Key.Q, KeyInputEventType.KEY_UP, 600));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_UP, 610));
        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_UP, 620));

        vts.advanceTimeBy(Duration.ofMillis(1000));

        assertTrue(out.isEmpty());
    }

    @Test
    void binding_switch() {
        List<Action> out = new ArrayList<>();
        var e = new InputEngine<Action, Key>(out::add, vts);

        e.setBindings(List.of(
                Bindings.createHoldBinding(Action.SAVE_AS, EnumSet.of(Key.CTRL, Key.S), Duration.ofMillis(500))
        ));

        e.onEvent(ev(Key.CTRL, KeyInputEventType.KEY_DOWN, 0));
        e.onEvent(ev(Key.S, KeyInputEventType.KEY_DOWN, 10));

        e.setBindings(List.of());

        vts.advanceTimeBy(Duration.ofMillis(1000));

        assertTrue(out.isEmpty());
    }
}