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


import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.web.WebView;

import java.net.URL;

public class WebNode implements PageNode{

    private final String webNodeName = "web";
    private JobManager jobManager;
    private UI  ui;
    private Parent webRoot;
    private WebView webView;


    public WebNode() {
        jobManager = App.getJobManager();
        ui = App.getUIManager();
        try {
            webRoot = FXMLLoader.load(getClass().getResource("/layouts/web.fxml"));
        } catch (Exception e) {
            ui.showThrowable("Fatal Error","error loading job root layout", e);
            ui.close();
        }
        webView = (WebView)webRoot.lookup("#webView");
        ui.addPageNode(webNodeName, this, webRoot);


    }
    @Override
    public Node getNode() {
        return webRoot;
    }

    @Override
    public String getName() {
        return webNodeName;
    }

    @Override
    public void onLeavePage() {

    }

    public void load(String htmlContent) {
        webView.getEngine().loadContent(htmlContent);
    }

    public void load(URL url) {
        webView.getEngine().load(url.toString());
    }

}
