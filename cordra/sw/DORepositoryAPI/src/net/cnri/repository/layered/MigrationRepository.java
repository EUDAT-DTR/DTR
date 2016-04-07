/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.layered;

import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.InternalException;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.ref.DigitalObjectReferenceCounter;
import net.cnri.repository.ref.ReferenceCountingRepositoryWrapper;

/**
 * {@link LayeredRepository} for performing migration from top to bottom.
 * Allows fast read-only access to objects in the bottom; all writes occur only in the top;
 * a write causes migration of that object.
 * Users may call {@link #migrate(String)} on each handle of the bottom repository to ensure migration.
 * Bottom objects which have been migrated will be deleted as they are garbage collected; calling {@link #cleanUp()}
 * periodically until {@link #getTotalReferenceCount()} returns zero will complete that process.
 */
public class MigrationRepository extends LayeredRepository {
    private CountDownLatch waiting;
    private DigitalObjectReferenceCounter counter = new DigitalObjectReferenceCounter() {
        @Override
        protected void unreachable(String handle) {
            MigrationRepository.this.unreachable(handle);
        }
        public void cleanUp() {
            super.cleanUp();
            if(waiting!=null && getTotalCount()==0) waiting.countDown();
        }
    };
    
    /**
     * Constructs a new repository allowing background migration from a bottom repository to a top repository.
     * 
     * @param top The top repository, to be migrated to.
     * @param bottom The bottom repository, to be migrated from and made empty.
     */
    public MigrationRepository(Repository top, Repository bottom) throws RepositoryException {
        super(top,null,false);
        Repository wrappedBottom = new ReferenceCountingRepositoryWrapper(bottom,counter);
        setBottom(wrappedBottom);
    }
    
    private void unreachable(String handle) {
        if(topHandles.containsKey(handle)) {
            try {
                bottom.deleteDigitalObject(handle);
            }
            catch(NoSuchDigitalObjectException e) {}
            catch(RepositoryException e) {
                // TODO ... may be safe to ignore
            }
        }
    }

    /**
     * Migrates an object from the bottom repository to the top repository.
     * If it already exists in the top repository, it is deleted in the bottom.
     * Otherwise it is queued for deletion when all references are garbage collected.
     * 
     * @param handle The handle of the object in the bottom repository.
     */
    public void migrate(String handle) throws RepositoryException, IOException {
        if(topHandles.containsKey(handle)) {
            if(counter.get(handle)==0) unreachable(handle);
            cleanUp();
            return;
        }
        if(!objects.containsKey(handle)) return;
        liftToTop(new LayeredDigitalObject(this,handle,null,bottom.getDigitalObject(handle),null));
        cleanUp();
    }
    
    /**
     * Checks the reference counter queue for objects which may be deleted.
     */
    public void cleanUp() {
        counter.cleanUp();
    }
    
    /**
     * Returns the total number of references to bottom digital objects.
     * When this is zero, and all bottom objects have been migrated, the bottom repository should be empty. 
     * 
     * @return The total number of references to bottom digital objects.
     */
    public int getTotalReferenceCount() {
        cleanUp();
        return counter.getTotalCount();
    }
    
    /**
     * Checks whether all objects exist in the top.   Does not ensure that all bottom objects have been deleted.
     * 
     * @return Whether all objects exist in the top.
     */
    public boolean migrationComplete() {
        return topHandles.size()==objects.size();
    }
    
    /**
     * Close the bottom layer and stop using it.  Should only be called once migration is completed and {@link #getTotalReferenceCount()} returns 0.
     */
    public void closeBottom() {
        bottom.close();
        bottom = null;
        directBottom = null;
    }
    
    /**
     * Callable suitable for calling in a background thread to perform migration and delete bottom objects.
     */
    public class MigrationCallable implements Callable<Void> {
        @Override
        public Void call() throws RepositoryException {
            CloseableIterator<String> iter = bottom.listHandles();
            try {
                if(!iter.hasNext()) return null;
                while(iter.hasNext()) {
                    String handle = iter.next();
                    log(handle);
                    migrate(handle);
                }
            }
            catch(UncheckedRepositoryException e) {
                e.throwCause();
            }
            catch(IOException e) {
                throw new InternalException(e);
            }
            finally {
                iter.close();
            }
            if(waiting==null) waiting = new CountDownLatch(1);
            while(getTotalReferenceCount()>0) {
                try {
                    waiting.await(5,TimeUnit.SECONDS);
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return null;
                }
            }
                    
            return null;
        }
        
        /**
         * Hook for debugging log.
         * 
         * @param handle Handle about to be migrated
         */
        protected void log(String handle) {}
    }
}
