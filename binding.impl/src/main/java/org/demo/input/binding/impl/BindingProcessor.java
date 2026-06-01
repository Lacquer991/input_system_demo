package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;
import org.demo.input.source.KeyInputEvent;

import java.util.List;
import java.util.Optional;
import java.util.Set;

interface BindingProcessor<ActionType extends Enum<ActionType>> {

    void setBindings(List<Binding<ActionType>> bindings);

    Optional<ActionType> onEvent(KeyInputEvent<?> event, Set<Enum<?>> pressed);

    void dispose();
}
