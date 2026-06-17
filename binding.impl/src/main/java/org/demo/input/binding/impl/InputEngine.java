package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

class InputEngine<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> {

    private static final Duration SUBSET_CHORD_DELAY = Duration.ofMillis(100);

    private final Consumer<ActionType> emit;
    private final Set<Enum<?>> pressed = new HashSet<>();

    private final TapProcessor<ActionType> tapProcessor;
    private final DoubleTapProcessor<ActionType> doubleTapProcessor;
    private final ChordProcessor<ActionType> chordProcessor;
    private final HoldProcessor<ActionType> holdProcessor;

    private Set<Set<Enum<?>>> holdKeySets = Set.of();

    InputEngine(Consumer<ActionType> emit, Scheduler scheduler) {
        this.emit = emit;
        this.tapProcessor = new TapProcessor<>();
        this.doubleTapProcessor = new DoubleTapProcessor<>(scheduler, this::resolve);
        this.chordProcessor = new ChordProcessor<>(pressed, scheduler, this::resolve);
        this.holdProcessor = new HoldProcessor<>(pressed, scheduler, this::resolve);
    }

    void setBindings(List<Binding<ActionType>> bindings) {
        pressed.clear();
        tapProcessor.setBindings(bindings);
        doubleTapProcessor.setBindings(bindings);

        Map<Set<Enum<?>>, Binding.Chord<ActionType>> chords = new HashMap<>();
        Map<Set<Enum<?>>, Binding.Hold<ActionType>> holds = new HashMap<>();

        for (Binding<ActionType> b : bindings) {
            if (b instanceof Binding.Chord<ActionType> c) chords.put(Set.copyOf(c.getKeys()), c);
            if (b instanceof Binding.Hold<ActionType> h) holds.put(Set.copyOf(h.getKeys()), h);
        }

        Set<Set<Enum<?>>> allSets = new HashSet<>();
        allSets.addAll(chords.keySet());
        allSets.addAll(holds.keySet());

        List<ComboRule<ActionType>> chordRules = new ArrayList<>();
        List<ComboRule<ActionType>> holdRules = new ArrayList<>();

        for (Set<Enum<?>> req : allSets) {
            Set<Enum<?>> blockers = computeBlockers(req, allSets);
            Set<Enum<?>> observed = new HashSet<>(req);
            observed.addAll(blockers);
            boolean exact = !blockers.isEmpty();

            var chord = chords.get(req);
            var hold = holds.get(req);

            if (chord != null) {
                Duration delay = (hold == null && exact) ? SUBSET_CHORD_DELAY : Duration.ZERO;
                chordRules.add(new ComboRule<>(Set.copyOf(req), Set.copyOf(observed), blockers, exact, delay, chord.getActionType(), null));
            }
            if (hold != null) {
                holdRules.add(new ComboRule<>(Set.copyOf(req), Set.copyOf(observed), blockers, exact, Duration.ZERO, hold.getActionType(), hold.getDuration()));
            }
        }

        holdKeySets = Set.copyOf(holds.keySet());
        chordProcessor.setRules(chordRules);
        holdProcessor.setRules(holdRules);
    }

    void onEvent(KeyInputEvent<KeyType> event) {
        if (event.getEventType() == KeyInputEventType.KEY_DOWN) {
            pressed.add(event.getKeyType());
        } else {
            pressed.remove(event.getKeyType());
        }

        Optional<ActionType> dtap = doubleTapProcessor.update(event);
        if (dtap.isPresent()) {
            tapProcessor.consume(event.getKeyType());
        }

        tapProcessor.update(event);
        chordProcessor.update(event);
        holdProcessor.update(event);

        if (dtap.isPresent()) {
            emit.accept(dtap.get());
            return;
        }

        resolve();
    }

    private void resolve() {
        for (Enum<?> key : doubleTapProcessor.boundKeys()) {
            var dtap = doubleTapProcessor.poll(key);
            if (dtap.isPresent()) {
                tapProcessor.consume(key);
                emit.accept(dtap.get());
                return;
            }
        }

        for (Set<Enum<?>> keySet : chordProcessor.keySets()) {
            boolean hasHoldConflict = holdKeySets.contains(keySet);
            boolean holdFired = holdProcessor.isFired(keySet);

            if (!hasHoldConflict && chordProcessor.isReadyToEmit(keySet)) {
                emit.accept(chordProcessor.consumeAction(keySet));
                return;
            }

            if (hasHoldConflict && chordProcessor.isReleased(keySet) && !holdFired) {
                emit.accept(chordProcessor.consumeAction(keySet));
                return;
            }
        }

        for (Set<Enum<?>> keySet : holdProcessor.keySets()) {
            if (holdProcessor.isFired(keySet)) {
                chordProcessor.consumeAction(keySet);
                emit.accept(holdProcessor.consumeAction(keySet));
                return;
            }
        }

        for (Enum<?> key : tapProcessor.boundKeys()) {
            if (doubleTapProcessor.isPending(key)) continue;
            Optional<ActionType> tap = tapProcessor.poll(key);
            if (tap.isPresent()) {
                emit.accept(tap.get());
                return;
            }
        }
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

    void dispose() {
        tapProcessor.dispose();
        doubleTapProcessor.dispose();
        chordProcessor.dispose();
        holdProcessor.dispose();
    }
}