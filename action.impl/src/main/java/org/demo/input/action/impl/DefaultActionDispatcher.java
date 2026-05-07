package org.demo.input.action.impl;

import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionManager;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import javafx.application.Platform;

import java.util.function.Consumer;

class DefaultActionDispatcher<ActionType extends Enum<ActionType>> implements ActionDispatcher<ActionType> {

    private final Disposable sub;

    public DefaultActionDispatcher(Publisher<ActionType> actions, ActionManager<ActionType> manager,
                                   boolean marshalToFx, Consumer<Throwable> onError) {

        this.sub = Flux.from(actions).subscribe(actionType -> {
            Runnable runnable = () -> {
                try {
                    manager.execute(actionType);
                } catch (Throwable e) {
                    onError.accept(e);
                }
            };

            if (!marshalToFx) {
                runnable.run();
            } else {
                if (Platform.isFxApplicationThread()) {
                    runnable.run();
                } else {
                    Platform.runLater(runnable);
                }
            }
        }, onError);
    }

    @Override
    public void close() {
        sub.dispose();
    }
}
