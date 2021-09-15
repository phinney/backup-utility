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

import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.event.EventType;
import javafx.fxml.FXML;
import javafx.geometry.HPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Callback;
import javafx.util.StringConverter;

import javax.swing.*;
import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Parameter;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

public class FileSelectionNode implements PageNode, FileSelection {

    private final String nodeName = "fileSelection";
    private UI ui;
    private ScrollPane dirScrollPane;

    @FXML
    private SplitPane splitPane;        // this is BackupNode's top level page node

    private FileSystemView fileSystemView = FileSystemView.getFileSystemView();
    private Desktop desktop = Desktop.getDesktop();
    private VBox dirContentPane;

    private TreeView treeView;

    private File currentFile;   // currently selected file
    private File currentDir;    // current directory being shown in table

    private TableView table;
    @FXML
    private TableColumn<Parameter, ImageView> iconCol;
    @FXML
    private TableColumn<Parameter, String> filenameCol;
    @FXML
    private TableColumn<Parameter, String> pathCol;
    @FXML
    private TableColumn<Parameter, Long>sizeCol;
    @FXML
    private TableColumn<Parameter, String> lastModCol;
    @FXML
    private TableColumn<Parameter, CheckBox> rCol;
    @FXML
    private TableColumn<Parameter, CheckBox> wCol;
    @FXML
    private TableColumn<Parameter, CheckBox> eCol;
    @FXML
    private TableColumn<Parameter, CheckBox> dCol;
    @FXML
    private TableColumn<Parameter, CheckBox> fCol;

    private ToolBar toolBar;

    private GridPane fileDetail;

    private HBox flags;
    private HBox iconName;
    private Text pathName;
    private Text lastModified ;
    private Text size;
    private RadioButton isDirectory;
    private RadioButton isFile;
    private CheckBox isReadable;
    private CheckBox isWritable;
    private CheckBox isExecutable;

    private DateFormat dateFormat = new SimpleDateFormat("MMM dd,yyy hh:mm:ss.SSS");

    double progressIncr;
    double progress = 0;



    public FileSelectionNode(UI ui)  throws Exception {

        this.ui = ui;
        splitPane = new SplitPane();
        initSplitPane();
        App.setFileSelection(this);
    }

    @Override
    public Node getNode(){
        return splitPane;
    }

    public String getName() {
        return nodeName;
    }

    @Override
    public void onLeavePage() {
        Job activeJob = App.getJobManager().getActiveJob();
        if (activeJob!= null) {
            activeJob.setNodes(getPaths());
            activeJob.save();
        }
    }

    void initSplitPane() {
        splitPane.getItems().addAll(initDirPane(), initDirContentPane());
        splitPane.prefHeightProperty().bind(ui.getParent().getScene().heightProperty());
    }

    private Node initDirPane() {
        dirScrollPane  = new ScrollPane();
        dirScrollPane.setFitToHeight(true);
        dirScrollPane.setFitToWidth(true);
        dirScrollPane.setMinWidth(200);

        CheckBoxTreeItem<File> root = new CheckBoxTreeItem<>(null);
        switch (ui.getOperatingSystem()) {
            case WINDOWS:
                populateTreeForWindows(root);
                break;
        }
        treeView = new TreeView(root);
        treeView.getSelectionModel().selectedItemProperty().addListener(new ChangeListener() {
            @Override
            public void changed(ObservableValue observableValue, Object o, Object t1) {
                CheckBoxTreeItem<File> node = (CheckBoxTreeItem<File>)t1;
                 if (node.getValue() != currentFile) {
                    setFileDetail(node);
                    showChildren(node);
                }
            }
        });
        treeView.setCellFactory(new CallBackWrapper());
        treeView.setShowRoot(false);
        dirScrollPane.setContent(treeView);

        return dirScrollPane;
    }

    private void populateTreeForWindows(CheckBoxTreeItem<File> rootItem) {
        File[] roots = fileSystemView.getRoots();
        for (File fileSystemRoot: roots) {
            //CheckBoxTreeItem<File> node = new CheckBoxTreeItem<>(fileSystemRoot);
            //rootItem.getChildren().add (node);
            File[] files = fileSystemRoot.listFiles();
            for (File file: files) {
                if (file.isDirectory())  {
                    boolean addDir = false;
                    if (!fileSystemView.isFileSystem(file)) {
                        switch(fileSystemView.getSystemDisplayName(file)) {
                            case "This PC":
                                File[] thisPCFiles = fileSystemView.getFiles(file, false);
                                for (File thisPCFile: thisPCFiles) {
                                    if (fileSystemView.isFileSystem(thisPCFile)) {
                                        if (!thisPCFile.getAbsolutePath().startsWith("C:\\Users\\")) {
                                            rootItem.getChildren().add(new CheckBoxTreeItem<>(thisPCFile));
                                        }
                                    }
                                }
                                addDir = false;
                                break;
                            case "Network":
                                addDir = true;
                                break;
                            default:
                                addDir = false;
                        }
                    } else {
                        if (!file.getAbsolutePath().startsWith("C:\\Users\\")) addDir = true;
                    }
                    if (addDir) {
                        rootItem.getChildren().add(new CheckBoxTreeItem<>(file));
                    }
                }
            }
        }
    }

    private Node initDirContentPane() {
        dirContentPane = new VBox();
        dirContentPane.setMinWidth(200);
        initTableView();
        initToolBar();
        initFlags();
        initFileDetail();
        VBox mainContent = new VBox();
        mainContent.getChildren().addAll(
                toolBar, fileDetail
        );
        dirContentPane.getChildren().addAll(table,mainContent);
        return dirContentPane;
    }

    private void initTableView() {
        table = new TableView();
        table.getSelectionModel().selectedItemProperty().addListener(tableRowSelected);
        table.setEditable(true);
        iconCol = new TableColumn("Icon");
        iconCol.setPrefWidth(30);
        iconCol.resizableProperty().set(false);
        iconCol.setCellValueFactory(new PropertyValueFactory<Parameter, ImageView>("icon"));
        filenameCol = new TableColumn("Name");
        filenameCol.setResizable(true);
        filenameCol.minWidthProperty().setValue(80);
        filenameCol.setCellValueFactory(new PropertyValueFactory<Parameter, String>("filename"));
        pathCol = new TableColumn("Path/name");
        pathCol.resizableProperty().set(true);
        pathCol.minWidthProperty().setValue(80);
        pathCol.setCellValueFactory(new PropertyValueFactory<Parameter, String>("path"));
        sizeCol = new TableColumn("Size");
        sizeCol.setResizable(true);
        sizeCol.setStyle("-fx-alignment: CENTER-RIGHT;");
        sizeCol.setCellValueFactory(new PropertyValueFactory<Parameter, Long>("size"));
        lastModCol = new TableColumn("Last Modified");
        lastModCol.setResizable(true);
        lastModCol.setCellValueFactory(new PropertyValueFactory<Parameter,String>("lastModified"));
        rCol = new TableColumn("R");
        rCol.setPrefWidth(20);
        rCol.resizableProperty().set(false);
        rCol.setCellValueFactory(new PropertyValueFactory<Parameter,CheckBox>("read"));
        wCol = new TableColumn("W");
        wCol.setPrefWidth(20);
        wCol.resizableProperty().set(false);
        wCol.setCellValueFactory(new PropertyValueFactory<Parameter,CheckBox>("write"));
        eCol = new TableColumn("E");
        eCol.setPrefWidth(20);
        eCol.resizableProperty().set(false);
        eCol.setCellValueFactory(new PropertyValueFactory<Parameter,CheckBox>("execute"));
        dCol = new TableColumn("D");
        dCol.setPrefWidth(20);
        dCol.resizableProperty().set(false);
        dCol.setCellValueFactory(new PropertyValueFactory<Parameter,CheckBox>("directory"));
        fCol = new TableColumn("F");
        fCol.setPrefWidth(20);
        fCol.resizableProperty().set(false);
        fCol.setCellValueFactory(new PropertyValueFactory<Parameter,CheckBox>("file"));
        table.getColumns().addAll(iconCol,filenameCol,sizeCol,pathCol,lastModCol,rCol,wCol,eCol,dCol,fCol);
    }

    private ChangeListener tableRowSelected = new ChangeListener() {
        @Override
        public void changed(ObservableValue observableValue, Object o, Object t1) {
            FileEntry entry = (FileEntry)observableValue.getValue();
            if (entry != null) {
                // the icon ImageView needs to be cloned, it'll be removed from the table if we don't
                setFileDetail(new File(((FileEntry) observableValue.getValue()).getPath()),
                        cloneImageView(entry.getIcon()));
            }

        }
    };

    ImageView cloneImageView(ImageView imageIn) {
        Image image = imageIn.getImage();
        return new ImageView(image);
    }

    private void initToolBar(){
        toolBar = new ToolBar();
        Button locateButton = new Button("locate");
        toolBar.getItems().add(locateButton);
        Button openButton = new Button("open");
        toolBar.getItems().add(openButton);
        Button editButton = new Button("edit");
        toolBar.getItems().add(editButton);
        Button printButton = new Button("print");
        toolBar.getItems().add(printButton);
        locateButton.setOnAction(handler);
        openButton.setOnAction(handler);
        editButton.setOnAction(handler);
        printButton.setOnAction(handler);
    }

    private void initFileDetail() {
        fileDetail = new GridPane();

        GridPane.setHgrow(fileDetail,Priority.NEVER);
        addDetailLabel("File ", 0,0);
        addDetailLabel("Path/name ",0,1);
        addDetailLabel("Last Modified ",0,2);
        addDetailLabel("File size ",0,3);
        addDetailLabel("Type ",0,4);
        iconName = new HBox();
        pathName = new Text();
        lastModified = new Text();
        size = new Text();
        addDetailValue(iconName,1,0);
        addDetailValue(pathName,1,1);
        addDetailValue(lastModified, 1,2);
        addDetailValue(size,1,3);
        addDetailValue(flags,1,4);

        ColumnConstraints labelColumnConstraint = new ColumnConstraints();
        labelColumnConstraint.minWidthProperty().setValue(80);
        labelColumnConstraint.setPrefWidth(80);
        fileDetail.getColumnConstraints().add(0,labelColumnConstraint);
    }

    private void addDetailLabel(String string, int row, int col) {
        Label label = new Label(string);
        GridPane.setHalignment(label, HPos.RIGHT);
        fileDetail.add (label, row, col);
        GridPane.setHgrow(label,Priority.NEVER);
    }

    private void addDetailValue(Node node, int row, int col) {
        fileDetail.add(node, row, col);
    }

    private void initFlags() {
        flags = new HBox();
        isDirectory = new RadioButton();
        isDirectory.setDisable(true);
        Label isDirectoryLabel = new Label("Directory  ");
        isDirectoryLabel.setMinWidth(60);
        isFile = new RadioButton();
        isFile.setDisable(true);
        Label isFileLabel = new Label("File  ");
        isFileLabel.setMinWidth(30);
        Label flagsLabel = new Label("  :: Flags  ");
        isReadable = new CheckBox();
        isReadable.setDisable(true);
        Label isReadableLabel = new Label("Read  ");
        isReadableLabel.setMinWidth(40);
        isWritable = new CheckBox();
        isWritable.setDisable(true);
        Label isWritableLabel = new Label("Write  ");
        isWritableLabel.setMinWidth(40);
        isWritable.setDisable(true);
        isExecutable = new CheckBox();
        Label isExecutableLabel = new Label("Execute  ");
        isExecutableLabel.setMinWidth(60);
        isExecutable.setDisable(true);
        flags.getChildren().addAll(
                isDirectory, isDirectoryLabel,
                isFile, isFileLabel,
                isReadable, isReadableLabel,
                isWritable, isWritableLabel,
                isExecutable, isExecutableLabel);

    }

    EventHandler handler = new EventHandler() {
        @Override
        public void handle(Event event) {
            if (currentFile != null) {
                Object source = event.getSource();
                if (source instanceof Button) {
                    switch (((Button) source).getText()) {
                        case "locate":
                            try {
                                if (fileSystemView.isFileSystem(currentFile)) {
                                    if (fileSystemView.isFileSystemRoot(currentFile)) {
                                        desktop.open(currentFile);
                                    } else {
                                        desktop.open(currentFile.getParentFile());
                                    }
                                } else {
                                    if (fileSystemView.getSystemDisplayName(currentFile).equals("Network")) {
                                        openNetwork();
                                    }

                                }
                            } catch (Exception e) {
                                ui.showThrowable("Locate Error ", "an exception was thrown trying to locate file",e);
                            }
                            break;
                        case "open":
                            try {
                                if (fileSystemView.isFileSystem(currentFile)) {
                                    desktop.open(currentFile);
                                } else {
                                    if (fileSystemView.getSystemDisplayName(currentFile).equals("Network")) {
                                        openNetwork();
                                    }
                                }
                            } catch (Exception e) {
                                ui.showThrowable("Open Error ", "an exception was thrown trying to open file",e);
                            }
                            break;
                        case "edit":
                            try {
                                if (fileSystemView.isFileSystem(currentFile)) {
                                    desktop.edit(currentFile);
                                } else {
                                    ui.showErrorMessage("this item cannot be edited", "Information");
                                }
                            } catch (IOException e) {
                                ui.showThrowable("Edit Error ", "an exception was thrown trying to edit file",e);
                            }
                            break;
                        case "print":
                            try {
                                if (fileSystemView.isFileSystem(currentFile)) {
                                    desktop.print(currentFile);
                                } else {
                                    ui.showErrorMessage("this item cannot be printed", "Information");
                                }
                            } catch (IOException e) {
                                ui.showThrowable("Print Error ", "an exception was thrown trying to print file",e);
                            }
                            break;
                    }
                }
            }
        }
    };

    private void openNetwork() throws Exception {
             Process process = Runtime.getRuntime().exec("explorer shell:NetworkPlacesFolder");
    }

    private ImageView iconToImage(Icon icon) {
        int w = icon.getIconWidth();
        int h = icon.getIconHeight();
        GraphicsEnvironment ge =
                GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        GraphicsConfiguration gc = gd.getDefaultConfiguration();
        BufferedImage image = gc.createCompatibleImage(w, h, Transparency.TRANSLUCENT);
        Graphics2D g = image.createGraphics();
        icon.paintIcon(null, g, 0, 0);
        ImageView imageFx = convertToFxImage(image);
        g.dispose();
        return imageFx;
    }
    private ImageView convertToFxImage(BufferedImage image) {
        WritableImage wr = null;
        if (image != null) {
            wr = new WritableImage(image.getWidth(), image.getHeight());
            PixelWriter pw = wr.getPixelWriter();
            for (int x = 0; x < image.getWidth(); x++) {
                for (int y = 0; y < image.getHeight(); y++) {
                    pw.setArgb(x, y, image.getRGB(x, y));
                }
            }
        }

        return new ImageView(wr);
    }

    void showChildren(CheckBoxTreeItem<File> node) {
        File file = node.getValue();
        if (file.isFile()) {
            if (fileSystemView.isFileSystem(file)) {
                file = file.getParentFile();
            }
        }
        if (file != currentDir) {
            treeView.setDisable(true);
            progress = .1;
            ui.showProgressBar(progress);

            Task<Void> worker = new Task<Void>() {
                @Override
                protected Void call() throws Exception {
                    boolean isParentSelected = node.isSelected();
                    if (node.getChildren().size() == 0) {
                        File[] files = fileSystemView.getFiles(node.getValue(), false);
                        double progressIncr = .5 / files.length;
                        for (File file : files) {
                            if (file.isDirectory()) {
                                CheckBoxTreeItem<File> childNode = new CheckBoxTreeItem<>(file);
                                if (isParentSelected) childNode.setSelected(true);
                                node.getChildren().add(childNode);
                                progress += progressIncr;
                                final double p = progress;
                                Platform.runLater(() -> ui.setProgress(p));
                            }
                        }
                        for (File file : files) {
                            if (!file.isDirectory()) {
                                CheckBoxTreeItem<File> childNode = new CheckBoxTreeItem<>(file);
                                if (isParentSelected) childNode.setSelected(true);
                                node.getChildren().add(childNode);
                                progress += progressIncr;
                                Double p = progress;
                                Platform.runLater(() -> ui.setProgress(p));
                            }
                        }
                    }
                    setTableData();
                    return null;
                }
            };
            worker.setOnSucceeded(showChildrenEvent);
            worker.setOnFailed(showChildrenEvent);
            Platform.runLater(worker);
        }
    }

    EventHandler showChildrenEvent = new EventHandler() {
        @Override
        public void handle(Event event) {
            if (event instanceof WorkerStateEvent) {
                WorkerStateEvent e = (WorkerStateEvent) event;
                EventType<? extends Event> eventType = e.getEventType();
                if (WorkerStateEvent.WORKER_STATE_FAILED.equals(eventType)) {
                    ui.showErrorMessage("an error occurred trying to expand directory tree", "Error");
                    finish();
                } else if (WorkerStateEvent.WORKER_STATE_CANCELLED.equals(eventType)) {
                    finish();
                } else if (WorkerStateEvent.WORKER_STATE_SUCCEEDED.equals(eventType)) {
                    finish();
                }

            }
        }

        private void finish() {
            treeView.setDisable(false);
            ui.hideProgressBar();

        }
    };

    void expandChildren(CheckBoxTreeItem<File> node) {
        boolean isParentSelected = node.isSelected();
        if (node.getChildren().size() == 0) {
            File[] files = fileSystemView.getFiles(node.getValue(), false);
            double progressIncr = .5 / files.length;
            for (File file : files) {
                if (file.isDirectory()) {
                    CheckBoxTreeItem<File> childNode = new CheckBoxTreeItem<>(file);
                    if (isParentSelected) childNode.setSelected(true);
                    node.getChildren().add(childNode);
                }
            }
            for (File file : files) {
                if (!file.isDirectory()) {
                    CheckBoxTreeItem<File> childNode = new CheckBoxTreeItem<>(file);
                    if (isParentSelected) childNode.setSelected(true);
                   node.getChildren().add(childNode);
                }
            }
        }
    }

    synchronized void setFileDetail(CheckBoxTreeItem<File> node) {
        File file = node.getValue();
        ImageView icon = iconToImage(fileSystemView.getSystemIcon(file));
        setFileDetail(node.getValue(), icon);
    }

    /*
     * This method is called from the table change listener. For a network file
     * we are not able to us fileSystem.getSystemIcon(file) to get the image. But
     * fortunate the table change Listener already has the icon.
     */
    synchronized void setFileDetail(File file, ImageView icon) {
        if (file != currentFile) {
            currentFile = file;
            iconName.getChildren().clear();
            Text fileName = new Text(fileSystemView.getSystemDisplayName(file));
            iconName.getChildren().addAll(icon, new Label(" "), fileName);
            if (fileSystemView.isFileSystem(file)) {
                pathName.setText(file.getAbsolutePath());
            } else {
                pathName.setText(file.getName());
            }
            Date lastModifiedDate = new Date(file.lastModified());
            lastModified.setText(dateFormat.format(lastModifiedDate));
            size.setText(Long.toString(file.length()) + " bytes");
            if (file.isDirectory()) {
                isDirectory.setSelected(true);
                isFile.setSelected(false);
            } else {
                isDirectory.setSelected(false);
                isFile.setSelected(true);
            }
            isReadable.setSelected(file.canRead());
            isWritable.setSelected(file.canWrite());
            isExecutable.setSelected(file.canExecute());
        }
    }

    private void setTableData() {
        File dir = currentFile;
        boolean rebuildTable = false;
        if (currentFile.isDirectory()) {
            if (dir != currentDir) {
                rebuildTable = true;
            }
        } else if (currentFile.getParentFile() != currentDir) {
            rebuildTable = true;
            dir = currentFile.getParentFile();
        }
        ObservableList<FileEntry> fileEntries = FXCollections.observableArrayList();
        if (rebuildTable) {
            currentDir = dir;
            File[] files = dir.listFiles();
             // first add directories
            for (File file: files) {
                if (file.isDirectory()) {
                    fileEntries.add(createFileEntry(file));
                    progress += progressIncr;
                    ui.setProgress(progress);
                }
            }
            // second add files
            for (File file: files) {
                if (file.isFile()) {
                    fileEntries.add(createFileEntry(file));
                    progress += progressIncr;
                    ui.setProgress(progress);
                }
            }
            table.setItems(fileEntries);
        }
    }

    private FileEntry createFileEntry(File file) {
        return new FileEntry(
                iconToImage(fileSystemView.getSystemIcon(file)),
                file.getName(),
                file.length(),
                file.getPath(),
                dateFormat.format(new Date(file.lastModified())),
                file.canRead(),
                file.canWrite(),
                file.canExecute(),
                file.isDirectory(),
                file.isFile()
        );
    }

    @Override
    public List<String> getPaths() {
        List<String> paths = new LinkedList<>();
        CheckBoxTreeItem<File> item = (CheckBoxTreeItem<File>)treeView.getRoot();
        addPaths(item, paths);
        return paths;
    }

    void addPaths(CheckBoxTreeItem<File> item, List<String> paths) {
        if (item.isSelected()) {
            paths.add(item.getValue().getPath());
        } else {
            if (item.isIndeterminate()) {
                //ObservableList list = item.getChildren();
                Object[] children =  item.getChildren().toArray();
                for (Object child :children) {
                    CheckBoxTreeItem<File> childItem = (CheckBoxTreeItem<File>)child;
                    if (childItem.isSelected()) {
                        paths.add(childItem.getValue().getPath());
                    } else if (childItem.isIndeterminate()) {
                        addPaths(childItem, paths);
                    }
                }
            }
        }
    }

    @Override
    public boolean setTreeNodePaths(List<String> paths) {
        boolean result = true;
        clearAllSelectedNodes();
        for (String path: paths) {
            String[] treeNodes = path.split("\\\\");
            int end = treeNodes.length-1;
            CheckBoxTreeItem<File> treeNode = (CheckBoxTreeItem<File>)treeView.getRoot();
            for (int i = 0; i<end; i++) {
                treeNode = expandNode(treeNode, treeNodes[i]);
                if (treeNode == null) {
                    result = false;
                    break;
                }
            }
            if (!selectNode(treeNode, treeNodes[end])) result = false;
         }
        return result;
    }

    public void clearAllSelectedNodes() {
        ObservableList selectedItems =  treeView.getSelectionModel().getSelectedItems();
        for (Object observable : selectedItems) {
            CheckBoxTreeItem<File> item = (CheckBoxTreeItem<File>) observable;
            clear(item);
        }
    }

    void clear(CheckBoxTreeItem<File> item) {
        if (item.isSelected()) {
           item.setSelected(false);
        } else if (item.isIndeterminate()){
            ObservableList  children = item.getChildren();
            for (Object child : children) {
                CheckBoxTreeItem<File> childItem = (CheckBoxTreeItem<File>)child;
                clear(childItem);
            }
        }
    }

    /**
     * find the node that is a child node of the parent with the given name and select it
     * @param parentNode the parent node
     * @param name the name of the child noe
     * @return true if the child node was found and selected, else false which indicates that the
     * child node with the given name was not found
     */
    boolean selectNode(CheckBoxTreeItem<File> parentNode, String name) {
        Object[]  children = parentNode.getChildren().toArray();
        for (Object child : children) {
            CheckBoxTreeItem<File> childItem = (CheckBoxTreeItem<File>) child;
            if (name.equals(childItem.getValue().getName())) {
                childItem.setSelected(true);
                //treeView.getSelectionModel().select(child);
                return true;
            }
        }
        return false;
    }

    /**
     * expand the node with the given name that is a child node for the given parent
     * @param parentNode the parent node that the name should be located in
     * @param name the name of the node to file
     * @return true if the child node was found, false if the child node was not found
     */
    private CheckBoxTreeItem<File> expandNode(CheckBoxTreeItem<File> parentNode, String name) {
        Object[] items = parentNode.getChildren().toArray();
        for (Object obj : items) {
            CheckBoxTreeItem<File> item = (CheckBoxTreeItem<File>) obj;
            String itemName;
            File file = item.getValue();
            if (fileSystemView.isFileSystemRoot(file)) {
                // for root directories, the file name is blank, so we have to use the path instead of the name
                itemName = file.getPath();
            } else {
                itemName = item.getValue().getName();
            }
            if (itemName.endsWith("\\")) {
                // need to trim off the ending "/" for directories
                itemName = itemName.substring(0,itemName.length()-1);
            }
            if (name.equals(itemName)) {
                if (!item.isExpanded()) {
                    expandChildren(item);
                }
                return item;
            }
        }
        return null;
    }

    @Override
    public  boolean areAnyNodesSelected() {
        CheckBoxTreeItem<File> treeNode = (CheckBoxTreeItem<File>)treeView.getRoot();
        if (treeNode.isSelected() || treeNode.isIndeterminate()) return true;
        return false;
    }

    // create enums for each operating system
    private class CallBackWrapper implements Callback<TreeView<File>, TreeCell<File>> {

        Callback<TreeView<File>, TreeCell<File>> theCallback;

        private CallBackWrapper() {
            theCallback = CheckBoxTreeCell.<File>forTreeView(getSelectedProperty, converter);
        }

        @Override
        public TreeCell<File> call(TreeView<File> fileTreeView) {
            return theCallback.call(fileTreeView);
        }

        final Callback<TreeItem<File>, ObservableValue<Boolean>> getSelectedProperty = (TreeItem<File> item) -> {
                if (item instanceof CheckBoxTreeItem<?>) {
                    return ((CheckBoxTreeItem<?>) item).selectedProperty();
                }
                return null;
            };
        final StringConverter<TreeItem<File>> converter = new StringConverter<TreeItem<File>>() {

                @Override
                public String toString(TreeItem<File> object) {
                    File item = object.getValue();
                    return fileSystemView.getSystemDisplayName(item);
                }

                @Override
                public TreeItem<File> fromString(String string) {
                    return new TreeItem<File>(new File(string));
                    }
        };
    }

    public static class FileEntry {
        SimpleObjectProperty<ImageView> icon;
        SimpleStringProperty filename;
        SimpleLongProperty size;
        SimpleStringProperty path;
        SimpleStringProperty lastModified;
        SimpleObjectProperty<CheckBox> read;
        SimpleObjectProperty<CheckBox> write;
        SimpleObjectProperty<CheckBox> execute;
        SimpleObjectProperty<CheckBox> directory;
        SimpleObjectProperty<CheckBox> file;

        private FileEntry (ImageView icon, String fileName, Long size, String path, String lastModified, boolean canRead,
                           boolean canWrite, boolean canExecute, boolean isDirectory, boolean isFile) {
            this.icon = new SimpleObjectProperty<ImageView>(icon);
            this.filename = new SimpleStringProperty(fileName);
            this.size = new SimpleLongProperty(size);
            this.path = new SimpleStringProperty(path);
            this.lastModified = new SimpleStringProperty(lastModified);
            this.read = new SimpleObjectProperty<CheckBox>(new CheckBox());
            ((CheckBox)this.read.get()).setSelected(canRead);
            this.write = new SimpleObjectProperty<CheckBox>(new CheckBox());
            ((CheckBox)this.write.get()).setSelected(canWrite);
            this.execute = new SimpleObjectProperty<CheckBox>(new CheckBox());
            ((CheckBox)this.execute.get()).setSelected(canExecute);
            this.directory = new SimpleObjectProperty<CheckBox>(new CheckBox());
            ((CheckBox)this.directory.get()).setSelected(isDirectory);
            this.file = new SimpleObjectProperty<CheckBox>(new CheckBox());
            ((CheckBox)this.file.get()).setSelected(isFile);
        }

        public ImageView getIcon(){
            return icon.get();
        }
        public void setIcon(ImageView icon) {
            this.icon.set(icon);
        }

        public String getFilename() {
            return filename.get();
        }
        public void setFilename(String filename) {
            this.filename.set(filename);
        }

        public String getPath() {
            return path.get();
        }
        public void setPath(String path) {
            this.path.set(path);
        }

        public Long getSize() {
            return size.get();
        }
        public void setSize(Long size) {
            this.size.set(size);
        }

        public String getLastModified() {
            return this.lastModified.get();
        }
        public void setLastModified(String lastModified) {
            this.lastModified.set(lastModified);
        }

        public CheckBox getRead() {
            return this.read.get();
        }
        public void setRead(boolean canRead) {
            CheckBox read = new CheckBox();
            read.setSelected(canRead);
            this.read.set(read);
        }

        public CheckBox getWrite() {
            return this.write.get();
        }
        public void setWrite(boolean canWrite) {
            CheckBox write = new CheckBox();
            write.setSelected(canWrite);
            this.write.set(write);
        }

        public CheckBox getExecute() {
            return this.execute.get();
        }
        public void setExecute(boolean canExecute) {
            CheckBox exexute = new CheckBox();
            exexute.setSelected(canExecute);
            this.execute.set(exexute);
        }

        public CheckBox getDirectory() {
            return this.directory.get();
        }
        public void setDirectory(boolean isDirectory) {
            CheckBox directory = new CheckBox();
            directory.setSelected(isDirectory);
            this.directory.set(directory);
        }

        public CheckBox getFile() {
            return this.file.get();
        }
        public void setFile(boolean isFile) {
            CheckBox file = new CheckBox();
            file.setSelected(isFile);
            this.directory.set(file);
        }
    }

}
