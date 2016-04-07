/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.cordra.api;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class CordraObject {

    public String id;
    public String type;
    public JsonElement content;
    public AccessControlList acl;
    public Metadata metadata;
    public List<Payload> payloads = new ArrayList<Payload>();
    
    private List<String> payloadsToDelete = new ArrayList<String>();
    
    public CordraObject() { }
    
    public CordraObject(String type, String json) {
        this.type = type;
        setContent(json);
    }
    
    public CordraObject(String type, JsonElement content) {
        this.type = type;
        this.content = content;
    }
    
    public CordraObject(String type, Object object) {
        this.type = type;
        Gson gson = new Gson();
        this.content = gson.toJsonTree(object);
    }
    
    public void setContent(String json) {
        content = new JsonParser().parse(json);
    }
    
    public void setContent(Object object) {
        Gson gson = new Gson();
        this.content = gson.toJsonTree(object);
    }
    
    public String getContentAsString() {
        Gson gson = new Gson();
        return gson.toJson(content);
    }
    
    public <T> T getContent(Class<T> klass) {
        Gson gson = new Gson();
        return gson.fromJson(content, klass);
    }
    
    public void addPayload(String name, String filename, String mediaType, InputStream in) {
        Payload p = new Payload();
        p.name = name;
        p.filename = filename;
        p.mediaType = mediaType;
        p.setInputStream(in);
        payloads.add(p);
    }
    
    public void deletePayload(String name) {
        payloadsToDelete.add(name);
        removePayloadFromList(name);
    }
    
    public List<String> getPayloadsToDelete() {
        return payloadsToDelete;
    }
    
    private void removePayloadFromList(String name) {
        Iterator<Payload> iter = payloads.iterator();
        while (iter.hasNext()) {
            Payload p = iter.next();
            if (p.name.equals(name)) {
                iter.remove();
            }
        }
    }
    
    public static class AccessControlList {
        public List<String> readers;
        public List<String> writers;
    }
    
    public static class Metadata {
        public long createdOn;
        public String createdBy;
        public long modifiedOn;
        public String modifiedBy;
        public boolean isVersion;
        public String versionOf;
        public String publishedBy;
        public Long publishedOn;
        public String remoteRepository;
        
        public Long txnId; //TODO
    }
}
