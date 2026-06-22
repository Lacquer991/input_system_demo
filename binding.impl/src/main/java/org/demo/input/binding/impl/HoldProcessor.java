package org.demo.input.binding.impl;

import org.demo.input.source.KeyInputEvent;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

class HoldProcessor<ActionType extends Enum<ActionType>> implements BindingProcessor<ActionType> {

    private List<ComboRule<ActionType>> rules = List.of();
    private ProcessorState<ActionType> currentState;

    void setRules(List<ComboRule<ActionType>> rules) {
        reset();
        this.rules = rules.stream()
                .sorted(Comparator.comparingInt(
                        (ComboRule<ActionType> rule) -> rule.requiredKeys().size()).reversed())
                .toList();
    }

    @Override
    public void update(KeyInputEvent<?> event, Set<Enum<?>> pressedKeys, long nowMillis) {
        Optional<ComboRule<ActionType>> match = bestMatch(pressedKeys);

        if (currentState != null && match.isPresent() && currentState.keys().equals(match.get().requiredKeys())) {
            return;
        }

        currentState = match.map(rule -> new ProcessorState<>(
                ProcessorState.Phase.ACTIVE, rule.action(), rule.requiredKeys(), nowMillis + rule.duration().toMillis())).orElse(null);
    }

    private Optional<ComboRule<ActionType>> bestMatch(Set<Enum<?>> pressedKeys) {
        return rules.stream()
                .filter(rule -> isActive(rule, pressedKeys))
                .findFirst();
    }

    private boolean isActive(ComboRule<ActionType> rule, Set<Enum<?>> pressedKeys) {
        if (!pressedKeys.containsAll(rule.requiredKeys())) return false;
        return rule.blockers().stream().noneMatch(pressedKeys::contains);
    }

    @Override
    public Optional<ProcessorState<ActionType>> getCurrentState() {
        return Optional.ofNullable(currentState);
    }

    @Override
    public void reset() {
        currentState = null;
    }
}
