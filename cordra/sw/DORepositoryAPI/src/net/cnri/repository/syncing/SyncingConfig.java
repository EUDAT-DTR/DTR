/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.syncing;

import java.util.HashMap;
import java.util.Map;

import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.search.Query;

public class SyncingConfig {
    private Map<String, Query> pullQueries = null;
    private Map<String, Query> pushQueries = null;
    private SyncIntervalManager syncIntervalManager = null;
    private boolean syncAllChanges = false;
    private boolean pullsOverwriteIfNewer = true;
    
    public boolean pullsOverwriteIfNewer() {
        return pullsOverwriteIfNewer;
    }

    public void setPullsOverwriteIfNewer(boolean pullsOverwriteIfNewer) {
        this.pullsOverwriteIfNewer = pullsOverwriteIfNewer;
    }

    public SyncingConfig() {
        pullQueries = new HashMap<String, Query>();
        pushQueries = new HashMap<String, Query>();
    }
    
    public void addPullQuery(String name, Query q) {
        pullQueries.put(name, q);
    }
    
    public void removePullQuery(String name) {
        pullQueries.remove(name);
    }
    
    public Map<String, Query> getPullQueries() {
        return pullQueries;
    }
    
    public void addPushQuery(String name, Query q) {
        pushQueries.put(name, q);
    }
    
    public void removePushQuery(String name) {
        pushQueries.remove(name);
    }
    
    public Map<String, Query> getPushQueries() {
        return pushQueries;
    }
    
    public void setSyncIntervalManager(SyncIntervalManager syncIntervalManager) {
        this.syncIntervalManager = syncIntervalManager;
    }
    
    public SyncIntervalManager getSyncIntervalManager() {
        return this.syncIntervalManager;
    }
    
    public boolean getSyncAllChanges() { 
        return syncAllChanges; 
    }
    
    public void setSyncAllChanges(boolean b) {
        this.syncAllChanges = b;
        if (syncAllChanges) {
            Query syncAllModifiedObjects = new AttributeQuery(SyncingRepository.INTERNAL_SYNC, "true");
            addPushQuery("syncAllModifiedObjects", syncAllModifiedObjects);
        } else {
            removePushQuery("syncAllModifiedObjects");
        }
    }
}
