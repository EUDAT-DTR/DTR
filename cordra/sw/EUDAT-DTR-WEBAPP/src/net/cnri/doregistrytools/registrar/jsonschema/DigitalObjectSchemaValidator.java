/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;

public class DigitalObjectSchemaValidator {
    
    private Repository repo;
    
    public DigitalObjectSchemaValidator(Repository repo) {
        this.repo = repo;
    }
    
    public Map<String, JsonNode> schemaValidateAndReturnKeywordsMap(JsonNode dataNode, JsonNode schemaJson, JsonSchema schema) throws InvalidException {
        if (schema == null) throw new InvalidException("Null schema");
        ProcessingReport report = validateJson(dataNode, schema);
        if (!report.isSuccess()) {
            throw new InvalidException(report);
        }
        return SchemaExtractor.extract(report, schemaJson);
    }
    
    public void postSchemaValidate(JsonNode dataNode, Map<String, JsonNode> pointerToSchemaMap) throws InvalidException, RepositoryException {
        //validatePayloads(payloads, pointerToSchemaMap);
        validateHandleReferences(dataNode, pointerToSchemaMap);
    }

//    void validatePayloads(List<Payload> payloads, Map<String, JsonNode> keywordsMap) throws InvalidException {
//        Set<String> expectedPayloadPointers = DigitalObjectSchemaValidator.getPayloadPointers(keywordsMap);
//        if (payloads == null) {
//            if (expectedPayloadPointers.size() > 0) throw new InvalidException("Expected " + expectedPayloadPointers.size() + " payloads, received 0");
//            else return;
//        }
//        if (expectedPayloadPointers.size() != payloads.size()) throw new InvalidException("Expected " + expectedPayloadPointers.size() + " payloads, received " + payloads.size());
//        Set<String> expectedSet = new HashSet<String>(expectedPayloadPointers);
//        for (Payload payload : payloads) {
//            if (!expectedSet.contains(payload.jsonPointer)) throw new InvalidException("Received unexpected payload " + payload.jsonPointer);
//        }
//    }
    
    void validateHandleReferences(JsonNode dataNode, Map<String, JsonNode> pointerToSchemaMap) throws InvalidException, RepositoryException {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode handleReferenceNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "type", "handleReference");
            if (handleReferenceNode == null) continue;
            JsonNode handleReferenceTypeNode = handleReferenceNode.get("types");
            if (handleReferenceTypeNode == null) continue;
            
            JsonNode remoteRepositoryNode = handleReferenceNode.get("remoteRepository");
            if (remoteRepositoryNode != null) continue; //TODO consider retrieving the object from the remote server
            
            List<String> referenceTypes = getHandleReferenceTypes(handleReferenceTypeNode);
            if (referenceTypes.isEmpty()) continue;
            
            JsonNode referenceNode = dataNode.at(jsonPointer);
            if (referenceNode == null) throw new InvalidException("Unexpected missing handle reference node " + jsonPointer);
            validateHandleReference(referenceNode.asText(), referenceTypes, jsonPointer);
        }
    }
    
    public List<String> getHandleReferenceTypes(JsonNode handleReferenceTypeNode) {
        List<String> result = new ArrayList<String>();
        if (handleReferenceTypeNode.isTextual()) {
            String handleReferenceType = handleReferenceTypeNode.asText(null);
            result.add(handleReferenceType);
        } else if (handleReferenceTypeNode.isArray()) {
            Iterator<JsonNode> iter = handleReferenceTypeNode.elements();
            while (iter.hasNext()) {
                JsonNode current = iter.next();
                if (current.isTextual()) {
                    String handleReferenceType = current.asText(null);
                    result.add(handleReferenceType);
                }
            }
        }
        return result;
    }

    void validateHandleReference(String reference, List<String> handleReferenceTypes, String jsonPointer) throws InvalidException, RepositoryException {
        if ("".equals(reference)) return; //We currently permit empty String for the reference 
        if (reference == null) throw new InvalidException("Unexpected missing handle reference node " + jsonPointer);
        DigitalObject dobj = repo.getDigitalObject(reference);
        if (dobj == null) throw new InvalidException("Unexpected missing digital object " + reference + " at " + jsonPointer);
        String type = dobj.getAttribute("type");
        if (type == null || !handleReferenceTypes.contains(type)) {
            throw new InvalidException("Digital object " + reference + " referenced at " + jsonPointer + " has type " + type + ", expected " + handleReferenceTypes);
        }
    }

    ProcessingReport validateJson(JsonNode dataNode, JsonSchema schema) throws InvalidException {
        try {
            return schema.validate(dataNode);
        } catch (ProcessingException e) {
            throw new InvalidException(e);
        }
    }

    public void validatePayloads(List<Payload> payloads) throws InvalidException {
        Set<String> seenNames = new HashSet<String>();
        for (Payload payload : payloads) {
            if (payload.name == null || payload.name.isEmpty()) {
                String filename = payload.filename;
                if (filename != null && !filename.isEmpty()) {
                    throw new InvalidException("Payload for filename " + filename + " missing name");
                } else {
                    throw new InvalidException("Payload missing name");
                }
            }
            if (seenNames.contains(payload.name)) {
                throw new InvalidException("Duplicate payload " + payload.name);
            } else {
                seenNames.add(payload.name);
            }
        }
    }

//    public static Set<String> getPayloadPointers(Map<String, JsonNode> keywordsMap) {
//        if (keywordsMap == null) return Collections.emptySet();
//        Set<String> res = new HashSet<String>();
//        for (Map.Entry<String, JsonNode> entry : keywordsMap.entrySet()) {
//            JsonNode subSchema = entry.getValue();
//            if (JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "type", "payload") != null) {
//                String jsonPointer = entry.getKey();
//                res.add(jsonPointer);
//                continue;
//            }
////            
////            JsonNode formatNode = subSchema.get("format");
////            if (formatNode == null) continue;
////            String format = formatNode.asText();
////            if ("file".equals(format)) {
////                String jsonPointer = entry.getKey();
////                res.add(jsonPointer);
////            }
//        }
//        return res;
//    }
    
}
