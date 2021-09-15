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

import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.List;

import static com.phinneyridge.tools.backup.Job.option.dateDir;
import static com.phinneyridge.tools.backup.Job.option.jobDir;

public class JobNode implements PageNode{

    private final String jobsNodeName = "jobs";
    private Parent jobRoot;
    private JobManager jobManager;
    private UIManager ui;
    private ChoiceBox jobChoice;
    private Button chooseDir;
    private Text destinationDir;
    private RadioButton always;
    private RadioButton byDate;
    private RadioButton alwaysPrompt;
    private RadioButton addJobName;
    private RadioButton addDate;
    private VBox numDatesLabel;
    private TextField numDates;
    private ChoiceBox encryptionEnabled;
    private boolean isAlwaysReplace;
    private boolean isEncryptionEnabled;

    public JobNode(){
        jobManager = App.getJobManager();
        ui = App.getUIManager();
        try {
            jobRoot = FXMLLoader.load(getClass().getResource("/layouts/jobs.fxml"));
        } catch (Exception e) {
            ui.showThrowable("Fatal Error","error loading job root layout", e);
            ui.close();
        }
        jobChoice = (ChoiceBox) jobRoot.lookup("#jobChoice");
        jobChoice.getItems().add("<new>");
        jobChoice.getItems().add("$adhoc$");
        List<String> existingJobs = jobManager.getAllJobNames();
        for (String jobName: existingJobs) {
            jobChoice.getItems().add(jobName);
        }
        jobChoice.getSelectionModel().selectedIndexProperty().addListener(
                (ObservableValue<? extends Number> obs, Number oldValue, Number newValue) -> {
            String jobName = (String)jobChoice.getItems().get(newValue.intValue());
            if (jobName == null || jobName.isEmpty()) return;
            if (jobName.equals("<new>")) {
                String name = jobManager.getActiveJob().getName();
                createNewJob();
                if (jobManager.getActiveJob().getName().equals(name)) {
                    // for this case we need to change the selected item "<new>" to
                    // back to its previous job name
                    if (!(jobName.equals("$adhoc$") || jobName.equals("<new>"))) {
                        jobManager.setActiveJob(selectJob(jobName));
                    } else {
                        if (jobName.equals("<new>")) {
                            // for this case we need to change the selected item "<new>" to
                            // back to its previous job name
                            jobManager.setActiveJob(jobManager.getActiveJob());
                            int jobChoiceIdx = getJobChoiceIndex(jobManager.getActiveJob().getName());
                            if (jobChoiceIdx > 0) {
                                jobChoice.getSelectionModel().select(jobChoiceIdx);
                            }
                        } else {
                            // $adhoc$ job
                            jobManager.init(new Job("$adhoc$"));
                        }
                    }
                }
            } else {
                if (!jobName.equals("$adhoc$")) {
                    Job job = Job.load(jobName);
                    jobManager.setActiveJob(job);
                }

            }
        });
        int i = 0;
        for (String item: (ObservableList<String>)jobChoice.getItems()) {
            String jobName = jobManager.getActiveJob().getName();
            if (jobName.equals(item)) {
                jobChoice.getSelectionModel().select(i);
                break;
            } else if (jobName.isEmpty()) {
                jobChoice.getSelectionModel().select(1);
            }
            i++;
        }
        chooseDir = (Button) jobRoot.lookup("#chooseDir");
        chooseDir.setOnAction(event->{
            chooseBackupLocation();
        });
        destinationDir = (Text ) jobRoot.lookup("#destinationDir");

        always = (RadioButton)jobRoot.lookup("#always");
        always.setOnAction(e->changeFileReplacementPolicy());
        byDate = (RadioButton)jobRoot.lookup("#byDate");
        byDate.setOnAction(e->changeFileReplacementPolicy());

        alwaysPrompt = (RadioButton)jobRoot.lookup("#alwaysPrompt");
        alwaysPrompt.setOnAction(e->toggleAlwaysPrompt());

        encryptionEnabled = (ChoiceBox) jobRoot.lookup("#encryptionEnabled");
        encryptionEnabled.getItems().add("disabled");
        encryptionEnabled.getItems().add("enabled");
        encryptionEnabled.getSelectionModel().select(0);
        encryptionEnabled.setOnAction(e->encryptionChoice());

        addJobName = (RadioButton) jobRoot.lookup("#addJobName");
        addJobName.setOnAction(e-> toggleAddJobName());
        addDate = (RadioButton) jobRoot.lookup("#addDate");
        addDate.setOnAction(e->toggleAddDate());
        numDatesLabel = (VBox) jobRoot.lookup("#numDatesLabel");
        numDates = (TextField) jobRoot.lookup("#numDates");
        //numDates.setOnKeyReleased(e->setNumDates());
        numDates.textProperty().addListener(new ChangeListener<String>() {
            @Override
            public void changed(ObservableValue<? extends String> observable,
                                String oldValue, String newValue) {
                String numDateField = numDates.getText();
                if (newValue.isEmpty() || newValue.matches("-?\\d+")) {
                    if (!newValue.isEmpty() && Integer.valueOf(newValue) <= 0) {
                        App.getUIManager().showErrorMessage("value must be greater than 0", "Input Error");
                        numDates.setText(oldValue);
                    } else {
                        dateDir.setValue(newValue);
                    }
                } else {
                    App.getUIManager().showErrorMessage("only integer values greater than 0 are allowed", "Input Error");
                    numDates.setText(oldValue);
                }
            }

        });

        ui.addPageNode(jobsNodeName, this, jobRoot);
    }

    int getJobChoiceIndex (String jobName) {

        int idx = 0;
        ObservableList jobNames = jobChoice.getItems();
        for (; idx < jobNames.size() ;idx++) {
            if (jobNames.get(idx).equals(jobName)) return idx;
        }
        return -1;
    }


    private void chooseBackupLocation() {
        Job job = jobManager.getActiveJob();
        DirectoryChooser dirChooser = new DirectoryChooser();
        dirChooser.setTitle("Select Backup Location");
        File selectedDir = dirChooser.showDialog(ui.getStage());
        if (selectedDir != null) {
            String destination = selectedDir.getPath();
            if (destination.isEmpty()) {
                destinationDir.setText("");
                alwaysPrompt.setSelected(true);
                job.setAlwaysPrompt(true);
                job.setDestination("");
            } else {
                if (destination.equals("$adhoc$")) {
                    destinationDir.setText("");
                    job.setAlwaysPrompt(true);
                    alwaysPrompt.setSelected(true);
                } else {
                    destinationDir.setText(destination);
                    job.setDestination(destination);
                    job.setAlwaysPrompt(false);
                    alwaysPrompt.setSelected(false);
                }
            }
        }
    }

    Job createNewJob() {
        String name = jobManager.getActiveJob().getName();
        Job job = jobManager.createJob();
        // test whether a new job was actually created.  We do this by seeing if the name was changed
        if (job.getName().equals(name)) {
            // the job name wasn't changed
            jobChoice.setValue(job.getName());
        } else {
            // we got a new job with a new name assigned to it
            jobManager.setActiveJob( job);
            jobChoice.getItems().add(job.getName());
            jobChoice.setValue(job.getName());
            if (job.getDestination() == null || job.getDestination().isEmpty()) {
                destinationDir.setText("<destination not specified>");
            } else {
                destinationDir.setText(job.getDestination());
            }
        }
        return job;
    }

    /**
     * select the job with the given name.  This method can be called with
     * even if the
     * @param name the name of the job to selecgt
     * @return the job
     */
    Job selectJob(String name) {
        Job job = null;
        if (!(name.equals("<new>") || name.equals("$adhoc$"))) {
            job = Job.load(name);
            if (job.getDestination() == null || job.getDestination().isEmpty()) {
                destinationDir.setText("<destination not specified>");
            } else {
                destinationDir.setText(job.getDestination());
                destinationDir.setVisible(true);
                alwaysPrompt.setSelected(false);
                always.setSelected(job.getReplacementPolicy().equals("always"));
                byDate.setSelected(job.getReplacementPolicy().equals("byDate"));
            }
        } else {
            job = jobManager.getActiveJob();
            if (job.getAlwaysPrompt()) {
                destinationDir.setText("");
                alwaysPrompt.setSelected(false);
            } else {
                destinationDir.setText(job.getDestination());
                alwaysPrompt.setSelected(false);
            }
            if (job.getReplacementPolicy().equals("always")) {
                if (byDate != null) byDate.setSelected(false);
                if (always != null) always.setSelected(true);
            } else {
                // byDate
                if (byDate != null) byDate.setSelected(true);
                if (always != null)always.setSelected(false);
            }
        }
        if (job.getOptions().contains(Job.option.encrypt)) {
            encryptionEnabled.getSelectionModel().select(1);
        } else {
            encryptionEnabled.getSelectionModel().select(0);
        }
        if  (job.getOptions().contains(jobDir)) {
            addJobName.setSelected(true);
        } else {
            addJobName.setSelected(false);
        }
        if  (job.getOptions().contains(dateDir)) {
            addDate.setSelected(true);
            numDates.setText(dateDir.getValue());
            numDates.setVisible(true);
            numDatesLabel.setVisible(true);
        } else {
            addDate.setSelected(false);
            numDates.setVisible(false);
            numDatesLabel.setVisible(false);
        }
        return job;
    }

    synchronized void changeFileReplacementPolicy() {
        if (isAlwaysReplace) {
            always.setSelected(false);
            byDate.setSelected(true);
            isAlwaysReplace = false;
            jobManager.getActiveJob().setReplacementPolicy("byDate");
        } else {
            always.setSelected(true);
            byDate.setSelected(false);
            isAlwaysReplace = true;
            jobManager.getActiveJob().setReplacementPolicy("always");
        }
    }

    synchronized void toggleAlwaysPrompt() {
        Job job = jobManager.getActiveJob();
        if (job.getAlwaysPrompt()) {
            destinationDir.setText("");
            alwaysPrompt.setSelected(false);
            job.setAlwaysPrompt(false);
        } else {
            alwaysPrompt.setSelected(true);
            destinationDir.setText("");
            job.setAlwaysPrompt(true);
        }
    }

    synchronized void encryptionChoice() {
        String isEncypted = (String)encryptionEnabled.getSelectionModel().getSelectedItem();
        if (isEncypted.equals("enabled")) {
            isEncryptionEnabled = true;
            jobManager.getActiveJob().addOption(Job.option.encrypt);

        } else if (isEncypted.equals("disabled")) {
            isEncryptionEnabled = false;
            jobManager.getActiveJob().removeOption(Job.option.encrypt);
        }
    }

    synchronized void toggleAddJobName() {
        if (jobManager.getActiveJob().getOptions().contains(jobDir)) {
            jobManager.getActiveJob().getOptions().remove(jobDir);
        } else {
            jobManager.getActiveJob().addOption(jobDir);
        }
    }

    synchronized void toggleAddDate() {
        if (jobManager.getActiveJob().getOptions().contains(dateDir)) {
            jobManager.getActiveJob().getOptions().remove(dateDir);
            numDates.setVisible(false);
            numDatesLabel.setVisible(false);
        } else {
            jobManager.getActiveJob().addOption(dateDir);
            numDates.setText("3");              // three is the default value
            dateDir.setValue("3");
            numDates.setVisible(true);
            numDatesLabel.setVisible(true);
        }
    }

    synchronized  void setNumDates() {
        String numDateField = numDates.getText();
        if (numDateField.matches("-?\\d+")) {
            dateDir.setValue(numDates.getText());
        } else {
            App.getUIManager().showErrorMessage("only integer values are allowed", "Input Error");
            numDates.setText(numDates.getText().substring(0, numDates.getText().length()-1));
        }
    }

    @Override
    public Node getNode() {
        return jobRoot;
    }

    @Override
    public String getName() {
        return jobsNodeName;
    }

    @Override
    public void onLeavePage() {
        Job activeJob = jobManager.getActiveJob();
        if (activeJob != null) {
            activeJob.save();
        }
    }
}
