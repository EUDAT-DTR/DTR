/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;

public class AccessControlList {

    public static final String ACL_READ = "aclRead";
    public static final String ACL_WRITE = "aclWrite";
    public static final String CREATED_BY_ATTRIBUTE = "createdBy";
    public static final String MODIFIED_BY_ATTRIBUTE = "modifiedBy";

    
    private DigitalObject dobj;
    private AuthConfig authConfig;
    
    public AccessControlList(DigitalObject dobj) {
        this(dobj, null);
    }
    
    public AccessControlList(DigitalObject dobj, AuthConfig authConfig) {
        this.dobj = dobj;
        this.authConfig = authConfig;
    }
    
    public void grantRead(String userId) throws RepositoryException {
        grantPermission(userId, ACL_READ);
    }
    
    public void grantWrite(String userId) throws RepositoryException {
        grantPermission(userId, ACL_WRITE);
    }
    
    public void replaceWrite(String[] userIds) throws RepositoryException {
        replacePermission(userIds, ACL_WRITE);
    }
    
    public void replaceRead(String[] userIds) throws RepositoryException {
        replacePermission(userIds, ACL_READ);
    }
    
    private void replacePermission(String[] userIds, String permission) throws RepositoryException {
        if (userIds == null) {
            dobj.deleteAttribute(permission);
        } else {
            String aclString = toNewLineSeparatedString(Arrays.asList(userIds));
            dobj.setAttribute(permission, aclString);
        }
    }
    
    private void grantPermission(String userId, String permission) throws RepositoryException {
        String aclString = dobj.getAttribute(permission);
        if (aclString == null) {
            aclString = "";
        }
        String[] ids = aclString.split("\n");
        Set<String> aclSet = new HashSet<String>(Arrays.asList(ids));
        if (!aclSet.contains(userId)) {
            aclSet.add(userId);
            String newAclString = toNewLineSeparatedString(aclSet);
            dobj.setAttribute(permission, newAclString);
        }
    }
    
    public void denyRead(String userId) throws RepositoryException { 
        denyPermission(userId, ACL_READ);
    }
    
    public void denyWrite(String userId) throws RepositoryException { 
        denyPermission(userId, ACL_WRITE);
    }
    
    private void denyPermission(String userId, String permission) throws RepositoryException { 
        String aclString = dobj.getAttribute(permission);
        if (aclString == null) {
            aclString = "";
        }
        String[] ids = aclString.split("\n");
        Set<String> aclSet = new HashSet<String>(Arrays.asList(ids));
        if (aclSet.contains(userId)) {
            aclSet.remove(userId);
            String newAclString = toNewLineSeparatedString(aclSet);
            dobj.setAttribute(permission, newAclString);
        }
    }
    
    public boolean canRead(String userId) throws RepositoryException {
        return canPermission(userId, ACL_READ);
    }
    
    public boolean canWrite(String userId) throws RepositoryException {
        return canPermission(userId, ACL_WRITE);
    }
    
    private boolean canPermission(String userId, String permission) throws RepositoryException {
        String aclString = null;
        String handle = null;
        String creator = null;
        if (dobj != null) {
            aclString = dobj.getAttribute(permission);
            handle = dobj.getHandle();
            creator = dobj.getAttribute(CREATED_BY_ATTRIBUTE);
        }
        if (aclString == null && authConfig != null) {
            DefaultAcls defaultAcls;
            if (dobj == null) {
                defaultAcls = authConfig.defaultAcls;
            } else {
                String objectType = dobj.getAttribute("type");
                defaultAcls = authConfig.schemaAcls.get(objectType);
                if (defaultAcls == null) {
                    defaultAcls = authConfig.defaultAcls;
                }
            }
            return isPermittedByDefaultAcls(defaultAcls, handle, creator, userId, permission);
        }
        if (aclString == null) {
            aclString = "";
        }
        String[] ids = aclString.split("\n");
        return isPermittedByAcl(Arrays.asList(ids), handle, creator, userId);
    }
    
    private static boolean isPermittedByDefaultAcls(DefaultAcls defaultAcls, String objectId, String creatorId, String userId, String permission) {
        if (ACL_READ.equals(permission)) {
            return isPermittedByAcl(defaultAcls.defaultAclRead, objectId, creatorId, userId);
        } else if (ACL_WRITE.equals(permission)) {
            return isPermittedByAcl(defaultAcls.defaultAclWrite, objectId, creatorId, userId);
        } else {
            throw new AssertionError("Unexpected permission " + permission);
        }
    }
    
    private static boolean isPermittedByAcl(List<String> acl, String objectId, String creatorId, String userId) {
        if ("admin".equals(userId)) {
            return true;
        }
        for (String permittedId : acl) {
            if ("public".equals(permittedId)) return true;
            if (userId != null && "authenticated".equals(permittedId)) return true;
            if (objectId != null && objectId.equalsIgnoreCase(userId) && "self".equals(permittedId)) return true;
            if (creatorId != null && creatorId.equalsIgnoreCase(userId) && "creator".equals(permittedId)) return true;
            if (permittedId.equalsIgnoreCase(userId)) return true;
        }
        return false;
    }

    public String toNewLineSeparatedString(Collection<String> ids) {
        StringBuilder sb = new StringBuilder();
        for (String userId : ids) {
            sb.append(userId).append("\n");
        }
        return sb.toString();
    }
    
    public SerializedAcl serialize() throws RepositoryException {
        return new SerializedAcl(dobj);
    }
    
    public static class SerializedAcl {
        public String[] read;
        public String[] write;
        
        public SerializedAcl(DigitalObject dobj) throws RepositoryException {
            read = getAclAsList(AccessControlList.ACL_READ, dobj);
            write = getAclAsList(AccessControlList.ACL_WRITE, dobj);
        }
        
        private String[] getAclAsList(String permission, DigitalObject dobj) throws RepositoryException {
            String aclString = dobj.getAttribute(permission);
            if (aclString == null) {
                return null;
            }
            String[] aclArray = aclString.split("\n");
            return aclArray;
        }
    }
}
