package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

class InputEngine<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> {

    private static final Duration SUBSET_CHORD_DELAY = Duration.ofMillis(100);

    private final Consumer<ActionType> emit;
    private final Scheduler scheduler;
    private final Scheduler.Worker inputLoop;
    private final boolean ownsInputLoop;
    private final Set<Enum<?>> pressedKeys = new HashSet<>();

    private final TapProcessor<ActionType> tapProcessor = new TapProcessor<>();
    private final DoubleTapProcessor<ActionType> doubleTapProcessor = new DoubleTapProcessor<>();
    private final ChordProcessor<ActionType> chordProcessor = new ChordProcessor<>();
    private final HoldProcessor<ActionType> holdProcessor = new HoldProcessor<>();

    private Disposable resolutionTimer;
    private Set<Enum<?>> consumedKeys = Set.of();

    InputEngine(Consumer<ActionType> emit, Scheduler scheduler) {
        this(emit, scheduler, scheduler.createWorker(), true);
    }

    InputEngine(Consumer<ActionType> emit, Scheduler scheduler, Scheduler.Worker inputLoop) {
        this(emit, scheduler, inputLoop, false);
    }

    private InputEngine(Consumer<ActionType> emit, Scheduler scheduler,
                        Scheduler.Worker inputLoop, boolean ownsInputLoop) {
        this.emit = emit;
        this.scheduler = scheduler;
        this.inputLoop = inputLoop;
        this.ownsInputLoop = ownsInputLoop;
    }

    void setBindings(List<Binding<ActionType>> bindings) {
        cancelResolutionTimer();
        pressedKeys.clear();
        consumedKeys = Set.of();
        tapProcessor.setBindings(bindings);
        doubleTapProcessor.setBindings(bindings);

        Map<Set<Enum<?>>, Binding.Chord<ActionType>> chords = new LinkedHashMap<>();
        Map<Set<Enum<?>>, Binding.Hold<ActionType>> holds = new LinkedHashMap<>();
        Set<Set<Enum<?>>> allKeySets = new LinkedHashSet<>();
        for (Binding<ActionType> binding : bindings) {
            if (binding instanceof Binding.Chord<ActionType> chord) {
                Set<Enum<?>> keys = Set.copyOf(chord.getKeys());
                chords.put(keys, chord);
                allKeySets.add(keys);
            }
            if (binding instanceof Binding.Hold<ActionType> hold) {
                Set<Enum<?>> keys = Set.copyOf(hold.getKeys());
                holds.put(keys, hold);
                allKeySets.add(keys);
            }
        }

        List<ComboRule<ActionType>> chordRules = new ArrayList<>();
        List<ComboRule<ActionType>> holdRules = new ArrayList<>();
        for (Set<Enum<?>> requiredKeys : allKeySets) {
            Set<Enum<?>> blockers = computeBlockers(requiredKeys, allKeySets);

            Binding.Chord<ActionType> chord = chords.get(requiredKeys);
            Binding.Hold<ActionType> hold = holds.get(requiredKeys);
            if (chord != null) {
                Duration delay = hold == null && !blockers.isEmpty() ? SUBSET_CHORD_DELAY : Duration.ZERO;
                chordRules.add(new ComboRule<>(requiredKeys, blockers, delay, chord.getActionType(), null));
            }
            if (hold != null) {
                holdRules.add(new ComboRule<>(requiredKeys, blockers, Duration.ZERO, hold.getActionType(), hold.getDuration()));
            }
        }

        chordProcessor.setRules(chordRules);
        holdProcessor.setRules(holdRules);
    }

    void onEvent(KeyInputEvent<KeyType> event) {
        cancelResolutionTimer();
        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            pressedKeys.add(event.getKeyType());
        } else {
            pressedKeys.remove(event.getKeyType());
        }

        if (!consumedKeys.isEmpty()) {
            if (pressedKeys.stream().noneMatch(consumedKeys::contains)) {
                consumedKeys = Set.of();
            }
            resetProcessors();
            return;
        }

        long nowMillis = scheduler.now(TimeUnit.MILLISECONDS);
        Set<Enum<?>> pressedKeysCopy = Set.copyOf(pressedKeys);
        doubleTapProcessor.update(event, pressedKeysCopy, nowMillis);
        tapProcessor.update(event, pressedKeysCopy, nowMillis);
        holdProcessor.update(event, pressedKeysCopy, nowMillis);
        chordProcessor.update(event, pressedKeysCopy, nowMillis);

        resolveConflicts();
    }

    private void resolveConflicts() {
        resolutionTimer = null;
        long nowMillis = scheduler.now(TimeUnit.MILLISECONDS);
        doubleTapProcessor.expire(nowMillis);

        Optional<ProcessorState<ActionType>> doubleTapState = doubleTapProcessor.getCurrentState();
        Optional<ProcessorState<ActionType>> tapState = tapProcessor.getCurrentState();
        Optional<ProcessorState<ActionType>> holdState = readyWhenDue(holdProcessor.getCurrentState(), nowMillis);
        Optional<ProcessorState<ActionType>> chordState = readyWhenDue(chordProcessor.getCurrentState(), nowMillis);

        if (doubleTapState.filter(ProcessorState::isReady).isPresent()) {
            publish(doubleTapState.orElseThrow());
            return;
        }

        boolean chordHoldConflict = chordState.isPresent() && holdState.isPresent() && chordState.get().keys().equals(holdState.get().keys());

        if (chordHoldConflict) {
            if (holdState.get().isReady()) {
                publish(holdState.get());
                return;
            }
        } else if (chordState.filter(ProcessorState::isReady).isPresent()) {
            publish(chordState.orElseThrow());
            return;
        }

        if (!chordHoldConflict && holdState.filter(ProcessorState::isReady).isPresent()) {
            publish(holdState.orElseThrow());
            return;
        }

        boolean doubleTapPendingForTap = doubleTapState.isPresent() && tapState.isPresent() && doubleTapState.get().keys().equals(tapState.get().keys());
        if (!doubleTapPendingForTap && tapState.isPresent()) {
            publish(tapState.get());
            return;
        }

        scheduleNextResolution(nowMillis, doubleTapState, chordHoldConflict ? Optional.empty() : chordState, holdState);
    }

    private Optional<ProcessorState<ActionType>> readyWhenDue(
            Optional<ProcessorState<ActionType>> state, long nowMillis) {
        return state.map(candidate -> candidate.phase() == ProcessorState.Phase.ACTIVE
                                      && nowMillis >= candidate.readyAtMillis() ? candidate.ready() : candidate);
    }

    private void scheduleNextResolution(long nowMillis, Optional<ProcessorState<ActionType>> doubleTap, Optional<ProcessorState<ActionType>> chord, Optional<ProcessorState<ActionType>> hold) {
        long nextDeadline = Long.MAX_VALUE;
        if (doubleTap.filter(state -> state.phase() == ProcessorState.Phase.WAITING).isPresent()) {
            nextDeadline = Math.min(nextDeadline, doubleTap.get().readyAtMillis());
        }
        if (chord.filter(state -> state.phase() == ProcessorState.Phase.ACTIVE).isPresent()) {
            nextDeadline = Math.min(nextDeadline, chord.get().readyAtMillis());
        }
        if (hold.filter(state -> state.phase() == ProcessorState.Phase.ACTIVE).isPresent()) {
            nextDeadline = Math.min(nextDeadline, hold.get().readyAtMillis());
        }

        if (nextDeadline != Long.MAX_VALUE) {
            resolutionTimer = inputLoop.schedule(this::resolveConflicts,
                    Math.max(0, nextDeadline - nowMillis), TimeUnit.MILLISECONDS);
        }
    }

    private void publish(ProcessorState<ActionType> state) {
        emit.accept(state.action());
        if (state.keys().stream().anyMatch(pressedKeys::contains)) {
            consumedKeys = state.keys();
        }
        resetProcessors();
    }

    private void resetProcessors() {
        cancelResolutionTimer();
        tapProcessor.reset();
        doubleTapProcessor.reset();
        chordProcessor.reset();
        holdProcessor.reset();
    }

    private void cancelResolutionTimer() {
        if (resolutionTimer != null) {
            resolutionTimer.dispose();
            resolutionTimer = null;
        }
    }

    private static Set<Enum<?>> computeBlockers(Set<Enum<?>> base, Set<Set<Enum<?>>> all) {
        Set<Enum<?>> blockers = new HashSet<>();
        for (Set<Enum<?>> other : all) {
            if (other.size() > base.size() && other.containsAll(base)) {
                for (Enum<?> key : other) {
                    if (!base.contains(key)) blockers.add(key);
                }
            }
        }
        return Set.copyOf(blockers);
    }

    void dispose() {
        cancelResolutionTimer();
        tapProcessor.dispose();
        doubleTapProcessor.dispose();
        chordProcessor.dispose();
        holdProcessor.dispose();
        pressedKeys.clear();
        consumedKeys = Set.of();
        if (ownsInputLoop) inputLoop.dispose();
    }
}
