package org.demo.input.action.impl;

import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.Scene;
import org.demo.input.action.Action;
import org.demo.input.action.ActionLayer;
import org.demo.input.action.ActionManager;
import org.demo.input.action.LayerHandle;
import org.demo.input.action.exceptions.ActionNotFoundException;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.Map;

final class DefaultActionManager<ActionType extends Enum<ActionType>> implements ActionManager<ActionType> {

    private final Map<Node, Deque<LayerRegistration>> nodeLayers = new IdentityHashMap<>();
    private final Deque<LayerRegistration> transientLayers = new ArrayDeque<>();

    private Scene scene;

    @Override
    public LayerHandle register(Node node, ActionLayer<ActionType> layer) {
        if (scene == null) throw new IllegalStateException("Scene is not bound");

        LayerRegistration registration = new LayerRegistration(node, layer, false);
        registration.install();

        return registration;
    }

    @Override
    public LayerHandle pushTransient(ActionLayer<ActionType> layer) {
        LayerRegistration registration = new LayerRegistration(null, layer, true);
        transientLayers.push(registration);
        registration.active = true;

        return registration;
    }

    @Override
    public void bindScene(Scene scene) {
        this.scene = scene;
    }

    @Override
    public Action<ActionType> getAction(ActionType type) {
        if (scene == null) throw new IllegalStateException("Scene is not bound");


        Action<ActionType> transientAction = findIn(transientLayers, type);
        if (transientAction != null) {
            return transientAction;
        }

        Node current = scene.getFocusOwner();

        if (current == null) {
            current = scene.getRoot();
        }

        while (current != null) {
            Deque<LayerRegistration> layers = nodeLayers.get(current);

            if (layers != null) {
                Action<ActionType> action = findIn(layers, type);

                if (action != null) {
                    return action;
                }
            }

            current = current.getParent();
        }

        throw new ActionNotFoundException(type.name());
    }

    private Action<ActionType> findIn(Iterable<LayerRegistration> registrations, ActionType type) {
        for (LayerRegistration registration : registrations) {
            ActionLayer<ActionType> layer = registration.layer;
            Action<ActionType> action = layer.findAction(type).orElse(null);

            if (action != null) {
                return action;
            }
            if (layer.isExclusive()) {
                throw new ActionNotFoundException(type.name());
            }
        }
        return null;
    }

    private void activate(LayerRegistration registration) {
        if (registration.active) {
            return;
        }

        nodeLayers.computeIfAbsent(registration.node, ignored -> new ArrayDeque<>()).push(registration);
        registration.active = true;
    }

    private void deactivate(LayerRegistration registration) {
        if (!registration.active) {
            return;
        }

        Deque<LayerRegistration> registrations = nodeLayers.get(registration.node);
        if (registrations != null) {
            registrations.removeFirstOccurrence(registration);
            if (registrations.isEmpty()) {
                nodeLayers.remove(registration.node);
            }
        }
        registration.active = false;
    }

    private final class LayerRegistration implements LayerHandle {

        private final Node node;
        private final ActionLayer<ActionType> layer;
        private final boolean transientLayer;

        private boolean active;
        private boolean closed;

        private final ChangeListener<Scene> sceneListener = (observable, oldScene, newScene) -> refresh();

        private LayerRegistration(Node node, ActionLayer<ActionType> layer, boolean transientLayer) {
            this.node = node;
            this.layer = layer;
            this.transientLayer = transientLayer;
        }

        private void install() {
            if (transientLayer) {
                return;
            }
            node.sceneProperty().addListener(sceneListener);
            refresh();
        }

        private void refresh() {
            if (closed || transientLayer) {
                return;
            }

            boolean shouldBeActive = node.getScene() == scene;

            if (shouldBeActive) {
                activate(this);
            } else {
                deactivate(this);
            }
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }

            closed = true;

            if (transientLayer) {
                transientLayers.removeFirstOccurrence(this);
                active = false;
                return;
            }

            node.sceneProperty().removeListener(sceneListener);
            deactivate(this);
        }

        @Override
        public boolean isActive() {
            return active && !closed;
        }
    }
}