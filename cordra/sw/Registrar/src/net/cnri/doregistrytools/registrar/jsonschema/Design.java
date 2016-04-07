/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.List;
import java.util.Map;

import net.cnri.doregistrytools.registrar.auth.AuthConfig;
import net.cnri.doregistrytools.registrar.replication.RemoteRepositoryInfo;
import net.cnri.doregistrytools.registrar.replication.ReplicationCredentials;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.JsonNode;

public class Design {
    public Map<String, JsonNode> schemas; // map from name to schema json
    public JsonNode uiConfig;
    public String serverPrefix;
    public List<RemoteRepositoryInfo> remoteRepositories; //registrars this server will pull from
    @JsonView(Views.Internal.class) public List<ReplicationCredentials> replicationCredentials; //Usernames and passwords that can be used to pull from this server
    public Map<String, Map<String, JsonNode>> alienSchemas; //schemas replicated from a remote registrar <remoteRepository, <type, schema>>
    public AuthConfig authConfig;
    public HandleMintingConfig handleMintingConfig; 
    public Map<String, String> schemaIds; // map from id to name
    
    public Design(Map<String, JsonNode> schemas, JsonNode uiConfig, String serverPrefix, List<RemoteRepositoryInfo> remoteRepositories, 
                    List<ReplicationCredentials> replicationCredentials, Map<String, Map<String, JsonNode>> alienSchemas, AuthConfig authConfig, HandleMintingConfig handleMintingConfig, Map<String, String> schemaIds) {
        this.schemas = schemas;
        this.uiConfig = uiConfig;
        this.serverPrefix = serverPrefix;
        this.remoteRepositories = remoteRepositories;
        this.replicationCredentials = replicationCredentials;
        this.alienSchemas = alienSchemas;
        this.authConfig = authConfig;
        this.handleMintingConfig = handleMintingConfig;
        this.schemaIds = schemaIds;
    }

}
