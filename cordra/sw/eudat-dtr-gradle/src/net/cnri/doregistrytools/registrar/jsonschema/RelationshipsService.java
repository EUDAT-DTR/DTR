/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.doregistrytools.registrar.auth.QueryRestrictor;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.util.CollectionUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

public class RelationshipsService {
    private static Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    private final RegistrarService registrar;
    
    public RelationshipsService(RegistrarService registrar) {
        this.registrar = registrar;
    }

    public Relationships getRelationshipsFor(String objectId, boolean outboundOnly, String userId) throws RepositoryException, InvalidException {
        String queryString = "internal.pointsAt:"+objectId;
        List<String> groupIds = registrar.getAclEnforcer().getGroupsForUser(userId);
        boolean excludeVersions = true;
        queryString = QueryRestrictor.restrict(queryString, userId, groupIds, registrar.getDesign().authConfig, registrar.getRemoteAuthConfigs(), excludeVersions);
        Query whoPointsAtObject = new RawQuery(queryString);
        
        List<Edge> edges = new ArrayList<Edge>();
        List<Node> nodes = new ArrayList<Node>();
        Map<String, SearchResult> results = new HashMap<String, SearchResult>(); 
        JsonParser parser = new JsonParser();
        
        Node selfNode = new Node(objectId, objectId);
        nodes.add(selfNode);
        
        DigitalObject selfObject = registrar.getDigitalObject(objectId);
        results.put(objectId, searchResultFor(selfObject, parser));

        if (!outboundOnly) {
            Set<DigitalObject> pointersToObjectSet = CollectionUtil.asSet(registrar.searchRepo(whoPointsAtObject));
            
            for (DigitalObject dobj : pointersToObjectSet) {
                String id = dobj.getHandle();
                String type = dobj.getAttribute(RegistrarService.TYPE);
                String remoteRepository = dobj.getAttribute(RegistrarService.REMOTE_REGISTRAR);
                String json = dobj.getAttribute(RegistrarService.JSON);
                Node node = new Node();
                node.setId(id);
                nodes.add(node);
                
                for (String jsonPointer : getJsonPointersTo(objectId, type, remoteRepository, json)) {
                //for (String jsonPointer : getJsonPointersTo(objectId, remoteRepository, type, json)) {
                    Edge edge = new Edge();
                    edge.setFrom(id);
                    edge.setTo(objectId);
                    edge.setJsonPointer(jsonPointer);
                    edges.add(edge);
                }
                results.put(id, searchResultFor(dobj, parser));
            } 
        }
        
        List<ObjectPointer> pointersFromObjectList = pointedAtIds(objectId);
        for (ObjectPointer objectPointer : pointersFromObjectList) {   
            String id = objectPointer.objectId;
            //if (!pointersToObject.containsKey(id)) {
            if (!results.containsKey(id)) {
                if (objectPointer.remoteRepository == null) {
                    try {
                        DigitalObject dobj = registrar.getDigitalObject(id);
                        if (!registrar.getAclEnforcer().canRead(userId, dobj)) {
                            continue;
                        }
                        results.put(id, searchResultFor(dobj, parser));
                    } catch (NoSuchDigitalObjectException e) {
                        //Someone deleted an object that the focus object was pointing at. Don't include it in the results.
                        continue;
                    }
                } else {
                    results.put(id, searchResultForRemoteObject(objectPointer));
                }
                Node node = new Node();
                node.setId(id);
                nodes.add(node);
            }
            Edge edge = new Edge();
            edge.setTo(id);
            edge.setFrom(objectId);
            edge.setJsonPointer(objectPointer.jsonPointer);
            edges.add(edge);
        }
        return new Relationships(nodes, edges, results);
    }
    
    private static SearchResult searchResultFor(DigitalObject dobj, JsonParser parser) throws RepositoryException {
        String id = dobj.getHandle();
        String type = dobj.getAttribute("type");
        String json = dobj.getAttribute("json");
        String remoteRepository = dobj.getAttribute("remoteRepository");
        String createdOnString = dobj.getAttribute("internal.created");
        long createdOn = Long.valueOf(createdOnString);
        String createdBy = dobj.getAttribute("createdBy");
        JsonElement jsonObject = parser.parse(json);
        SearchResult searchResult = new SearchResult(id, type, jsonObject, remoteRepository, createdOn, createdBy);
        return searchResult;
    }
    
    private static SearchResult searchResultForRemoteObject(ObjectPointer objectPointer) {
        String id = objectPointer.objectId;
        SearchResult searchResult = new SearchResult(id, objectPointer.remoteRepository);
        return searchResult;
    }

    public List<ObjectPointer> pointedAtIds(String objectId, String objectType, String remoteRepository, JsonNode jsonNode) throws InvalidException {
        List<ObjectPointer> pointedAtIds = new ArrayList<ObjectPointer>();
        Map<String, JsonNode> pointerToSchemaMap = registrar.getPointerToSchemaMap(objectType, remoteRepository, jsonNode);
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode handleReferenceNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "type", "handleReference");
            if (handleReferenceNode == null) continue;
            JsonNode handleReferenceTypeNode = handleReferenceNode.get("types");
            if (handleReferenceTypeNode == null) continue;
            if (!handleReferenceTypeNode.isTextual() && !handleReferenceTypeNode.isArray()) continue;
            
            JsonNode remoteRepositoryNode = handleReferenceNode.get("remoteRepository");
            String remoteRepositoryBeingPointedAt = null;
            if (remoteRepositoryNode != null) {
                remoteRepositoryBeingPointedAt = remoteRepositoryNode.asText();
            }
            
            JsonNode referenceNode = jsonNode.at(jsonPointer);
            if (referenceNode == null) {
                logger.warn("Unexpected missing handle reference node " + jsonPointer + " in " + objectId);
            } else {
                pointedAtIds.add(new ObjectPointer(referenceNode.asText(), jsonPointer, remoteRepositoryBeingPointedAt));
            }
        }
        return pointedAtIds;
    }
    
    private List<ObjectPointer> pointedAtIds(String objectId) throws RepositoryException, NoSuchDigitalObjectException, InvalidException {
        DigitalObject dobj = registrar.getDigitalObject(objectId);
        String objectType = dobj.getAttribute(RegistrarService.TYPE);
        if (objectType == null) {
            throw new NoSuchDigitalObjectException(objectId);
        }
        String remoteRepository = dobj.getAttribute(RegistrarService.REMOTE_REGISTRAR);
        String json = dobj.getAttribute(RegistrarService.JSON);
        if (json == null) {
            throw new NoSuchDigitalObjectException(objectId);
        }
        JsonNode jsonNode = JsonUtil.parseJson(json);
        return pointedAtIds(objectId, objectType, remoteRepository, jsonNode);
    }
    
    public class ObjectPointer {
        public String objectId;
        public String jsonPointer;
        public String remoteRepository;
        
        public ObjectPointer(String objectId, String jsonPointer, String remoteRepository) {
            this.objectId = objectId;
            this.jsonPointer = jsonPointer;
            this.remoteRepository = remoteRepository;
        }
    }
    
    
    // returns all the json pointers in dobj that are handleReferenceName pointing to objectId
    List<String> getJsonPointersTo(String objectId, String type, String remoteRepository, String json) throws RepositoryException, InvalidException {
        if (type == null || json == null) return Collections.emptyList();
        JsonNode jsonNode = JsonUtil.parseJson(json);
        List<String> res = new ArrayList<String>();
        Map<String, JsonNode> pointerToSchemaMap = registrar.getPointerToSchemaMap(type, remoteRepository, jsonNode);
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode handleReferenceNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "type", "handleReference");
            if (handleReferenceNode == null) continue;
            JsonNode referenceNode = jsonNode.at(jsonPointer);
            if (referenceNode == null) {
                logger.warn("Unexpected missing handleReferenceType node " + jsonPointer);
            } else if (objectId.equals(referenceNode.asText())) {
                res.add(jsonPointer);
            }
        }
        return res;
    }
}
