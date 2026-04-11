package org.demo.input.binding;

import java.time.Duration;
import java.util.Collection;
import java.util.Set;

public class Bindings {

    public static <A extends Enum<A>> Binding.Tap<A> createTapBinding(A actionType, Enum<?> keyType, Duration duration) {
        return new Binding.Tap<>() {
            @Override
            public Enum<?> getKey() {
                return keyType;
            }

            @Override
            public Duration getDuration() {
                return duration;
            }

            @Override
            public A getActionType() {
                return actionType;
            }
        };
    }

    public static <A extends Enum<A>> Binding.DoubleTap<A> createDouleTapBinding(A actionType, Enum<?> keyType, Duration duration) {
        return new Binding.DoubleTap<>() {
            @Override
            public Enum<?> getKey() {
                return keyType;
            }

            @Override
            public Duration getDuration() {
                return duration;
            }

            @Override
            public A getActionType() {
                return actionType;
            }
        };
    }

    public static <A extends Enum<A>> Binding.Hold<A> createHoldBinding(A actionType, Collection<? extends Enum<?>> keyTypes, Duration duration) {
        return new Binding.Hold<>() {
            @Override
            public Set<Enum<?>> getKeys() {
                return Set.copyOf(keyTypes);
            }

            @Override
            public Duration getDuration() {
                return duration;
            }

            @Override
            public A getActionType() {
                return actionType;
            }
        };
    }

    public static <A extends Enum<A>> Binding.Chord<A> createChordBinding(A actionType, Collection<? extends Enum<?>> keyTypes) {
        return new Binding.Chord<>() {
            @Override
            public Set<Enum<?>> getKeys() {
                return Set.copyOf(keyTypes);
            }

            @Override
            public A getActionType() {
                return actionType;
            }
        };
    }
}
