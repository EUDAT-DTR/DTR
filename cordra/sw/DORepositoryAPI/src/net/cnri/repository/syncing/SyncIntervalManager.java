/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.syncing;

import java.io.IOException;

import net.cnri.repository.RepositoryException;

public abstract class SyncIntervalManager {
    
    protected SyncingRepository syncingRepository;
    
    public abstract void startSyncing(SyncingRepository syncingRepositoryParam); 
    
    public abstract void stopSyncing();
    
    protected void onPerformSync() throws RepositoryException, IOException {
        syncingRepository.syncNow();
    }
}
