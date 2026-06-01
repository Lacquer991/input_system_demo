package org.demo.input.application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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

    private LayerHandle editorLayerHandle;
    private LayerHandle dialogLayerHandle;

    private ActionManager<ActionType> rootManager;
    private ActionManager<ActionType> editorManager;
    private ActionManager<ActionType> dialogManager;

    private final SimpleBooleanProperty canDelete = new SimpleBooleanProperty(true);

    private TextArea log;
    private Label hierarchyStatus;

    private Button saveBtn;
    private Button deleteBtn;
    private Button pushEditor;
    private Button popEditor;
    private Button pushDialog;
    private Button popDialog;

    @Override
    public void start(Stage stage) {
        log = new TextArea();
        log.setEditable(false);
        log.setFocusTraversable(false);

        hierarchyStatus = new Label();

        BindingImplProvider provider = BindingServiceLocator.getBindingImplProvider();
        loop = Schedulers.newSingle("input-loop");

        BorderPane root = new BorderPane();
        Scene scene = new Scene(root, 960, 640);

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

        rootManager = ActionManager.create();
        rootManager.pushLayer(ActionLayer.of(Map.of(
                ActionType.OPEN_MAP, Action.of(ActionType.OPEN_MAP, () -> log("ACTION [root] OPEN_MAP")),
                ActionType.SELECT_HOME_POINT, Action.of(ActionType.SELECT_HOME_POINT, () -> log("ACTION [root] SELECT_HOME_POINT")),
                ActionType.GO_TO_HOME_POINT, Action.of(ActionType.GO_TO_HOME_POINT, () -> log("ACTION [root] GO_TO_HOME_POINT")),
                ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION [root] SAVE")),
                ActionType.SAVE_AS, Action.of(ActionType.SAVE_AS, () -> log("ACTION [root] SAVE_AS")),
                ActionType.DELETE, Action.of(ActionType.DELETE, () -> log("ACTION [root] DELETE"), canDelete)
        )));

        editorManager = rootManager.createChild();
        dialogManager = editorManager.createChild();

        dispatcher = ActionDispatcher.bind(
                actionPublisher.getActionPublisher(),
                rootManager,
                true,
                err -> {
                    log("ERROR: " + err.getMessage());

                }
        );

        root.setTop(buildControls());
        root.setCenter(log);
        root.setFocusTraversable(true);

        stage.setScene(scene);
        stage.setTitle("InputJavaFxTestApp");
        stage.show();
        Platform.runLater(root::requestFocus);
        updateStatus();
    }

    private VBox buildControls() {
        saveBtn = ActionUtils.createButton(rootManager.getAction(ActionType.SAVE), "Save (CTRL+S)");
        deleteBtn = ActionUtils.createButton(rootManager.getAction(ActionType.DELETE), "Delete");
        Button openMapBtn = ActionUtils.createButton(rootManager.getAction(ActionType.OPEN_MAP), "Open Map (M)");

        Button toggleDeleteBtn = new Button("Toggle Delete enabled");
        toggleDeleteBtn.setOnAction(e -> {
            canDelete.set(!canDelete.get());
            log("canDelete: " + canDelete.get());
        });

        HBox actionButtons = new HBox(8, saveBtn, deleteBtn, openMapBtn, toggleDeleteBtn);
        actionButtons.setAlignment(Pos.CENTER_LEFT);

        pushEditor = new Button("Push: editor layer");
        popEditor = new Button("Pop: editor layer");
        popEditor.setDisable(true);

        pushEditor.setOnAction(e -> {
            if (editorLayerHandle != null && editorLayerHandle.isActive()) return;

            editorLayerHandle = editorManager.pushLayer(ActionLayer.of(Map.of(
                    ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION [editor] SAVE")),
                    ActionType.DELETE, Action.of(ActionType.DELETE, () -> log("ACTION [editor] DELETE"), canDelete)
            )));

            ActionUtils.configure(rootManager.getAction(ActionType.SAVE), saveBtn);
            ActionUtils.configure(rootManager.getAction(ActionType.DELETE), deleteBtn);

            log("editor layer добавлен");
            pushEditor.setDisable(true);
            popEditor.setDisable(false);
            updateStatus();
        });

        popEditor.setOnAction(e -> {
            if (dialogLayerHandle != null && dialogLayerHandle.isActive()) {
                dialogLayerHandle.close();
                dialogLayerHandle = null;
                pushDialog.setDisable(false);
                popDialog.setDisable(true);
                log("dialog layer снят (вместе с editor)");
            }
            if (editorLayerHandle != null) {
                editorLayerHandle.close();
                editorLayerHandle = null;
            }
            ActionUtils.configure(rootManager.getAction(ActionType.SAVE), saveBtn);
            ActionUtils.configure(rootManager.getAction(ActionType.DELETE), deleteBtn);

            log("editor layer снят");
            pushEditor.setDisable(false);
            popEditor.setDisable(true);
            updateStatus();
        });

        pushDialog = new Button("Push: dialog layer");
        popDialog = new Button("Pop: dialog layer");
        popDialog.setDisable(true);

        pushDialog.setOnAction(e -> {
            if (dialogLayerHandle != null && dialogLayerHandle.isActive()) return;

            dialogLayerHandle = dialogManager.pushLayer(ActionLayer.of(Map.of(
                    ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION [dialog] SAVE: подтвердить"))
            )));

            ActionUtils.configure(rootManager.getAction(ActionType.SAVE), saveBtn);

            log("dialog layer добавлен");
            pushDialog.setDisable(true);
            popDialog.setDisable(false);
            updateStatus();
        });

        popDialog.setOnAction(e -> {
            if (dialogLayerHandle != null) {
                dialogLayerHandle.close();
                dialogLayerHandle = null;
            }
            ActionUtils.configure(rootManager.getAction(ActionType.SAVE), saveBtn);

            log("dialog layer снят");
            pushDialog.setDisable(false);
            popDialog.setDisable(true);
            updateStatus();
        });

        Button clearLog = new Button("Очистить логи");
        clearLog.setOnAction(e -> log.clear());

        HBox editorRow = new HBox(8, new Label("editorManager:"), pushEditor, popEditor);
        HBox dialogRow = new HBox(8, new Label("dialogManager:"), pushDialog, popDialog);
        editorRow.setAlignment(Pos.CENTER_LEFT);
        dialogRow.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(8,
                hierarchyStatus,
                new Label(""),
                actionButtons,
                new Label(""),
                editorRow,
                dialogRow,
                clearLog
        );
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");
        return controls;
    }

    private void updateStatus() {
        var sb = new StringBuilder("Current: root");
        if (editorLayerHandle != null && editorLayerHandle.isActive()) {
            sb.append(" -> editor");
        }
        if (dialogLayerHandle != null && dialogLayerHandle.isActive()) {
            sb.append(" -> dialog");
        }
        hierarchyStatus.setText(sb.toString());
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