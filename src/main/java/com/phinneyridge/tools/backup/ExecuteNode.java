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
import javafx.concurrent.Task;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import javax.swing.filechooser.FileSystemView;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;


public class ExecuteNode implements PageNode {

    private final String executeNodeName = "execute";
    private UI ui;
    private JobManager jobManager;
    private FileSelection fileSelection;
    private VBox executeRoot;
    private Button begin;
    private Button cancel;
    private Button copyLog;
    private RadioButton backupMode;
    private RadioButton restoreMode;
    private RadioButton always;
    private RadioButton byDate;
    private Label consoleOut;
    private ScrollPane scroll;
    private boolean isExecuting = false;
    private Task executeTask = null;
    private final String encryptionFileExtension = ".iv16enc";
    private enum Result {
        indeterminate,              // not executed
        success,                    // successfully executed
        successWithMissingPaths,    // successful, but some paths did not exist
        fail,                       // some file failed when being backed up
        requireDestination,         // execution was terminated because a destination wasn't supplied
        requireEncryptionPassword,  // execution was terminated because an encryption password wasn't supplied
        nonExistentDestination      // the destination directory cannot be created or not accessible
    };
    private Result backupResult = Result.indeterminate;
    private boolean missingPath = false;
    private boolean anyBackupErrors = false;
    private FileSystemView fileSystemView;
    //private boolean isBackupMode;
    private boolean isPathsCopyActive = false;

    private int noFilesCopied;          // number of Files copied or restored
    private int noFileCopyErrors;       // number of File copy errors
    private int noEncryptedFilesSkipped; // number of Encrypted files skipped because wrong key
    private int noFilesSkipped;         // number of files skipped because destination last mod date is greater than or
                                        // equal to the source last mod date
    private Pattern datePathPattern = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d@\\d\\d-\\d\\d-\\d\\d");
    private String datePath = "";

    long startTime;

    private Integer numOutstandingCopyRequests = 0;

    private ExecutorService executorService = startNewExecutorService();

    private ExecutorService startNewExecutorService() {
        return  Executors.newFixedThreadPool(6);
    }
    public DataEncryption dataEncryption = new DataEncryption();

    public ExecuteNode() {
        ui = App.getUIManager();
        jobManager = App.getJobManager();
        fileSelection = App.getFileSelection();
        fileSystemView = FileSystemView.getFileSystemView();
        Stage stage = App.getUIManager().getStage();
        //VBox root = App.getUIManager().getRoot();

        try {
            executeRoot = FXMLLoader.load(getClass().getResource("/layouts/execute.fxml"));
        } catch (Exception e) {
            ui.showThrowable("Fatal Error","error loading execute root layout", e);
            ui.close();
        }
        App.getUIManager().getRoot().getScene().getRoot().applyCss();
        VBox.setVgrow(executeRoot,Priority.ALWAYS);
        HBox.setHgrow(executeRoot,Priority.ALWAYS);
        begin = (Button)executeRoot.lookup("#begin");
        begin.setOnAction(e->{begin();});
        cancel = (Button)executeRoot.lookup("#cancel");
        cancel.setOnAction(e->{cancel();});
        copyLog = (Button) executeRoot.lookup("#copyLog");
        copyLog.setOnAction(e->{copyLog();});
        backupMode = (RadioButton) executeRoot.lookup("#backupMode");
        backupMode.setOnAction(e->backupModeClicked());
        restoreMode = (RadioButton) executeRoot.lookup("#restoreMode");
        restoreMode.setOnAction(e->restoreModeClicked());
        scroll = (ScrollPane) executeRoot.lookup("#consoleOutPane");
        //consoleOut = (Label)executeRoot.lookup("#console");  <- can't find consoleOut at this time!
        HBox.setHgrow (scroll,Priority.ALWAYS);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        ui.addPageNode(executeNodeName, this, executeRoot);
    }
    @Override
    public Node getNode() {
        return executeRoot;
    }

    @Override
    public String getName() {
        return executeNodeName;
    }

    @Override
    public void onLeavePage() {

    }
    boolean hasOnCompleteBeenCalled = false;



    synchronized void begin() {
        // you can't execute, if you'
        if (executorService.isShutdown())
            executorService = startNewExecutorService();
        hasOnCompleteBeenCalled = false;
        isExecuting = true;
        numOutstandingCopyRequests = 0;
        noFilesCopied = 0;
        noFileCopyErrors = 0;
        noEncryptedFilesSkipped = 0;
        noFilesSkipped = 0;
        executeTask = new Task(){

            @Override
            protected Object call() throws Exception {
                backupResult = null;
                startTime = System.currentTimeMillis();
                if (backupMode.isSelected()) {
                    performBackup();
                } else {
                    performRestore();
                }
                if (backupResult != null) {
                    if (backupResult == Result.requireDestination) {
                        App.getUIManager().showMessage("Destination directory is required", "Error");
                        consoleOut("Job canceled because no destination directory was provided");
                    } else if (backupResult == Result.requireEncryptionPassword) {
                        App.getUIManager().showMessage("Encryption password is required", "Error");
                        consoleOut("Job canceled because no encryption password was provided");
                    }
                }
                isExecuting = false;
                return null;
            }
        };
        executeTask.run();
    }

    void consoleOut (String msg) {
        consoleOut.setText(consoleOut.getText() + "\n" + msg);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                scroll.setVvalue(1.0);

            }
        });
     }

    void clearConsole() {
        if (consoleOut==null) {
            consoleOut = (Label)executeRoot.lookup("#console");
        }
        consoleOut.setText("");
    }

    void reportStatus() {
        consoleOut("");
        Job job = jobManager.getActiveJob();
        String jobModality = (job.isBackupJob())?"Backup":"Restore";
        String jobOperation = (job.isBackupJob())?"backed up":"restored";
        String completionStatus = (anyBackupErrors) ? "An error occurred during the " + jobModality +
                " job" :
                "The " + jobModality + " job completed without any errors";
        consoleOut(completionStatus);
        long sec = (System.currentTimeMillis() - startTime) / 1000;
        long min = sec / 60;
        sec -= min*60;
        String totalTime = jobModality + " Job Completed in " +
                min + " minutes and " + sec + " seconds";
        consoleOut(totalTime);
        String operation = isBackJob()?"backed up":"restored";
        consoleOut("Number of files " + operation + ": " + noFilesCopied);
        consoleOut("Number of copy errors: " + noFileCopyErrors);
        if (isRestoreJob()) {
            if (job.isEncryptionEnabled() && noEncryptedFilesSkipped > 0) {
                consoleOut("Number of encrypted files skipped because the encryption key did not match: "
                        + noEncryptedFilesSkipped);
                if (jobManager.getActiveJob().getReplacementPolicy().equals("byDate") && noFilesSkipped > 0) {
                    consoleOut("Number of files not restored because the origin exists and is up-to-date:  "
                            + noFilesSkipped);
                }
            }
        } else {
            if (job.getReplacementPolicy().equals("byDate") && noFilesSkipped > 0) {
                consoleOut("Number of files skipped because the destination already had an up-to-date copy:  "
                        + noFilesSkipped);
            }
        }
        long millisec = System.currentTimeMillis() - startTime;
        String completionTitle = jobModality + " Job Completed in (" +
                millisec + " millis seconds)";

    }

    void showJobInfo() {
        Job job = jobManager.getActiveJob();
        consoleOut("Job Name: "+ job.getName());
        consoleOut("Destination Directory: " + job.getDestination());
        consoleOut ("File Replacement Policy: " + job.getReplacementPolicy());
        consoleOut("Encrypt Backup Files: " + job.isEncryptionEnabled());
        consoleOut("Append Job Name to destination path: " + job.appendJobName());
        consoleOut("Append Date to destination path: " + job.appendDate());
        if (job.appendDate()) {
            String value = Job.option.dateDir.getValue();
            if (value.isEmpty()) {
                consoleOut("   keep all date paths");
            } else {
                consoleOut ("  only retain the last " + value + " date paths");
            }
        }
        consoleOut("");
    }

    public boolean isBackJob() {
        return backupMode.isSelected();
    }

    public boolean isRestoreJob() {
        return !backupMode.isSelected();
    }

    synchronized void cancel() {
        if (isExecuting) {
            executorService.shutdown();
            if (executeTask != null) executeTask.cancel(true);
            executeTask = null;
            isExecuting = false;
        } else {
            executorService.shutdown();
            if (executeTask != null) executeTask.cancel(true);
            executeTask = null;
            isExecuting = false;
            ui.showPageNode("fileSelection");
        }
    }

    synchronized void backupModeClicked() {
        if (backupMode.isSelected()) {
            backupMode.setSelected(true);
            restoreMode.setSelected(false);
            jobManager.getActiveJob().setBackupMode(true);
        } else {
            backupMode.setSelected(false);
            restoreMode.setSelected(true);
            jobManager.getActiveJob().setBackupMode(false);
        }
    }

    synchronized void restoreModeClicked() {
        if (restoreMode.isSelected()) {
            backupMode.setSelected(false);
            restoreMode.setSelected(true);
            jobManager.getActiveJob().setBackupMode(false);
        } else {
            backupMode.setSelected(true);
            restoreMode.setSelected(false);
            jobManager.getActiveJob().setBackupMode(true);
        }
    }

    synchronized void copyLog() {
        StringSelection selection = new StringSelection(consoleOut.getText());
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(selection, selection);
    }

     void performRestore() {
        /*
         * In restore modality, things get a little more obscure because jobs are specified in the
         * Backup point of view.  Fortunately, the Utility can infer what the corresponding restore operations
         * would be by just reversing the operations of the backup.
         */
         clearConsole();
         consoleOut("starting restore");
         showJobInfo();
         missingPath = false;
         anyBackupErrors = false;
         Job job = jobManager.getActiveJob();
         List<String> paths;
         String destinationDir;
         paths = job.getNodes();
         destinationDir = job.getDestination();
         if (destinationDir == null  || destinationDir.isEmpty()) {
             destinationDir = queryForDestination();
             if (destinationDir == null) {
                 backupResult = Result.requireDestination;
                 return;
             }
         }
         if (!destinationDir.endsWith("\\")) destinationDir += "\\";
         if (job.appendDate()) {
             // we need to get the date path to restore
             String dir = job.getDestination();
             if (job.appendJobName()) {
                 dir += "\\" + job.getName();
             }
             File runtimeDir = new File(dir);
             List<String> dateDirs = jobManager.getAvailableDatePaths(runtimeDir);
             if (dateDirs.isEmpty()) {
                 // no date paths were found, so there's nothing to restore.
                 consoleOut("no date paths were found for this job, so there's nothing to be restored");
                 return;
             } else {
                 if (dateDirs.size() == 1) {
                     // only one date directory was found for this job, so it's the date directory that
                     // will be used
                     datePath = dateDirs.get(0);
                 } else {
                     // more than one date directory was found for this job, so we need to ask the user
                     // which date directory do they want to restore from
                     datePath = ui.selectChoice(
                             dateDirs,
                             "Select Date Path",
                             "There are several date paths associated with this job.\n" +
                                     "Please select the date path you want to restore from.",
                             "Select date path to restore", true);

                     if (datePath == null) {
                         consoleOut ("No date path selected, so the job is cancelled");
                         return;
                     }
                 }
                 consoleOut("Restoring from date path: " + datePath);
             }
         }

         Set<Job.option> options = job.getOptions();
         if (options != null) {
             if (options.contains(Job.option.encrypt)) {
                 // verify that the password is supplied
                 String password = job.getPassword();
                 if (password == null || password.isEmpty()) {
                     String jobType = (job.isBackupJob())?"backup":"restore";
                     String title = "Password";
                     String header = "This " + jobType + " operation uses an encryption method that requires " +
                             "a password or phrase to obtain an encryption key.\n" +
                             "The same password must be used for both the backup and it corresponding restore.\n" +
                             "This utility does not save the password on any persistent storage storage device.\n" +
                             "You won't be able to decrypt content if you lose or forget the key that was used\n" +
                             "when it was encrypted.\n" +
                             "The confidentiality of encrypted content depends on how well you protect the\n" +
                             "confidentiality of the password and whether your password is easy for an attacker to guess";
                     String query = "Enter password/phrase:";
                     password = App.getUIManager().getPassword(title, header, query);
                     if (password == null || password.isEmpty()) {
                         backupResult = Result.requireEncryptionPassword;
                         return;
                     }
                     job.setPassword(password);

                 }
             }
         }

         final String dir = destinationDir;
         Task<Void> doRestore = new Task<Void>() {
             @Override
             protected Void call() throws Exception {
                 synchronized (numOutstandingCopyRequests) {
                     isPathsCopyActive = true;
                 }
                 performRestorePaths(paths, job.getRuntimeDestination(datePath));
                 executorService.shutdown();
                 if (!executorService.awaitTermination(120, TimeUnit.SECONDS)) {
                     // the executor service did not complete normally in the given time frame
                 }
                 onAllCopyComplete.run();
                 synchronized (numOutstandingCopyRequests) {
                     isPathsCopyActive = false;
                 }
                 consoleOut("restore completed");
                 reportStatus();
                 return null;
             }
         };
         doRestore.run();
     }

     void performBackup() {
        clearConsole();
        consoleOut("starting backup");
        showJobInfo();
        missingPath = false;
        anyBackupErrors = false;
        Job job = jobManager.getActiveJob();
        List<String> paths;
        String destinationDir;
        if (job == null) {
            // backup is not being done in context of a job
            paths = fileSelection.getPaths();
            destinationDir = queryForDestination();
            if (destinationDir == null) {
                backupResult = Result.requireDestination;
                return;
            }
            if (!destinationDir.endsWith("\\")) destinationDir += "\\";
        } else {
            paths = job.getNodes();
            destinationDir = job.getDestination();
            if (destinationDir == null  || destinationDir.isEmpty()) {
                destinationDir = queryForDestination();
                if (destinationDir == null) {
                    backupResult = Result.requireDestination;
                    return;
                }
            }
            if (!destinationDir.endsWith("\\")) destinationDir += "\\";
            Set<Job.option> options = job.getOptions();
            if (options != null) {
                if (options.contains(Job.option.encrypt)) {
                    // verify that the password is supplied
                    String password = jobManager.getActiveJob().getPassword();
                    if (password == null || password.isEmpty()) {
                        String jobType = (job.isBackupJob())?"backup":"restore";
                        String title = "Password";
                        String header = "This " + jobType + " operation uses an encryption method that requires " +
                                "a password or phrase to obtain an encryption key.\n" +
                                "The same password must be used for both the backup and it corresponding restore.\n" +
                                "This utility does not save the password on any persistent storage storage device.\n" +
                                "You won't be able to decrypt content if you lose or forget the key that was used\n" +
                                "when it was encrypted.\n" +
                                "The confidentiality of encrypted content depends on how well you protect the\n" +
                                "confidentiality of the password and whether your password is easy for an attacker to guess";
                        String query = "Enter password/phrase:";
                        password = App.getUIManager().getPassword(title, header, query);
                        if (password == null || password.isEmpty()) {
                            backupResult = Result.requireEncryptionPassword;
                            return;
                        }
                        jobManager.getActiveJob().setPassword(password);

                    }
                }
            }
        }
        final String dir = destinationDir;
        Task<Void> doBackup = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                isPathsCopyActive = true;
                String runtimeDestination = job.getRuntimeDestination(new Date(startTime));
                consoleOut("backing up to path: " + runtimeDestination);
                performBackupPaths(paths, runtimeDestination);
                executorService.shutdown();
                if (!executorService.awaitTermination(120, TimeUnit.SECONDS)) {
                    // the executor service did not complete normally in the given time frame
                    consoleOut("executor service did not complete normally in the given time frame\n" +
                            "examine log to verify that all expected files were backed up");
                }
                if (job.appendDate()) {
                    jobManager.checkDatePathRetain();
                }
                onAllCopyComplete.run();
                isPathsCopyActive = false;
                return null;
            }
        };
        doBackup.run();
    }

    Runnable onAllCopyComplete = ()->{
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                if (!hasOnCompleteBeenCalled) {
                    isExecuting = false;
                    jobManager.getActiveJob().setPassword("");
                    hasOnCompleteBeenCalled = true;
                    consoleOut("backup completed");
                    reportStatus();
                }
            }
        });
    };

    String queryForDestination() {
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Backup Location");
        File selectedDir = dirChooser.showDialog(ui.getStage());
        if (selectedDir != null) {
            return selectedDir.getPath();
        }
        return null;
    }

    /**
     * Recursively perform "backup path copies"  for each given path and the given destination directory
     * @param paths list of strings that contain path names
     * @param dir a path to the destination directory
     */
    void performBackupPaths(List<String> paths, String dir) {
        File destinationDir = new File(dir);
        if (!prepareDestination(destinationDir)) {
            backupResult = Result.nonExistentDestination;
            return;
        }
        for (String path: paths) {
            performBackupPathCopy(path, destinationDir);
        }
    }


    /**
     * performs the Restore Paths operation. In restore modality, the paths and dir concept gets inverted; remember
     * they are specified in terms of backup.  The only requirement is that the restore operation should be able to
     * move backed-up content back to a previous state backed up state.
     * @param paths files and directories that were selected when the job was run
     * @param dir the destination to where the backup copy is stored.
     */
    void performRestorePaths(List<String> paths, String dir) {
        File sourceDir = new File(dir);
        File[]dirs = sourceDir.listFiles();
        for (File file: dirs) {
            if (file.isFile()) {
                // depending on whether the job is encrypted or not and whether the
                // file under consideration is encrypted, we decide if it should go on
                // for further processing.  I.E.  The restore operation for encrypted job will only
                // continue processing files that were produced by an encrypted backup operation.  Its
                // possible and reasonable for the restore from directory to contain content the is both encrypted
                // and non-encrypted content. Similarly, jobs without the "encrypt" option set should only process
                // non-encrypted files for the restore.
                if (file.getName().endsWith(encryptionFileExtension)) {
                    // this is an encrypted file, skip further processing of this file if the
                    // current job does not have the  "encrypt" option
                    if (jobManager.getActiveJob().getOptions().contains(Job.option.encrypt)) {
                        String fileName = file.getName();
                        File decryptedFile = new File(file.getParentFile(), fileName.substring(0,
                                fileName.length()-encryptionFileExtension.length()));
                        if (isFileOnPaths(paths, decryptedFile)) {
                            File destinationDir = new File(dir);
                            if (!prepareDestination(destinationDir)) {
                                backupResult = Result.nonExistentDestination;
                                return;
                            }
                            File srcDir = new File(convertBackupPathToSourcePath(file));
                            performRestorePathCopy(file.getPath(), srcDir);
                        }
                    }
                } else {
                    // the file we have is not encrypted, skip further processing of this file if the
                    // current job has the "encrypt" option
                    if (!jobManager.getActiveJob().getOptions().contains(Job.option.encrypt)) {
                        if (isFileOnPaths(paths, file)) {
                            File destinationDir = new File(dir);
                            if (!prepareDestination(destinationDir)) {
                                backupResult = Result.nonExistentDestination;
                                return;
                            }
                            File srcDir = new File(convertBackupPathToSourcePath(file));
                            performRestorePathCopy(file.getPath(), srcDir);
                        }
                    }

                }
            } else {
                performRestorePaths(paths, file.getPath());
            }
        }
     }

    /**
     * lets you know if the given file in on one of the given paths
     * @param paths a list of paths to selected directories or file
     * @param file a file in the job's destination dir
     * @return true -> the given file is on one of the paths
     * false -> the given file is not on any of the paths
     */
    public boolean isFileOnPaths(List<String> paths, File file) {
        String filePath = file.getPath();
        File srcFile = new File(convertBackupPathToSourcePath(file));
        for (String path: paths) {
            if (isFileOnPath(path, srcFile)) return (true);
        }
        return false;
    }

    String convertBackupPathToSourcePath(File backupFile) {        String filePath = backupFile.getPath();
        Job job = jobManager.getActiveJob();
        String backupPath = backupFile.getPath();
        String destDir = job.getDestination();
        if (destDir == null || destDir.isEmpty()) return backupFile.getPath();
        if (job.appendJobName()) {
            // remove jobName from path
            int posStart = destDir.length() + 1;
            int posEnd = backupPath.indexOf("\\",posStart + 1);
            backupPath = backupPath.substring(0, posStart) + backupPath.substring(posEnd + 1);
         }
        if (job.appendDate()) {
            // remove date from path
            int posStart =destDir.length() + 1;
            int posEnd = backupPath.indexOf("\\",posStart + 1);
            backupPath = backupPath.substring(0, posStart) + backupPath.substring(posEnd + 1);
        }
        String path = backupPath.substring(destDir.length()+ 1);
        String driveLetter = "";
        int drivePos = path.indexOf("\\");
        if (drivePos >0) driveLetter = backupPath.substring(0,drivePos);
        path = path.substring(drivePos);
        return driveLetter + ":" + path;

    }

    /**
     * lets you know if the given file is on the given path
     * @param path a path to a selected directory or file
     * @param file a file in the job's source directory. file must be a file and not a directory
     * @return true -> the given file is on the path
     * false -> the given file is not on the path
     */
    public boolean isFileOnPath(String path, File file) {
        boolean result = false;
        File pathFile = new File(path);
        if (pathFile.isDirectory() || !pathFile.exists()) {
            if (file.getPath().startsWith(pathFile.getPath())) {
                result = true;
            }
        } else {
            if (file.getPath().equals(pathFile.getPath())) {
                result = true;
            }
        }
        return result;
    }

    /*
     * prepares the destination dir by making sure that all intermediary
     * path directories are made.
     * @return true, If the destination parents directories all exist.
     *         false, if the destination directory is non-existent.
     */
    boolean prepareDestination(File destinationDir) {
        if (!fileSystemView.isFileSystemRoot(destinationDir)) {
            destinationDir.mkdirs();
        }
        if (!destinationDir.exists()) {
            backupResult = Result.nonExistentDestination;
            return false;
        }
        return true;
    }

    /**
     * recursively perform a Path copy for a single path and destination dir
     * @param path path containing content to be copied
     * @param destinationDir the path to the destination directory
     */
    void performBackupPathCopy(String path, File destinationDir){
        File pathFile = new File(path);
        if (!pathFile.exists()) {
            // path doesn't exist,  we'll denote that and just go on.
            missingPath = true;
            return;
        }
        if (pathFile.isFile()) {
            scheduleFilePathCopyTask(pathFile, destinationDir);
        } else {
            performDirPathCopy(pathFile, destinationDir);
        }
    }

    /**
     * perform a restore path copy
     * @param srcpath the path to the file being restored
     * @param destinationPath the path to the destination
     */
    void performRestorePathCopy(String srcpath, File destinationPath){
        File srcFile = new File(srcpath);
        if (srcFile.isFile()) {
            scheduleFilePathCopyTask(srcFile, destinationPath);
        } else {
            performRestorePathCopy(srcFile.getPath(), destinationPath);
        }
    }


        /**
         * performs a pathCopy on a given path directory and destination directory
         * @param pathDir
         * @param destinationDir
         */
    void performDirPathCopy(File pathDir, File destinationDir) {
        File[] children = pathDir.listFiles();
        for (File child: children) {
            if (child.isFile()) {
                scheduleFilePathCopyTask(child, destinationDir);
            } else {
                performDirPathCopy(child, destinationDir);
            }
        }

    }
    synchronized void scheduleFilePathCopyTask(File pathFile, File destinationDir) {
        final File path = pathFile;
        final File destination = destinationDir;
        Runnable runnable = ()-> {
            performFilePathCopy(path, destination);
            boolean runOnComplete = false;
            synchronized (numOutstandingCopyRequests) {
                //System.out.println("numOutstandingCopyRequests: " + numOutstandingCopyRequests +
                //        " isPathsCopyActive: " + isPathsCopyActive);
                numOutstandingCopyRequests--;
                if (numOutstandingCopyRequests == 0 && !isPathsCopyActive) {
                    runOnComplete = true;
                }
            }
            if (runOnComplete) {
                onAllCopyComplete.run();
            }
        };
        synchronized (numOutstandingCopyRequests) {
            numOutstandingCopyRequests++;
        }
        executorService.submit(runnable);
    }

    /**
     * performs the file path copy for the given file path and destination directory.
     * This method implements the active job's replacement policy
     * @param pathFile - path to the file to be copied
     * @param destinationDir path to the destination directory
     */
    void performFilePathCopy(File pathFile, File destinationDir) {
        // this is where we get the replacement policy
        if (jobManager.getActiveJob().getReplacementPolicy().equals("always")) {
            // replacement policy: always replace destination with source
            if (jobManager.getActiveJob().getOptions().contains(Job.option.encrypt)) {
                if (isBackJob()) {
                    doEncryptFilePathCopy(pathFile, destinationDir);
                    consoleOut ("backed up: " + pathFile.getPath());
                } else {
                    String fileName = destinationDir.getName();
                    File destinationFile = new File (destinationDir.getParentFile(), fileName.substring(0,
                            fileName.length() - encryptionFileExtension.length()));
                    try {
                        doDecryptFilePathCopy(pathFile, destinationFile);
                        consoleOut ("restored: " + pathFile.getPath());
                        noFilesCopied++;
                    } catch (InvalidKeyException e) {
                        noEncryptedFilesSkipped++;
                        consoleOut ("skipped: " + pathFile.getPath() + " - invalid encryption key");
                    }
                }
            } else{
                doFilePathCopy(pathFile, destinationDir);
            }
        } else {
            // replacement policy: only replace if source last mod date is newer
            // than destination file last mod date
            if (doesSourceDateExceedDestinationDate(pathFile, destinationDir)) {
                // replacement policy: always replace destination with source
                if (jobManager.getActiveJob().getOptions().contains(Job.option.encrypt)) {
                    doEncryptFilePathCopy(pathFile, destinationDir);
                } else{
                    doFilePathCopy(pathFile, destinationDir);
                }
            } else {
                noFilesSkipped++;
            }

        }
    }

    /**
     * determine whether the date last mod time of the source pathFile is newer than
     * the last date mod time of the destination
     * @param pathFile
     * @param destinationDir
     * @return true, if the date last mod time of the source pathFile is newer than
     * the last date mod time of the destination or the destination file doesn't exist<br>
     * false, if the last mod time of the source pathFile is older or equal to
     * the last date mod time of the destination file
     */
    boolean doesSourceDateExceedDestinationDate(File pathFile, File destinationDir) {
        boolean result = true;
        long srcDate = pathFile.lastModified();
        File destFile = new File(destinationDir, getRelativePathName(pathFile));
        if (destFile.exists()) result = srcDate > destFile.lastModified();
        return result;
    }

    /**
     * performs the actual copy for the given path file and destination directory
     * @param pathFile - path to the file to be copied
     * @param destinationDir path to the destination directory
     */
    void doFilePathCopy(File pathFile, File destinationDir) {
        //String copyToLocation = destinationDir.getPath() + "\\" + pathFile.getName();
        //File copyToDir = new File (copyToLocation);
        noFilesCopied ++;
        destinationDir.getParentFile().mkdirs();
        String jobType = (jobManager.getActiveJob().isBackupJob())?"Backing up":"Restoring" ;
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                consoleOut(jobType + " " + pathFile.getPath()  + " to " + destinationDir.getPath() );
            }
        });
        boolean copyOkay = true;
        try {
            copyFileUsingChannel(pathFile, new File (destinationDir, getRelativePathName(pathFile)));
        } catch (IOException e) {
            anyBackupErrors = true;
            copyOkay = false;
        }
        if (!copyOkay) noFileCopyErrors++;
        destinationDir.setLastModified(pathFile.lastModified());

    }

    void doEncryptFilePathCopy(File pathFile, File destinationDir) {
        noFilesCopied++;
        File destinationFile = makeEncryptedDestinationFile (pathFile, destinationDir);
        dataEncryption.cipherStreamEncrypt(App.getJobManager().getActiveJob().getPassword(), pathFile, destinationFile);
        destinationDir.setLastModified(pathFile.lastModified());
    }

    void doDecryptFilePathCopy(File pathFile, File destinationDir) throws InvalidKeyException {
        byte[] salt = new byte[8];
        (new Random()).nextBytes(salt);
        dataEncryption.cipherStreamDecrypt(App.getJobManager().getActiveJob().getPassword(), pathFile, destinationDir);
        destinationDir.setLastModified(pathFile.lastModified());
    }


    File makeDestinationFile(File srcFile, File destinationDir) {
        String path = destinationDir.getPath() + "\\" + getRelativePathName(srcFile);
        return new File(path);
    }

    File makeEncryptedDestinationFile(File srcFile, File destinationDir) {
        String path = destinationDir.getPath() + "\\" + getRelativePathName(srcFile) + encryptionFileExtension;
        return new File(path);
    }

    String getRelativePathName (File file) {
        String relativePathName = file.getPath();
        if (relativePathName.startsWith("\\\\")) {
            // this is a host name, strip the host name from the relativePathName
            int idx = relativePathName.indexOf("\\", 2);
            relativePathName = relativePathName.substring(idx);
        } else {
            switch (ui.getOperatingSystem()) {
                case WINDOWS:
                    relativePathName = relativePathName.replaceFirst(":", "");
                    break;
                default:
                    // currently we are only supporting Windows file system;
                    // it's our goal is to make this cross-platform interoperable.
                    break;
            }
        }
        return relativePathName;
    }

    private void copyFileUsingChannel(File source, File dest) throws IOException {
        FileChannel sourceChannel = null;
        FileChannel destChannel = null;
        try {
            sourceChannel = new FileInputStream(source).getChannel();
            if (!dest.exists()) {
                dest.getParentFile().mkdirs();
                dest.createNewFile();
            }
            destChannel = (new FileOutputStream(dest)).getChannel();
            destChannel.transferFrom(sourceChannel, 0, sourceChannel.size());
        }finally{
            sourceChannel.close();
            destChannel.close();
        }
        Path sourcePath = Paths.get(source.getPath());
        Path destinationPath = Paths.get(dest.getPath());
        dest.setExecutable(true,false);
        Files.setLastModifiedTime(destinationPath, Files.getLastModifiedTime(sourcePath));
    }
}
