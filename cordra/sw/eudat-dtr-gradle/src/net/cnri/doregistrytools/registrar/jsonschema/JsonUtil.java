/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.google.common.collect.Lists;

public class JsonUtil {

    public static List<String> findObjectsWithProperty(TreeNode jsonNode, String prop) {
        List<String> result = new ArrayList<String>();
        findObjectsWithProperty(jsonNode, prop, "", result);
        return result;
    }
     
    private static void findObjectsWithProperty(TreeNode jsonNode, String prop, String pointer, List<String> result) {
        if (jsonNode.isArray()) {
            for (int i = 0; i < jsonNode.size(); i++) {
                TreeNode child = jsonNode.path(i);
                findObjectsWithProperty(child, prop, pointer + "/" + i, result);
            }
        } else if (jsonNode.isObject()) {
            Iterator<String> iter = jsonNode.fieldNames();
            while (iter.hasNext()) {
                String fieldName = iter.next();
                if (fieldName.equals(prop)) {
                    result.add(pointer);
                }
                TreeNode child = jsonNode.path(fieldName);
                findObjectsWithProperty(child, prop, pointer + "/" + encodeSegment(fieldName), result);
            }
        }
    }

    public static String encodeSegment(String s) {
        return s.replaceAll("~", "~0").replaceAll("/", "~1");
    }
    
    public static String decodeSegment(String s) {
        return s.replaceAll("~1", "/").replaceAll("~0", "~");
    }
    
    public static void deletePointee(JsonNode jsonNode, String pointer) {
        if ("".equals(pointer)) {
            if (jsonNode.isObject()) {
                Collection<String> allFieldNames = Lists.newArrayList(((ObjectNode)jsonNode).fieldNames());
                ((ObjectNode) jsonNode).remove(allFieldNames);
            } else if (jsonNode.isArray()) {
                ((ArrayNode) jsonNode).removeAll();
            }
            return;
        }
        String parentPointer = getParentJsonPointer(pointer);
        String lastSegment = getLastSegmentFromJsonPointer(pointer);
        String fieldName = decodeSegment(lastSegment);
        JsonNode parentNode = jsonNode.at(parentPointer);
        if (parentNode.isObject()) {
            ((ObjectNode) parentNode).remove(fieldName);
        } else if (parentNode.isArray()) {
            int indexToRemove = Integer.parseInt(fieldName);
            ((ArrayNode) parentNode).remove(indexToRemove);
        }
    }

    public static void replaceJsonAtPointer(JsonNode jsonNode, String pointer, JsonNode replacement) {
        if ("".equals(pointer)) {
            throw new IllegalArgumentException("Can't replace empty pointer");
        }
        String parentPointer = getParentJsonPointer(pointer);
        String lastSegment = getLastSegmentFromJsonPointer(pointer);
        String fieldName = decodeSegment(lastSegment);
        JsonNode parentNode = jsonNode.at(parentPointer);
        if (parentNode.isObject()) {
            ((ObjectNode) parentNode).set(fieldName, replacement);
        } else if (parentNode.isArray()) {
            try {
                int index = Integer.parseInt(fieldName);
                ((ArrayNode) parentNode).set(index, replacement);
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Found array at " + parentPointer + " but next segment is " + lastSegment);
            }
        }
    }
    
    public static String prettyPrintJson(JsonNode jsonNode) throws RepositoryException {
        ObjectMapper mapper = new ObjectMapper();
        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode);
        } catch (JsonProcessingException e) {
            throw new InternalException(e);
        }
    }
    
    public static String getLastSegmentFromJsonPointer(String pointer) {
        if (!"".equals(pointer) && pointer.charAt(0) != '/') {
            throw new IllegalArgumentException("A Json Pointer that is not the empty string must start with a slash.");
        }
        int lastSlash = pointer.lastIndexOf("/");
        if (lastSlash == -1) {
            return pointer;
        }
        String lastSegment = pointer.substring(lastSlash+1);
        return lastSegment;
    }
    
    public static String getParentJsonPointer(String pointer) {
        if (!"".equals(pointer) && pointer.charAt(0) != '/') {
            throw new IllegalArgumentException("A Json Pointer that is not the empty string must start with a slash.");
        }
        int lastSlash = pointer.lastIndexOf("/");
        if (lastSlash == -1) {
            return pointer;
        }
        String parentPointer = pointer.substring(0, lastSlash);
        return parentPointer;
    }
    
    public static String convertJsonPointerToUseWildCardForArrayIndices(String jsonPointer, JsonNode jsonNode) {
        if ("/".equals(jsonPointer)) {
            return jsonPointer;
        }
        String[] segments = jsonPointer.split("/");
        String resultPointer = "";
        String parentPointer = "";
        String currentPointer = "";
        for (int i = 1; i < segments.length; i++) {
            String segment = segments[i];
            parentPointer = currentPointer;
//            if ("".equals(segment)) {
//                continue;
//            }
            
            currentPointer = currentPointer + "/" + segment;
            JsonNode parentNode = JsonUtil.getJsonAtPointer(parentPointer, jsonNode);
            if (parentNode.isArray()) {
                resultPointer = resultPointer + "/_"; 
            } else {
                resultPointer = resultPointer + "/" + segment; 
            }
        }
        return resultPointer;
    }
    
    
    public static JsonSchema parseJsonSchema(JsonNode schemaNode) throws ProcessingException {
        final JsonSchemaFactory factory = JsonSchemaFactoryHolder.getJsonSchemaFactory();
        final JsonSchema schema = factory.getJsonSchema(schemaNode);
        return schema;
    }

    public static JsonNode parseJson(String jsonData) throws InvalidException {
        try {
            JsonNode dataNode = JsonLoader.fromString(jsonData);
            return dataNode;
        } catch (Exception e) {
            throw new InvalidException(e);
        }
    }

    public static JsonNode getJsonAtPointer(String jsonPointer, JsonNode dataNode) {
        return dataNode.at(jsonPointer);
    }

    public static boolean isValidJsonPointer(String jsonPointer) {
        if (jsonPointer.isEmpty()) return true;
        if (!jsonPointer.startsWith("/")) return false;
        for (int i = 0; i < jsonPointer.length(); i++) {
            if (jsonPointer.charAt(i) == '~') {
                if (i + 1 >= jsonPointer.length()) return false;
                char ch = jsonPointer.charAt(i+1);
                if (ch != '0' && ch != '1') return false; 
            }
        }
        return true;
    }
    
    public static JsonNode getDeepProperty(JsonNode origin, String... keys) {
        JsonNode current = origin;
        for (String key : keys) {
            current = current.get(key);
            if (current == null) return null;
        }
        return current;
    }

    public static boolean isPayloadPointer(String jsonPointer) {
        return jsonPointer.isEmpty() || jsonPointer.startsWith("/");
    }
}
