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
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.cnri.doregistrytools.registrar.auth.AuthConfig;
import net.cnri.doregistrytools.registrar.auth.QueryRestrictor;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.networked.NetworkedRepository;
import net.cnri.repository.search.RawQuery;

public class VersionManager {

    private Repository repo;
    private HandleMinter handleMinter;

    public static final String PUBLISHED_BY = "publishedBy";
    public static final String PUBLISHED_ON = "publishedOn";
    public static final String VERSION_OF = "versionOf";
    public static final String IS_VERSION = "isVersion";
    
    public VersionManager(Repository repo, HandleMinter handleMinter) {
        this.repo = repo;
        this.handleMinter = handleMinter;
    }
    
    public DigitalObject publishVersion(String objectId, String userId) throws RepositoryException, VersionException {
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) {
            throw new VersionException("Cannot publish a version for an object that deos not exist.");
        }
        if (!isTipObject(dobj)) {
            throw new VersionException("Cannot publish a version for an object that is not the tip.");
        }
        DigitalObject version = null;
        while (version == null) {
            String handle = handleMinter.mintByTimestamp();
            try {
                version = repo.createDigitalObject(handle); 
            } catch (CreationException e) {
                // retry
            }
        }
        try {
            copyAllDataElementsIntoDestination(dobj, version);
            Map<String, String> srcAtts = dobj.getAttributes();
            long now = System.currentTimeMillis();
            if (userId == null) {
                userId = "anonymous";
            }
            srcAtts.put(PUBLISHED_BY, userId);
            srcAtts.put(VERSION_OF, objectId);
            srcAtts.put(PUBLISHED_ON, String.valueOf(now));
            srcAtts.put(IS_VERSION, "true");
            version.setAttributes(srcAtts);
        } catch (IOException e) {
            repo.deleteDigitalObject(version.getHandle());
            e.printStackTrace();
            throw new VersionException("Could not create new version.");
        }
        if (repo instanceof NetworkedRepository) {
            ((NetworkedRepository) repo).ensureIndexUpToDate();
        }
        return version;
    }
    
    private void copyAllDataElementsIntoDestination(DigitalObject src, DigitalObject destination) throws RepositoryException, IOException {
        List<DataElement> srcElements = src.getDataElements();
        for (DataElement srcElement : srcElements) {
            String name = srcElement.getName();
            DataElement destElement = destination.createDataElement(name);
            Map<String, String> srcAtts = srcElement.getAttributes();
            destElement.setAttributes(srcAtts);
            
            InputStream in = srcElement.read();
            try {
            destElement.write(in);
            } finally {
                in.close();
            }
        }
    }
    
    private boolean isTipObject(DigitalObject dobj) throws RepositoryException {
        String tipId = dobj.getAttribute(VERSION_OF);
        if (tipId == null) {
            return true;
        } else {
            return false;
        }
    }
    
    public List<DigitalObject> getVersionsFor(String objectId, String userId, List<String> groupIds, AuthConfig authConfig, Map<String, AuthConfig> remoteAuthConfigs) throws RepositoryException {
        List<DigitalObject> result = new ArrayList<DigitalObject>();
        DigitalObject dobj = repo.getDigitalObject(objectId);
        if (dobj == null) {
            return Collections.emptyList();
        }
        String tipId = dobj.getAttribute(VERSION_OF);
        if (tipId != null) {
            dobj = repo.getDigitalObject(tipId);
            if (dobj == null) {
                return Collections.emptyList();
            }
        } else {
            tipId = objectId;
        }
        String query = "objatt_"+VERSION_OF+":\""+tipId + "\"";
        boolean excludeVersions = false;
        String restrictedQuery = QueryRestrictor.restrict(query, userId, groupIds, authConfig, remoteAuthConfigs, excludeVersions);
        CloseableIterator<DigitalObject> iter = repo.search(new RawQuery(restrictedQuery));
        while(iter.hasNext()) {
            DigitalObject current = iter.next();
            result.add(current);
        }
        iter.close();
        result.add(dobj); //Should we include the tip in the response?
        return result;
    }
}
