package org.demo.input.action.impl;

import javafx.application.Platform;
import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

final class DefaultActionDispatcher<ActionType extends Enum<ActionType>> implements ActionDispatcher {

    private final Disposable subscription;

    DefaultActionDispatcher(Publisher<ActionType> publisher, ActionManager<ActionType> manager, boolean marshalToFx, Consumer<Throwable> onError) {

        this.subscription = Flux.from(publisher).subscribe(type -> dispatch(type, manager, marshalToFx, onError), onError);
    }

    private void dispatch(ActionType type, ActionManager<ActionType> manager, boolean marshalToFx, Consumer<Throwable> onError) {
        Runnable run = () -> {
            try {
                manager.execute(type);
            } catch (Throwable t) {
                onError.accept(t);
            }
        };

        if (marshalToFx && !Platform.isFxApplicationThread()) {
            Platform.runLater(run);
        } else {
            run.run();
        }
    }

    @Override
    public void close() {
        subscription.dispose();
    }
}