package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

class TapOrDoubleTapProcessor<ActionType extends Enum<ActionType>, KeyType extends Enum<KeyType>>
        implements ActionProcessor<ActionType, KeyType> {

    private final Binding.Tap<ActionType> tapBinding;

    private final Binding.DoubleTap<ActionType> doubleTapBinding;

    private final Enum<?> key;

    TapOrDoubleTapProcessor(Binding.Tap<ActionType> tapBinding, Binding.DoubleTap<ActionType> doubleTapBinding) {
        this.tapBinding = tapBinding;
        this.doubleTapBinding = doubleTapBinding;
        this.key = tapBinding != null ? tapBinding.getKey() : doubleTapBinding.getKey();
    }

    private record PendingFirst(boolean validSingle, Instant firstUpAt) {
    }

    private final class State {
        Instant downAt;
        boolean interrupted;

        PendingFirst pendingFirst;
        boolean waitingSecondUp;
        Disposable intervalTimer;

        void cancelTimer() {
            if (intervalTimer != null) {
                intervalTimer.dispose();
                intervalTimer = null;
            }
        }

        void clearPending() {
            cancelTimer();
            pendingFirst = null;
            waitingSecondUp = false;
        }
    }

    @Override
    public Publisher<ActionCandidate<ActionType>> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {

        Flux<KeyInputEvent<KeyType>> keyEvents = Flux.from(events);

        return Flux.create(sink -> {
            State st = new State();

            Disposable sub = keyEvents.subscribe(
                    e -> onEvent(e, st, sink, scheduler),
                    sink::error,
                    sink::complete
            );

            sink.onDispose(() -> {
                sub.dispose();
                st.cancelTimer();
            });
        }, FluxSink.OverflowStrategy.BUFFER);

    }

    private void onEvent(KeyInputEvent<KeyType> e, State st, FluxSink<ActionCandidate<ActionType>> sink, Scheduler scheduler) {

        boolean isOurKey = e.getKeyType().equals(key);

        if (!isOurKey && st.downAt != null) {
            st.interrupted = true;
        }

        if (!isOurKey) return;

        if (e.getEventType() == KeyInputEventType.KEY_DOWN) {
            onKeyDown(e.getTimestamp(), st, sink);
        } else {
            onKeyUp(e.getTimestamp(), st, sink, scheduler);
        }
    }

    private void onKeyDown(Instant ts, State st, FluxSink<ActionCandidate<ActionType>> sink) {

        if (st.downAt != null) return;

        if (st.pendingFirst != null && !st.waitingSecondUp && doubleTapBinding != null) {
            Duration dt = Duration.between(st.pendingFirst.firstUpAt(), ts);

            if (!dt.isNegative() && dt.compareTo(doubleTapBinding.getInterval()) <= 0) {
                st.waitingSecondUp = true;
                st.cancelTimer();
            } else {
                flushFirstSingleIfAny(st, sink);
                st.clearPending();
            }
        }

        st.downAt = ts;
        st.interrupted = false;
    }

    private void onKeyUp(Instant upAt, State st, FluxSink<ActionCandidate<ActionType>> sink, Scheduler scheduler) {
        if (st.downAt == null) return;

        Instant downAt = st.downAt;
        st.downAt = null;

        Duration pressed = Duration.between(downAt, upAt);
        if (pressed.isNegative()) return;

        boolean validForTap = tapBinding != null && !st.interrupted && pressed.compareTo(tapBinding.getDuration()) <= 0;

        boolean validForDoubleTap = doubleTapBinding != null && !st.interrupted && pressed.compareTo(doubleTapBinding.getDuration()) <= 0;

        if (st.pendingFirst != null && st.waitingSecondUp) {
            if (validForDoubleTap) {
                sink.next(ActionCandidate.doubleTap(doubleTapBinding.getActionType(), key));
            } else {
                flushFirstSingleIfAny(st, sink);
                if (validForTap) {
                    sink.next(ActionCandidate.tap(tapBinding.getActionType(), key));
                }
            }
            st.clearPending();
            return;
        }

        if (doubleTapBinding != null && validForDoubleTap) {
            st.pendingFirst = new PendingFirst(validForTap, upAt);

            st.intervalTimer = scheduler.schedule(() -> {
                flushFirstSingleIfAny(st, sink);
                st.clearPending();
            }, doubleTapBinding.getInterval().toMillis(), TimeUnit.MILLISECONDS);

            return;
        }

        if (validForTap) {
            sink.next(ActionCandidate.tap(tapBinding.getActionType(), key));
        }
    }

    private void flushFirstSingleIfAny(State st, FluxSink<ActionCandidate<ActionType>> sink) {
        if (st.pendingFirst == null) return;
        if (st.pendingFirst.validSingle() && tapBinding != null) {
            sink.next(ActionCandidate.tap(tapBinding.getActionType(), key));
        }
    }
}
