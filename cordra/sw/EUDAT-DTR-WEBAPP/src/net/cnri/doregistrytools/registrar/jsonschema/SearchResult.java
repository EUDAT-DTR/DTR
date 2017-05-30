/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import com.google.gson.JsonElement;

public class SearchResult {
    String id;
    String type;
    JsonElement json;
    String remoteRepository = null;
    long createdOn;
    String createdBy;
    
    public SearchResult(String id, String type, JsonElement json, String remoteRepository, long createdOn, String createdBy) {
        this.id = id;
        this.type = type;
        this.json = json;
        this.remoteRepository = remoteRepository;
        this.createdOn = createdOn;
        this.createdBy = createdBy;
    }
    
    public SearchResult(String id, String type, JsonElement json, String remoteRepository) {
        this.id = id;
        this.type = type;
        this.json = json;
        this.remoteRepository = remoteRepository;
    }
    
    public SearchResult(String id, String remoteRepository) {
        this.id = id;
        this.remoteRepository = remoteRepository;
    }
}
