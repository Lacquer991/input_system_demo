package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

class ComboProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>> {
    private final ComboRule<ActionType> rule;
    private final Scheduler scheduler;

    private final Consumer<ActionType> emit;

    private boolean active;

    private boolean holdFired;

    private Disposable holdTimer;

    private Disposable chordConfirmTimer;

    ComboProcessor(ComboRule<ActionType> rule, Scheduler scheduler, Consumer<ActionType> emit) {
        this.rule = rule;

        this.scheduler = scheduler;

        this.emit = emit;
    }

    void onEvent(KeyInputEvent<KeyType> e, Set<Enum<?>> pressedGlobal) {
        Enum<?> key = e.getKeyType();

        boolean wasActive = active;
        boolean nowActive = computeActive(pressedGlobal);

        boolean activationEvent = (e.getEventType() == KeyInputEventType.KEY_DOWN) && rule.requiredKeys().contains(key);

        if (!wasActive && nowActive && activationEvent) {
            active = true;
            holdFired = false;
            onActivated();
            return;
        }

        if (wasActive && !nowActive) {
            active = false;
            onDeactivated(e);
        }
    }


    private boolean computeActive(Set<Enum<?>> pressedGlobal) {
        for (Enum<?> key : rule.requiredKeys()) {
            if (!pressedGlobal.contains(key)) return false;
        }

        if (!rule.exactMatch()) return true;

        int observedPressed = 0;
        for (Enum<?> key : rule.observedKeys()) {
            if (pressedGlobal.contains(key)) observedPressed++;
        }
        return observedPressed == rule.requiredKeys().size();
    }

    private void onActivated() {
        if (rule.holdAction() != null && rule.holdDuration() != null) {
            cancelHold();
            holdTimer = scheduler.schedule(() -> {
                if (active && !holdFired) {
                    holdFired = true;
                    emit.accept(rule.holdAction());
                }
            }, rule.holdDuration().toMillis(), TimeUnit.MILLISECONDS);
        }

        if (rule.chordAction() != null) {
            boolean hasHold = rule.holdAction() != null;

            if (!hasHold) {
                if (rule.chordDelay() == null || rule.chordDelay().isZero() || rule.chordDelay().isNegative()) {
                    emit.accept(rule.chordAction());
                } else {
                    cancelChordConfirm();
                    chordConfirmTimer = scheduler.schedule(() -> {
                        emit.accept(rule.chordAction());
                    }, rule.chordDelay().toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        }
    }

    private void onDeactivated(KeyInputEvent<KeyType> e) {
        cancelHold();

        if (rule.chordAction() != null
            && (rule.holdAction() == null)
            && chordConfirmTimer != null
            && e.getEventType() == KeyInputEventType.KEY_DOWN
            && rule.blockers().contains(e.getKeyType())) {
            cancelChordConfirm();
        }


        if (rule.chordAction() != null && rule.holdAction() != null) {
            if (!holdFired
                && e.getEventType() == KeyInputEventType.KEY_UP
                && rule.requiredKeys().contains(e.getKeyType())) {
                emit.accept(rule.chordAction());
            }
        }
    }

    private void cancelHold() {
        if (holdTimer != null) {
            holdTimer.dispose();
            holdTimer = null;
        }
    }

    private void cancelChordConfirm() {
        if (chordConfirmTimer != null) {
            chordConfirmTimer.dispose();
            chordConfirmTimer = null;
        }
    }

    void dispose() {
        cancelHold();
        cancelChordConfirm();
        active = false;
        holdFired = false;
    }


}
