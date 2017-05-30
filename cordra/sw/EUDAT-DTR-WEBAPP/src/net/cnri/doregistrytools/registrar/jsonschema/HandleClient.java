/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.util.StringUtils;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AdminRecord;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.CreateHandleRequest;
import net.handle.hdllib.DeleteHandleRequest;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Util;

public class HandleClient {
    public static final byte[] LOCATION_TYPE = Util.encodeString("10320/loc");
    public static final byte[] REGISTRY_TYPE = Util.encodeString("10320/registry");
    public static final byte[] OLD_REPO_LOOKUP_TYPE = Util.encodeString("CNRI.OBJECT_SERVER");

    private final AuthenticationInfo authInfo;
    
    private final HandleMintingConfig handleMintingConfig;
    private final HandleResolver resolver;
    private final String repoHandle;
    
    public HandleClient(AuthenticationInfo authInfo, HandleMintingConfig handleMintingConfig, String repoHandle) {
        this.authInfo = authInfo;
        this.handleMintingConfig = handleMintingConfig;
        this.resolver = new HandleResolver();
        this.repoHandle = repoHandle;
    }

    private static String ensureSlash(String s) {
        if (s.endsWith("/")) return s;
        else return s + "/";
    }
    
    public void registerHandle(String handle, DigitalObject dobj, String type, JsonNode dataNode) throws HandleException, RepositoryException {
        AdminRecord adminRecord = new AdminRecord(authInfo.getUserIdHandle(),authInfo.getUserIdIndex(),true,true,true,true,true,true,true,true,true,true,true,true);
        HandleValue adminValue = new HandleValue(100,Common.ADMIN_TYPE,Encoder.encodeAdminRecord(adminRecord));

        //String locXml = getLocationsXml(baseUri, handle);
        String locXml = LocBuilder.createLocFor(handleMintingConfig, dobj, type, dataNode);
        
        HandleValue locationValue = new HandleValue(1, LOCATION_TYPE, Util.encodeString(locXml));
        HandleValue registryValue = new HandleValue(2, REGISTRY_TYPE, Util.encodeString(repoHandle));
        HandleValue repoLookupValue = new HandleValue(3, OLD_REPO_LOOKUP_TYPE, Util.encodeString(repoHandle));

        CreateHandleRequest req = new CreateHandleRequest(Util.encodeString(handle), new HandleValue[] {adminValue, locationValue, registryValue, repoLookupValue}, authInfo);
        // req.overwriteWhenExists = true;
        AbstractResponse response = resolver.processRequest(req);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected response: " + response);
        }
    }
    
    public void deleteHandle(String handle) throws HandleException {
        DeleteHandleRequest req = new DeleteHandleRequest(Util.encodeString(handle), authInfo);
        AbstractResponse response = resolver.processRequest(req);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected response: " + response);
        }
    }

    private static String getLocationsXml(String baseUri, String id) {
        String viewUrl = baseUri + "#objects/" + StringUtils.encodeURLPath(id);
        String viewJson = baseUri + "objects/" + StringUtils.encodeURLPath(id);

        return "<locations>\n"+
        "<location href=\""+viewUrl+"\" weight=\"1\" view=\"ui\" />\n"+
        "<location href=\""+viewJson+"\" weight=\"0\" view=\"json\" />\n"+
        "</locations>";
    }

    public void updateHandleFor(String handle, DigitalObject dobj, String type, JsonNode dataNode) throws HandleException, RepositoryException {
        AdminRecord adminRecord = new AdminRecord(authInfo.getUserIdHandle(),authInfo.getUserIdIndex(),true,true,true,true,true,true,true,true,true,true,true,true);
        HandleValue adminValue = new HandleValue(100,Common.ADMIN_TYPE,Encoder.encodeAdminRecord(adminRecord));

        String locXml = LocBuilder.createLocFor(handleMintingConfig, dobj, type, dataNode);
        
        HandleValue locationValue = new HandleValue(1, LOCATION_TYPE, Util.encodeString(locXml));
        HandleValue registryValue = new HandleValue(2, REGISTRY_TYPE, Util.encodeString(repoHandle));
        HandleValue repoLookupValue = new HandleValue(3, OLD_REPO_LOOKUP_TYPE, Util.encodeString(repoHandle));

        CreateHandleRequest req = new CreateHandleRequest(Util.encodeString(handle), new HandleValue[] {adminValue, locationValue, registryValue, repoLookupValue}, authInfo);
        req.overwriteWhenExists = true;
        AbstractResponse response = resolver.processRequest(req);
        if (response.responseCode != AbstractMessage.RC_SUCCESS) {
            throw new HandleException(HandleException.INTERNAL_ERROR, "Unexpected response: " + response);
        }
    }

}
