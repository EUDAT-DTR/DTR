/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.ref;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;

import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;

class DigitalObjectReferenceTracker {
    final ReferenceQueue<Object> queue = new ReferenceQueue<Object>();
    final ConcurrentHashMap<Reference<?>,Boolean> trackedReferences = new ConcurrentHashMap<Reference<?>,Boolean>();
    
    public void track(DigitalObject dobj) {
       trackedReferences.put(new DigitalObjectPhantomReference(dobj,queue),Boolean.TRUE);
//       trackedReferences.put(new DigitalObjectWeakReference(dobj,queue),Boolean.TRUE);
    }

    public void track(DataElement el) {
        trackedReferences.put(new DataElementPhantomReference(el,queue),Boolean.TRUE);
//        trackedReferences.put(new DataElementWeakReference(el,queue),Boolean.TRUE);
    }
    
    public String pollHandle() {
        return getHandle(queue.poll());
    }
    
    public String removeHandle() throws InterruptedException {
        return getHandle(queue.remove());
    }

    public String removeHandle(long timeout) throws InterruptedException {
        return getHandle(queue.remove(timeout));
    }

    private String getHandle(Reference<?> ref) {
        if(ref==null) return null;
        ref.clear();
        trackedReferences.remove(ref);
        if(ref instanceof HandleBearer) {
            return ((HandleBearer)ref).getHandle();
        }
        else throw new AssertionError();
    }
    
    static interface HandleBearer {
        String getHandle(); 
    }
    
    static class DigitalObjectPhantomReference extends PhantomReference<DigitalObject> implements HandleBearer {
        final String handle;
        
        DigitalObjectPhantomReference(DigitalObject dobj, ReferenceQueue<? super DigitalObject> rq) {
            super(dobj,rq);
            this.handle = dobj.getHandle();
        }
        
        public String getHandle() {
            return handle;
        }
    }

    static class DataElementPhantomReference extends PhantomReference<DataElement> implements HandleBearer {
        final String handle;
        
        DataElementPhantomReference(DataElement el, ReferenceQueue<? super DataElement> rq) {
            super(el,rq);
            this.handle = el.getDigitalObject().getHandle();
        }
        
        public String getHandle() {
            return handle;
        }
    }

    static class DigitalObjectWeakReference extends WeakReference<DigitalObject> implements HandleBearer {
        final String handle;
        
        DigitalObjectWeakReference(DigitalObject dobj, ReferenceQueue<? super DigitalObject> rq) {
            super(dobj,rq);
            this.handle = dobj.getHandle();
        }
        
        public String getHandle() {
            return handle;
        }
    }

    static class DataElementWeakReference extends WeakReference<DataElement> implements HandleBearer {
        final String handle;
        
        DataElementWeakReference(DataElement el, ReferenceQueue<? super DataElement> rq) {
            super(el,rq);
            this.handle = el.getDigitalObject().getHandle();
        }
        
        public String getHandle() {
            return handle;
        }
    }
}
