/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.ref;

import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.wrapper.DigitalObjectWrapper;
import net.cnri.repository.wrapper.RepositoryWrapper;

public class ReferenceCountingRepositoryWrapper extends RepositoryWrapper {
    private final DigitalObjectReferenceCounter counter;
    
    public ReferenceCountingRepositoryWrapper(Repository repo,DigitalObjectReferenceCounter counter) {
        super(repo);
        this.counter = counter;
    }
    
    @Override
    protected DigitalObject wrap(DigitalObject dobj) {
        counter.cleanUp();
        counter.track(dobj);
        return super.wrap(dobj);
    }
    
    @Override
    protected DataElement wrap(DigitalObjectWrapper dobj, DataElement el) {
        counter.cleanUp();
        counter.track(el);
        return super.wrap(dobj,el);
    }

    public int getReferenceCount(String handle) {
        counter.cleanUp();
        return counter.get(handle);
    }
    
    
}
