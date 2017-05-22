/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;

public class ObjectComponentJson extends ObjectComponent {

    public final JsonNode json;
    
    public ObjectComponentJson(JsonNode json, String type, String remoteRepository, String mediaType) {
        super(type, remoteRepository, mediaType);
        this.json = json;
    }
}
