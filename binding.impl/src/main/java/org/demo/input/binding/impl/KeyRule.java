package org.demo.input.binding.impl;

import org.demo.input.binding.Binding;

record KeyRule<ActionType extends Enum<ActionType>>(
        Binding.Tap<ActionType> tap,
        Binding.DoubleTap<ActionType> doubleTap
) {
}
