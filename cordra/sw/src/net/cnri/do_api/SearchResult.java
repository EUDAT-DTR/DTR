/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.do_api;

import java.util.LinkedHashMap;
import java.util.Map;

import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;
import net.cnri.util.StringUtils;

public class SearchResult {
    public static class Element {
        private final String elementID;
        private String text;
        private Map<String,String> fields;
        private Map<String,String> atts;
        private Map<String,String> pAtts;
        private Map<String,String> mpAtts;
        
        public Element(String elementID) {
            this.elementID = elementID;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public Map<String,String> getFields() {
            return fields;
        }

        public void setField(String name, String value) {
            if(this.fields == null) this.fields = new LinkedHashMap<String,String>();
            this.fields.put(name,value);
        }

        public Map<String,String> getAtts() {
            return atts;
        }

        public void setAtt(String name, String value) {
            if(this.atts == null) this.atts = new LinkedHashMap<String,String>();
            this.atts.put(name,value);
        }

        public Map<String,String> getPAtts() {
            return pAtts;
        }

        public void setPAtt(String name, String value) {
            if(this.pAtts == null) this.pAtts = new LinkedHashMap<String,String>();
            this.pAtts.put(name,value);
        }

        public Map<String,String> getMPAtts() {
            return mpAtts;
        }

        public void setMPAtt(String name, String value) {
            if(this.mpAtts == null) this.mpAtts = new LinkedHashMap<String,String>();
            this.mpAtts.put(name,value);
        }

        public String getElementID() {
            return elementID;
        }
    }

    private String objectID;
    private String repoID;
    private String score;
    private Map<String,String> fields;
    private Map<String,String> atts;
    private Map<String,Element> elements;
    
    public SearchResult() {}

    public Map<String,String> getFields() {
        return fields;
    }

    public void setField(String name, String value) {
        if(this.fields == null) this.fields = new LinkedHashMap<String,String>();
        this.fields.put(name,value);
    }

    public Map<String,String> getAtts() {
        return atts;
    }

    public void setAtt(String name, String value) {
        if(this.atts == null) this.atts = new LinkedHashMap<String,String>();
        this.atts.put(name,value);
    }

    public Map<String,Element> getElements() {
        return elements;
    }

    public Element getElement(String elementID) {
        if(elements==null) elements = new LinkedHashMap<String,Element>();
        Element res = elements.get(elementID);
        if(res==null) {
            res = new Element(elementID);
            elements.put(elementID,res);
        }
        return res;
    }
    
    public void setElements(Map<String,Element> elements) {
        this.elements = elements;
    }

    public String getObjectID() {
        return objectID;
    }

    public String getRepoID() {
        return repoID;
    }

    public String getScore() {
        return score;
    }

    public void setObjectID(String objectID) {
        this.objectID = objectID;
    }

    public void setRepoID(String repoID) {
        this.repoID = repoID;
    }

    public void setScore(String score) {
        this.score = score;
    }

    public static SearchResult ofHeaderSet(HeaderSet in) {
        SearchResult res = new SearchResult();
        for(HeaderItem item : in) {
            String name = item.getName();
            String value = item.getValue();
            
            if(name==null) continue;
            if(value==null) value = "";
            
            if(name.equals("objectid")) res.setObjectID(value);
            else if(name.equals("repoid")) res.setRepoID(value);
            else if(name.equals("score")) res.setScore(value);
            else {
                if(name.startsWith("field:")) name = name.substring("field:".length());
                if(name.startsWith("objatt_")) {
                    name = name.substring("objatt_".length());
                    res.setAtt(name,value);
                }
                else {
                    int underscore1;
                    if(name.startsWith("el") && (underscore1 = name.indexOf("_")) >= 0) {
                        int underscore2 = -1;
                        String type = name.substring(2,underscore1);
                        if(type.equals("att") || type.equals("patt") || type.equals("mpatt")) {
                            underscore2 = name.indexOf("_",underscore1+1);
                        }
                        if(underscore2 < 0) {
                            String elementID = name.substring(underscore1+1);
                            name = name.substring(2,underscore1);
                            Element el = res.getElement(elementID);
                            if(name.equals("text")) {
                                el.setText(value);
                            }
                            else {
                                el.setField(name,value);
                            }
                        }
                        else {
                            String elementID = StringUtils.decodeURLIgnorePlus(name.substring(underscore1+1,underscore2));
                            name = name.substring(underscore2+1);
                            Element el = res.getElement(elementID);
                            if(type.equals("att")) el.setAtt(name,value);
                            else if(type.equals("patt")) el.setPAtt(name,value);
                            else if(type.equals("mpatt")) el.setMPAtt(name,value);
//                            else el.setField(type + "_" + name, value);
                        }
                    }
                    else {
                        if("id".equals(name) && value.equals(res.getObjectID())) continue;
                        if("repoid".equals(name) && value.equals(res.getRepoID())) continue;
                        res.setField(name,value);
                    }
                }
            }
        }
        return res;
    }
}
