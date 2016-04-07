/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;

import com.fasterxml.jackson.databind.JsonNode;

public class SchemaNameProcessor {
    private RegistrarService registrar;
    
    public SchemaNameProcessor(RegistrarService registrar) {
        this.registrar = registrar;
    }
    
    public void preprocess(String type, String handle, JsonNode json) throws RepositoryException, InvalidException {
        if (!"Schema".equals(type)) return;
        synchronized (SchemaNameProcessor.class) {
            registrar.ensureIndexUpToDate();
            String name = JsonUtil.getJsonAtPointer("/name", json).asText();
            if (!isSchemaNameUnique(handle, name)) {
                throw new InvalidException("Schema name  "+name+" is not unique.");
            } 
        }
    }
    
    public void process(String type, DigitalObject dobj, JsonNode json) throws RepositoryException, InvalidException {
        if (!"Schema".equals(type)) return;
        synchronized (SchemaNameProcessor.class) {
            registrar.ensureIndexUpToDate();
            String name = JsonUtil.getJsonAtPointer("/name", json).asText();
            if (!isSchemaNameUnique(dobj.getHandle(), name)) {
                throw new InvalidException("Schema name  "+name+" is not unique.");
            } 
            dobj.setAttribute("schemaName", name);
        }
    }
    
    private boolean isSchemaNameUnique(String id, String name) throws RepositoryException {
        if ("Schema".equals(name)) {
            return false; 
        }
        String foundId = registrar.idFromType(name);
        return foundId == null || foundId.equals(id);
    }
}
