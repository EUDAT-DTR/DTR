package net.cnri.repository.util;

/*************************************************************************\
Copyright (c) 2015 Corporation for National Research Initiatives;
                    All rights reserved.
 The CNRI open source license for this software is available at
              http://hdl.handle.net/20.1000/106
\*************************************************************************/

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.CollectionUtil;
import net.cnri.util.StreamUtil;
import net.cnri.util.StringUtils;

public class FileSystemDigitalObjectUtil {

    public static final String ATTRIBUTES_DIR = "attributes";
    public static final String ELEMENTS_DIR = "elements";

    public static DigitalObject loadDigitalObjectFromDir(String objectDirName, Repository repo) throws RepositoryException, IOException {
        File objectDir = new File(objectDirName);
        DigitalObject dobj = loadDigitalObjectFromDir(objectDir, repo);
        return dobj;
    }

    public static List<DigitalObject> loadAllObjectsFromDir(File objectsDir, Repository repo) throws RepositoryException, IOException {
        List<DigitalObject> result = new ArrayList<DigitalObject>();
        File[] objectDirs = objectsDir.listFiles();
        for (File objectDir : objectDirs) {
            String filename = objectDir.getName();
            if (".DS_Store".equals(filename)) {
                continue;
            } 
            DigitalObject dobj = loadDigitalObjectFromDir(objectDir, repo);
            result.add(dobj);
        }
        return result;
    }

    public static DigitalObject loadDigitalObjectFromDir(File objectDir, Repository repo) throws RepositoryException, IOException {
        File attributesDir = new File(objectDir, ATTRIBUTES_DIR);
        File elementsDir = new File(objectDir, ELEMENTS_DIR);
        String objectId = StringUtils.decodeURLIgnorePlus(objectDir.getName());
        DigitalObject dobj = repo.getOrCreateDigitalObject(objectId);
        cleanExistingObject(dobj);
        loadAttributes(attributesDir, dobj);
        loadElements(elementsDir, dobj);
        return dobj;
    }

    private static void cleanExistingObject(DigitalObject dobj) throws RepositoryException {
        Map<String, String> attributes = dobj.getAttributes();
        for (String attribute : attributes.keySet()) {
            dobj.deleteAttribute(attribute);
        }
        List<String> elementNames = CollectionUtil.asList(dobj.listDataElementNames());
        for (String elementName : elementNames) {
            dobj.deleteDataElement(elementName);
        }
    }

    private static void loadAttributes(File attributesDir, DigitalObject dobj) throws IOException, RepositoryException {
        File[] attributeFiles = attributesDir.listFiles();
        for (File file : attributeFiles) {
            String attributeName = file.getName();
            if (".DS_Store".equals(attributeName)) {
                continue;
            }
            attributeName = StringUtils.decodeURLIgnorePlus(attributeName);
            String value = StreamUtil.readFullyAsString(file);
            dobj.setAttribute(attributeName, value);
        }
    }

    private static void loadElements(File elementsDir, DigitalObject dobj) throws CreationException, RepositoryException, IOException {
        File[] elements = elementsDir.listFiles();
        for (File elementDir : elements) {
            String filename = elementDir.getName();
            if (".DS_Store".equals(filename)) {
                continue;
            }
            loadElement(elementDir, dobj);
        }
    }

    private static void loadElement(File elementDir, DigitalObject dobj) throws CreationException, RepositoryException, IOException {
        String elementName = StringUtils.decodeURLIgnorePlus(elementDir.getName());
        DataElement element = dobj.createDataElement(elementName);

        File[] files = elementDir.listFiles();

        File attributesDir = new File(elementDir, ATTRIBUTES_DIR);
        if (attributesDir.exists()) {
            loadElementAttributes(attributesDir, element);
        }

        for (File file : files) {
            String filename = file.getName();
            if (filename.equals(ATTRIBUTES_DIR)) {
                continue;
            } else if (".DS_Store".equals(filename)) {
                continue;
            } else {
                if (element.getAttribute("filename") == null) element.setAttribute("filename", filename);
                FileInputStream in = new FileInputStream(file);
                element.write(in);
                in.close();
                break;
            }
        }
    }

    private static void loadElementAttributes(File attributesDir, DataElement element) throws IOException, RepositoryException {
        File[] attributeFiles = attributesDir.listFiles();
        for (File file : attributeFiles) {
            String attributeName = file.getName();
            if (".DS_Store".equals(attributeName)) {
                continue;
            }
            attributeName = StringUtils.decodeURLIgnorePlus(attributeName);
            String value = StreamUtil.readFullyAsString(file);
            element.setAttribute(attributeName, value);
        }
    }
}
