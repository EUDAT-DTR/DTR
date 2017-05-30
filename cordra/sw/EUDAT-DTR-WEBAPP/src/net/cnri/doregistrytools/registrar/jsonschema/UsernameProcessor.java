/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.networked.NetworkedRepository;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.util.CollectionUtil;

public class UsernameProcessor {

    private Repository repo;
    
    public UsernameProcessor(Repository repo) {
        this.repo = repo;
    }
    
    public void preprocess(String handle, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws RepositoryException, InvalidException {
        synchronized (UsernameProcessor.class) { 
            for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
                String jsonPointer = entry.getKey();
                JsonNode subSchema = entry.getValue();
                JsonNode authNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "auth");
                if (authNode == null || !"username".equals(authNode.asText())) continue;

                JsonNode usernameNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
                String username = usernameNode.asText();

                if (isUsernameUnique(username, handle)) {
                    break;
                } else {
                    throw new InvalidException("Username "+username+" is not unique.");
                }
            }
        }
    }
    
    public void process(DigitalObject dobj, JsonNode json, Map<String, JsonNode> pointerToSchemaMap) throws RepositoryException, InvalidException {
        synchronized (UsernameProcessor.class) { 
            for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
                String jsonPointer = entry.getKey();
                JsonNode subSchema = entry.getValue();
                JsonNode authNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "auth");
                if (authNode == null || !"username".equals(authNode.asText())) continue;

                JsonNode usernameNode = JsonUtil.getJsonAtPointer(jsonPointer, json);
                String username = usernameNode.asText();

                if (isUsernameUnique(username, dobj.getHandle())) {
                    dobj.setAttribute("username", username);
                    break;
                } else {
                    throw new InvalidException("Username "+username+" is not unique.");
                }
            }
        }
    }
    
    public boolean isUsernameUnique(String username, String handle) throws RepositoryException {
        if (repo instanceof NetworkedRepository) {
            ((NetworkedRepository) repo).ensureIndexUpToDate();
        }
        if ("admin".equals(username)) {
            return false; //admin is a reserved username. Users cannot change their name to admin
        }
        Query q = new RawQuery("username:\"" + username + "\"");
        List<String> handles = CollectionUtil.asList(repo.searchHandles(q));
        if (handles.size() == 0) {
            return true;
        } else if (handles.size() == 1) {
            String foundHandle = handles.get(0);
            if (foundHandle.equals(handle)) {
                return true;
            } else {
                return false;
            }
        } else {
            //should never happen
            return false;
        }
    }
}
