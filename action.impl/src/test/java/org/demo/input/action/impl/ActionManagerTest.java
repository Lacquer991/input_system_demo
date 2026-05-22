package org.demo.input.action.impl;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import org.demo.input.action.Action;
import org.demo.input.action.ActionLayer;
import org.demo.input.action.ActionManager;
import org.demo.input.action.LayerHandle;
import org.demo.input.action.exceptions.ActionDisabledException;
import org.demo.input.action.exceptions.ActionNotFoundException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class ActionManagerTest {

    enum ActionEn {SAVE, SAVE_AS, DELETE, OPEN_MAP}

    @BeforeAll
    static void startFx() {
        try {
            javafx.application.Platform.startup(() -> {
            });
        } catch (IllegalStateException ignored) {
        }
    }

    private ActionManager<ActionEn> manager;

    @BeforeEach
    void setUp() {
        manager = new DefaultActionManagerFactory().create();
    }

    @Test
    void getAction_notFound() {

        assertThrows(ActionNotFoundException.class, () -> manager.getAction(ActionEn.SAVE));
    }

    @Test
    void getAction_found() {
        Action<ActionEn> save = Action.of(ActionEn.SAVE, () -> {
        });
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, save)));

        assertSame(save, manager.getAction(ActionEn.SAVE));
    }

    @Test
    void execute_runs() {
        AtomicBoolean called = new AtomicBoolean();
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> called.set(true)))));
        manager.execute(ActionEn.SAVE);

        assertTrue(called.get());
    }

    @Test
    void execute_disabled() {
        BooleanProperty enabled = new SimpleBooleanProperty(false);
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> {
        }, enabled))));

        assertThrows(ActionDisabledException.class, () -> manager.execute(ActionEn.SAVE));
    }

    @Test
    void topLayer_overrides() {
        Action<ActionEn> base = Action.of(ActionEn.SAVE, () -> {
        });
        Action<ActionEn> upper = Action.of(ActionEn.SAVE, () -> {
        });
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, base)));
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, upper)));

        assertSame(upper, manager.getAction(ActionEn.SAVE));
    }

    @Test
    void fallthrough_toLowerLayer() {
        Action<ActionEn> open = Action.of(ActionEn.OPEN_MAP, () -> {
        });
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.OPEN_MAP, open)));
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> {
        }))));

        assertSame(open, manager.getAction(ActionEn.OPEN_MAP));
    }

    @Test
    void handle_close_removesLayer() {
        LayerHandle handle = manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> {
        }))));

        assertTrue(handle.isActive());
        handle.close();
        assertFalse(handle.isActive());
        assertThrows(ActionNotFoundException.class, () -> manager.getAction(ActionEn.SAVE));
    }

    @Test
    void handle_close_idempotent() {
        LayerHandle handle = manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> {
        }))));
        handle.close();

        assertDoesNotThrow(handle::close);
    }

    @Test
    void handle_close_outOfOrder() {
        Action<ActionEn> saveAs = Action.of(ActionEn.SAVE_AS, () -> {
        });
        Action<ActionEn> delete = Action.of(ActionEn.DELETE, () -> {
        });
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE_AS, saveAs)));
        LayerHandle mid = manager.pushLayer(ActionLayer.of(Map.of(ActionEn.SAVE, Action.of(ActionEn.SAVE, () -> {
        }))));
        manager.pushLayer(ActionLayer.of(Map.of(ActionEn.DELETE, delete)));

        mid.close();

        assertSame(delete, manager.getAction(ActionEn.DELETE));
        assertSame(saveAs, manager.getAction(ActionEn.SAVE_AS));
        assertThrows(ActionNotFoundException.class, () -> manager.getAction(ActionEn.SAVE));
    }

    @Test
    void layer_defensiveCopy() {
        Action<ActionEn> save = Action.of(ActionEn.SAVE, () -> {
        });
        Map<ActionEn, Action<ActionEn>> map = new HashMap<>();
        map.put(ActionEn.SAVE, save);
        ActionLayer<ActionEn> layer = ActionLayer.of(map);
        map.clear();
        manager.pushLayer(layer);

        assertSame(save, manager.getAction(ActionEn.SAVE));
    }
}