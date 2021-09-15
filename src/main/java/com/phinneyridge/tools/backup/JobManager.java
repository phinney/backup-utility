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

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Region;

import java.io.File;
import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JobManager {
    private Job activeJob;
    private UIManager ui;
    private FileSelection fileSelection;

    private String jobsDir = System.getProperty("user.home") + "/BackupJobs";
    private File jobsDirFile = new File(jobsDir);
    private Pattern datePathPattern = Pattern.compile("\\d\\d\\d\\d-\\d\\d-\\d\\d@\\d\\d-\\d\\d-\\d\\d");

    public JobManager() {
        ui = App.getUIManager();
        fileSelection = App.getFileSelection();
    }

    public void init(Job activeJob) {
        if (activeJob.getName().equals("$adhoc$")) {
            activeJob.getJobFile().delete();
            activeJob.getJobNodeFile().delete();
        } else {
            activeJob = activeJob;
        }
        setActiveJob(activeJob);
    }

    /**
     * gets a list of all the existing job names
     * @return a list of all the existing job names
     */
    List<String> getAllJobNames() {
        List<String> jobNames = new LinkedList<>();
        File[] jobFiles = jobsDirFile.listFiles();
        if (jobFiles != null) {
            for (File jobFile : jobFiles) {
                String name = jobFile.getName();
                if (!name.contains("_nodes.")) {
                    int idx = name.lastIndexOf(".");
                    jobNames.add(name.substring(0, idx));
                }
            }
        }
        return jobNames;
    }

    public Job createJob() {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setHeaderText("Enter a name that you will use to refer to this backup job");
        dialog.setTitle("Job Name");
        dialog.setContentText("job name: ");
        boolean isNameValid = false;
        Optional<String> jobName = null;
        while (!isNameValid) {
            jobName = dialog.showAndWait();
            if (jobName.isPresent()) {
                String name= jobName.get();
                if (name != null && !name.isEmpty() && isValidFilename(name)) {
                    isNameValid = true;
                } else {
                    if (isValidFilename(name)) {
                        if (name.isEmpty()) {
                            isNameValid = true;
                            return activeJob;  // cancel out from providing a name
                        }
                    } else {
                        isNameValid = false;
                        App.getUIManager().showMessage("The name you enter contain at least one character that is not allowed!" +
                                        " The name needs to conform to the rules of your devices file system file naming rules",
                                "Invalid Name");
                    }
                }
            } else {
                // cancelled supplying name
                isNameValid = true;
            }
        }
        if (jobName.isPresent()) {
            Job newJob = new Job(jobName.get());
            if (newJob.exists()) {
                ui.showErrorMessage("Job with the given name already exists", "Try Again");
            }
            if (fileSelection.areAnyNodesSelected()) {
                ButtonType yes = new ButtonType("yes", ButtonBar.ButtonData.OK_DONE);
                ButtonType no = new ButtonType("no", ButtonBar.ButtonData.CANCEL_CLOSE);
                Alert alert = new Alert(Alert.AlertType.NONE,
                        "Do you want the job to be initialized with the current selected files and directories?",
                        yes,
                        no);
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                alert.setTitle("Use current selections?");
                Optional<ButtonType> result = alert.showAndWait();

                if (result.get() == yes) {
                    newJob.setNodes(fileSelection.getPaths());
                } else {
                    fileSelection.clearAllSelectedNodes();
                }
            }
            if (!newJob.save()) {
                ui.showErrorMessage("Exception occurred trying to create job file", "Save Error" );
                return null;
            }
            return newJob;
        }
        return activeJob;
    }

    public Job getActiveJob() {
        return activeJob;
    }

    public void setActiveJob(Job job) {
        if (job != activeJob ) {
            activeJob = job;
            if (job == null) {
                ui.setJobTitle(null);
            } else {
                ui.setJobTitle(job.getName());
                fileSelection.setTreeNodePaths(job.getNodes());
            }
        }
    }

    boolean isValidFilename(String name) {
        switch (App.getUIManager().getOperatingSystem()) {
            case WINDOWS:
                Pattern p = Pattern.compile(("[<>*:\"/|\\\\?]"));
                Matcher m = p.matcher(name);
                return !m.matches();
        }
        return false;
    }

    /**
     * get the File object use for storing information about the given job name
     * @param jobName the name of the job
     * @return the File object used for storing job info for the given job name
     */
    File getJobFile(String jobName) {
        return new File(jobsDir, jobName + ".xml");
    }

    /**
     * get the File object use for storing information about the nodes for the given job name
     * @param jobName the name of the job
     * @return the File object used for storing job info about the nodes for the given job name
     */
    File getJobNodeFile(String jobName) {
        return new File(jobsDir, jobName + "_nodes.xml");
    }

    public boolean isDatePath(File dir) {
        Matcher matcher = datePathPattern.matcher(dir.getName());
        if (matcher.matches()) return true;
        else return false;
    }

    public boolean isDatePath(String dirFileName) {
        Matcher matcher = datePathPattern.matcher(dirFileName);
        if (matcher.matches()) return true;
        else return false;
    }


    /**
     *  checks that the number of date paths to retain is not exceeded.  If it is,
     *  the older date path directories are deleted.
     * @return true is the routine successfully completed the operation.<br>
     *         false is there was a problem deleting a directory
     */
    boolean checkDatePathRetain () {
        boolean result = true;
        String runtime = getActiveJob().getRuntimeDestination("");
        File runtimeDestination = new File(runtime);
        if (!isDatePath(runtimeDestination)) {
            String dateDirValue = Job.option.dateDir.getValue();
            if (!(dateDirValue == null || dateDirValue.isEmpty())) {
                Integer numDatePathRetain = Integer.valueOf(dateDirValue);
                List<String> dates = getAvailableDatePaths(runtimeDestination);
                if (dates.size() > numDatePathRetain) {
                    // got too many date path directories that we are retaining
                    // delete the older date path directories
                    int i = dates.size();
                    while (i > numDatePathRetain) {
                        if (!deleteOldestPathPath(dates)) {
                            result = false;
                            break;
                        }
                        i--;
                    }
                }
            }
        }
        return  result;
    }

    boolean deleteOldestPathPath(List<String>dates) {
        Collections.sort(dates);
        boolean result = deleteDatePath(dates.get(0));
        dates.remove(0);
        return result;
    }

    boolean deleteDatePath(String date) {
        String datePath = activeJob.getRuntimeDestination(date);
        try {
            Files.walk(Paths.get(datePath))
                    .map(Path::toFile)
                    .sorted((o1, o2) -> -o1.compareTo(o2))
                    .forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    List<String> getAvailableDatePaths(File runtimeDestination) {
        List<String> paths = new LinkedList<>();
        File[] files = runtimeDestination.listFiles();
        for (File file: files) {
            if (file.isDirectory()) {
                if (isDatePath(file)) {
                    paths.add(file.getName());
                }
            }
        }
        return paths;
    }

}
