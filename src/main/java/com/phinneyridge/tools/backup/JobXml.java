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

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.Stack;

public class JobXml extends DefaultHandler {

    private int level = -1;     // xml element level
                                // level -1 means documented not started or ended
                                // level 0 means that the document has started
                                // Level 1  is the root plan xml element
                                // level 1+ is the nested level of the current xml element
    private enum xmlFileType {job, nodes};
    private xmlFileType currentXmlFileType;
    private JobManager jobManager;
    private UI ui;
    private Job job; // current job being parsed
    private Stack<String> elementStack = new Stack<>();
    private String parentNode;

    public JobXml() {
        jobManager = App.getJobManager();
        ui = App.getUIManager();
    }

    /**
     * this parses the Job xml file and returns the job
     * @return the Job. If the returned value is null, that means that there was an error
     * parsing the job xml file.
     */
    public synchronized Job parseJob(String jobName) {
        job = new Job(jobName);
        File file = jobManager.getJobFile(jobName);
        SAXParserFactory f = SAXParserFactory.newInstance();
        SAXParser parser = null;
        try {
            parser = f.newSAXParser();
            level = -1;
            currentXmlFileType = xmlFileType.job;
            parser.parse(file, this);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            ui.showThrowable("Exception","exception thrown parsing job_node xml file", e);
            return null;
        }
        return job;
    }


    /**
     * this parses the Job_node xml file and adds the nodes to the give job
     * @param job the job that the nodes must be added to.  Please note that the
     *            "nodes' xml element has a name element and that name is validated that
     *            it matches the given Job's name.
     * @return true if the job_node file was parsed without any errors, else false
     */
    public synchronized boolean parseNodes(Job job) {
        boolean success = true;
        this.job = job;
        File file = jobManager.getJobNodeFile(job.getName());
        SAXParserFactory f = SAXParserFactory.newInstance();
        SAXParser parser = null;
        try {
            parser = f.newSAXParser();
            level = -1;
            currentXmlFileType = xmlFileType.nodes;
            parser.parse(file, this);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            ui.showThrowable("Exception","exception thrown parsing job_node xml file", e);
            success = false;
        }
        return success;
    }

    @Override
    protected Object clone() throws CloneNotSupportedException {
        return super.clone();
    }

    public int getLevel() {
        return level;
    }

    private void incrementLevel() {
        level++;
    }

    private void decrementLevel() {
        level--;
    }

    @Override
    public void startDocument() throws SAXException {
        incrementLevel();
    }

    @Override
    public void endDocument() throws SAXException {
        decrementLevel();
    }
    public void startElement(String uri, String localName, String qName, Attributes attributes)
            throws SAXException {

        String name;
        if (elementStack.isEmpty()) {
            parentNode = "";
        } else {
            parentNode = elementStack.peek();
        }
        elementStack.push(qName);
        incrementLevel();
        switch (currentXmlFileType) {
            case job:
                if (getLevel() == 1) {
                    if (!qName.equals("job")) {
                        throw new SAXException("expected job for root element, but found " + qName + " instead");
                    }
                    name = attributes.getValue("name");
                    if (name == null || name.isEmpty()) {
                        throw new SAXException("job element missing required name attribute");
                    }
                    if (!name.equals(job.getName())) {
                        throw new SAXException("the job's xml name does not match its file name");
                    }
                } else if (getLevel() == 2) {
                    String type;
                    String value;
                    switch (qName) {
                        case "destination":
                            String destinationDir = attributes.getValue("dir");
                            if (destinationDir == null || destinationDir.isEmpty()) {
                                throw new SAXException("destination element missing required dir attribute");
                            }
                            job.setDestination(destinationDir);
                            break;
                        case "mode":
                            type = attributes.getValue("type");
                            if (!job.setMode(type)) {
                                throw new SAXException("mode elements type attribute has an invalid value");
                            }
                            break;
                        case "options":
                            break;
                        case "replacementPolicy":
                            value = attributes.getValue("value");
                            job.setReplacementPolicy(value);
                            break;
                        default:
                            throw new SAXException("unexpected element " + qName + " found for parent element " + parentNode);
                    }
                } else if(getLevel() == 3) {
                    if (qName.equals("option")) {
                        name = attributes.getValue("name");
                        if (name == null || name.isEmpty()) {
                            throw new SAXException("option element missing required name attribute");
                        }
                        if (!job.addOption(name)) {
                            throw new SAXException("option elements name attribute has an invalid value");
                        }
                        String value = attributes.getValue("value");
                        if (value != null && !value.isEmpty()) {
                            job.getOption(name).setValue(value);
                        }
                    } else {
                        throw new SAXException("unexpected element " + qName + " found for parent element " + parentNode);
                    }
                } else {
                    throw new SAXException("unexpected element " + qName + " found for parent element " + parentNode);
                }
                break;
            case nodes:
                if (getLevel() == 1) {
                    if (!qName.equals("nodes")) {
                        throw new SAXException("expected node for root element, but found " + qName + " instead");
                    }
                    name  = attributes.getValue("name");
                    if (name == null || name.isEmpty()) {
                        throw new SAXException("nodes element missing required name attribute");
                    }
                    if (!name.equals(job.getName())) {
                        throw new SAXException("the job's xml name does not match its file name");
                    }
                } else if (getLevel() == 2) {
                    if (!qName.equals("node")) {
                        throw new SAXException("expected node element, but found " + qName + " instead");
                    } else {
                        String nodePath = attributes.getValue("path");
                        if (nodePath == null || nodePath.isEmpty()) {
                            throw new SAXException("node element is missing required attribute path");
                        }
                        job.addNode(nodePath);
                    }
                } else {
                    throw new SAXException("unexpected element " + qName + " found for parent element " + parentNode);
                }
                break;
        }
    }
    @Override
    public void endElement(String uri, String localName, String qName) {
        elementStack.pop();
        if (elementStack.isEmpty()) {
            parentNode = "";
        } else {
            // pop the current element so we can get it parent element
            // after we got the parent element, we'll push the current element back on the stack
            String currentElement = elementStack.pop();
            if (elementStack.isEmpty()) {
                parentNode = null;
            } else {
                parentNode = elementStack.peek();
            }
            elementStack.push(currentElement);
        }
        decrementLevel();
    }



    /**
     * save the given job in the job and job_node xml files
     * @param job
     * @return
     */
    void save(Job job) throws FileNotFoundException, XMLStreamException {
        if (job.hasJobChanged)  saveJob(job);
        if (job.haveNodesChanged)  saveNodes(job);
    }

    private void saveJob(Job job) throws FileNotFoundException, XMLStreamException {

        XMLOutputFactory output = XMLOutputFactory.newInstance();
        File jobFile = job.getJobFile();
        jobFile.getParentFile().mkdirs();
        XMLStreamWriter writer = output.createXMLStreamWriter(
                new FileOutputStream(jobFile));
        writer.writeStartDocument();
        writer.writeCharacters("\n");
        writer.writeStartElement("job");
        writer.writeAttribute("name", job.getName());
        writer.writeAttribute("version", "1.0.0");
        writer.writeCharacters("\n");

            String destination = job.getDestination();
            if (destination != null && !destination.isEmpty()) {
                writer.writeStartElement("destination");
                writer.writeAttribute("dir", destination);
                writer.writeEndElement();
                writer.writeCharacters("\n");
            }

            writer.writeStartElement("mode");
            writer.writeAttribute("type", job.getJobModeName());
            writer.writeEndElement();
            writer.writeCharacters("\n");

            writer.writeStartElement("replacementPolicy");
            writer.writeAttribute("value", job.getReplacementPolicy());
            writer.writeEndElement();

            Set<Job.option> options = job.getOptions();
            if (!options.isEmpty()) {
                writer.writeStartElement("options");
                writer.writeCharacters("\n");
                for (Job.option option: options) {
                    writer.writeStartElement("option");
                    writer.writeAttribute("name", option.name());
                    String optionValue = option.getValue();
                    if (optionValue != null && !optionValue.isEmpty()) {
                        writer.writeAttribute("value", optionValue);
                    }
                    writer.writeEndElement();
                    writer.writeCharacters("\n");
                }
                writer.writeEndElement();
            }

        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeEndDocument();
        writer.flush();
        writer.close();
    }

    private void saveNodes(Job job) throws FileNotFoundException, XMLStreamException {
        XMLOutputFactory output = XMLOutputFactory.newInstance();
        XMLStreamWriter writer = output.createXMLStreamWriter(
                new FileOutputStream(job.getJobNodeFile()));
        writer.writeStartDocument();
        writer.writeCharacters("\n");
        writer.writeStartElement("nodes");
        writer.writeAttribute("name",job.getName());
        writer.writeCharacters("\n");

            List<String> paths = job.getNodes();
            if (paths != null && !paths.isEmpty()) {
                for (String path: paths) {
                    writer.writeStartElement("node");
                    writer.writeAttribute("path", path);
                    writer.writeEndElement();
                    writer.writeCharacters("\n");
                }
            }

        writer.writeEndElement();
        writer.writeCharacters("\n");
        writer.writeEndDocument();
        writer.flush();
        writer.close();

    }
}
