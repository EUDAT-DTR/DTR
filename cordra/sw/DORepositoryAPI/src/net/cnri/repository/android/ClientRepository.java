/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.util.List;

import android.content.Context;
import android.os.Bundle;

import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.Query;

public class ClientRepository extends EmbeddedRepository {

    public ClientRepository(Context context) throws RepositoryException {
        super(context, Provider.AUTHORITY);
    }

    public enum SyncPriority {
        NORMAL, HIGH
    }
    
    public enum Destination {
        AGGREGATION_SERVER, DEVICE_MANAGER
    }
    
    public DigitalObject createDigitalObject() throws CreationException, RepositoryException {
        return super.createDigitalObject(null);
    }

    public void syncObject(DigitalObject dobj, Destination destination, SyncPriority priority, boolean deleteFromLocalAfterSync) throws RepositoryException {
        syncObject(dobj.getHandle(), destination, priority, deleteFromLocalAfterSync);
    } 
    
    /**
     * Given a DigitalObject id sets the necessary attributes on this object to
     * indicate that it should be synced. This method does not cause the sync to
     * happen immediately. The object will be synced according to the attributes
     * set when the sync process next occurs.
     * 
     * @throws RepositoryException
     */
    public void syncObject(String objectId, SyncPriority priority, boolean deleteFromLocalAfterSync) throws RepositoryException {
        Bundle args = new Bundle();
        args.putString("id", objectId);
        args.putString("destination", Destination.AGGREGATION_SERVER.name());
        args.putString("priority",priority.name());
        args.putBoolean("deleteFromLocalAfterSync", deleteFromLocalAfterSync);
        call("syncObject",null,args);
    }
    
    public void syncObject(String objectId, Destination destination, SyncPriority priority, boolean deleteFromLocalAfterSync) throws RepositoryException {
        Bundle args = new Bundle();
        args.putString("id", objectId);
        args.putString("destination", destination.name());
        args.putString("priority",priority.name());
        args.putBoolean("deleteFromLocalAfterSync", deleteFromLocalAfterSync);
        call("syncObject",null,args);
    }

    public void landingZoneSync() throws RepositoryException {
        call("lzsync", null, null);
    }

    public void syncNow(String action) throws RepositoryException {
        call("syncNow", action, null);
    }

    public Bundle prefs() throws RepositoryException {
        return call("prefs", null, null);
    }

    public void requestDownloadSync() throws RepositoryException {
        syncNow("net.cnri.agora.action.DOWNLOAD_ONLY_FOR_OWNER");
    }
    
    public int countQueuedObjects(Query query) throws RepositoryException {
        Bundle args = new Bundle();
        args.putParcelable("query",new ParcelableQuery(query));
        Bundle res = call("countQueuedObjects",null,args);
        return res.getInt("result");
    }
    
    public List<String> listQueuedHandles(Query query) throws RepositoryException {
        Bundle args = new Bundle();
        args.putParcelable("query", new ParcelableQuery(query));
        Bundle res = call("listQueuedHandles", null, args);
        return res.getStringArrayList("result");
    }
    
    public boolean isQueuedObject(String handle) throws RepositoryException {
        Bundle args = new Bundle();
        args.putString("handle", handle);
        Bundle res = call("isQueuedObject",null,args);
        return res.getBoolean("result");
    }
    
    /**
     * @return true if the sync on this object was successfully canceled, false if the object had already been synced
     */
    public boolean cancelObjectSync(String handle) throws RepositoryException {
        Bundle args = new Bundle();
        args.putString("handle", handle);
        Bundle res = call("cancelObjectSync",null,args);
        return res.getBoolean("result");
    }
}
