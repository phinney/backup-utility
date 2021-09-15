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

import javax.xml.stream.XMLStreamException;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class Job {
    private JobManager jobManager;
    private String name;
    private File jobFile;
    private File jobNodeFile;
    private String destination;
    private File destinationFile;
    private boolean alwaysPrompt = true; // if true, always Prompt for destination when run
    protected boolean hasJobChanged = true;
    protected boolean haveNodesChanged = true;
    private List<String> nodes = new LinkedList<>();
    private EnumMap<option, String> options = new EnumMap<>(option.class);

    private JobXml jobXml = null;
    private JobXml getJobXml() {
        if (jobXml == null) {
            jobXml = new JobXml();
        }
        return jobXml;
    }
    public enum replacementPolicy {
        always, // always replace destination from source
        byDate // only replace when src file last mod date is
               // greater than corresponding destination file last mod date.
    }
    private replacementPolicy currentReplacementPolicy = replacementPolicy.always;

    public enum mode {
        copy,  // copy to file and directory
        zip,   // copy to zip file
        vcs    // copy to version control system
    }
    private mode jobMode;

    private boolean isBackupMode = true;

    public boolean isBackupJob() {
        return isBackupMode;
    }
    public boolean isRestoreJob() {
        return !isBackupMode;
    }

    public void setBackupMode (boolean isBackupMode) {
        this.isBackupMode = isBackupMode;
    }

    public enum option {
        jobDir,      // add job name directory to destination dir
        dateDir,     // add job execution Date director to destination dir
        encrypt;      // encrypt file content
        private String value;
        public void setValue(String value) {
            this.value = value;
        }
        public String getValue() {
            return value;
        }

    }


    private String password;

    public void setPassword(String pw) {
        password = pw;
    }
    public String getPassword() {
        return password;
    }

    public Job(String name) {
        this.name = name;
        jobManager = App.getJobManager();
        jobFile = jobManager.getJobFile(name);
        jobNodeFile = jobManager.getJobNodeFile(name);
        jobMode = mode.copy;  // default job mode
    }

    public String getName() {
        return name;
    }

    public File getJobFile() {
        return jobFile;
    }

    public File getJobNodeFile() {
        return jobNodeFile;
    }

    boolean exists() {
        return jobFile.exists();
    }

    void createJobFile() throws IOException {
        if (!jobFile.exists()) {
            jobFile.getParentFile().mkdirs();
            jobFile.createNewFile();
        }
    }

    public static Job load(String name) {
        JobXml xml = new JobXml();
        Job job = xml.parseJob(name);
        if (job == null) {
            App.getUIManager().showErrorMessage("Error","an error occurred parsing job xml file");
            return  null;
        }
        if (!xml.parseNodes(job)) {
            App.getUIManager().showErrorMessage("Error","an error occurred parsing job_node xml file");
            return  null;
        }
        return job;
    }

    boolean save() {
        try {
            getJobXml().save(this);
        } catch (XMLStreamException | FileNotFoundException e) {
           return false;
        }
        hasJobChanged = false;
        haveNodesChanged = false;
        return true;
    }

    void setDestination(String destination) {
        this.destination = destination;
        this.destinationFile = new File(destination);
        hasJobChanged = true;
    }

    String getDestination(){
        return destination;
    }

    void setDestinationFile(File destinationFile) {
        this.destinationFile = destinationFile;
        this.destination = destinationFile.getPath();
        hasJobChanged = true;
    }

    /**
     * Get the Destination at runtime.  At runtime, the destination file may have
     * additional paths added (i.e. Job and/or date paths may be added) This method
     * is called from performBackup.
     * @param date the date used for creating a date path.  This is only used
     *             if the job has the dateDir option set
     * @return the adjusted runtime destination
     */
    String getRuntimeDestination(Date date) {
        String runtimePath = "";
        if (getOptions().contains(option.jobDir)) {
            runtimePath +=  "\\" + getName();
        }
        if (getOptions().contains(option.dateDir)) {
            String pattern = "yyyy-MM-dd@HH-mm-ss"; // format selected for natural sort
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat(pattern);
            String dateDirName = simpleDateFormat.format(date);
            runtimePath +=  "\\" + dateDirName;
        }
        return  getDestination() + runtimePath;

    }

    /**
     * Get the Destination at runtime.  At runtime, the destination file may have
     * additional paths added (i.e. Job and/or date paths may be added) This method is
     * called from performRestore.
     * @param datePath the datePath used for creating a date path.  This is only used
     *             if the job has the dateDir option set
     * @return the adjusted runtime destination
     */
    String getRuntimeDestination(String datePath) {
        String runtimePath = "";
        if (getOptions().contains(option.jobDir)) {
            runtimePath += jobManager.getActiveJob().getName() + "\\";
        }
        if (getOptions().contains(option.dateDir)) {
            if (!datePath.isEmpty()) {
                runtimePath += datePath + "\\";
            }
        }
        return getDestination() + "\\" + runtimePath;
    }

    File getDestinationFile() {
        return destinationFile;
    }

    List<String> getNodes() {
        return nodes;
    }

    void addNode(String path) {
        nodes.add(path);
        haveNodesChanged = true;
    }

    void setNodes(List<String> paths) {
        // check if the paths were really changed
        if (nodes.size() != paths.size()) {
            haveNodesChanged = true;
        } else {
            int idx = 0;
            for (String path : paths) {
                if (!nodes.get(idx).equals(path)) {
                    haveNodesChanged = true;
                    break;
                }
            }
        }
        if (haveNodesChanged == true) {
            nodes = paths;
        }
    }

    Set<option> getOptions(){
        return options.keySet();
    }

    void addOption (option optionValue) {
        options.put(optionValue, optionValue.name());
    }

    void removeOption(option optionValue) {
        options.remove(optionValue);
    }

    void removeOption(String name) {
        options.remove(option.valueOf(name));
    }

    boolean addOption(String name) {
        option opt = option.valueOf(name);
        if (opt == null) return false;
        options.put(opt, name);
        return true;
    }

    option getOption(String name) {
        return option.valueOf(name);
    }

    boolean doesJobContainOption(String name) {
        return options.containsValue(name);
    }

    boolean doesJobContainOption(option key) {
        return options.containsKey(key);
    }

    boolean setMode(String modeName) {
        mode m = mode.valueOf(modeName);
        if (m == null) return false;
        jobMode = m;
        return true;
    }

    public void setMode(mode m) {
        jobMode = m;
    }
    mode getJobMode() {
        return jobMode;
    }
    public String getJobModeName() {
        return jobMode.name();
    }

    public void setReplacementPolicy(String name) {
        try {
            currentReplacementPolicy = replacementPolicy.valueOf(name);
        } catch (Exception e) {
            // invalid name - default to always
            currentReplacementPolicy = replacementPolicy.always;
        }
    }
    public String getReplacementPolicy(){
        return currentReplacementPolicy.name();
    }

    public int getNumberOfDatePaths(){
        if (getOptions().contains(option.dateDir)) {
            return Integer.valueOf(option.dateDir.getValue());
        } else {
            return 0;
        }
    }

    public void setNumberOfDatePaths(Integer count) {
        option.dateDir.setValue(count.toString());
    }

    public boolean isEncryptionEnabled() {
        return getOptions().contains(option.encrypt)?true:false;
    }
    public boolean appendJobName() {
        return getOptions().contains(option.jobDir)?true:false;
    }
    public boolean appendDate() {
        return getOptions().contains(option.dateDir)?true:false;
    }

    public void setAlwaysPrompt(boolean alwaysPrompt) {
        this.alwaysPrompt = alwaysPrompt;
    }

    public boolean getAlwaysPrompt() {
        return alwaysPrompt;
    }
}
