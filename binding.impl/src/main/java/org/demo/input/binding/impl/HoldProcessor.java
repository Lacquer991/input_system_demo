package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

class HoldProcessor<ActionType extends Enum<ActionType>> {

    private static final class RuleState {
        boolean active;
        boolean fired;
        Disposable timer;

        void cancelTimer() {
            if (timer != null) { timer.dispose(); timer = null; }
        }
    }

    private final Set<Enum<?>> pressed;
    private final Scheduler scheduler;
    private final Runnable onStateChanged;
    private final Map<Set<Enum<?>>, ComboRule<ActionType>> rules = new HashMap<>();
    private final Map<Set<Enum<?>>, RuleState> states = new HashMap<>();

    HoldProcessor(Set<Enum<?>> pressed, Scheduler scheduler, Runnable onStateChanged) {
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
        for (var entry : rules.entrySet()) {
            ComboRule<ActionType> rule = entry.getValue();
            RuleState state = states.get(entry.getKey());
            boolean nowActive = isActive(rule);

            if (!state.active && nowActive) {
                state.active = true;
                state.fired = false;
                state.cancelTimer();
                state.timer = scheduler.schedule(() -> {
                    if (state.active && !state.fired) {
                        state.fired = true;
                        onStateChanged.run();
                    }
                }, rule.duration().toMillis(), TimeUnit.MILLISECONDS);
            }

            if (state.active && !nowActive) {
                state.active = false;
                state.fired  = false;
                state.cancelTimer();
            }
        }
    }

    boolean isFired(Set<Enum<?>> keySet) {
        RuleState state = states.get(keySet);
        return state != null && state.fired;
    }

    ActionType consumeAction(Set<Enum<?>> keySet) {
        RuleState state = states.get(keySet);
        if (state == null) return null;
        state.fired = false;
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