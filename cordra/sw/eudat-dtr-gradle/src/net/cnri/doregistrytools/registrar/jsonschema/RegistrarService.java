/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.github.fge.jsonschema.main.JsonSchema;
import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import net.cnri.doregistrytools.registrar.auth.AccessControlList;
import net.cnri.doregistrytools.registrar.auth.AclEnforcer;
import net.cnri.doregistrytools.registrar.auth.AuthConfig;
import net.cnri.doregistrytools.registrar.auth.AuthConfigFactory;
import net.cnri.doregistrytools.registrar.auth.RegistrarAuthenticator;
import net.cnri.doregistrytools.registrar.jsonschema.AllHandlesUpdater.UpdateStatus;
import net.cnri.doregistrytools.registrar.replication.RemoteRepositoryInfo;
import net.cnri.doregistrytools.registrar.replication.ReplicationCredentials;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.NoSuchDataElementException;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.networked.NetworkedRepository;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;
import net.cnri.repository.search.QueryResults;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.search.SortField;
import net.cnri.repository.util.CollectionUtil;
import net.cnri.repository.util.RepositoryJsonSerializer;
import net.cnri.util.FastDateFormat;
import net.cnri.util.StreamUtil;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleRecord;

public class RegistrarService {
    private static Logger logger = LoggerFactory.getLogger(RegistrarService.class);

    public static final String JSON = "json";
    public static final String TYPE = "type";
    public static final String REMOTE_REGISTRAR = "remoteRepository";
    public static final String DESIGN_OBJECT_ID = "design";
    public static final String SCHEMAS = "schemas";
    private static final String defaultUiConfig = getDefaultUiConfig();
    public static final String REPOSITORY_SERVICE_PREFIX_HANDLE = "0.NA/20.5000";

    final Repository repo;
    final String serverPrefix;
    final RegistrarAuthenticator authenticator;
    final AclEnforcer aclEnforcer;
    final DigitalObjectSchemaValidator validator;
    final HandleMinter handleMinter;
    final VersionManager versionManager;
    HandleClient handleClient;
    static final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    final AuthenticationInfo authInfo;
    final AllHandlesUpdater handlesUpdater;

    final JsonNode schemaSchemaNode;
    final JsonSchema schemaSchema;

    Design design;
    volatile Map<TypeAndRegistrar, SchemaAndNode> schemas;
    Map<String, AuthConfig> remoteAuthConfigs;
    
    public RegistrarService(Repository repo, String serverPrefix) throws RepositoryException, InvalidException {
        this(repo, serverPrefix, null, null);
    }

    public RegistrarService(Repository repo, String serverPrefix, AuthenticationInfo authInfo, RegistrarAuthenticator authenticator) throws RepositoryException, InvalidException {
        this.repo = repo;
        this.serverPrefix = serverPrefix;
        this.authInfo = authInfo;
        this.authenticator = authenticator;
        this.validator = new DigitalObjectSchemaValidator(repo);
        this.handleMinter = new HandleMinter(serverPrefix);
        this.aclEnforcer = new AclEnforcer(repo);
        this.versionManager = new VersionManager(repo, handleMinter);
        String schemaSchemaString = DefaultSchemasFactory.getSchemaSchema();
        this.schemaSchemaNode = JsonUtil.parseJson(schemaSchemaString);
        try {
            this.schemaSchema = JsonUtil.parseJsonSchema(schemaSchemaNode);
        } catch (ProcessingException e) {
            throw new InternalException(e);
        }
        this.handlesUpdater = new AllHandlesUpdater();
        
        markRepoObject();
        MigrationService.createDesignFromSchemasObjectIfNeeded(repo); //Migration from old object name
        MigrationService.updateDesignToVersionOneIfNeeded(repo, this.handleMinter);
        MigrationService.updateDesignToVersionTwoIfNeeded(repo);

        loadPersistentMetadata();
        if (serverPrefix != null && authInfo != null) {
            if (design.handleMintingConfig.baseUri == null || design.handleMintingConfig.baseUri.isEmpty()) {
                this.handleClient = null;
            } else {
                this.handleClient = new HandleClient(authInfo, design.handleMintingConfig, repo.getHandle()); 
            }
        } else {
            this.handleClient = null;
        }
    }
    
    private void markRepoObject() throws RepositoryException {
        if (repo.getHandle() == null) return;
        DigitalObject repoObj = repo.getDigitalObject(repo.getHandle());
        if (repoObj != null) {
            if (!"true".equals(repoObj.getAttribute("meta"))) {
                repoObj.setAttribute("meta", "true");
            }
        }
    }
    
    private JsonNode getJsonSchemaNodeFromSchemaDigitalObject(DigitalObject schemaObject) throws RepositoryException, InvalidException {
        String jsonContent = schemaObject.getAttribute(JSON);
        JsonNode node = JsonUtil.parseJson(jsonContent);
        JsonNode schemaNode = JsonUtil.getJsonAtPointer("/schema", node);
        return schemaNode;
    }

    private String getJsonSchemaNameFromSchemaDigitalObject(DigitalObject schemaObject) throws RepositoryException, InvalidException {
        String jsonContent = schemaObject.getAttribute(JSON);
        JsonNode node = JsonUtil.parseJson(jsonContent);
        String name = JsonUtil.getJsonAtPointer("/name", node).asText();
        return name;
    }
    
    public synchronized void loadPersistentMetadata() throws RepositoryException, InvalidException {

        boolean reloadUIConfig = Boolean.getBoolean("dtr.jsonschema.uiconfig.reload");

        try {
            DigitalObject designObject = getDesignDigitalObject();
            if (designObject == null) {
                logger.info("Creating new design object.");
                System.out.println("Creating new design object.");
                designObject = createNewDesignDigitalObject();
            }
                 
            if (!"true".equals(designObject.getAttribute("meta"))) {
                designObject.setAttribute("meta", "true");
            }
            if (designObject.getAttribute("uiConfig") == null || reloadUIConfig) {
                designObject.setAttribute("uiConfig", defaultUiConfig);
            }
            if (designObject.getAttribute("authConfig") == null) {
                AuthConfig defaultAuthConfig = AuthConfigFactory.getDefaultAuthConfig();
                String defaultAuthConfigJson = gson.toJson(defaultAuthConfig);
                designObject.setAttribute("authConfig", defaultAuthConfigJson);
            }
            
            if (designObject.getAttribute("handleMintingConfig") == null) {
                HandleMintingConfig defaultHandleMintingConfig = HandleMintingConfig.getDefaultConfig();
                String defaultHandleMintingConfigJson = gson.toJson(defaultHandleMintingConfig);
                designObject.setAttribute("handleMintingConfig", defaultHandleMintingConfigJson);
            }
            
//            if (designObject.getAttribute("baseUri") != null) {
//                String baseUri = designObject.getAttribute("baseUri");
//                
//            }
            
            
            JsonNode uiConfig = null;
            AuthConfig authConfig = null;
            List<RemoteRepositoryInfo> remoteRepositories = new ArrayList<RemoteRepositoryInfo>();
            List<ReplicationCredentials> replicationCredentials = new ArrayList<ReplicationCredentials>();
            Map<String, String> atts = designObject.getAttributes();
            
            //String baseUri = null;
            HandleMintingConfig handleMintingConfig = null;
            
            for (String key : atts.keySet()) {
                if ("uiConfig".equals(key)) {
                    String uiConfigJson = designObject.getAttribute(key);
                    uiConfig = JsonUtil.parseJson(uiConfigJson);
                    continue;
                }
                if ("authConfig".equals(key)) {
                    String authConfigJson = designObject.getAttribute(key);
                    authConfig = gson.fromJson(authConfigJson, AuthConfig.class);
                    continue;
                }
                if ("baseUri".equals(key)) {
                    String baseUri = designObject.getAttribute(key);
                    //TODO migrate to handleMintingConfig
                    continue;
                }
                if ("handleMintingConfig".equals(key)) {
                    String handleMintingConfigJson = designObject.getAttribute(key);
                    handleMintingConfig = gson.fromJson(handleMintingConfigJson, HandleMintingConfig.class);
                    continue;
                }
                if (key.startsWith("remoteRepositories")) {
                    String remoteRepositoryInfoString = designObject.getAttribute(key);
                    remoteRepositories = gson.fromJson(remoteRepositoryInfoString, new TypeToken<List<RemoteRepositoryInfo>>(){}.getType());
                    for (RemoteRepositoryInfo remoteRepository : remoteRepositories) {
                        remoteRepository.baseUri = trimTrailingSlash(remoteRepository.baseUri);
                    }
                    continue;
                }
                if (key.startsWith("replicationCredentials")) {
                    String replicationCredentialsString = designObject.getAttribute(key);
                    replicationCredentials = gson.fromJson(replicationCredentialsString, new TypeToken<List<ReplicationCredentials>>(){}.getType());
                    continue;
                }
            }
            Map<String, AuthConfig> remoteAuthConfigs = new HashMap<String, AuthConfig>();
            loadRemoteAuthConfigs(remoteRepositories, remoteAuthConfigs);
            this.design = new Design(null, uiConfig, serverPrefix, remoteRepositories, replicationCredentials, null, authConfig, handleMintingConfig, null);

            Map<String, String> knownSchemaIds = getKnownSchemaIdsFromDesignObject(designObject);
            List<DigitalObject> knownSchemaObjects = objectListFromHandleList(knownSchemaIds.keySet());
            rebuildSchemasFromListOfObjects(knownSchemaObjects);

            this.remoteAuthConfigs = remoteAuthConfigs;
            aclEnforcer.setAuthConfig(authConfig);
            aclEnforcer.setRemoteAuthConfigs(remoteAuthConfigs);
        } catch (ProcessingException e) {
            throw new InternalException(e);
        }
    }

    private Map<String, String> getKnownSchemaIdsFromDesignObject(DigitalObject designObject) throws RepositoryException {
        String knownSchemaIdsJson = designObject.getAttribute(SCHEMAS);
        Map<String, String> knownSchemaIds;
        if (knownSchemaIdsJson == null) {
            knownSchemaIds = new ConcurrentHashMap<String, String>();
        } else {
            knownSchemaIds = gson.fromJson(knownSchemaIdsJson, new TypeToken<Map<String, String>>() {}.getType());
        }
        return knownSchemaIds;
    }

    private List<DigitalObject> objectListFromHandleList(Collection<String> knownSchemaHandles) throws RepositoryException {
        List<DigitalObject> knownSchemaObjects = new ArrayList<DigitalObject>();
        for (String schemaHandle : knownSchemaHandles) {
            knownSchemaObjects.add(getDigitalObject(schemaHandle));
        }
        return knownSchemaObjects;
    }
    
    public synchronized void updateKnownSchemasBySearch() throws RepositoryException, InvalidException, ProcessingException {
        ((NetworkedRepository) repo).ensureIndexUpToDate();
        DigitalObject designObject = this.getDesignDigitalObject();
        Map<String, String> knownSchemaIds = getKnownSchemaIdsFromDesignObject(designObject);
        List<String> knownSchemaHandles = new ArrayList<String>(knownSchemaIds.keySet());
        CloseableIterator<String> schemasIter = repo.searchHandles(new RawQuery("type:Schema -" + VersionManager.IS_VERSION + ":true"));
        try {
            for (String handle : CollectionUtil.forEach(schemasIter)) {
                if (!knownSchemaHandles.contains(handle)) {
                    knownSchemaHandles.add(handle);
                }
            }
        } catch (UncheckedRepositoryException e) {
            e.throwCause();
        } finally {
            schemasIter.close();
        }
        List<DigitalObject> knownSchemaObjects = objectListFromHandleList(knownSchemaHandles);
        rebuildSchemasFromListOfObjects(knownSchemaObjects);
    }
    
    private synchronized void rebuildSchemasFromListOfObjects(List<DigitalObject> objects) throws RepositoryException, InvalidException, ProcessingException {
        Map<String, String> schemaIds = new ConcurrentHashMap<String, String>();
        Map<TypeAndRegistrar, SchemaAndNode> schemas = new ConcurrentHashMap<TypeAndRegistrar, SchemaAndNode>();
        schemas.put(new TypeAndRegistrar("Schema", null), new SchemaAndNode(schemaSchema, schemaSchemaNode));
        List<String> schemaHandles = new ArrayList<String>();
        Map<String, JsonNode> schemaNodes = new HashMap<String, JsonNode>();
        for (DigitalObject schemaObject : objects) {
            String schemaHandle = schemaObject.getHandle();
            schemaHandles.add(schemaHandle);
            JsonNode schemaNode = getJsonSchemaNodeFromSchemaDigitalObject(schemaObject);
            JsonSchema schema = JsonUtil.parseJsonSchema(schemaNode);
            String type = getJsonSchemaNameFromSchemaDigitalObject(schemaObject);
            String remoteRegistrar = schemaObject.getAttribute(REMOTE_REGISTRAR);
            schemas.put(new TypeAndRegistrar(type, remoteRegistrar), new SchemaAndNode(schema, schemaNode));
            schemaIds.put(schemaHandle, type);
            if (remoteRegistrar == null) {
                schemaNodes.put(type, schemaNode);
            }
        }
        this.schemas = schemas;
        design.schemas = getSchemaNodes();
        design.alienSchemas = getAlienSchemaNodes();
        design.schemaIds = schemaIds;
        String schemaIdsJson = gson.toJson(schemaIds);
        DigitalObject designObject = getDesignDigitalObject();
        designObject.setAttribute(SCHEMAS, schemaIdsJson);
    }
    
    public static String getDefaultUiConfig() {
        InputStream resource = RegistrarService.class.getResourceAsStream("uiconfig.json");
        try {
            return StreamUtil.readFully(new InputStreamReader(resource, "UTF-8"));
        } catch (Exception e) {
            throw new AssertionError(e);
        } finally {
            try { resource.close(); } catch (Exception e) { }
        }
    }
    
    private Map<String, JsonNode> getSchemaNodes() {
        Map<String, JsonNode> result = new HashMap<String, JsonNode>();
        for (Map.Entry<TypeAndRegistrar, SchemaAndNode> entry : schemas.entrySet()) {
            TypeAndRegistrar typeAndRegistrar = entry.getKey();
            String remoteRepository = typeAndRegistrar.remoteRepository;
            if (remoteRepository == null) {
                result.put(typeAndRegistrar.type, entry.getValue().schemaNode);
            } 
        }
        return result;
    }
    
    private Map<String, Map<String, JsonNode>> getAlienSchemaNodes() {
        Map<String, Map<String, JsonNode>> result = new HashMap<String, Map<String, JsonNode>>();
        for (Map.Entry<TypeAndRegistrar, SchemaAndNode> entry : schemas.entrySet()) {
            TypeAndRegistrar typeAndRegistrar = entry.getKey();
            String remoteRepository = typeAndRegistrar.remoteRepository;
            if (remoteRepository != null) {
                Map<String, JsonNode> registraAlienSchemas = result.get(remoteRepository);
                if (registraAlienSchemas == null) {
                    registraAlienSchemas = new HashMap<String, JsonNode>();
                    result.put(remoteRepository, registraAlienSchemas);
                }
                SchemaAndNode schemaAndNode = entry.getValue();
                JsonNode alienSchema = schemaAndNode.schemaNode;
                registraAlienSchemas.put(typeAndRegistrar.type, alienSchema);
            } 
        }
        return result;
    }
    
    private void loadRemoteAuthConfigs(List<RemoteRepositoryInfo> remoteRepositories, Map<String,AuthConfig> remoteAuthConfigs) throws RepositoryException, InvalidException {
        List<DigitalObject> remoteSchemasObjects = getRemoteSchemasObjects(remoteRepositories);
        for (DigitalObject remoteSchemasObject : remoteSchemasObjects) {
            String remoteRepository = remoteSchemasObject.getAttribute("remoteRepository");
//            loadRemoteSchemasFromDigitalObject(remoteSchemasObject, remoteRepository, res);
            AuthConfig remoteAuthConfig = loadAuthConfigFromDigitalObject(remoteSchemasObject);
            remoteAuthConfigs.put(remoteRepository, remoteAuthConfig);
        }
    }
    
    private AuthConfig loadAuthConfigFromDigitalObject(DigitalObject remoteSchemasObject) throws RepositoryException {
        String authConfigString = remoteSchemasObject.getAttribute("authConfig");
        if (authConfigString == null) return null;
        AuthConfig result = gson.fromJson(authConfigString, AuthConfig.class);
        return result;
    }

    private List<DigitalObject> getRemoteSchemasObjects(List<RemoteRepositoryInfo> remoteRepositories) throws RepositoryException {
        try {
            List<DigitalObject> result = new ArrayList<DigitalObject>();
            for (RemoteRepositoryInfo remoteRepositoryInfo : remoteRepositories) {
                String objectId = "design." + remoteRepositoryInfo.baseUri;
                DigitalObject dobj = repo.getDigitalObject(objectId);
                if (dobj != null) {
                    result.add(dobj);
                }
            }
            return result;
        } catch (RepositoryException e) {
            logger.error("Error searching", e);
            return Collections.<DigitalObject>emptyList();
        }
    }

//    private void loadRemoteSchemasFromDigitalObject(DigitalObject remoteSchemasObject, String remoteRepository, Map<TypeAndRegistrar, SchemaAndNode> res) throws RepositoryException, InvalidException {
//        Map<String, String> atts = remoteSchemasObject.getAttributes();
//        for (String key : atts.keySet()) {
//            if (key.startsWith("schema.")) {
//                String jsonSchema = remoteSchemasObject.getAttribute(key);
//                String objectType = schemaNameFromAttributeName(key);
//                try {
//                    JsonNode schemaNode = JsonUtil.parseJson(jsonSchema);
//                    JsonSchema schema = JsonUtil.parseJsonSchema(schemaNode);
//                    res.put(new TypeAndRegistrar(objectType, remoteRepository), new SchemaAndNode(schema, schemaNode));
//                } catch (ProcessingException e) {
//                    logger.error("Error processing schema for " + remoteRepository + " " + objectType, e);
//                }
//            }
//        }
//    }
//    
    public void persistRemoteRepositoryDesign(String remoteRepository, AuthConfig authConfig) throws RepositoryException {
        Map<String, String> atts = new HashMap<String, String>();
        atts.put("type", "remoteSchemas");
        atts.put("meta", "true");
        atts.put("remoteRepository", remoteRepository);
//        for (Map.Entry<String, JsonElement> entry : schemasObject.entrySet()) {
//            String type = entry.getKey();
//            String schema = gson.toJson(entry.getValue());
//            atts.put("schema." + type, schema);
//        }
        String authConfigJson = gson.toJson(authConfig);
        atts.put("authConfig", authConfigJson);
        
        DigitalObject dobj = repo.getOrCreateDigitalObject("design." + remoteRepository);
        dobj.setAttributes(atts);
    }
    
    public void persistRemoteRepositoryInfos(List<RemoteRepositoryInfo> remoteRepositories) throws RepositoryException {
        for (RemoteRepositoryInfo remoteRepository : remoteRepositories) {
            remoteRepository.baseUri = trimTrailingSlash(remoteRepository.baseUri);
        }
        Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
        String json = prettyGson.toJson(remoteRepositories);
        getDesignDigitalObject().setAttribute("remoteRepositories", json);
        design.remoteRepositories = remoteRepositories;
    }
    
    private static String trimTrailingSlash(String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        } else { 
            return s;
        }
    }
    
    public RegistrarAuthenticator getAuthenticator() {
        return authenticator;
    }
    
    public AclEnforcer getAclEnforcer() {
        return aclEnforcer;
    }
    
    public boolean isKnownType(String type, String remoteRepository) {
        if ("Schema".equals(type)) {
            return true;
        }
        return schemas.containsKey(new TypeAndRegistrar(type, remoteRepository));
    }

    public SchemaAndNode getSchema(String type, String remoteRepository) {
        if ("Schema".equals(type)) {
            return schemas.get(new TypeAndRegistrar("Schema", null));
        } else {
            return schemas.get(new TypeAndRegistrar(type, remoteRepository));
        }
    }
    
    public JsonNode updateUiConfig(String uiConfigJson) throws RepositoryException, InvalidException {
        JsonNode uiConfig = JsonUtil.parseJson(uiConfigJson);
        //Consider further validation of the uiConfig to ensure it conforms. 
        design.uiConfig = uiConfig;
        getDesignDigitalObject().setAttribute("uiConfig", uiConfigJson);
        return uiConfig;
    }
    
//    public void updateBaseUri(String baseUri) throws RepositoryException {
//        if ("".equals(baseUri) || baseUri == null) {
//            handleClient = null;
//            design.baseUri = null;
//            getDesignDigitalObject().deleteAttribute("baseUri");
//        } else {
//            design.baseUri = ensureSlash(baseUri);
//            if (serverPrefix != null && authInfo != null) {
//                handleClient = new HandleClient(authInfo, baseUri, repo.getHandle());
//            }
//            getDesignDigitalObject().setAttribute("baseUri", baseUri);
//        }
//    }
    
    public void updateHandleMintingConfig(HandleMintingConfig handleMintingConfig) throws RepositoryException {
        String baseUri = handleMintingConfig.baseUri;
        baseUri = ensureSlash(baseUri);
        handleMintingConfig.baseUri = baseUri;
        if ("".equals(baseUri) || baseUri == null) {
            handleClient = null;
            //getDesignDigitalObject().deleteAttribute("baseUri");
        } else {
            if (serverPrefix != null && authInfo != null) {
                handleClient = new HandleClient(authInfo, handleMintingConfig, repo.getHandle());
            }
        }
        String json = gson.toJson(handleMintingConfig);
        getDesignDigitalObject().setAttribute("handleMintingConfig", json);
        design.handleMintingConfig = handleMintingConfig;
    }
    
    private static String ensureSlash(String s) {
        if (s == null) return null; 
        if (s.endsWith("/")) return s;
        else return s + "/";
    }

    public void updateAuthConfig(AuthConfig authConfig) throws RepositoryException {
        design.authConfig = authConfig;
        aclEnforcer.setAuthConfig(authConfig);
        String authConfigJson = gson.toJson(authConfig);
        getDesignDigitalObject().setAttribute("authConfig", authConfigJson);
    }
    
    public void updateReplicationCredentials(List<ReplicationCredentials> credentialsUpdate) throws RepositoryException {
        design.replicationCredentials = credentialsUpdate;
        String credentialsUpdateJson = gson.toJson(credentialsUpdate);
        getDesignDigitalObject().setAttribute("replicationCredentials", credentialsUpdateJson);
    }
    
    public String idFromType(String type) throws RepositoryException {
        CloseableIterator<DigitalObject> iter = repo.search("type:Schema AND remoteRepository:null AND schemaName:\"" + type +"\"");
        try {
            while (iter.hasNext()) {
                DigitalObject dobj = iter.next();
                String schemaInstanceJson = dobj.getAttribute(JSON);
                SchemaInstance schemaInstance = gson.fromJson(schemaInstanceJson, SchemaInstance.class);
                if (type.equals(schemaInstance.name)) {
                    return dobj.getHandle();
                }
            }
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        } finally {
            iter.close();
        }
        return null;
    }
    
    public DataElement getElementFromDesign(String elementName) throws RepositoryException {
        DigitalObject designObject =  getDesignDigitalObject();
        DataElement result = designObject.getDataElement(elementName);
        return result;
    }
    
    DigitalObject getDesignDigitalObject() throws RepositoryException {
        DigitalObject designObject = repo.getDigitalObject(DESIGN_OBJECT_ID);
        return designObject;
    }
    
    // Note: consider changing MigrationService when changing this method
    private DigitalObject createNewDesignDigitalObject() throws RepositoryException {
        DigitalObject designObject = repo.createDigitalObject(DESIGN_OBJECT_ID);
        Map<String, String> atts = new HashMap<String, String>();
        atts.put("uiConfig", defaultUiConfig);
        atts.put("type", "design");
        atts.put("meta", "true");
        AuthConfig defaultAuthConfig = AuthConfigFactory.getDefaultAuthConfig();
        String defaultAuthConfigJson = gson.toJson(defaultAuthConfig);
        atts.put("authConfig", defaultAuthConfigJson);
//        atts.put("schema.user", DefaultSchemasFactory.getDefaultUserSchema());
//        atts.put("schema.group", DefaultSchemasFactory.getDefaultGroupSchema());
//        atts.put("schema.document", DefaultSchemasFactory.getDefaultDocumentSchema());
        
        Map<String, String> schemaIds = new HashMap<String, String>();
        String userId = createSchemaObject("User", DefaultSchemasFactory.getDefaultUserSchema());
        schemaIds.put(userId, "User");

        String remoteUserId = createSchemaObject("RemoteUser", DefaultSchemasFactory.getDefaultRemoteUserSchema());
        schemaIds.put(remoteUserId, "RemoteUser");

        String groupId = createSchemaObject("Group", DefaultSchemasFactory.getDefaultGroupSchema());
        schemaIds.put(groupId, "Group");
        String docId = createSchemaObject("Document", DefaultSchemasFactory.getDefaultDocumentSchema());
        schemaIds.put(docId, "Document");

        String dataTypeId = createSchemaObject("DataType 1.0", DefaultSchemasFactory.getDefaultDataTypeSchema());
        schemaIds.put(dataTypeId, "DataType 1.0");
        
        atts.put(SCHEMAS, gson.toJson(schemaIds));
        designObject.setAttributes(atts);
        return designObject;
    }

    private String createSchemaObject(String name, String schema) throws RepositoryException {
        return createSchemaObject(this.handleMinter, this.repo, name, schema);
    }
    
    public static String createSchemaObject(HandleMinter handleMinter, Repository repo, String name, String schema) throws RepositoryException {
        DigitalObject dobj = null;
        String handle;
        while (dobj == null) {
            handle = handleMinter.mintByTimestamp();
            try {
                dobj = repo.createDigitalObject(handle); 
            } catch (CreationException e) {
                // retry
            }
        }

        SchemaInstance schemaInstance = new SchemaInstance();
        schemaInstance.identifier = dobj.getHandle();
        schemaInstance.name = name;
        schemaInstance.schema = new JsonParser().parse(schema);
        String json = gson.toJson(schemaInstance);
        
        Map<String, String> atts = new HashMap<String, String>();
        atts.put(RegistrarService.TYPE, "Schema");
        atts.put(RegistrarService.JSON, json);
        atts.put(AccessControlList.CREATED_BY_ATTRIBUTE, "admin");
        atts.put(AccessControlList.MODIFIED_BY_ATTRIBUTE, "admin");
        atts.put("schemaName", name);
        atts.put("version", "1");
        dobj.setAttributes(atts);
        return dobj.getHandle();
    }
    
    public Map<String, AuthConfig> getRemoteAuthConfigs() {
        return remoteAuthConfigs;
    }
    
    public Design getDesign() {
        return design;
    }
    
    private static final FastDateFormat.FormatSpec formatSpec = new FastDateFormat.FormatSpec("", "", "", "", "", true, true);
    
    private String dateForSearch(long date) {
        return FastDateFormat.getUtcFormat().format(formatSpec, date).substring(0, 17);
    }
    
    public CloseableIterator<DigitalObject> getModifiedObjectsSince(long since, String type) throws RepositoryException {
        String queryString = "objmodified:[" + dateForSearch(since) + " TO 99991231235959999] AND NOT objatt_remote:true AND NOT objatt_meta:true";
        if (type != null) {
            queryString += " AND type:"+ type;
        }
        Query q = new RawQuery(queryString);
        return repo.search(q);
    }
    
    public CloseableIterator<DigitalObject> getModifiedObjectsSince(long since, String type, String[] includes, String[] excludes) throws RepositoryException {
        String queryString = "objmodified:[" + dateForSearch(since) + " TO 99991231235959999] AND NOT objatt_remote:true AND NOT objatt_meta:true";
        if (includes != null) {
            String includeTypes = getOrTypeQueryFragment(includes);
            queryString += " AND (" + includeTypes + ")";
        }
        if (excludes != null) {
            String excludeTypes = getOrTypeQueryFragment(excludes);
            queryString += " AND NOT (" + excludeTypes + ")";
        }
        
        if (type != null) {
            queryString += " AND type:"+ type;
        }
        Query q = new RawQuery(queryString);
        return repo.search(q);
    }
    
    public CloseableIterator<DigitalObject> getLocalSchemas() throws RepositoryException {
        String queryString = "type:Schema AND remoteRepository:null";
        Query q = new RawQuery(queryString);
        return repo.search(q);
    }
    
    private String getOrTypeQueryFragment(String[] types) {
        String result = "";
        for (int i = 0; i < types.length; i++) {
            if (i != 0) {
                result += " OR ";
            }
            result += "type:" + types[i];
        }
        return result;
    }
    
    public String getAllLocalSchemasAsJsonString() throws RepositoryException {
        try {
            Map<String, JsonNode> schemaNodes = new HashMap<String, JsonNode>();
            for (Map.Entry<TypeAndRegistrar, SchemaAndNode> entry : schemas.entrySet()) {
                if (entry.getKey().remoteRepository == null) {
                    schemaNodes.put(entry.getKey().type, entry.getValue().schemaNode);
                }
            }
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writeValueAsString(schemaNodes);
            return json;
        } catch (JsonProcessingException e) {
            throw new InternalException(e);
        }
    }
    
    public String getLocalSchemaAsJsonString(String objectType) throws RepositoryException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            SchemaAndNode schema = schemas.get(new TypeAndRegistrar(objectType, null));
            if (schema == null) return null; 
            String json = mapper.writeValueAsString(schema.schemaNode);
            return json;
        } catch (JsonProcessingException e) {
            throw new InternalException(e);
        }
    }
    
    public ObjectComponent getObjectComponent(String objectId, String jsonPointer, boolean metadata, Long start, Long end) throws RepositoryException, InvalidException {
        ObjectComponent result =  null;
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) throw new NoSuchDigitalObjectException(objectId);
        String type = dobj.getAttribute("type");
        String remoteRepository = dobj.getAttribute("remoteRepository");
        String jsonData = dobj.getAttribute(JSON);
        if (jsonData == null) throw new InternalException("Missing JSON attribute on " + objectId);
        JsonNode dataNode = JsonUtil.parseJson(jsonData);
        String mediaType = getMediaType(type, remoteRepository, dataNode, jsonPointer);
        DataElement el = dobj.getDataElement(jsonPointer);
        if (el != null) {
            if (mediaType == null) {
                mediaType = el.getAttribute("mimetype");
            }
            String filename = el.getAttribute("filename");
            long size = el.getSize();
            InputStream stream = null;
            if (!metadata) {
                if (size <= 0 || (start == null && end == null)) {
                    stream = el.read(); // TODO deal with unknown size in range requests?
                } else if (end == null) {
                    end = size - 1;
                    stream = el.read(start.longValue(), -1);
                } else if (start == null) {
                    start = size - end.longValue();
                    end = size - 1;
                    stream = el.read(start, -1);
                } else if (start > end) {
                    // no stream
                } else {
                    if (end > size - 1) end = size - 1;
                    long length = end - start + 1;
                    stream = el.read(start, length);
                }
            }
            result = new ObjectComponentStream(stream, filename, type, remoteRepository, mediaType, start, end, size);
        } else {
            JsonNode subNode = dataNode.at(jsonPointer);
            if (subNode.isMissingNode()) return null;
            result = new ObjectComponentJson(subNode, type, remoteRepository, mediaType);
        }
        return result;
    }
    
    public String getObjectJson(String objectId) throws RepositoryException {
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) throw new NoSuchDigitalObjectException(objectId);
        String jsonData = dobj.getAttribute(JSON);
        if (jsonData == null) throw new InternalException("Missing JSON attribute on " + objectId);
        return jsonData;
    }
    
    public DigitalObject getDigitalObject(String objectId) throws RepositoryException {
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) throw new NoSuchDigitalObjectException(objectId);
        return dobj;
    }
    
    public AccessControlList getAclFor(String objectId) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(objectId);
        if (dobj == null) throw new NoSuchDigitalObjectException(objectId);
        AccessControlList acl = new AccessControlList(dobj);
        return acl;
    }
    
    public void delete(String objectId) throws RepositoryException {
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) {
            throw new NoSuchDigitalObjectException(objectId);
        } else if ("true".equals(dobj.getAttribute("remote")) || "true".equals(dobj.getAttribute("meta"))) {
            throw new InternalException("Object not valid for deletion: " + objectId);
        } else {
            String type = dobj.getAttribute(TYPE);
            repo.deleteDigitalObject(objectId);
            if ("Schema".equals(type)) {
                deleteFromKnownSchemas(objectId);
            }
            if (handleClient != null) {
                try {
                    handleClient.deleteHandle(dobj.getHandle());
                } catch (HandleException e) {
                    logger.warn("Failure to delete handle " + dobj.getHandle() + ", out of sync", e);
                    // throw new InternalException(e);
                }
            }
        }
    }

    public void deleteJsonPointer(String objectId, String jsonPointer, String userId) throws RepositoryException, InvalidException {
//        if (jsonPointer == null || jsonPointer.isEmpty()) {
//            delete(objectId);
//            return;
//        }
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) {
            throw new NoSuchDigitalObjectException(objectId);
        } else if ("true".equals(dobj.getAttribute("remote")) || "true".equals(dobj.getAttribute("meta"))) {
            throw new InternalException("Object not valid for deletion: " + objectId);
        }
        String existingJsonData = dobj.getAttribute(JSON);
        JsonNode existingJsonNode = JsonUtil.parseJson(existingJsonData);
        JsonUtil.deletePointee(existingJsonNode, jsonPointer);
        String modifiedJson = existingJsonNode.toString();
        writeJsonAndPayloadsIntoDigitalObjectIfValidAsUpdate(objectId, modifiedJson, null, userId, Collections.singletonList(jsonPointer));
    }
    
    public void deletePayload(String objectId, String payloadName, String userId) throws RepositoryException, InvalidException {
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) {
            throw new NoSuchDigitalObjectException(objectId);
        } else if ("true".equals(dobj.getAttribute("remote")) || "true".equals(dobj.getAttribute("meta"))) {
            throw new InternalException("Object not valid for deletion: " + objectId);
        }
        String type = dobj.getAttribute(TYPE);
        if (type == null) {
            throw new NoSuchDigitalObjectException(objectId);
        }
        String existingJsonData = dobj.getAttribute(JSON);
        List<String> payloadsToDelete = Collections.singletonList(payloadName);
        updateDigitalObject(dobj, type, existingJsonData, payloadsToDelete, null, userId);
    }
    
    public DigitalObject writeJsonAndPayloadsIntoDigitalObjectIfValidAsUpdate(String objectId, String jsonData, List<Payload> newPayloads, String userId, Collection<String> payloadsToDelete) throws RepositoryException, InvalidException {
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) {
            throw new NoSuchDigitalObjectException(objectId);
        } else if ("true".equals(dobj.getAttribute("remote")) || "true".equals(dobj.getAttribute("meta"))) {
            throw new InternalException("Object not valid for update: " + objectId);
        }
        String type = dobj.getAttribute(TYPE);
        if (type == null) {
            throw new NoSuchDigitalObjectException(objectId);
        }
        SchemaAndNode schema = schemas.get(new TypeAndRegistrar(type, null));
        if (schema == null) {
            throw new InvalidException("Unknown type " + type);
        }
        JsonNode jsonNode = JsonUtil.parseJson(jsonData);
        Map<String, JsonNode> pointerToSchemaMap = validator.schemaValidateAndReturnKeywordsMap(jsonNode, schema.schemaNode, schema.schema);
        validator.postSchemaValidate(jsonNode, pointerToSchemaMap);
        validator.validatePayloads(newPayloads);
        processObjectBasedOnJsonAndType(dobj, type, jsonNode, pointerToSchemaMap, userId);
        jsonData = JsonUtil.prettyPrintJson(jsonNode);
        updateDigitalObject(dobj, type, jsonData, payloadsToDelete, newPayloads, userId);
        if ("Schema".equals(type)) {
            addToKnownSchemas(dobj.getHandle(), true);
        }
        if (handleClient != null) {
            try {
                handleClient.updateHandleFor(dobj.getHandle(), dobj, type, jsonNode);
            } catch (HandleException e) {
                logger.error("Failure to update handle after updating object " + dobj.getHandle() + ", out of sync", e);
                throw new InternalException(e);
            }
        }
        return dobj;
    }
    
    public SchemaAndNode getSchemaAndNode(String type, String remoteRepository) {
        SchemaAndNode schema = schemas.get(new TypeAndRegistrar(type, null));
        return schema;
    }
    
    public DigitalObject writeJsonAndPayloadsIntoDigitalObjectIfValid(String type, String jsonData, List<Payload> payloads, String handle, String creatorId) throws RepositoryException, InvalidException {
        SchemaAndNode schema = schemas.get(new TypeAndRegistrar(type, null));
        if (schema == null) {
            throw new InvalidException("Unknown type " + type);
        }
        JsonNode jsonNode = JsonUtil.parseJson(jsonData);
        Map<String, JsonNode> pointerToSchemaMap = validator.schemaValidateAndReturnKeywordsMap(jsonNode, schema.schemaNode, schema.schema);
        validator.postSchemaValidate(jsonNode, pointerToSchemaMap);
        validator.validatePayloads(payloads);
        preprocessObjectBasedOnJsonAndType(null, type, jsonNode, pointerToSchemaMap, creatorId);
        DigitalObject dobj = createNewDigitalObject(jsonNode, pointerToSchemaMap, handle, creatorId);
        processObjectBasedOnJsonAndType(dobj, type, jsonNode, pointerToSchemaMap, creatorId);
        jsonData = JsonUtil.prettyPrintJson(jsonNode);
        updateDigitalObject(dobj, type, jsonData, Collections.<String>emptyList(), payloads, creatorId);
        if (handleClient != null) {
            try {
                handleClient.registerHandle(dobj.getHandle(), dobj, type, jsonNode);
            } catch (HandleException e) {
                try {
                    dobj.delete();
                } catch (RepositoryException ex) {
                    logger.error("Failure to delete new object after failure to register handle " + dobj.getHandle() + ", out of sync", e);
                }
                throw new InternalException(e);
            }
        }
        if ("Schema".equals(type)) {
            addToKnownSchemas(dobj.getHandle(), false);
        }
        return dobj;
    }
    
    public void updateAllHandleRecords() {
        handlesUpdater.updateAllHandles(handleClient, repo);
    }
    
    private void preprocessObjectBasedOnJsonAndType(String handle, String type, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap, String creatorId) throws RepositoryException, InvalidException {
        SchemaNameProcessor schemaNameProcessor = new SchemaNameProcessor(this);
        schemaNameProcessor.preprocess(type, handle, jsonNode);
        UsernameProcessor usernameProcessor = new UsernameProcessor(repo);
        usernameProcessor.preprocess(handle, jsonNode, pointerToSchemaMap);
        PasswordProcessor passwordProcessor = new PasswordProcessor();
        passwordProcessor.preprocess(jsonNode, pointerToSchemaMap);
    }
    
    private void processObjectBasedOnJsonAndType(DigitalObject dobj, String type, JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap, String creatorId) throws RepositoryException, InvalidException {
        SchemaNameProcessor schemaNameProcessor = new SchemaNameProcessor(this);
        schemaNameProcessor.process(type, dobj, jsonNode);
        UsernameProcessor usernameProcessor = new UsernameProcessor(repo);
        usernameProcessor.process(dobj, jsonNode, pointerToSchemaMap);
        PasswordProcessor passwordProcessor = new PasswordProcessor();
        passwordProcessor.process(dobj, jsonNode, pointerToSchemaMap);
        UsersListProcessor usersListProcessor = new UsersListProcessor();
        usersListProcessor.process(dobj, jsonNode, pointerToSchemaMap);
        JsonAugmenter jsonAugmenter = new JsonAugmenter(); 
        jsonAugmenter.augment(dobj, jsonNode, pointerToSchemaMap, creatorId);
    }
    
    private synchronized void addToKnownSchemas(String handle, boolean update) throws RepositoryException {
        DigitalObject designObject = this.getDesignDigitalObject();
        Map<String, String> knownSchemaIds = getKnownSchemaIdsFromDesignObject(designObject);
        List<String> knownSchemaHandles = new ArrayList<String>(knownSchemaIds.keySet());
        if (!knownSchemaHandles.contains(handle) || update) {
            if (!knownSchemaHandles.contains(handle)) knownSchemaHandles.add(handle);
            List<DigitalObject> knownSchemaObjects = objectListFromHandleList(knownSchemaHandles);
            try {
                rebuildSchemasFromListOfObjects(knownSchemaObjects);
            } catch (InvalidException e) {
                throw new InternalException(e.getMessage());
            } catch (ProcessingException e) {
                throw new InternalException(e.getMessage());
            }
        }
    }
    
    private synchronized void deleteFromKnownSchemas(String handle) throws RepositoryException {
        DigitalObject designObject = this.getDesignDigitalObject();
        Map<String, String> knownSchemaIds = getKnownSchemaIdsFromDesignObject(designObject);
        List<String> knownSchemaHandles = new ArrayList<String>(knownSchemaIds.keySet());
        if (knownSchemaHandles.contains(handle)) {
            knownSchemaHandles.remove(handle);
            List<DigitalObject> knownSchemaObjects = objectListFromHandleList(knownSchemaHandles);
            try {
                rebuildSchemasFromListOfObjects(knownSchemaObjects);
            } catch (InvalidException e) {
                throw new InternalException(e.getMessage());
            } catch (ProcessingException e) {
                throw new InternalException(e.getMessage());
            }
        }
    }

    public String getHandleForSuffix(String suffix) {
        return handleMinter.mintWithSuffix(suffix);
    }

    DigitalObject createNewDigitalObject(JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap, String handle, String creatorId) throws RepositoryException {
        DigitalObject result = null;
        if (handle != null) {
            result = repo.createDigitalObject(handle);
        } else {
            String primaryData = getPrimaryData(jsonNode, pointerToSchemaMap);
            if (primaryData != null) {
                handle = handleMinter.mint(primaryData);
                try {
                    result = repo.createDigitalObject(handle);
                } catch (Exception e) {
                    // retry
                }
            }
            while (result == null) {
                handle = handleMinter.mintByTimestamp();
                try {
                    result = repo.createDigitalObject(handle); 
                } catch (CreationException e) {
                    // retry
                }
            }
        }
        if (creatorId == null) {
            creatorId = "anonymous";
        }
        Map<String, String> atts = new HashMap<String, String>();
        atts.put(AccessControlList.CREATED_BY_ATTRIBUTE, creatorId);
        atts.put(AccessControlList.MODIFIED_BY_ATTRIBUTE, creatorId);
        result.setAttributes(atts);
        return result;
    }
    
    public DigitalObject publishVersion(String objectId, String userId) throws RepositoryException, VersionException {
        DigitalObject result = versionManager.publishVersion(objectId, userId);
        if (handleClient != null) {
            try {
                JsonNode dataNode = JsonUtil.parseJson(result.getAttribute(JSON));
                String type = result.getAttribute(TYPE);
                
                handleClient.registerHandle(result.getHandle(), result, type, dataNode);
            } catch (HandleException e) {
                try {
                    result.delete();
                } catch (RepositoryException ex) {
                    logger.error("Failure to delete new object after failure to register handle " + result.getHandle() + ", out of sync", e);
                }
                throw new InternalException(e);
            } catch (InvalidException e) {
                try {
                    result.delete();
                } catch (RepositoryException ex) {
                    logger.error("Failure to delete new object after failure to register handle " + result.getHandle() + ", out of sync", e);
                }
                throw new InternalException(e);
            }
        }                
        return result;
    }
    
    public List<DigitalObject> getVersionsFor(String objectId, String userId) throws RepositoryException {
        List<String> groupIds = getAclEnforcer().getGroupsForUser(userId);
        return versionManager.getVersionsFor(objectId, userId, groupIds, design.authConfig, remoteAuthConfigs);
    }
    
    public long addReplicatedObject(JsonReader jsonReader, String remoteRepository) throws RepositoryException, IOException {
        DigitalObject dobj = RepositoryJsonSerializer.loadJsonForOneObjectIntoRepository(jsonReader, repo);
        String type = dobj.getAttribute("type");
        long modified = Long.parseLong(dobj.getAttribute("internal.modified"));
        Map<String, String> atts = dobj.getAttributes();
        atts.put("remote", "true");
        atts.put("remoteRepository", remoteRepository);
        dobj.setAttributes(atts);
        if ("Schema".equals(type)) {
            addToKnownSchemas(dobj.getHandle(), true);
        }
        return modified;
    }

    private static void updateDigitalObject(DigitalObject dobj, String objectType, String jsonData, Collection<String> payloadsToDelete, List<Payload> payloads, String userId) throws RepositoryException {
        Map<String, String> atts = new HashMap<String, String>();
        atts.put(TYPE, objectType);
        atts.put(JSON, jsonData);
        if (userId == null) {
            userId = "anonymous";
        }
        atts.put(AccessControlList.MODIFIED_BY_ATTRIBUTE, userId);
        dobj.setAttributes(atts);
        if (payloadsToDelete != null) {
            for (String payloadName : payloadsToDelete) {
                try {
                    dobj.deleteDataElement(payloadName);
                } catch (NoSuchDataElementException e) {
                    // ignoring this
                }
            }
        }
        if (payloads != null) {
            for (Payload payload : payloads) {
                DataElement el = dobj.getOrCreateDataElement(payload.name);
                try {
                    if (payload.in == null) {
                        el.write(new ByteArrayInputStream(new byte[0]));
                    } else {
                        el.write(payload.in);
                    }
                    el.setAttribute("mimetype", payload.mimetype);
                    el.setAttribute("filename", payload.filename);
                } catch (IOException e) {
                    throw new InternalException(e);
                } finally {
                    if (payload.in != null) try { payload.in.close(); } catch (Exception e) { }
                }
            }
        }
    }
    
    public QueryResults<DigitalObject> searchRepo(Query query) throws RepositoryException {
        return (QueryResults<DigitalObject>) repo.search(query);
    }
    
    public SearchResults search(String query, int pageNum, int pageSize, String sortFieldsString) throws RepositoryException {
        List<SortField> sortFields = null;
        if (sortFieldsString != null) {
            sortFields = getSortFieldsFromParam(sortFieldsString);
        }
        
        QueryParams params = new QueryParams(pageNum, pageSize, sortFields);
        Query q = new RawQuery("valid:true AND (" + query + ")");
        int totalMatches = -1;
        List<ContentPlusMeta> resultsList = new ArrayList<ContentPlusMeta>();
        CloseableIterator<DigitalObject> results = repo.search(q, params);
        try {
            if (results instanceof QueryResults) {
                totalMatches = ((QueryResults<DigitalObject>)results).size();
            }
            while (results.hasNext()) {
                DigitalObject dobj = results.next();
                ContentPlusMeta d = createContentPlusMeta(dobj);
                resultsList.add(d);
            }
        } catch (UncheckedRepositoryException e) {
            throw e.getCause();
        } finally {
            results.close();
        }
        SearchResults searchResults = new SearchResults(totalMatches, pageNum, pageSize, resultsList);
        return searchResults;
    }
    
    public void search(String query, int pageNum, int pageSize, String sortFieldsString, PrintWriter printWriter) throws RepositoryException, IOException {
        Gson gson = GsonUtility.getPrettyGson();
        
        List<SortField> sortFields = null;
        if (sortFieldsString != null) {
            sortFields = getSortFieldsFromParam(sortFieldsString);
        }
        QueryParams params = new QueryParams(pageNum, pageSize, sortFields);
        Query q = new RawQuery("valid:true AND (" + query + ")");
        QueryResults<DigitalObject> results = (QueryResults<DigitalObject>) repo.search(q, params);
        
        JsonWriter writer = new JsonWriter(printWriter);
        writer.setIndent("  ");
        writer.beginObject();
        writer.name("pageNum").value(pageNum);
        writer.name("pageSize").value(pageSize);
        writer.name("size").value(results.size());
        writer.name("results").beginArray();
        while (results.hasNext()) {
            DigitalObject dobj = results.next();
            ContentPlusMeta d = createContentPlusMeta(dobj);
            gson.toJson(d, ContentPlusMeta.class, writer);
        }
        writer.endArray();
        writer.endObject();
    }
    
    public static ContentPlusMeta createContentPlusMeta(DigitalObject dobj) throws RepositoryException {
        ContentPlusMeta result = new ContentPlusMeta();
        result.type = dobj.getAttribute("type");
        result.id = dobj.getHandle();
        JsonParser parser = new JsonParser();
        String jsonData = dobj.getAttribute("json");
        JsonElement content = parser.parse(jsonData);
        result.content = content;
        result.metadata = getMetadataForObject(dobj);
        
        List<DataElement> elements = dobj.getDataElements();
        if (elements.size() > 0) {
            result.payloads = new ArrayList<ContentPlusMeta.PayloadMetadata>();
            for (DataElement el : elements) {
                ContentPlusMeta.PayloadMetadata payloadMetadata = new ContentPlusMeta.PayloadMetadata();
                payloadMetadata.name = el.getName();
                payloadMetadata.filename = el.getAttribute("filename");
                payloadMetadata.mediaType = el.getAttribute("mimetype");
                payloadMetadata.size = el.getSize();
                result.payloads.add(payloadMetadata);
            }
        }
        return result;
    }
  
    private static ContentPlusMeta.Metadata getMetadataForObject(DigitalObject dobj) throws RepositoryException {
        ContentPlusMeta.Metadata metadata = new ContentPlusMeta.Metadata();
        metadata.createdOn = Long.valueOf(dobj.getAttribute("internal.created"));
        metadata.createdBy = dobj.getAttribute("createdBy");
        metadata.modifiedOn = Long.valueOf(dobj.getAttribute("internal.modified"));
        metadata.modifiedBy = dobj.getAttribute("modifiedBy");
        metadata.remoteRepository = dobj.getAttribute("remoteRepository");
        String isVersion = dobj.getAttribute("isVersion");
        if ("true".equals(isVersion)) {
            metadata.isVersion = true;
            metadata.versionOf = dobj.getAttribute("versionOf");
            metadata.publishedBy = dobj.getAttribute("publishedBy");
            metadata.publishedOn = Long.valueOf(dobj.getAttribute("publishedOn"));
        } else {
            metadata.isVersion = false;
        }
        return metadata;
    }
    
    private List<SortField> getSortFieldsFromParam(String sortFields) {
        if (sortFields == null || "".equals(sortFields)) {
            return null;
        } else {
            List<SortField> result = new ArrayList<SortField>();
            List<String> sortFieldStrings = getFieldsFromString(sortFields);
            for (String sortFieldString : sortFieldStrings) {
                result.add(getSortFieldFromString(sortFieldString));
            }
            return result;
        }
    }
    
    private SortField getSortFieldFromString(String sortFieldString) {
        String[] terms = sortFieldString.split(" ");
        boolean reverse = false;
        if (terms.length > 1) {
            String direction = terms[1];
            if ("DESC".equals(direction)) reverse = true;
        }
        String fieldName = terms[0];
        return new SortField(fieldName, reverse);
    }
    
    private List<String> getFieldsFromString(String s) {
        return Arrays.asList(s.split(","));
    }
    
    public Map<String, JsonNode> getPointerToSchemaMap(String objectType, String remoteRepository, JsonNode jsonNode) throws InvalidException {
        SchemaAndNode schema;
        if ("Schema".equals(objectType)) {
            schema = schemas.get(new TypeAndRegistrar("Schema", null));
        } else {
            schema = schemas.get(new TypeAndRegistrar(objectType, remoteRepository));
        }
        if (schema == null) {
            throw new InvalidException("Unknown type " + objectType);
        }
        Map<String, JsonNode> keywordsMap = validator.schemaValidateAndReturnKeywordsMap(jsonNode, schema.schemaNode, schema.schema);
        return keywordsMap;
    }

    void validateForTesting(String objectType, String remoteRepository, String jsonData) throws InvalidException, RepositoryException {
        JsonNode jsonNode = JsonUtil.parseJson(jsonData);
        Map<String, JsonNode> keywordsMap = getPointerToSchemaMap(objectType, remoteRepository, jsonNode);
        validator.postSchemaValidate(jsonNode, keywordsMap);
    }
    
    static String getPrimaryData(JsonNode jsonNode, Map<String, JsonNode> pointerToSchemaMap) {
        for (Map.Entry<String, JsonNode> entry : pointerToSchemaMap.entrySet()) {
            String jsonPointer = entry.getKey();
            JsonNode subSchema = entry.getValue();
            JsonNode isPrimaryNode = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "preview", "isPrimary");
            if (isPrimaryNode == null) continue;
            if (isPrimaryNode.asBoolean()) {
                JsonNode referenceNode = jsonNode.at(jsonPointer);
                if (referenceNode == null) {
                    logger.warn("Unexpected missing isPrimary node " + jsonPointer);
                } else {
                    return referenceNode.asText();
                }
            }
        }
        return null;
    }

    public String getMediaType(String type, String remoteRepository, JsonNode jsonNode, String jsonPointer) throws InvalidException {
        Map<String, JsonNode> pointerToSchemaMap = getPointerToSchemaMap(type, remoteRepository, jsonNode);
        JsonNode subSchema = pointerToSchemaMap.get(jsonPointer);
        if (subSchema == null) return null;
        JsonNode mediaType = JsonUtil.getDeepProperty(subSchema, Constants.REPOSITORY_SCHEMA_KEYWORD, "response", "mediaType");
        if (mediaType == null) return null;
        return mediaType.asText();
    }

    public Map<String, Integer> getTypeCount() throws RepositoryException {
    	Map<String, Integer> result = new HashMap<String, Integer>();
    	for (String type : design.schemas.keySet()) {
    		Query typeQuery = new RawQuery("type:" + type);
            QueryResults<String> results = (QueryResults<String>) repo.searchHandles(typeQuery);
    		try {
    		    Integer count = results.size();
    		    result.put(type, count);
    		} finally {
    		    results.close();
    		}
    	}
    	return result;
    }
    
    public List<UserObjectCount> getCountByUser() throws RepositoryException {
        List<UserObjectCount> result = new ArrayList<UserObjectCount>();
        Query allUsersQuery = new RawQuery("username:[* TO *]");
        CloseableIterator<DigitalObject> userResults = repo.search(allUsersQuery);
        while (userResults.hasNext()) {
            DigitalObject userObject = userResults.next();
            String userHandle = userObject.getHandle();
            String username = userObject.getAttribute("username");
            Query createdByQuery = new RawQuery("createdBy:"+userHandle);
            QueryResults<String> createdByResults = (QueryResults<String>) repo.searchHandles(createdByQuery);
            int count = createdByResults.size();
            UserObjectCount userObjectCount = new UserObjectCount();
            userObjectCount.username = username;
            userObjectCount.handle = userHandle;
            userObjectCount.createCount = count;
            result.add(userObjectCount);
            createdByResults.close();
        }
        userResults.close();
        Query createdByAdminQuery = new RawQuery("createdBy:admin");
        QueryResults<String> createdByAdminResults = (QueryResults<String>) repo.searchHandles(createdByAdminQuery);
        int adminCount = createdByAdminResults.size();
        UserObjectCount userObjectCount = new UserObjectCount();
        userObjectCount.username = "admin";
        userObjectCount.handle = "admin";
        userObjectCount.createCount = adminCount;
        
        result.add(userObjectCount);
        createdByAdminResults.close();
        return result;
    }
    
    public Status getStatus() throws RepositoryException {
    	Status status = new Status();
    	status.typeCount = getTypeCount();
    	return status;
    }

    public DigitalObjectSchemaValidator getValidator() {
        return validator;
    }

    public void ensureIndexUpToDate() throws RepositoryException {
        if (repo instanceof NetworkedRepository) {
            ((NetworkedRepository) repo).ensureIndexUpToDate();
        }
    }
    
    public void setAdminPassword(String password) throws Exception {
        authenticator.setAdminPassword(password);
    }

    public void shutdown() {
        handlesUpdater.shutdown();
        repo.close();
    }

    public UpdateStatus getHandleUpdateStatus() {
        return this.handlesUpdater.getStatus();
    }
}
