/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.cnri.doregistrytools.registrar.auth.AccessControlList;
import net.cnri.doregistrytools.registrar.replication.RemoteRepositoryInfo;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;

public class MigrationService {
    private static Logger logger = LoggerFactory.getLogger(new Object() {}.getClass().getEnclosingClass());
    
    public static final String SCHEMAS_OBJECT_ID = "schemas"; //Defunct old name
    private static final String VERSION = "version";

    private static Gson gson = new Gson();
    
    public static void createDesignFromSchemasObjectIfNeeded(Repository repo) throws RepositoryException {
        if (repo.verifyDigitalObject(SCHEMAS_OBJECT_ID)) {
            logger.info("Old schemas object found. Converting to design object.");
            DigitalObject schemas = repo.getDigitalObject(SCHEMAS_OBJECT_ID);
            DigitalObject design = repo.getOrCreateDigitalObject(RegistrarService.DESIGN_OBJECT_ID);
            design.setAttribute("meta", "true");
            Map<String, String> oldAtts = schemas.getAttributes();
            design.setAttributes(oldAtts);
            CloseableIterator<DataElement> elIt = schemas.listDataElements();
            try {
                while (elIt.hasNext()) {
                    DataElement oldEl = elIt.next();
                    Map<String, String> elAtts = oldEl.getAttributes();
                    DataElement newEl = design.getOrCreateDataElement(oldEl.getName());
                    newEl.setAttributes(elAtts);
                    InputStream in = null;
                    try {
                        in = oldEl.read();
                        newEl.write(in);
                    } catch (IOException e) {
                        throw new InternalException(e);
                    } finally {
                        if (in != null) {
                            try { in.close(); } catch (IOException e) {}
                        }
                    }
                }
            } catch (UncheckedRepositoryException e) {
                e.throwCause();
            } finally {
                elIt.close();
            }
            schemas.delete();
        } else {
            return;
        }
    }

    public static void updateDesignToVersionOneIfNeeded(Repository repo, HandleMinter handleMinter) throws RepositoryException {
        DigitalObject designObject = repo.getDigitalObject(RegistrarService.DESIGN_OBJECT_ID);
        if (designObject == null) return;
        String version = designObject.getAttribute(VERSION);
        if (version != null) return;
        Map<String, String> designAtts = designObject.getAttributes();
        List<RemoteRepositoryInfo> remoteRepositories = new ArrayList<RemoteRepositoryInfo>();
        Map<String, String> schemaIds = new HashMap<String, String>();
        for (String key : designAtts.keySet()) {
            if (key.startsWith("schema.")) {
                String jsonSchema = designObject.getAttribute(key);
                String objectType = schemaNameFromAttributeName(key);
                designObject.deleteAttribute(key);
                String schemaHandle = RegistrarService.createSchemaObject(handleMinter, repo, objectType, jsonSchema);
                schemaIds.put(schemaHandle, objectType);
                continue;
            }
            if (key.startsWith("remoteRepositories")) {
                String remoteRepositoryInfoString = designObject.getAttribute(key);
                remoteRepositories = gson.fromJson(remoteRepositoryInfoString, new TypeToken<List<RemoteRepositoryInfo>>(){}.getType());
                for (RemoteRepositoryInfo remoteRepository : remoteRepositories) {
                    remoteRepository.baseUri = trimTrailingSlash(remoteRepository.baseUri);
                }
                // Potentially remove old schema attributes from design.http://blabla object
                // Potentially query http://blabla for its schema objects
                continue;
            }
        }
        String schemaIdsJson = gson.toJson(schemaIds);
        designObject.setAttribute(RegistrarService.SCHEMAS, schemaIdsJson);
        designObject.setAttribute(VERSION, "1");
    }
    
    public static void updateDesignToVersionTwoIfNeeded(Repository repo) throws RepositoryException {
        DigitalObject designObject = repo.getDigitalObject(RegistrarService.DESIGN_OBJECT_ID);
        if (designObject == null) return;
        String version = designObject.getAttribute(VERSION);
        if (!"1".equals(version)) return;
        String baseUri = designObject.getAttribute("baseUri");
        HandleMintingConfig defaultHandleMintingConfig = HandleMintingConfig.getDefaultConfig();
        defaultHandleMintingConfig.baseUri = baseUri;
        String handleMintingConfigJson = gson.toJson(defaultHandleMintingConfig);
        designObject.setAttribute("handleMintingConfig", handleMintingConfigJson);
        if (baseUri != null) {    
            designObject.deleteAttribute("baseUri");
        }
        designObject.setAttribute(VERSION, "2");
    }    
    
    private static String schemaNameFromAttributeName(String schemaAttributeName) {
        if (schemaAttributeName.startsWith("schema.")) {
            return schemaAttributeName.substring(7);
        } else {
            return null;
        }
    }
    
    private static String trimTrailingSlash(String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        } else { 
            return s;
        }
    }
    
}
