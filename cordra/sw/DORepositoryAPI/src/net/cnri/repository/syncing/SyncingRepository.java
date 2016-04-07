/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.syncing;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.DirectRepository;
import net.cnri.repository.Repositories;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.wrapper.DigitalObjectWrapper;
import net.cnri.repository.wrapper.DirectRepositoryWrapper;

/**
 * Given two other repositories, a local and a remote this repository manages pulling objects from remote to local and 
 * pushing objects from local to remote.
 * 
 * Which objects are pulled and pushed is determined by the queries that are added to the SyncingConfig object. 
 * For example you might have a query to pull all objects that have been modified on remote and push all objects that 
 * have been modified on local.
 */
public class SyncingRepository extends DirectRepositoryWrapper {

    public static final String LOCAL_CONFIG_HANDLE = "internal.config";
    public static final String INTERNAL_SYNC = "internal.sync";
    public static final String INTERNAL_DELETE_LOCAL_AFTER_SYNC = "internal.deleteLocalAfterSync";
    
    private long greatestModifiedFromRemote = 0;
    private long lastSyncTime = 0;
    private long priorLastSyncTime = 0;
    private AtomicBoolean syncing = null;
    private Repository remote;
    private DirectRepository local;
    private SyncingConfig config = null;
    private final ConcurrentHashMap<String,CountDownLatch> objectLocks = new ConcurrentHashMap<String,CountDownLatch>();
    private DigitalObject localConfig = null;
    
    public SyncingRepository(Repository remote, DirectRepository local, SyncingConfig syncingConfig) throws RepositoryException {
        super(local);
        syncing = new AtomicBoolean(false);
        
        this.remote = remote;
        this.local = local;
        this.config = syncingConfig;
        
        localConfig = getOrCreateLocalConfigObject();
        String greatestModifiedFromRemoteAttribute = localConfig.getAttribute("greatestModifiedFromRemote");
        if (greatestModifiedFromRemoteAttribute == null) {
            System.out.println("greatestModifiedFromRemote did not exist");
            localConfig.setAttribute("greatestModifiedFromRemote", "0");
            greatestModifiedFromRemote = 0;
        } else {
            greatestModifiedFromRemote = Long.parseLong(greatestModifiedFromRemoteAttribute);
            System.out.println("greatestModifiedFromRemote was " + greatestModifiedFromRemote);
        }
    }
    
    public void lock(String handle) {
        //lock.readLock().lock();
        CountDownLatch newLatch = new CountDownLatch(1);
        while(true) {
            CountDownLatch existing = objectLocks.putIfAbsent(handle,newLatch);
            if(existing!=null) {
                try {
                    existing.await();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            } else {
                return;
            }
        }
    }
    
    public SyncingConfig getConfig() {
        return config;
    }
    
    public long getGreatestModifiedFromRemote() {
        return greatestModifiedFromRemote;
    }
    
    public long getLastSyncTime() {
        return lastSyncTime;
    }
    
    public long getPriorLastSyncTime() {
        return priorLastSyncTime;
    }
    
    public static class ModifiedSinceLastSyncQuery extends RawQuery {
        private SyncingRepository syncingRepository;
        
        public ModifiedSinceLastSyncQuery(SyncingRepository syncingRepository) {
            super("");
            this.syncingRepository = syncingRepository;
        }
        
        @Override
        public String getQueryString() {
            long timeStamp = syncingRepository.getGreatestModifiedFromRemote();
            
            //Backtrack 1ms just to be sure
            timeStamp = timeStamp - 1;
            if (timeStamp < 0) {
                timeStamp = 0;
            }
            String result = "objatt_internal.modified:["+timeStamp+" TO " + Long.MAX_VALUE + "]";
            System.out.println("QUERY = " + result);
            return result;
        }
    }
    
    private DigitalObject createLocalConfigDigitalObject() throws CreationException, RepositoryException {
        System.out.println("Creating Local config object");
        DigitalObject localConfigObject = local.createDigitalObject(LOCAL_CONFIG_HANDLE);
        localConfigObject.setAttribute("greatestModifiedFromRemote", "0");
        return localConfigObject;
    }
    
    private DigitalObject getOrCreateLocalConfigObject() throws RepositoryException {
        if (local.verifyDigitalObject(LOCAL_CONFIG_HANDLE)) {
            System.out.println("Local config object exists already");
            return local.getDigitalObject(LOCAL_CONFIG_HANDLE);
        } else {
            return createLocalConfigDigitalObject();
        }
    }
    
    public void unlock(String handle) {
        CountDownLatch existing = objectLocks.remove(handle);
        if(existing!=null) existing.countDown();
        //lock.readLock().unlock();
    }
    
    public void startSyncing() {
        config.getSyncIntervalManager().startSyncing(this);
    }
    
    public void stopSyncing() {
        config.getSyncIntervalManager().stopSyncing();
    }
    
    public void syncNow() throws RepositoryException, IOException {
        if(!syncing.compareAndSet(false, true)) return;
        priorLastSyncTime = lastSyncTime;
        lastSyncTime = System.currentTimeMillis();
        RepositoryException exception = null;
        try {
            try { 
                downloadFromRemote();
                localConfig.setAttribute("greatestModifiedFromRemote", String.valueOf(greatestModifiedFromRemote));
                System.out.println("Set greatestModifiedFromRemote to " + greatestModifiedFromRemote);
            } catch (RepositoryException e) {
                exception = e;
            } 
            try { 
                uploadToRemote();
            } catch (RepositoryException e) {
                exception = e;
            } 
        }
        finally {
            syncing.set(false);
        }
        if (exception != null) {
            throw exception;
        }
    }
    
    private void downloadFromRemote() throws RepositoryException, IOException {
        Map<String, Query> pullQueries = config.getPullQueries();
        for (String queryName  : pullQueries.keySet()) {
            Query q = pullQueries.get(queryName);
            downloadFromRemoteWithQuery(q);
        }
    }
    
    private void downloadFromRemoteWithQuery(Query q) throws RepositoryException, IOException {
        List<String> includes = new ArrayList<String>();
        RepositoryException exception = null;
        CloseableIterator<DigitalObject> downloadIter = null;
        downloadIter = remote.search(q);
        try {
            while(downloadIter.hasNext()) {
                DigitalObject remoteObject = downloadIter.next();
                String handle = remoteObject.getHandle();
                System.out.println(handle + " is ready to sync");
                CheckGreatestModified(remoteObject);
                try {
                    if (local.verifyDigitalObject(handle)) {
                        DigitalObject localObject = local.getDigitalObject(handle);
                        if (config.pullsOverwriteIfNewer()) {
                            System.out.println("pulls overwrite if newer");
                            if (isRemoteNewer(remoteObject, localObject)) {
                                System.out.println("copying " +  handle + " to local");
                                Repositories.copy(remoteObject, local, includes, false);
                            } else {
                                System.out.println("local is newer not overwriting");
                            }
                        }
                    } else {
                        System.out.println("copying " +  handle + " to local");
                        Repositories.copy(remoteObject, local, includes, false);
                    }
                }
                catch(RepositoryException e) {
                    if(exception==null) exception = e;
                }
            }
        }
        finally {
            if(downloadIter!=null) downloadIter.close();
        }
        if (exception != null) throw exception;
    }
    
    private void CheckGreatestModified(DigitalObject dobj) throws RepositoryException {
        String modifiedString = dobj.getAttribute(Repositories.INTERNAL_MODIFIED);
        if (modifiedString == null) {
            System.out.println("No modified string on object " + dobj.getHandle());
            return;
        }
        long modified = Long.parseLong(modifiedString);
        if (modified > greatestModifiedFromRemote) {
            System.out.println("Setting greatest modified "+ modified);
            greatestModifiedFromRemote = modified;
        }
    }
    
    private static boolean isRemoteNewer(DigitalObject remoteObject, DigitalObject localObject) throws RepositoryException {
        String localModifiedString = localObject.getAttribute(Repositories.INTERNAL_MODIFIED);
        String remoteModifiedString = remoteObject.getAttribute(Repositories.INTERNAL_MODIFIED);
        System.out.println("localModifiedString " + localModifiedString + " remoteModifiedString" + remoteModifiedString);
        if (localModifiedString == null && remoteModifiedString == null) {
            return false;
        } else if (localModifiedString == null && remoteModifiedString != null) {
            return true;
        } else {
            long localModified = Long.parseLong(localModifiedString);
            long remoteModified = Long.parseLong(remoteModifiedString);
            if (remoteModified > localModified) {
                return true;
            } else {
                return false;
            }
        }
    }
    
    private void uploadToRemote() throws RepositoryException, IOException {
        Map<String, Query> pushQueries = config.getPushQueries();
        for (String queryName  : pushQueries.keySet()) {
            Query q = pushQueries.get(queryName);
            uploadToRemoteWithQuery(q);
        }
    }
    
    private void uploadToRemoteWithQuery(Query q) throws RepositoryException, IOException {
        RepositoryException exception = null;
        CloseableIterator<DigitalObject> uploadIter = null;
        uploadIter = local.search(q);
        try {
            while(uploadIter.hasNext()) {
                DigitalObject dobj = uploadIter.next();
                try {
                    upLoadObjectToRemote(dobj);
                }
                catch(RepositoryException e) {
                    if(exception==null) exception = e;
                }
            }
        }
        finally {
            if(uploadIter!=null) uploadIter.close();
        }
        if (exception != null) throw exception;
    }
    
    private void upLoadObjectToRemote(DigitalObject dobj) throws RepositoryException, IOException {
        lock(dobj.getHandle());
        try {
            String remoteId = dobj.getHandle();
            Map<String, String> atts = new HashMap<String, String>();
            for(Map.Entry<String,String> entry : dobj.getAttributes().entrySet()) {
                if(entry.getKey().equals(INTERNAL_SYNC)) continue;
                atts.put(entry.getKey(), entry.getValue());
            }
            DigitalObject remoteObject;
            if(remoteId!=null && remote.verifyDigitalObject(remoteId)) {
                remoteObject = remote.getDigitalObject(remoteId);
            }
            else {
                remoteObject = remote.createDigitalObject(remoteId);
            }
            remoteObject.setAttributes(atts);
            CloseableIterator<DataElement> elements = dobj.listDataElements();
            Set<String> elementNames = new HashSet<String>();
            try {
                while(elements.hasNext()) {
                    DataElement element = elements.next();
                    elementNames.add(element.getName());
                    DataElement remoteElement;
                    if(remoteObject.verifyDataElement(element.getName())) {
                        remoteElement = remoteObject.getDataElement(element.getName());
                    }
                    else {
                        remoteElement = remoteObject.createDataElement(element.getName());
                    }
                    Map<String,String> elAtts = new HashMap<String, String>();
                    for(Map.Entry<String,String> entry : element.getAttributes().entrySet()) {
                        atts.put(entry.getKey(), entry.getValue());
                    }
                    remoteElement.setAttributes(elAtts);
                    remoteElement.write(element.read(), true);
                }
            }
            finally {
                elements.close();
            }
            CloseableIterator<DataElement> iter = remoteObject.listDataElements();
            while(iter.hasNext()){
                DataElement datael = iter.next();
                String name = datael.getName();
                if(!elementNames.contains(name)) {
                    remoteObject.deleteDataElement(name);
                }
            }
            dobj.deleteAttribute(INTERNAL_SYNC);
            if ("true".equals(dobj.getAttribute(INTERNAL_DELETE_LOCAL_AFTER_SYNC))) {
                dobj.delete();
            }
        } finally {
            unlock(dobj.getHandle());
        }
    }
    
    @Override
    protected SyncingDigitalObject wrap(DigitalObject dobj) {
        return new SyncingDigitalObject(this, dobj);
    }
    
    protected DataElement wrap(DigitalObjectWrapper dobj, DataElement el) {
        return new SyncingDataElement(dobj, el);
    }
    
    @Override
    public void close() {
        local.close();
        remote.close();
    }
    
    protected void setInternalMetaData(DigitalObject dobj) throws RepositoryException {
        if (config.getSyncAllChanges()) {
            dobj.setAttribute(SyncingRepository.INTERNAL_SYNC, "true");
        }
    }

    @Override
    public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        DigitalObject result = super.createDigitalObject(handle);
        setInternalMetaData(result);
        return result;
//        DigitalObject result = local.createDigitalObject(handle);
//        setSyncFlagIfNeeded(result);
//        return new SyncingDigitalObject(this, result);
    }

   
//    @Override
//    public CloseableIterator<String> listHandles() throws RepositoryException {
//        return local.listHandles();
//    }
//
//    @Override
//    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
//        return local.listObjects();
//    }
//    
//    @Override
//    public CloseableIterator<DigitalObject> search(Query query) throws RepositoryException {
//        return local.search(query); 
//    }   
//
//    @Override
//    public CloseableIterator<String> searchHandles(Query query) throws RepositoryException {
//        return local.searchHandles(query);
//    }  
//    
    @Override
    public void setAttributes(String handle, String elementName, Map<String, String> attributes) throws RepositoryException {
        local.setAttributes(handle, elementName, attributes);
        setInternalMetaData(local.getDigitalObject(handle));
    }

    @Override
    public void setAttribute(String handle, String elementName, String name, String value) throws RepositoryException {
        local.setAttribute(handle, elementName, name, value);
        setInternalMetaData(local.getDigitalObject(handle));
    }

    @Override
    public void deleteAttributes(String handle, String elementName, List<String> names) throws RepositoryException {
        local.deleteAttributes(handle, elementName, names);
        setInternalMetaData(local.getDigitalObject(handle));
    }

    @Override
    public void deleteAttribute(String handle, String elementName, String name) throws RepositoryException {
        local.deleteAttribute(handle, elementName, name);
        setInternalMetaData(local.getDigitalObject(handle));
    }

    @Override
    public void createDataElement(String handle, String name) throws CreationException, RepositoryException {
        local.createDataElement(handle, name);
        setInternalMetaData(local.getDigitalObject(handle));
    }

    @Override
    public void deleteDataElement(String handle, String name) throws RepositoryException {
        local.deleteDataElement(handle, name); 
        setInternalMetaData(local.getDigitalObject(handle));
    }

    @Override
    public long write(String handle, String elementName, InputStream data, boolean append) throws IOException, RepositoryException {
        setInternalMetaData(local.getDigitalObject(handle));
        return local.write(handle, elementName, data, append);
    }

}
