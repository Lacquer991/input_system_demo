package org.demo.input.action.impl;

import org.demo.input.action.Action;
import org.demo.input.action.ActionLayer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ActionLayerTest {

    enum ActionEn {SAVE, SAVE_AS}

    @BeforeAll
    static void startFx() {
        try {
            javafx.application.Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
    }

    @Test
    void of_found() {
        Action<ActionEn> save = Action.of(ActionEn.SAVE, () -> {
        });
        ActionLayer<ActionEn> layer = ActionLayer.of(Map.of(ActionEn.SAVE, save));

        assertSame(save, layer.findAction(ActionEn.SAVE).orElseThrow());
    }

    @Test
    void of_notFound() {
        ActionLayer<ActionEn> layer = ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> {
        })));

        assertTrue(layer.findAction(ActionEn.SAVE_AS).isEmpty());
    }

    @Test
    void of_defensiveCopy() {
        Action<ActionEn> save = Action.of(ActionEn.SAVE, () -> {
        });
        Map<ActionEn, Action<ActionEn>> map = new HashMap<>();
        map.put(ActionEn.SAVE, save);
        ActionLayer<ActionEn> layer = ActionLayer.of(map);
        map.clear();

        assertSame(save, layer.findAction(ActionEn.SAVE).orElseThrow());
    }

    @Test
    void lambda_customLookup() {
        Action<ActionEn> save = Action.of(ActionEn.SAVE, () -> {
        });
        ActionLayer<ActionEn> layer = type -> type == ActionEn.SAVE
                ? Optional.of(save)
                : Optional.empty();

        assertSame(save, layer.findAction(ActionEn.SAVE).orElseThrow());
        assertTrue(layer.findAction(ActionEn.SAVE_AS).isEmpty());
    }
}