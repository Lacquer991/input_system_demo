package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

class ComboProcessor<ActionType extends Enum<ActionType>>
        implements BindingProcessor<ActionType> {

    private static final Duration SUBSET_DELAY = Duration.ofMillis(100);

    private final Scheduler scheduler;
    private final Consumer<ActionType> emit;

    private List<ComboRule<ActionType>> rules = List.of();

    private final Map<Set<Enum<?>>, ComboState<ActionType>> states = new HashMap<>();

    ComboProcessor(Scheduler scheduler, Consumer<ActionType> emit) {
        this.scheduler = scheduler;
        this.emit = emit;
    }

    @Override
    public void setBindings(List<Binding<ActionType>> bindings) {
        dispose();
        states.clear();

        Map<Set<Enum<?>>, Binding.Chord<ActionType>> chords = new HashMap<>();
        Map<Set<Enum<?>>, Binding.Hold<ActionType>> holds = new HashMap<>();

        for (Binding<ActionType> b : bindings) {
            if (b instanceof Binding.Chord<ActionType> c) chords.put(Set.copyOf(c.getKeys()), c);
            if (b instanceof Binding.Hold<ActionType> h) holds.put(Set.copyOf(h.getKeys()), h);
        }

        Set<Set<Enum<?>>> allSets = new HashSet<>();
        allSets.addAll(chords.keySet());
        allSets.addAll(holds.keySet());

        List<ComboRule<ActionType>> compiled = new ArrayList<>();
        for (Set<Enum<?>> req : allSets) {
            Set<Enum<?>> blockers = computeBlockers(req, allSets);
            Set<Enum<?>> observed = new HashSet<>(req);
            observed.addAll(blockers);

            boolean exact = !blockers.isEmpty();
            var chord = chords.get(req);
            var hold = holds.get(req);
            Duration chordDelay = (chord != null && hold == null && exact) ? SUBSET_DELAY : Duration.ZERO;

            compiled.add(new ComboRule<>(
                    Set.copyOf(req), Set.copyOf(observed), blockers,
                    exact, chordDelay,
                    chord != null ? chord.getActionType() : null,
                    hold != null ? hold.getActionType() : null,
                    hold != null ? hold.getDuration() : null
            ));
        }
        this.rules = List.copyOf(compiled);
        for (ComboRule<ActionType> rule : rules) {
            states.put(rule.requiredKeys(), new ComboState<>());
        }
    }

    @Override
    public Optional<ActionType> onEvent(KeyInputEvent<?> event, Set<Enum<?>> pressed) {
        for (ComboRule<ActionType> rule : rules) {
            ComboState<ActionType> state = states.get(rule.requiredKeys());
            Optional<ActionType> result = processRule(rule, state, event, pressed);
            if (result.isPresent()) return result;
        }
        return Optional.empty();
    }

    private Optional<ActionType> processRule(ComboRule<ActionType> rule, ComboState<ActionType> state, KeyInputEvent<?> event, Set<Enum<?>> pressed) {
        Enum<?> key = event.getKeyType();
        if (!rule.observedKeys().contains(key)) return Optional.empty();

        boolean wasActive = state.active;
        boolean nowActive = computeActive(rule, pressed);
        boolean activationEvent = event.getEventType() == KeyInputEventType.KEY_DOWN
                                  && rule.requiredKeys().contains(key);

        if (!wasActive && nowActive && activationEvent) {
            state.active = true;
            state.holdFired = false;
            return onActivated(rule, state);
        }

        if (wasActive && !nowActive) {
            state.active = false;
            return onDeactivated(rule, state, event);
        }

        return Optional.empty();
    }

    private Optional<ActionType> onActivated(ComboRule<ActionType> rule, ComboState<ActionType> state) {
        if (rule.holdAction() != null && rule.holdDuration() != null) {
            cancelHold(state);
            state.holdTimer = scheduler.schedule(() -> {
                if (state.active && !state.holdFired) {
                    state.holdFired = true;
                    emit.accept(rule.holdAction());
                }
            }, rule.holdDuration().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (rule.chordAction() != null && rule.holdAction() == null) {
            if (rule.chordDelay().isZero()) {
                return Optional.of(rule.chordAction());
            }
            cancelChordConfirm(state);
            state.chordTimer = scheduler.schedule(
                    () -> emit.accept(rule.chordAction()),
                    rule.chordDelay().toMillis(), TimeUnit.MILLISECONDS
            );
        }
        return Optional.empty();
    }

    private Optional<ActionType> onDeactivated(ComboRule<ActionType> rule, ComboState<ActionType> state, KeyInputEvent<?> event) {

        cancelHold(state);

        if (rule.chordAction() != null && rule.holdAction() == null
            && state.chordTimer != null
            && event.getEventType() == KeyInputEventType.KEY_DOWN
            && rule.blockers().contains(event.getKeyType())) {
            cancelChordConfirm(state);
        }

        if (rule.chordAction() != null && rule.holdAction() != null
            && !state.holdFired
            && event.getEventType() == KeyInputEventType.KEY_UP
            && rule.requiredKeys().contains(event.getKeyType())) {
            return Optional.of(rule.chordAction());
        }

        return Optional.empty();
    }

    private boolean computeActive(ComboRule<ActionType> rule, Set<Enum<?>> pressed) {
        if (!pressed.containsAll(rule.requiredKeys())) return false;
        if (!rule.exactMatch()) return true;
        long observedPressed = rule.observedKeys().stream().filter(pressed::contains).count();
        return observedPressed == rule.requiredKeys().size();
    }

    private static Set<Enum<?>> computeBlockers(Set<Enum<?>> base, Set<Set<Enum<?>>> all) {
        Set<Enum<?>> blockers = new HashSet<>();
        for (Set<Enum<?>> other : all) {
            if (other.size() > base.size() && other.containsAll(base)) {
                for (Enum<?> k : other) if (!base.contains(k)) blockers.add(k);
            }
        }
        return Set.copyOf(blockers);
    }

    private void cancelHold(ComboState<ActionType> state) {
        if (state.holdTimer != null) {
            state.holdTimer.dispose();
            state.holdTimer = null;
        }
    }

    private void cancelChordConfirm(ComboState<ActionType> state) {
        if (state.chordTimer != null) {
            state.chordTimer.dispose();
            state.chordTimer = null;
        }
    }

    @Override
    public void dispose() {
        for (ComboState<ActionType> state : states.values()) {
            cancelHold(state);
            cancelChordConfirm(state);
            state.active = false;
            state.holdFired = false;
        }
    }

    private static final class ComboState<ActionType extends Enum<ActionType>> {
        boolean active;
        boolean holdFired;
        Disposable holdTimer;
        Disposable chordTimer;
    }
}