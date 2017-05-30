/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.Map;

import net.cnri.doregistrytools.registrar.auth.HashAndSalt;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.TextNode;

//Replaces password fields with null in the json and stores a hash and salt of the password on the DigitalObject 
public class PasswordProcessor {
    
    private int MIN_PASSWORD_LENGTH = 1;

    public void preprocess(JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws RepositoryException, InvalidException {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "auth");
            if (authNode == null || !"password".equals(authNode.asText())) continue;
            
            JsonNode passwordNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            String password = passwordNode.asText();
            if (!isValidPassword(password)) {
                throw new InvalidException("Password does not meet minumum length of " + MIN_PASSWORD_LENGTH);
            }
        }
    }
    
    public void process(DigitalObject dobj, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws RepositoryException, InvalidException {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode authNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "auth");
            if (authNode == null || !"password".equals(authNode.asText())) continue;
            
            JsonNode passwordNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
            String password = passwordNode.asText();
            if (!isValidPassword(password)) {
                throw new InvalidException("Password does not meet minumum length of " + MIN_PASSWORD_LENGTH);
            }
            
            JsonUtil.replaceJsonAtPointer(json, jsonPointer, new TextNode(""));

            HashAndSalt hashAndSalt = new HashAndSalt(password);
            String hash = hashAndSalt.getHashString();
            String salt = hashAndSalt.getSaltString();
            dobj.setAttribute("hash", hash);
            dobj.setAttribute("salt", salt);
        }
    }
    
    private boolean isValidPassword(String password) {
        if (password == null) {
            return false;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return false;
        }
        return true;
    }
}
