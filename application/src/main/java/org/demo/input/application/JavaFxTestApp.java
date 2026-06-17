package org.demo.input.application;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.demo.input.action.Action;
import org.demo.input.action.ActionDispatcher;
import org.demo.input.action.ActionLayer;
import org.demo.input.action.ActionManager;
import org.demo.input.action.ActionUtils;
import org.demo.input.action.LayerHandle;
import org.demo.input.action.exceptions.ActionNotFoundException;
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

    private Stage stage;
    private Scene scene;

    private ActionManager<ActionType> actionManager;

    private LayerHandle rootLayerHandle;
    private LayerHandle editorLayerHandle;
    private LayerHandle mapLayerHandle;
    private LayerHandle popupLayerHandle;

    private final SimpleBooleanProperty canDelete = new SimpleBooleanProperty(true);

    private BorderPane root;
    private HBox workspace;
    private VBox editorPane;
    private VBox mapPane;

    private TextArea editorArea;
    private TextArea mapArea;
    private TextArea log;

    private Label status;

    private Button saveBtn;
    private Button saveAsBtn;
    private Button deleteBtn;
    private Button openMapBtn;

    private Button showPopupBtn;
    private Button closePopupBtn;

    private Popup popup;
    private Node focusBeforePopup;

    @Override
    public void start(Stage stage) {
        this.stage = stage;

        BindingImplProvider provider = BindingServiceLocator.getBindingImplProvider();
        loop = Schedulers.newSingle("input-loop");

        root = new BorderPane();
        root.setId("root");
        root.setFocusTraversable(true);

        scene = new Scene(root, 1100, 720);

        KeyInputSource<KeyType> inputSource = new JavaFxKeyInputSource(scene);
        BindingService<ActionType> bindingService = provider.createBindingService();
        ActionPublisher<ActionType> actionPublisher = provider.createPublisher(inputSource, bindingService, loop);

        configureBindings(bindingService);
        buildUi();

        actionManager = ActionManager.create();
        actionManager.bindScene(scene);

        registerRootLayer();
        registerSceneGraphLayers();

        Flux<ActionType> emittedActions = Flux.from(actionPublisher.getActionPublisher()).doOnNext(type -> log("EMIT  : " + type));
        dispatcher = ActionDispatcher.bind(emittedActions, actionManager, true, this::handleError);

        events = Flux.from(inputSource.getEventPublisher()).subscribe(event -> log("EVENT : " + event));

        scene.focusOwnerProperty().addListener((observable, oldNode, newNode) -> {
            refreshActionButtons();
            updateStatus();
        });

        stage.setScene(scene);
        stage.setTitle("InputJavaFxTestApp");
        stage.show();

        Platform.runLater(() -> {
            root.requestFocus();
            refreshActionButtons();
            updateStatus();
        });
    }

    private void configureBindings(BindingService<ActionType> bindingService) {
        bindingService.setBindings(List.of(
                Bindings.createChordBinding(ActionType.SAVE, EnumSet.of(KeyType.CTRL, KeyType.S)),
                Bindings.createHoldBinding(ActionType.SAVE_AS, EnumSet.of(KeyType.CTRL, KeyType.S), Duration.ofMillis(500)),
                Bindings.createHoldBinding(ActionType.DELETE, EnumSet.of(KeyType.CTRL, KeyType.S, KeyType.Q), Duration.ofMillis(500)),
                Bindings.createTapBinding(ActionType.OPEN_MAP, KeyType.M, Duration.ofMillis(300)),
                Bindings.createTapBinding(ActionType.SELECT_HOME_POINT, KeyType.H, Duration.ofMillis(300)),
                Bindings.createDoubleTapBinding(ActionType.GO_TO_HOME_POINT, KeyType.H, Duration.ofMillis(200), Duration.ofMillis(300))
        ));
    }

    private void buildUi() {
        log = new TextArea();
        log.setEditable(false);
        log.setFocusTraversable(false);
        log.setPrefRowCount(14);

        editorArea = new TextArea();
        editorArea.setId("editorArea");
        editorArea.setPromptText("Editor: Ctrl+S = SAVE");
        editorArea.setPrefRowCount(10);
        editorArea.setFocusTraversable(true);

        mapArea = new TextArea();
        mapArea.setId("mapArea");
        mapArea.setPromptText("Map: Ctrl+S = SAVE, M/H = actions");
        mapArea.setPrefRowCount(10);
        mapArea.setFocusTraversable(true);

        editorPane = createContextPane("editorPane", "Editor", editorArea);
        mapPane = createContextPane("mapPane", "Map", mapArea);

        workspace = new HBox(10, editorPane, mapPane);
        workspace.setPadding(new Insets(10));

        root.setTop(buildControls());
        root.setCenter(workspace);
        root.setBottom(log);
    }

    private VBox createContextPane(String id, String title, Node content) {
        Label label = new Label(title);
        VBox pane = new VBox(6, label, content);
        pane.setId(id);
        pane.setPadding(new Insets(10));
        pane.setPrefWidth(520);
        pane.setStyle("-fx-border-color: #999; -fx-border-radius: 4; -fx-background-color: #fafafa;");
        return pane;
    }

    private VBox buildControls() {
        status = new Label();

        saveBtn = controlButton("Save");
        saveAsBtn = controlButton("Save As");
        deleteBtn = controlButton("Delete");
        openMapBtn = controlButton("Open Map");

        Button focusRootBtn = controlButton("Root");
        Button focusEditorBtn = controlButton("Editor");
        Button focusMapBtn = controlButton("Map");

        Button removeEditorBtn = controlButton("Remove editor");
        Button restoreEditorBtn = controlButton("Restore editor");

        showPopupBtn = controlButton("Show popup");
        closePopupBtn = controlButton("Close popup");
        closePopupBtn.setDisable(true);

        Button toggleDeleteBtn = controlButton("Toggle Delete");
        Button clearLogBtn = controlButton("Clear");

        focusRootBtn.setOnAction(event -> root.requestFocus());
        focusEditorBtn.setOnAction(event -> editorArea.requestFocus());
        focusMapBtn.setOnAction(event -> mapArea.requestFocus());

        removeEditorBtn.setOnAction(event -> removeEditorNode());
        restoreEditorBtn.setOnAction(event -> restoreEditorNode());

        showPopupBtn.setOnAction(event -> showTransientPopup());
        closePopupBtn.setOnAction(event -> closeTransientPopup());

        toggleDeleteBtn.setOnAction(event -> {
            canDelete.set(!canDelete.get());
            log("DELETE: " + canDelete.get());
            refreshActionButtons();
        });

        clearLogBtn.setOnAction(event -> log.clear());

        HBox actionRow = new HBox(8, saveBtn, saveAsBtn, deleteBtn, openMapBtn, toggleDeleteBtn);
        HBox focusRow = new HBox(8, focusRootBtn, focusEditorBtn, focusMapBtn);
        HBox nodeRow = new HBox(8, removeEditorBtn, restoreEditorBtn);
        HBox popupRow = new HBox(8, showPopupBtn, closePopupBtn, clearLogBtn);

        actionRow.setAlignment(Pos.CENTER_LEFT);
        focusRow.setAlignment(Pos.CENTER_LEFT);
        nodeRow.setAlignment(Pos.CENTER_LEFT);
        popupRow.setAlignment(Pos.CENTER_LEFT);

        VBox controls = new VBox(8, status, actionRow, focusRow, nodeRow, popupRow);
        controls.setPadding(new Insets(10));
        controls.setStyle("-fx-border-color: #ccc; -fx-border-width: 0 0 1 0;");
        return controls;
    }

    private Button controlButton(String text) {
        Button button = new Button(text);
        button.setFocusTraversable(false);
        return button;
    }

    private void registerRootLayer() {
        ActionLayer<ActionType> rootLayer = ActionLayer.of(Map.of(
                ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION: [root] SAVE")),
                ActionType.SAVE_AS, Action.of(ActionType.SAVE_AS, () -> log("ACTION: [root] SAVE_AS")),
                ActionType.DELETE, Action.of(ActionType.DELETE, () -> log("ACTION: [root] DELETE"), canDelete),
                ActionType.OPEN_MAP, Action.of(ActionType.OPEN_MAP, () -> log("ACTION: [root] OPEN_MAP")),
                ActionType.SELECT_HOME_POINT, Action.of(ActionType.SELECT_HOME_POINT, () -> log("ACTION: [root] SELECT_HOME_POINT")),
                ActionType.GO_TO_HOME_POINT, Action.of(ActionType.GO_TO_HOME_POINT, () -> log("ACTION: [root] GO_TO_HOME_POINT"))
        ));

        rootLayerHandle = actionManager.register(root, rootLayer);
    }

    private void registerSceneGraphLayers() {
        ActionLayer<ActionType> editorLayer = ActionLayer.of(Map.of(
                ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION: [editor] SAVE")),
                ActionType.SAVE_AS, Action.of(ActionType.SAVE_AS, () -> log("ACTION: [editor] SAVE_AS")),
                ActionType.DELETE, Action.of(ActionType.DELETE, () -> log("ACTION: [editor] DELETE"), canDelete)
        ));

        ActionLayer<ActionType> mapLayer = ActionLayer.of(Map.of(
                ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION: [map] SAVE")),
                ActionType.OPEN_MAP, Action.of(ActionType.OPEN_MAP, () -> log("ACTION: [map] OPEN_MAP")),
                ActionType.SELECT_HOME_POINT, Action.of(ActionType.SELECT_HOME_POINT, () -> log("ACTION: [map] SELECT_HOME_POINT")),
                ActionType.GO_TO_HOME_POINT, Action.of(ActionType.GO_TO_HOME_POINT, () -> log("ACTION: [map] GO_TO_HOME_POINT"))
        ));

        editorLayerHandle = actionManager.register(editorPane, editorLayer);
        mapLayerHandle = actionManager.register(mapPane, mapLayer);
    }

    private void showTransientPopup() {
        if (popupLayerHandle != null && popupLayerHandle.isActive()) {
            return;
        }

        focusBeforePopup = scene.getFocusOwner();

        ActionLayer<ActionType> popupLayer = ActionLayer.of(Map.of(
                ActionType.SAVE, Action.of(ActionType.SAVE, () -> log("ACTION: [popup] SAVE")),
                ActionType.SAVE_AS, Action.of(ActionType.SAVE_AS, () -> log("ACTION: [popup] SAVE_AS")),
                ActionType.DELETE, Action.of(ActionType.DELETE, () -> log("ACTION: [popup] DELETE"), canDelete)
        ), true);

        popupLayerHandle = actionManager.pushTransient(popupLayer);

        Label title = new Label("Popup active");
        Label hint = new Label("Ctrl+S is handled here.\nM/H blocked.");
        Button closeBtn = controlButton("Close popup");
        closeBtn.setOnAction(event -> closeTransientPopup());

        VBox popupContent = new VBox(8, title, hint, closeBtn);
        popupContent.setPadding(new Insets(12));
        popupContent.setStyle("-fx-background-color: white; -fx-border-color: #444; -fx-border-radius: 4; -fx-background-radius: 4;");

        popup = new Popup();
        popup.setAutoHide(false);
        popup.getContent().setAll(popupContent);
        popup.show(stage, stage.getX() + 80, stage.getY() + 140);

        log("POPUP: opened");

        if (focusBeforePopup != null) {
            focusBeforePopup.requestFocus();
        } else {
            root.requestFocus();
        }

        showPopupBtn.setDisable(true);
        closePopupBtn.setDisable(false);
        refreshActionButtons();
        updateStatus();
    }

    private void closeTransientPopup() {
        if (popup != null) {
            popup.hide();
            popup = null;
        }

        if (popupLayerHandle != null) {
            popupLayerHandle.close();
            popupLayerHandle = null;
        }

        log("POPUP: closed");

        showPopupBtn.setDisable(false);
        closePopupBtn.setDisable(true);

        if (focusBeforePopup != null && focusBeforePopup.getScene() == scene) {
            focusBeforePopup.requestFocus();
        } else {
            root.requestFocus();
        }

        refreshActionButtons();
        updateStatus();
    }

    private void removeEditorNode() {
        if (!workspace.getChildren().contains(editorPane)) {
            return;
        }

        workspace.getChildren().remove(editorPane);
        root.requestFocus();

        log("EDITOR: removed");
        log("EDITOR active: " + editorLayerHandle.isActive());

        refreshActionButtons();
        updateStatus();
    }

    private void restoreEditorNode() {
        if (workspace.getChildren().contains(editorPane)) {
            return;
        }

        workspace.getChildren().add(0, editorPane);
        editorArea.requestFocus();

        log("EDITOR: restored");
        log("EDITOR active: " + editorLayerHandle.isActive());

        refreshActionButtons();
        updateStatus();
    }

    private void refreshActionButtons() {
        configureButtonIfActionExists(saveBtn, ActionType.SAVE, "Save");
        configureButtonIfActionExists(saveAsBtn, ActionType.SAVE_AS, "Save As");
        configureButtonIfActionExists(deleteBtn, ActionType.DELETE, "Delete");
        configureButtonIfActionExists(openMapBtn, ActionType.OPEN_MAP, "Open Map");
    }

    private void configureButtonIfActionExists(Button button, ActionType type, String text) {
        if (button == null || actionManager == null) {
            return;
        }

        try {
            Action<ActionType> action = actionManager.getAction(type);
            ActionUtils.configure(action, button);
            button.setText(text);
            button.setFocusTraversable(false);
        } catch (ActionNotFoundException exception) {
            ActionUtils.unconfigure(button);
            button.setText(text + " off");
            button.setDisable(true);
            button.setFocusTraversable(false);
        }
    }

    private void updateStatus() {
        if (status == null || scene == null) {
            return;
        }
        status.setText("Focus: " + describeNode(scene.getFocusOwner()) + " | popup=" + active(popupLayerHandle));
    }

    private boolean active(LayerHandle handle) {
        return handle != null && handle.isActive();
    }

    private String describePath(Node node) {
        if (node == null) {
            return "null";
        }

        StringBuilder builder = new StringBuilder();
        Node current = node;

        while (current != null) {
            if (!builder.isEmpty()) {
                builder.append(" -> ");
            }

            builder.append(describeNode(current));
            current = current.getParent();
        }

        return builder.toString();
    }

    private String describeNode(Node node) {
        String id = node.getId();
        String name = node.getClass().getSimpleName();

        if (id == null || id.isBlank()) {
            return name;
        }

        return name + "#" + id;
    }

    private void handleError(Throwable throwable) {
        if (throwable instanceof ActionNotFoundException) {
            log("SKIP  : " + throwable.getMessage());
            return;
        }

        log("ERROR : " + throwable.getClass().getSimpleName() + ": " + throwable.getMessage());
    }

    private void log(String message) {
        if (Platform.isFxApplicationThread()) {
            log.appendText(message + "\n");
        } else {
            Platform.runLater(() -> log.appendText(message + "\n"));
        }
    }

    @Override
    public void stop() {
        if (rootLayerHandle != null) {
            rootLayerHandle.close();
        }

        if (editorLayerHandle != null) {
            editorLayerHandle.close();
        }

        if (mapLayerHandle != null) {
            mapLayerHandle.close();
        }

        if (popupLayerHandle != null) {
            popupLayerHandle.close();
        }

        if (popup != null) {
            popup.hide();
        }

        if (events != null) {
            events.dispose();
        }

        if (dispatcher != null) {
            dispatcher.close();
        }

        if (loop != null) {
            loop.dispose();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}