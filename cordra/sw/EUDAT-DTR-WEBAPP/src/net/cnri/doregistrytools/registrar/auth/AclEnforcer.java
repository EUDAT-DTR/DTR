/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.util.CollectionUtil;

public class AclEnforcer {

    private Repository repo;
    private AuthConfig authConfig;
    private Map<String, AuthConfig> remoteAuthConfigs;
    
    public enum Permission {
        NONE,
        READ,
        WRITE;
    }
    
    public AclEnforcer(Repository repo) {
        this.repo = repo;
    }
    
    public Permission permittedOperations(String userId, String objectId) throws RepositoryException {
        DigitalObject dobj = repo.getDigitalObject(objectId);
        return permittedOperations(userId, dobj);
    }
    
    private Permission permittedOperationsForUser(String userId, DigitalObject dobj) throws RepositoryException {
        String remoteRepository = null;
        if (dobj != null) remoteRepository = dobj.getAttribute(RegistrarService.REMOTE_REGISTRAR);
        AuthConfig thisObjectAuthConfig = authConfig;
        if (remoteRepository != null) {
            thisObjectAuthConfig = remoteAuthConfigs.get(remoteRepository);
        }
        AccessControlList acl = new AccessControlList(dobj, thisObjectAuthConfig);
        boolean canWrite = acl.canWrite(userId);
        boolean canRead = canWrite || acl.canRead(userId);
        if (canWrite && remoteRepository == null) return Permission.WRITE;
        if (canRead) return Permission.READ;
        return Permission.NONE;
    }
    
    public Permission permittedOperations(String userId, DigitalObject dobj) throws RepositoryException {
        Permission res = permittedOperationsForUser(userId, dobj);
        List<String> groups = getGroupsForUser(userId);
        for (String group : groups) {
            Permission groupPerm = permittedOperationsForUser(group, dobj);
            if (doesPermissionAllowOperation(groupPerm, res)) res = groupPerm;
        }
        return res;
    }

    public boolean canRead(String userId, DigitalObject dobj) throws RepositoryException {
        Permission perm = permittedOperations(userId, dobj);
        return perm != Permission.NONE;
    }
    
    public static boolean doesPermissionAllowOperation(Permission permission, Permission requiredPermission) throws RepositoryException {
        if (requiredPermission == Permission.NONE) {
            return true;
        } else if (requiredPermission == Permission.READ) {
            return permission == Permission.READ || permission == Permission.WRITE;
        } else if (requiredPermission == Permission.WRITE) {
            return permission == Permission.WRITE;
        } else {
            throw new AssertionError();
        }
    }
    
    public void setAuthConfig(AuthConfig authConfig) {
        this.authConfig = authConfig;
    }
    
    public void setRemoteAuthConfigs(Map<String, AuthConfig> remoteAuthConfigs) {
        this.remoteAuthConfigs = remoteAuthConfigs;
    }
    
    private boolean isPermittedToCreateForUser(String userId, String objectType) {
        if ("admin".equals(userId)) return true;
        DefaultAcls acls = authConfig.getAclForObjectType(objectType);
        for (String permittedId : acls.aclCreate) {
            if ("public".equals(permittedId)) return true;
            if (userId != null && "authenticated".equals(permittedId)) return true;
            if (permittedId.equalsIgnoreCase(userId)) return true;
        }
        return false;
    }

    public boolean isPermittedToCreate(String userId, String objectType) throws RepositoryException {
        if (isPermittedToCreateForUser(userId, objectType)) return true;
        List<String> groups = getGroupsForUser(userId);
        for (String group : groups) {
            if (isPermittedToCreateForUser(group, objectType)) return true;
        }
        return false;
    }

    public List<String> getGroupsForUser(String userId) throws RepositoryException {
        if (userId == null || "admin".equals(userId)) return Collections.emptyList();
        Query query = new RawQuery("users:\"" + userId + "\"");
        return CollectionUtil.asList(repo.searchHandles(query));
    }
}
