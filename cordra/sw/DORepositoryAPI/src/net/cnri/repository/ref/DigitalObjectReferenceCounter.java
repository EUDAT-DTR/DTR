/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.ref;

import java.util.concurrent.atomic.AtomicInteger;

import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.util.ConcurrentCountingHashMap;

/**
 * Counts references by object handle to DigitalObjects and Data Elements.
 * Override {@link #unreachable(String)} to perform an object automatically when a handle has no extant references.
 */
public class DigitalObjectReferenceCounter extends DigitalObjectReferenceTracker {
    final ConcurrentCountingHashMap<String> count = new ConcurrentCountingHashMap<String>();
    final AtomicInteger totalCount = new AtomicInteger();
    
    /**
     * Track a reference to the given DigitalObject.
     */
    @Override
    public void track(DigitalObject dobj) {
        count.incrementAndGet(dobj.getHandle());
        totalCount.incrementAndGet();
        cleanUp();
        super.track(dobj);
    }

    /**
     * Track a reference to the given DataElement.
     */
    @Override
    public void track(DataElement el) {
        count.incrementAndGet(el.getDigitalObject().getHandle());
        totalCount.incrementAndGet();
        cleanUp();
        super.track(el);
    }
    
    /**
     * Remove references to DigitalObjects and DataElements that have been garbage collected.
     */
    public void cleanUp() {
        String handle;
        while((handle = pollHandle()) != null) {
            totalCount.decrementAndGet();
            if(count.decrementAndGet(handle)==0) unreachable(handle);
        }
    }
    
    /** 
     * Returns the total number of tracked references to all handles
     */
    public int getTotalCount() {
        return totalCount.get();
    }
    
    /** 
     * Return the number of tracked references to the given handle.
     */
    public int get(String handle) {
        return count.get(handle);
    }
    
    /**
     * An action which is performed when the a handle has its last extant reference removed during a call to {@link #cleanUp()}.
     * Intended for subclasses to override.
     * 
     * @param handle The newly unreachable handle.
     */
    protected void unreachable(String handle) {
        // to be overridden
    }
}
