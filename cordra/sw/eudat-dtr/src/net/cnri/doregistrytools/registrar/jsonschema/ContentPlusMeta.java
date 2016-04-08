/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.List;

import com.google.gson.JsonElement;

public class ContentPlusMeta {

    public String id;
    public String type;
    public JsonElement content;
    public Metadata metadata;
    
    public List<PayloadMetadata> payloads;
    
    public static class PayloadMetadata {
        public String name;
        public String filename;
        public String mediaType;
        public long size;
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
    }
}
