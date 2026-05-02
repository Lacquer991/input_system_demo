package org.demo.input.binding.impl;


import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

public class InputEngine<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> {

    private final Consumer<ActionType> emit;

    private final Scheduler scheduler;

    private final Set<Enum<?>> pressed = new HashSet<>();

    private final Map<Enum<?>, KeyRecognizer<ActionType>> keyRecognizers = new HashMap<>();
    private final Map<Set<Enum<?>>, ComboRecognizer<ActionType, KeyType>> comboRecognizers = new HashMap<>();

    private final Map<Enum<?>, List<ComboRecognizer<ActionType, KeyType>>> combosByObservedKey = new HashMap<>();

    InputEngine(Consumer<ActionType> emit, Scheduler scheduler) {
        this.emit = emit;
        this.scheduler = scheduler;
    }

    void setBindings(List<Binding<ActionType>> bindings) {
        dispose();
        pressed.clear();
        keyRecognizers.clear();
        comboRecognizers.clear();
        combosByObservedKey.clear();

        Compiled<ActionType> compiled = Compiled.compile(bindings, Duration.ofMillis(100));

        for (var e : compiled.keyRules.entrySet()) {
            keyRecognizers.put(e.getKey(), new KeyRecognizer<>(e.getKey(), e.getValue(), scheduler, emit));
        }

        for (ComboRule<ActionType> rule : compiled.comboRules.values()) {
            var rec = new ComboRecognizer<ActionType, KeyType>(rule, scheduler, emit);
            comboRecognizers.put(rule.requiredKeys(), rec);

            for (Enum<?> ok : rule.observedKeys()) {
                combosByObservedKey.computeIfAbsent(ok, __ -> new ArrayList<>()).add(rec);
            }
        }
    }

    void onEvent(KeyInputEvent<KeyType> e) {
        Enum<?> key = e.getKeyType();

        // 1) любое событие другой клавиши прерывает tap для всех клавиш, которые сейчас "в нажатии"
        for (KeyRecognizer<ActionType> r : keyRecognizers.values()) {
            if (!r.key().equals(key)) r.interruptIfDown();
        }

        // 2) обновляем глобальный pressed-set
        if (e.getEventType() == KeyInputEventType.KEY_DOWN) pressed.add(key);
        else pressed.remove(key);

        // 3) обновляем key recognizer (tap/dtap)
        KeyRecognizer<ActionType> kr = keyRecognizers.get(key);
        if (kr != null) kr.onEvent(e);

        // 4) обновляем только combo-распознаватели, которым важна эта клавиша (observed)
        List<ComboRecognizer<ActionType, KeyType>> list = combosByObservedKey.get(key);
        if (list != null) {
            for (ComboRecognizer<ActionType, KeyType> cr : list) {
                cr.onEvent(e, pressed);
            }
        }
    }

    private static final class Compiled<ActionType extends Enum<ActionType>> {

        final Map<Enum<?>, KeyRule<ActionType>> keyRules;

        final Map<Set<Enum<?>>, ComboRule<ActionType>> comboRules;

        private Compiled(Map<Enum<?>, KeyRule<ActionType>> keyRules, Map<Set<Enum<?>>, ComboRule<ActionType>> comboRules) {
            this.keyRules = keyRules;
            this.comboRules = comboRules;
        }

        static <ActionType extends Enum<ActionType>> Compiled<ActionType> compile(List<Binding<ActionType>> bindings, Duration subsetDelay) {

            Map<Enum<?>, Binding.Tap<ActionType>> taps = new HashMap<>();
            Map<Enum<?>, Binding.DoubleTap<ActionType>> doubleTaps = new HashMap<>();

            Map<Set<Enum<?>>, Binding.Hold<ActionType>> holds = new HashMap<>();
            Map<Set<Enum<?>>, Binding.Chord<ActionType>> chords = new HashMap<>();

            for (Binding<ActionType> binding : bindings) {
                switch (binding.getBindingType()) {
                    case TAP -> {
                        var tap = (Binding.Tap<ActionType>) binding;
                        taps.put(tap.getKey(), tap);
                    }
                    case DOUBLE_TAP -> {
                        var doubleTap = (Binding.DoubleTap<ActionType>) binding;
                        doubleTaps.put(doubleTap.getKey(), doubleTap);
                    }
                    case CHORD -> {
                        var chord = (Binding.Chord<ActionType>) binding;
                        chords.put(Set.copyOf(chord.getKeys()), chord);
                    }
                    case HOLD -> {
                        var hold = (Binding.Hold<ActionType>) binding;
                        holds.put(Set.copyOf(hold.getKeys()), hold);
                    }
                }
            }

            Set<Enum<?>> allKeys = new HashSet<>();
            allKeys.addAll(taps.keySet());
            allKeys.addAll(doubleTaps.keySet());

            Map<Enum<?>, KeyRule<ActionType>> keyRules = new HashMap<>();
            for (Enum<?> k : allKeys) keyRules.put(k, new KeyRule<>(taps.get(k), doubleTaps.get(k)));


            Set<Set<Enum<?>>> comboSets = new HashSet<>();
            comboSets.addAll(chords.keySet());
            comboSets.addAll(holds.keySet());

            List<Set<Enum<?>>> sets = comboSets.stream().map(Set::copyOf).toList();


            Map<Set<Enum<?>>, Set<Enum<?>>> blockersBySet = new HashMap<>();
            for (Set<Enum<?>> base : sets) {
                Set<Enum<?>> blockers = new HashSet<>();
                for (Set<Enum<?>> other : sets) {
                    if (other.size() > base.size() && other.containsAll(base)) {
                        for (Enum<?> k : other) if (!base.contains(k)) blockers.add(k);
                    }
                }
                blockersBySet.put(base, Set.copyOf(blockers));
            }


            Map<Set<Enum<?>>, ComboRule<ActionType>> comboRules = new HashMap<>();
            for (Set<Enum<?>> req : sets) {
                var chord = chords.get(req);
                var hold = holds.get(req);

                Set<Enum<?>> blockers = blockersBySet.getOrDefault(req, Set.of());
                Set<Enum<?>> observed = new HashSet<>(req);
                observed.addAll(blockers);

                boolean exact = !blockers.isEmpty();
                Duration chordDelay = (chord != null && hold == null && exact) ? subsetDelay : Duration.ZERO;

                comboRules.put(req, new ComboRule<>(req, Set.copyOf(observed), blockers, exact, chordDelay,
                        chord != null ? chord.getActionType() : null,
                        hold != null ? hold.getActionType() : null,
                        hold != null ? hold.getDuration() : null
                ));
            }
            return new Compiled<>(Map.copyOf(keyRules), Map.copyOf(comboRules));
        }
    }

    void dispose() {
        keyRecognizers.values().forEach(KeyRecognizer::dispose);
        comboRecognizers.values().forEach(ComboRecognizer::dispose);
    }
}
