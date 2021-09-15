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

import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.stage.Stage;

import java.util.List;

public interface UI {
    enum OS {
        WINDOWS, LINUX, MAC, SOLARIS
    };

    Stage getStage();
    Parent getParent();
    void addPageNode(String name, com.phinneyridge.tools.backup.PageNode pageNode, Node node);
    boolean showPageNode(String nodeName);
    void setJobTitle(String jobName);
    void showThrowable(String title, String header, Throwable e);
    void showErrorMessage(String error, String title);
    void showMessage(String message, String title);
    String selectChoice(List<String> choices, String title, String header, String query, boolean reverseOrder);
    OS getOperatingSystem();

    /**
     * show the ProgressBar with indeterminate value
     */
    void showProgressBar();

    /**
     * show the ProgressBar with the given initial value
     * @param s initial progress  (0.0 - 1.0)
     */
    void showProgressBar(double s);

    /**
     * set the ProgressBar's value
     * @param v progress value (0.0 - 1.0)
     */
    void setProgress(double v);

    /**
     * hide the ProgressBar()
     */
    void hideProgressBar();
    void close();

    void showPopup(String text, int seconds);
}
