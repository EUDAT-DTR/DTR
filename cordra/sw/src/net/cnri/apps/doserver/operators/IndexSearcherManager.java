/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexSearcherManager {
    static final Logger logger = LoggerFactory.getLogger(IndexSearcherManager.class);
    
    private DirectoryReader indexReader; // all access locked by readerLock
    private volatile LockTrackingIndexSearcherWrapper currentlyActiveIndexSearcherWrapper; // modifications locked by searcherLock
    private final Object readerLock = new Object();
    private final Object searcherLock = new Object();
    private volatile boolean reopenQueued;

    public IndexSearcherManager(DirectoryReader indexReader) {
        this.indexReader = indexReader;
        currentlyActiveIndexSearcherWrapper = new LockTrackingIndexSearcherWrapper();
    }

    public LockTrackingIndexSearcherWrapper getAndLockIndexSearcherWrapper() {
        reopenIfNecessary();
        synchronized(searcherLock) {
            currentlyActiveIndexSearcherWrapper.lock();
            return currentlyActiveIndexSearcherWrapper;
        }
    }
    
    public void close() throws IOException {
        synchronized(readerLock) {
            indexReader.close();
        }
    }
    
    private void reopenIfNecessary() {
        if(!reopenQueued) return;
        try {
            LockTrackingIndexSearcherWrapper oldIndexSearcherWrapper = null;
            synchronized(readerLock) {
                if(!reopenQueued) return;
                reopenQueued = false;
                DirectoryReader newReader = DirectoryReader.openIfChanged(indexReader);
                if(newReader != null) {
                    indexReader = newReader;
                    synchronized(searcherLock) {
                        oldIndexSearcherWrapper = currentlyActiveIndexSearcherWrapper;
                        currentlyActiveIndexSearcherWrapper = new LockTrackingIndexSearcherWrapper();
                    }
                }
            }
            if(oldIndexSearcherWrapper!=null) oldIndexSearcherWrapper.closeIfNeeded();
        }
        catch(Exception e) {
            logger.error("Error reopening reader after update",e);
        }
    }
    
    public void reopen() {
        reopenQueued = true;
    }

    public class LockTrackingIndexSearcherWrapper {
        private final AtomicInteger count = new AtomicInteger(0);
        private final IndexSearcher indexSearcher;

        private LockTrackingIndexSearcherWrapper() {
            indexSearcher = new IndexSearcher(indexReader);
        }
        
        public IndexSearcher getIndexSearcher() {
            return indexSearcher;
        }
        
        private void lock() { 
            count.incrementAndGet();
        }

        public void releaseAndCloseIfNeeded() { 
            if(count.decrementAndGet()==0 && this!=currentlyActiveIndexSearcherWrapper) close(); 
        }

        private void closeIfNeeded() { 
            if(count.get()==0 && this!=currentlyActiveIndexSearcherWrapper) close(); 
        }

        private void close() {
            try {
                this.indexSearcher.getIndexReader().close();
            }
            catch(IOException e) {}
        }

    }
}
