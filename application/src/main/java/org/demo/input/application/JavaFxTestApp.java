package org.demo.input.application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.demo.input.action.*;
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
import java.util.Map;

public class JavaFxTestApp extends Application {

    private Scheduler loop;
    private Disposable events;
    private ActionDispatcher dispatcher;

    private LayerHandle editorHandle;
    private LayerHandle dialogHandle;

    private TextArea log;
    private Label layerStatus;

    @Override
    public void start(Stage stage) {
        log = new TextArea();
        log.setEditable(false);
        log.setFocusTraversable(false);

        layerStatus = new Label("Активные слои: [base]");

        BindingImplProvider provider = BindingServiceLocator.getBindingImplProvider();
        loop = Schedulers.newSingle("input-loop");

        BorderPane root = new BorderPane();
        Scene scene = new Scene(root, 900, 600);

        KeyInputSource<KeyType> inputSource = new JavaFxKeyInputSource(scene);
        BindingService<ActionType> bindingService = provider.createBindingService();
        ActionPublisher<ActionType> actionPublisher = provider.createPublisher(inputSource, bindingService, loop);

        events = Flux.from(inputSource.getEventPublisher())
                .subscribe(e -> log("EVENT : " + e));

        bindingService.setBindings(List.of(
                Bindings.createChordBinding(ActionType.SAVE, EnumSet.of(KeyType.CTRL, KeyType.S)),
                Bindings.createHoldBinding(ActionType.SAVE_AS, EnumSet.of(KeyType.CTRL, KeyType.S), Duration.ofMillis(500)),
                Bindings.createHoldBinding(ActionType.DELETE, EnumSet.of(KeyType.CTRL, KeyType.S, KeyType.Q), Duration.ofMillis(500)),
                Bindings.createTapBinding(ActionType.OPEN_MAP, KeyType.M, Duration.ofMillis(300)),
                Bindings.createTapBinding(ActionType.SELECT_HOME_POINT, KeyType.H, Duration.ofMillis(300)),
                Bindings.createDouleTapBinding(ActionType.GO_TO_HOME_POINT, KeyType.H, Duration.ofMillis(200), Duration.ofMillis(300))
        ));

        ActionManager<ActionType> manager = ActionManager.create();

        manager.pushLayer(ActionLayer.of(Map.of(
                ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION [base] SAVE: глобальное сохранение")),
                ActionType.SAVE_AS, Action.of(ActionType.SAVE_AS, () -> log("ACTION [base] SAVE_AS: сохранить как")),
                ActionType.DELETE, Action.of(ActionType.DELETE, () -> log("ACTION [base] DELETE: удаление")),
                ActionType.OPEN_MAP, Action.of(ActionType.OPEN_MAP, () -> log("ACTION [base] OPEN_MAP: открыта карта")),
                ActionType.SELECT_HOME_POINT, Action.of(ActionType.SELECT_HOME_POINT, () -> log("ACTION [base] SELECT_HOME_POINT: точка выбрана")),
                ActionType.GO_TO_HOME_POINT, Action.of(ActionType.GO_TO_HOME_POINT, () -> log("ACTION [base] GO_TO_HOME_POINT: переход домой"))
        )));

        dispatcher = ActionDispatcher.bind(
                actionPublisher.getActionPublisher(),
                manager,
                true,
                err -> log("ERROR: " + err.getMessage())
        );

        root.setTop(buildControls(manager));
        root.setCenter(log);
        root.setFocusTraversable(true);

        stage.setScene(scene);
        stage.setTitle("InputJavaFxTestApp");
        stage.show();
        Platform.runLater(root::requestFocus);
    }

    private VBox buildControls(ActionManager<ActionType> manager) {
        Button pushEditor = new Button("Добавить: second layer");
        Button popEditor = new Button("Удалить: second layer");
        popEditor.setDisable(true);

        pushEditor.setOnAction(e -> {
            if (editorHandle != null && editorHandle.isActive()) return;
            editorHandle = manager.pushLayer(ActionLayer.of(Map.of(
                    ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION [second] SAVE: сохранить документ")),
                    ActionType.SAVE_AS, Action.of(ActionType.SAVE_AS, () -> log("ACTION [second] SAVE_AS: сохранить копию")),
                    ActionType.DELETE, Action.of(ActionType.DELETE, () -> log("ACTION [second] DELETE: удалить выделенное"))
            )));
            log("Editor layer добавлен (SAVE/SAVE_AS/DELETE переопределены)");
            pushEditor.setDisable(true);
            popEditor.setDisable(false);
            updateStatus();
        });

        popEditor.setOnAction(e -> {
            if (editorHandle != null) {
                editorHandle.close();
                editorHandle = null;
                log("Editor layer удален (вернулись к base)");
            }
            pushEditor.setDisable(false);
            popEditor.setDisable(true);
            updateStatus();
        });

        Button pushDialog = new Button("Добавить: third layer");
        Button popDialog = new Button("Удалить: third layer");
        popDialog.setDisable(true);

        pushDialog.setOnAction(e -> {
            if (dialogHandle != null && dialogHandle.isActive()) return;
            dialogHandle = manager.pushLayer(ActionLayer.of(Map.of(
                    ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION [third] SAVE: подтвердить диалог")),
                    ActionType.DELETE, Action.of(ActionType.DELETE, () -> {
                    }, new SimpleBooleanProperty(false))
            )));
            log("third layer добавлен (SAVE = подтвердить, DELETE заблокирован)");
            pushDialog.setDisable(true);
            popDialog.setDisable(false);
            updateStatus();
        });

        popDialog.setOnAction(e -> {
            if (dialogHandle != null) {
                dialogHandle.close();
                dialogHandle = null;
                log("third layer удален");
            }
            pushDialog.setDisable(false);
            popDialog.setDisable(true);
            updateStatus();
        });

        Button clearLog = new Button("Очистить логи");
        clearLog.setOnAction(e -> log.clear());

        HBox editorRow = new HBox(8, new Label("Second:"), pushEditor, popEditor);
        HBox dialogRow = new HBox(8, new Label("Third:"), pushDialog, popDialog);
        editorRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        dialogRow.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox controls = new VBox(6, layerStatus, editorRow, dialogRow, clearLog);
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");
        return controls;
    }

    private void updateStatus() {
        var sb = new StringBuilder("Активные слои: [base]");
        if (editorHandle != null && editorHandle.isActive()) sb.append(" + [second]");
        if (dialogHandle != null && dialogHandle.isActive()) sb.append(" + [third]");
        layerStatus.setText(sb.toString());
    }

    private void log(String msg) {
        if (Platform.isFxApplicationThread()) {
            log.appendText(msg + "\n");
        } else {
            Platform.runLater(() -> log.appendText(msg + "\n"));
        }
    }

    @Override
    public void stop() {
        if (events != null) events.dispose();
        if (dispatcher != null) dispatcher.close();
        if (loop != null) loop.dispose();
    }

    public static void main(String[] args) {
        launch(args);
    }
}