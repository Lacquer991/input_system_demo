package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class ChordProcessor<ActionType extends Enum<ActionType>> {

    private static final class RuleState {
        boolean active;
        boolean readyToEmit;
        boolean released;
        Disposable delayTimer;

        void cancelTimer() {
            if (delayTimer != null) { delayTimer.dispose(); delayTimer = null; }
        }
    }

    private final Set<Enum<?>> pressed;
    private final Scheduler scheduler;
    private final Runnable onStateChanged;
    private final Map<Set<Enum<?>>, ComboRule<ActionType>> rules = new HashMap<>();
    private final Map<Set<Enum<?>>, RuleState> states = new HashMap<>();

    ChordProcessor(Set<Enum<?>> pressed, Scheduler scheduler, Runnable onStateChanged) {
        this.pressed = pressed;
        this.scheduler = scheduler;
        this.onStateChanged = onStateChanged;
    }

    void setRules(List<ComboRule<ActionType>> rules) {
        dispose();
        this.rules.clear();
        this.states.clear();
        for (ComboRule<ActionType> rule : rules) {
            this.rules.put(rule.requiredKeys(), rule);
            this.states.put(rule.requiredKeys(), new RuleState());
        }
    }

    void update(KeyInputEvent<?> event) {
        boolean isDown = event.getEventType() == KeyInputEventType.KEY_DOWN;

        for (var entry : rules.entrySet()) {
            Set<Enum<?>> keySet = entry.getKey();
            ComboRule<ActionType> rule = entry.getValue();
            RuleState state = states.get(keySet);

            if (isDown) {
                if (state.active && rule.blockers().contains(event.getKeyType())) {
                    state.cancelTimer();
                    state.active = false;
                    state.readyToEmit = false;
                    state.released = false;
                    continue;
                }

                if (!state.active && isActive(rule)) {
                    state.active = true;
                    state.readyToEmit = false;
                    state.released = false;
                    state.cancelTimer();

                    if (rule.chordDelay().isZero()) {
                        state.readyToEmit = true;
                        onStateChanged.run();
                    } else {
                        state.delayTimer = scheduler.schedule(() -> {
                            if (state.active) {
                                state.readyToEmit = true;
                                onStateChanged.run();
                            }
                        }, rule.chordDelay().toMillis(), TimeUnit.MILLISECONDS);
                    }
                }
            } else {
                if (state.active && !isActive(rule)) {
                    state.active = false;
                    state.cancelTimer();
                    state.released = true;
                    state.readyToEmit = true;
                    onStateChanged.run();
                }
            }
        }
    }

    boolean isReadyToEmit(Set<Enum<?>> keySet) {
        RuleState state = states.get(keySet);
        return state != null && state.readyToEmit;
    }

    boolean isReleased(Set<Enum<?>> keySet) {
        RuleState state = states.get(keySet);
        return state != null && state.released;
    }

    ActionType consumeAction(Set<Enum<?>> keySet) {
        RuleState state = states.get(keySet);
        if (state == null) return null;
        state.readyToEmit = false;
        state.released = false;
        state.active = false;
        return rules.get(keySet).action();
    }

    Set<Set<Enum<?>>> keySets() {
        return rules.keySet();
    }

    private boolean isActive(ComboRule<ActionType> rule) {
        if (!pressed.containsAll(rule.requiredKeys())) return false;
        if (!rule.exactMatch()) return true;
        return rule.observedKeys().stream().filter(pressed::contains).count() == rule.requiredKeys().size();
    }

    void dispose() {
        states.values().forEach(RuleState::cancelTimer);
        states.clear();
    }
}