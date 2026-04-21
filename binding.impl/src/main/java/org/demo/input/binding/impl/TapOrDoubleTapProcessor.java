package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;
import org.demo.input.source.KeyInputEventType;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.Disposables;
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

    private static final class State {
        PendingFirst pendingFirst;
        boolean waitingSecondUp;
        Instant secondDownAt;
        Disposable timer;

        void cancelTimer() {
            if (timer != null) {
                timer.dispose();
                timer = null;
            }
        }

        void clear() {
            cancelTimer();
            pendingFirst = null;
            waitingSecondUp = false;
            secondDownAt = null;
        }
    }

    @Override
    public Publisher<ActionType> process(Publisher<KeyInputEvent<KeyType>> events, Scheduler scheduler) {

        Flux<KeyInputEvent<KeyType>> keyEvents = Flux.from(events).share();

        Flux<Instant> downs = keyEvents
                .filter(e -> e.getEventType() == KeyInputEventType.KEY_DOWN)
                .filter(e -> e.getKeyType().equals(key))
                .map(KeyInputEvent::getTimestamp);

        Flux<TapDetector.TapGesture> gestures = TapDetector.detect(keyEvents, key);

        return Flux.create(sink -> {
            State st = new State();
            var cd = Disposables.composite();

            cd.add(downs.subscribe(ts -> onKeyDown(ts, st), sink::error));
            cd.add(gestures.subscribe(g -> onGesture(g, st, sink, scheduler), sink::error));

            sink.onDispose(() -> {
                cd.dispose();
                st.cancelTimer();
            });
        }, FluxSink.OverflowStrategy.BUFFER);
    }


    private void onKeyDown(Instant ts, State st) {
        if (doubleTapBinding == null) return;
        if (st.pendingFirst == null || st.waitingSecondUp) return;

        Duration dt = Duration.between(st.pendingFirst.firstUpAt(), ts);
        if (!dt.isNegative() && dt.compareTo(doubleTapBinding.getInterval()) <= 0) {
            st.secondDownAt = ts;
            st.waitingSecondUp = true;
            st.cancelTimer();
        }
    }


    private void onGesture(TapDetector.TapGesture g, State st, FluxSink<ActionType> sink, Scheduler scheduler) {

        Duration dur = Duration.between(g.downAt(), g.upAt());

        boolean validSingle = tapBinding != null && !g.interrupted() && dur.compareTo(tapBinding.getDuration()) <= 0;
        boolean validForDoubleTap = doubleTapBinding != null && !g.interrupted() && dur.compareTo(doubleTapBinding.getDuration()) <= 0;

        if (st.pendingFirst != null && st.waitingSecondUp) {
            Duration interval = Duration.between(st.pendingFirst.firstUpAt(), st.secondDownAt);
            boolean intervalOk = !interval.isNegative() && interval.compareTo(doubleTapBinding.getInterval()) <= 0;

            if (intervalOk && validForDoubleTap) {
                sink.next(doubleTapBinding.getActionType());
            } else {
                flushPendingSingle(st, sink);
                if (validSingle) sink.next(tapBinding.getActionType());
            }
            st.clear();
            return;
        }

        if (doubleTapBinding != null && validForDoubleTap) {
            st.cancelTimer();
            st.pendingFirst = new PendingFirst(validSingle, g.upAt());

            st.timer = scheduler.schedule(() -> {
                flushPendingSingle(st, sink);
                st.clear();
            }, doubleTapBinding.getInterval().toMillis(), TimeUnit.MILLISECONDS);

            return;
        }

        if (validSingle) sink.next(tapBinding.getActionType());
    }

    private void flushPendingSingle(State st, FluxSink<ActionType> sink) {
        if (st.pendingFirst != null && st.pendingFirst.validSingle() && tapBinding != null) {
            sink.next(tapBinding.getActionType());
        }
    }
}
