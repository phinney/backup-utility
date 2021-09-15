/*
 * Copyright 2021 PhinneyRidge.com
 *        Licensed under the Apache License, Version 2.0 (the "License");
 *        you may not use this file except in compliance with the License.
 *        You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *        Unless required by applicable law or agreed to in writing, software
 *        distributed under the License is distributed on an "AS IS" BASIS,
 *        WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *        See the License for the specific language governing permissions and
 *        limitations under the License.
 */
package com.phinneyridge.tools.backup;


import com.sun.javafx.runtime.VersionInfo;
import com.sun.javafx.scene.paint.MaterialHelper;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.Match;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javafx.stage.StageStyle.TRANSPARENT;

public class UIManager implements UI {

    private Stage stage;
    private Parent root;
    private VBox pageContainer;

    private com.phinneyridge.tools.backup.PageNode currentPageNode;
    private String currentPageNodeName;
    private Node currentNode;


    private MenuBar menubar;
    //private ProgressBar progressBar;

    private String title = "Backup";

    Map<String, NodeTuple> pageNodeMap;

    private class NodeTuple {
        Node node;
        PageNode pageNode;
    }

    private String password;



    Class initialPageSceneClass = FileSelectionNode.class;

    public UIManager(Stage stage) {
        this.stage = stage;
        pageNodeMap = new HashMap<>();
        stage.setMinHeight(200);
        stage.setMinWidth(200);
        stage.getIcons().add(new Image(FileSelectionNode.class.getClassLoader().getResourceAsStream("images/BackupIcon.png")));
        try {
            root = FXMLLoader.load(getClass().getResource("/layouts/root.fxml"));
        } catch (Exception e) {
            showThrowable("Fatal Error","error loading root layout", e);
            stage.close();
        }
        stage.setTitle(title);
        stage.setScene(new Scene(root));
        menubar = (MenuBar) root.lookup("#menubar");
        pageContainer = (VBox) root.lookup("#pageScene");
        initMenuBar();
        PageNode initialPageNode = null;
        try {
            Constructor constructor = initialPageSceneClass.getConstructor(UI.class);
            initialPageNode = (PageNode)constructor.newInstance(this);
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
            showThrowable("Fatal Error","error creating initial Page", e);
            close();
        }
        addPageNode(initialPageNode.getName(), initialPageNode, initialPageNode.getNode());
        showPageNode(initialPageNode.getName());
        stage.show();
        switch (getOperatingSystem()) {
            case WINDOWS:
                break;
            default:
                showErrorMessage(getOperatingSystem() + " operating system is not currently supported",
                        "Error");
                stage.close();
        }
    }

    @Override
    public Stage getStage() {
        return stage;
    }

    @Override
    public Parent getParent() {
        return root;
    }

    @Override
    public void addPageNode(String name, PageNode pageNode, Node node) {
        NodeTuple tuple = new NodeTuple();
        tuple.pageNode  = pageNode;
        tuple.node = node;
        pageNodeMap.put(name,tuple);
    }

    public boolean showPageNode(String name) {
        boolean result = true;
        if (currentPageNodeName != name) {
            if (currentPageNode != null) currentPageNode.onLeavePage();
            NodeTuple tuple = pageNodeMap.get(name);
            currentNode = tuple.node;
            currentPageNode = tuple.pageNode;
            currentPageNodeName = name;

            if (tuple == null) {
                showErrorMessage("Node with the name: " + name + " does not exist", "Internal Error");
                result = false;
            } else {
                pageContainer.getChildren().clear();
                pageContainer.getChildren().add(tuple.node);
            }
        }
        return result;
    }



    @Override
    public void setJobTitle(String jobName) {
        if (jobName == null) {
            stage.setTitle(title);
        } else {
            stage.setTitle(title + " job: " + jobName);
        }
    }

    void initMenuBar() {
        Menu viewMenu = new Menu("View");
        MenuItem viewCurrentJob = new MenuItem("Active Job Settings");
        viewCurrentJob.setOnAction(viewJobsAction);
        MenuItem viewFileSelection = new MenuItem("Backup File Selection");
        viewFileSelection.setOnAction(viewFileSelectionAction);
        viewMenu.getItems().addAll(viewCurrentJob, viewFileSelection);
        Menu performBackup = new Menu("Backup/Restore");
        performBackup.onShownProperty().setValue(viewExecuteAction);
        performBackup.getItems().addAll(new MenuItem());
        Menu help = new Menu("Help");
        MenuItem helpItem = new MenuItem("Help");
        helpItem.setOnAction(viewHelp);
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(viewAbout);
        MenuItem licenseItem = new MenuItem("Licenses");
        licenseItem.setOnAction(viewLicense.get());
        help.getItems().addAll(helpItem,aboutItem,licenseItem);
        menubar.getMenus().addAll(viewMenu, performBackup, help);
    }

    EventHandler viewJobsAction = new EventHandler() {
        @Override
        public void handle(Event event) {
            showPageNode("jobs");
        }
    };
    EventHandler viewFileSelectionAction = new EventHandler() {
        @Override
        public void handle(Event event) {
            showPageNode("fileSelection");
        }
    };

    EventHandler viewExecuteAction = new EventHandler() {
        @Override
        public void handle(Event event) {
            ((Menu)event.getSource()).hide();
            showPageNode("execute");
        }
    };



    EventHandler viewHelp = new EventHandler() {
        @Override
        public void handle(Event event) {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                try {
                    Desktop.getDesktop().browse(new URI("file:///C:/Program%20Files/PhinneyRidge/Backup/doc/BackupUtilityUserGuide.pdf"));
                } catch (IOException e) {
                    e.printStackTrace();
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }
    };

    SimpleObjectProperty<EventHandler> viewLicense = new SimpleObjectProperty<>(this, "viewLicense", new EventHandler() {
        // called when Licenses menu item is clicked
        @Override
        public void handle(Event event) {
            /*
             * this handle displays licenses information in a custom dialog.
             * The information it displays comes from the "Notice" file.
             */
            InputStream is = getClass().getClassLoader().getResourceAsStream("\\Notice");
            String notice = "";
            // read the entire Notice file
            try {
                if (is.available() > 0) notice = new String(is.readAllBytes());
            } catch (IOException e) {
            }
            /*
             * the contents of the Notice file gets put into a TextFlow Object as a sequence of Text and
             * Hyperlink objects.  Url's contained in the content of the Notice file, get converted into
             * Hyperlink objects. When any of the  hyperlinks get clicked the "linkClicked" handler gets
             * called.  That handler is responsible for opening the link in the Systems desktop browser.
             */
            TextFlow textFlow = new TextFlow();
            Pattern  urlPattern = Pattern.compile("(ht|f)tp(s?)\\:\\/\\/[0-9a-zA-Z]([-.\\w]*[0-9a-zA-Z])*(:(0-9)*)*(\\/?)([a-zA-Z0-9\\-\\.\\?\\,\\'\\/\\\\\\+&amp;%\\$#=_]*)?");
            Matcher matcher = urlPattern.matcher(notice);
            int pos = 0;
            while (matcher.find()) {
                Text text = null;
                if (matcher.start() != 0) {
                    text = new Text(notice.substring(pos, matcher.start()-1));
                    textFlow.getChildren().add(text);
                }
                Hyperlink link = new Hyperlink(notice.substring(matcher.start(), matcher.end()));
                link.setOnAction(linkClicked);
                textFlow.getChildren().add(link);
                pos = matcher.end() +1;
            }
            if (pos <= notice.length()) {
                Text text = new Text(notice.substring(pos));
                textFlow.getChildren().add(text);
            }
            Stage stage = new Stage();
            stage.initModality(Modality.APPLICATION_MODAL);
            HBox hBox = new HBox();
            ScrollPane scrollPane = new ScrollPane(hBox);
            scrollPane.setFitToHeight(true);
            BorderPane root = new BorderPane(scrollPane);
            root.setPadding(new Insets(15));
            hBox.getChildren().add(textFlow);
            Scene scene = new Scene(root, 400, 400);
            stage.setScene(scene);
            stage.show();
        }
    });

    /**
     * this handler is call when a Hyperlink is clicked on.
     * The url for the HyperLink is opened in the System desktop browser
     */
    EventHandler linkClicked = new EventHandler() {
        @Override
        public void handle(Event event) {
            try {
                Desktop.getDesktop().browse(new URI(((Hyperlink) event.getSource()).getText()));
            } catch (IOException | URISyntaxException e) {
                e.printStackTrace();
            }
        }
    };

    EventHandler viewAbout = new EventHandler() {
        @Override
        public void handle(Event event) {
            InputStream is = getClass().getClassLoader().getResourceAsStream("\\META-INF\\MANIFEST.MF");
            try {
                Manifest manifest = new Manifest(is);
                Attributes attr = manifest.getMainAttributes();
                String appVersion = attr.getValue("Specification-Version");
                String impVersion = attr.getValue("Implementation-Version");
                try {
                    String about = "Backup Utility (version " + appVersion + " " + impVersion + ")\n" +
                            "an open source utility for backing up and restoring\n" +
                            "selected files and directories.\n" +
                            "Running on Java VM version: " + System.getProperty("java.vm.version") + "\n" +
                            "using JavaFX version: " + System.getProperty("javafx.runtime.version") + "\n" +
                            "License: Apache License version 2.0\n" +
                            "";
                 App.getUIManager().showMessage(about, "About");
                } catch (Exception e) {
                }
            } catch (IOException E) {
                // handle
            }
        }
    };

    public String getPassword(String title, String header, String query) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setGraphic(new Circle(15, Color.RED)); // Custom graphic
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        PasswordField pwd = new PasswordField();
        pwd.setMinWidth(500);
        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(10);
        content.getChildren().addAll(new Label(query), pwd);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return pwd.getText();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            return result.get();
        } else {
            return null;
        }
    }

    public OS getOperatingSystem()
    {
        // detecting the operating system using `os.name` System property
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return OS.WINDOWS;
        }
        else if (os.contains("nix") || os.contains("nux") || os.contains("aix")) {
            return OS.LINUX;
        }
        else if (os.contains("mac")) {
            return OS.MAC;
        }
        else if (os.contains("sunos")) {
            return OS.SOLARIS;
        }
        return null;
    }

    @Override
    public void showProgressBar() {
        //progressBar.setVisible(true);
        //progressBar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
    }

    @Override
    public void showProgressBar(double s) {
        //progressBar.setVisible(true);
        //progressBar.setProgress(s);
    }

    @Override
    public void setProgress(double v) {
        //progressBar.setProgress(v);
    }

    @Override
    public void hideProgressBar() {
        //progressBar.setVisible(false);
    }

    @Override
    public void close() {
        stage.close();
    }

    @Override
    public void showPopup(String text, int seconds) {
        Label label = new Label(text);
        label.setStyle("-fx-background-color: rgba(0, 0, 0, 0);");
        StackPane stackPane = new StackPane(label);
        stackPane.setStyle("-fx-background-color: rgba(0, 0, 0, 0);");
        Scene popupScene = new Scene(stackPane, 150, 50);
        Stage popupStage = new Stage();
        popupScene.setFill(null);
        popupStage.initStyle(StageStyle.UNDECORATED);
        popupStage.initStyle(TRANSPARENT);
        popupStage.setScene(popupScene);
        PauseTransition wait = new PauseTransition(Duration.seconds(3));
        wait.setOnFinished((e) -> {
            popupStage.close();
        });
        popupStage.show();
        wait.play();

    }

    public void showThrowable(String title, String header, Throwable e) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.getDialogPane().setMinWidth(Region.USE_PREF_SIZE);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(e.getMessage());
        alert.showAndWait();
    }

    public void showErrorMessage(String error, String title) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(error);
        alert.showAndWait();
    }
    public void showMessage(String message, String title) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public String getStackTrace(Throwable e) {
        String stackTrace = "";
        if (e!=null) {

            final ByteArrayOutputStream baos = new ByteArrayOutputStream();
            final String utf8 = StandardCharsets.UTF_8.name();
            try (PrintStream ps = new PrintStream(baos, true, utf8)) {
                e.printStackTrace(ps);
            } catch (UnsupportedEncodingException ex) {
            }
            stackTrace = baos.toString();
        }
        return stackTrace;
    }

    VBox getRoot() {
        return (VBox)root;
    }

    public PageNode getCurrentPageNode() {
        return currentPageNode;
    }

    @Override
    public String selectChoice(List<String> choices, String title, String header, String query, boolean reverseOrder) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(header);
        dialog.setGraphic(new Circle(15, Color.RED)); // Custom graphic
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        if (reverseOrder) {
            Collections.sort(choices,Collections.reverseOrder());
        } else {
            Collections.sort(choices);
        }

        final ObservableList<String> comboBoxItems = FXCollections.observableArrayList();
        final ComboBox<String> comboBox = new ComboBox<String>();
        comboBox.setItems(comboBoxItems);
         for (String choice: choices) {
            comboBoxItems.add(choice);
        }
        HBox content = new HBox();
        content.setAlignment(Pos.CENTER_LEFT);
        content.setSpacing(10);
        content.getChildren().addAll(new Label(query), comboBox);
        dialog.getDialogPane().setContent(content);
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return (String) comboBox.getSelectionModel().getSelectedItem();
            }
            return null;
        });

        Optional<String> result = dialog.showAndWait();

        if (result.isPresent()) {
            return result.get();
        } else {
            return null;
        }
    }

}
