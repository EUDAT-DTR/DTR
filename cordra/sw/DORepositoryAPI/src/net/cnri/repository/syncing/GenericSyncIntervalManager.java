/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.syncing;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import net.cnri.repository.RepositoryException;

public class GenericSyncIntervalManager extends SyncIntervalManager {

    public static final long MINS_5 = 1000*60*5;
    private long syncInterval = MINS_5;
    private SyncTask syncTask = null;
    private Timer syncTimer = null;
    
    public GenericSyncIntervalManager(long syncInterval) {
        this.syncInterval = syncInterval;
        syncTask = new SyncTask();
        syncTimer = new Timer();
    }
    
    @Override
    public void startSyncing(SyncingRepository syncingRepositoryParam) {
        this.syncingRepository = syncingRepositoryParam;
        syncTimer.scheduleAtFixedRate(syncTask, 0, syncInterval);
    }
    
    @Override 
    public void stopSyncing() {
        syncTimer.cancel();
    }
    
    private class SyncTask extends TimerTask {
        @Override
        public void run() {
            try {
                onPerformSync();
            } catch (RepositoryException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
