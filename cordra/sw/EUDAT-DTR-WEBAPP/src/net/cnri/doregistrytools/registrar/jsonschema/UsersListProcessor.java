/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.Iterator;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;

public class UsersListProcessor {

    public UsersListProcessor() {
    }
    
    public void process(DigitalObject dobj, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws RepositoryException {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "auth");
            if (authNode == null || !"usersList".equals(authNode.asText())) continue;
            
            JsonNode usersListNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            Iterator<JsonNode> elements = usersListNode.elements();
            StringBuilder attValue = new StringBuilder();
            while (elements.hasNext()) {
                JsonNode element = elements.next();
                String user = element.asText();
                attValue.append(user).append("\n");
            }
            dobj.setAttribute("users", attValue.toString());
        }
    }
}
