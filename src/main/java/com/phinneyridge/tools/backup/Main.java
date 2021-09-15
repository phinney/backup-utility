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

import javafx.application.Application;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.Event;
import javafx.event.EventHandler;
import javafx.stage.Stage;

public class Main extends Application implements AppInterface {
    private UIManager ui;
    private JobManager jobManager;
    private FileSelection fileSelection;

    private Stage stage;

    @Override
    public void start(Stage stage) throws Exception{
        App.setApp(this);
        ui = new UIManager(stage);
        jobManager = new JobManager();
        jobManager.init(new Job("$adhoc$"));
        Task<Void> worker = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                preparingNode = "jobs";
                new JobNode();
                preparingNode = "execute";
                new ExecuteNode();
                preparingNode = "web";
                new WebNode();
                return null;
            }
        };
        worker.setOnFailed(backgroundEvent);
        worker.setOnSucceeded(backgroundEvent);
        worker.run();
    }
    private String preparingNode;

    EventHandler backgroundEvent = new EventHandler() {

        @Override
        public void handle(Event event) {
            if (event.getEventType() == WorkerStateEvent.WORKER_STATE_FAILED) {
                ui.showErrorMessage("error occurred preparing node: " + preparingNode,"PageNode Error");
            }
        }
    };


    public static void main(String[] args) {
        System.out.println(("args: " + args));

        launch(args);
    }

    @Override
    public UIManager getUIManager() {
        return ui;
    }

    @Override
    public JobManager getJobManager() {
        return jobManager;
    }

    @Override
    public void setFileSelection (FileSelection fileSelection) {
        this.fileSelection = fileSelection;
    }

    @Override
    public FileSelection getFileSelection() {
        return fileSelection;
    }
}
