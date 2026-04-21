package org.demo.input.application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.stage.Stage;
import org.demo.input.application.source.JavaFxKeyInputSource;
import org.demo.input.application.source.KeyType;
import org.demo.input.binding.ActionPublisher;
import org.demo.input.binding.BindingService;
import org.demo.input.binding.BindingServiceLocator;
import org.demo.input.binding.Bindings;
import org.demo.input.binding.spi.BindingImplProvider;
import org.demo.input.source.KeyInputSource;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.EnumSet;
import java.util.List;

public class JavaFxTestApp extends Application {

    private Scheduler loop;

    private Disposable events;

    private Disposable actions;


    @Override
    public void start(Stage stage) {
        TextArea log = new TextArea();
        log.setEditable(false);
        log.setFocusTraversable(false);

        BorderPane root = new BorderPane(log);
        root.setFocusTraversable(true);

        Scene scene = new Scene(root, 900, 500);
        stage.setScene(scene);
        stage.setTitle("Input System Test");
        stage.show();

        Platform.runLater(root::requestFocus);

        BindingImplProvider provider = BindingServiceLocator.getBindingImplProvider();
        loop = Schedulers.newSingle("input-loop");

        KeyInputSource<KeyType> inputSource = new JavaFxKeyInputSource(scene);

        BindingService<ActionType> bindingService = provider.createBindingService();

        ActionPublisher<ActionType> actionPublisher = provider.createPublisher(inputSource, bindingService, loop);

        events = Flux.from(inputSource.getEventPublisher())
                .subscribe(e -> Platform.runLater(() -> log.appendText("EVENT: " + e + "\n")));

        actions = Flux.from(actionPublisher.getActionPublisher())
                .subscribe(a -> Platform.runLater(() -> log.appendText("ACTION: " + a + "\n")));

        bindingService.setBindings(List.of(
                Bindings.createChordBinding(ActionType.SAVE, EnumSet.of(KeyType.CTRL, KeyType.S)),
                Bindings.createHoldBinding(ActionType.SAVE_AS, EnumSet.of(KeyType.CTRL, KeyType.S), Duration.ofMillis(500)),
                Bindings.createHoldBinding(ActionType.DELETE, EnumSet.of(KeyType.CTRL, KeyType.S, KeyType.Q), Duration.ofMillis(500)),
                Bindings.createTapBinding(ActionType.OPEN_MAP, KeyType.M, Duration.ofMillis(300)),
                Bindings.createTapBinding(ActionType.SELECT_HOME_POINT, KeyType.H, Duration.ofMillis(300)),
                Bindings.createDouleTapBinding(ActionType.GO_TO_HOME_POINT, KeyType.H, Duration.ofMillis(200), Duration.ofMillis(300))
        ));
    }

    public void stop(){
        if (events != null) events.dispose();
        if (actions != null) actions.dispose();
        if (loop != null) loop.dispose();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
