package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import reactor.core.Disposable;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class KeyRecognizer<ActionType extends Enum<ActionType>> {

    private final Enum<?> key;

    private final KeyRule<ActionType> rule;

    private final Scheduler scheduler;

    private final Consumer<ActionType> emit;

    private Instant downAt;

    private Instant secondDownAt;

    private boolean interrupted;

    private Pending pending;

    private Disposable pendingTimer;

    private boolean waitingSecondUp;

    private record Pending(boolean validSingle, Instant firstUpAt) {
    }

    KeyRecognizer(Enum<?> key, KeyRule<ActionType> rule, Scheduler scheduler, Consumer<ActionType> emit) {
        this.key = key;
        this.rule = rule;
        this.scheduler = scheduler;
        this.emit = emit;
    }

    void interruptIfDown() {
        if (downAt != null) interrupted = true;
    }

    void onEvent(KeyInputEvent<?> e) {
        if (e.getKeyType() != key) return;

        if (e.getEventType() == KeyInputEventType.KEY_DOWN) {
            onDown(e.getTimestamp());
        } else {
            onUp(e.getTimestamp());
        }
    }

    private void onDown(Instant ts) {
        var doubleTap = rule.doubleTap();
        if (doubleTap != null && pending != null && !waitingSecondUp) {
            Duration gap = Duration.between(pending.firstUpAt(), ts);
            if (!gap.isNegative() && gap.compareTo(doubleTap.getInterval()) <= 0) {
                waitingSecondUp = true;
                secondDownAt = ts;
                cancelTimer();
            }
        }
        if (downAt == null) {
            downAt = ts;
            interrupted = false;
        }
    }

    private void onUp(Instant upAt) {
        if (downAt == null) return;

        Duration duration = Duration.between(downAt, upAt);
        downAt = null;

        boolean validTap = rule.tap() != null && !interrupted && duration.compareTo(rule.tap().getDuration()) <= 0;
        boolean validDTap = rule.doubleTap() != null && !interrupted && duration.compareTo(rule.doubleTap().getDuration()) <= 0;

        if (pending != null && waitingSecondUp) {
            Duration interval = Duration.between(pending.firstUpAt(), secondDownAt);
            boolean isValidInterval = !interval.isNegative() && interval.compareTo(rule.doubleTap().getInterval()) <= 0;

            if (isValidInterval && validDTap) {
                emit.accept(rule.doubleTap().getActionType());
            } else {
                flushPendingSingle();
                if (validTap) emit.accept(rule.tap().getActionType());
            }
            clearPending();
            return;
        }

        if (rule.doubleTap() != null && validDTap) {
            cancelTimer();
            pending = new Pending(validTap, upAt);
            pendingTimer = scheduler.schedule(() -> {
                flushPendingSingle();
                clearPending();
            }, rule.doubleTap().getInterval().toMillis(), TimeUnit.MILLISECONDS);
            return;
        }

        if (validTap) emit.accept(rule.tap().getActionType());
    }


    private void flushPendingSingle() {
        if (pending != null && pending.validSingle() && rule.tap() != null) {
            emit.accept(rule.tap().getActionType());
        }
    }

    private void clearPending() {
        cancelTimer();
        pending = null;
        waitingSecondUp = false;
        secondDownAt = null;
    }

    private void cancelTimer() {
        if (pendingTimer != null) {
            pendingTimer.dispose();
            pendingTimer = null;
        }
    }

    Enum<?> key() {
        return key;
    }

    void dispose() {
        cancelTimer();
        pending = null;
        waitingSecondUp = false;
        secondDownAt = null;
        downAt = null;
        interrupted = false;
    }
}
