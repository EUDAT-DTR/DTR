/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.IOException;
import java.util.Collection;

import net.cnri.do_api.DigitalObject;
import net.cnri.dobj.DOException;
import net.cnri.dobj.StorageProxy;

import org.apache.lucene.document.Document;

public interface IndexBuilder2 extends IndexBuilder {
    /** Creates a Lucene Document from an object in local storage. */
    public Document documentOfStorageProxy(StorageProxy objStore, Collection<Runnable> cleanupActions) throws DOException, IOException;    

    /** Creates a Lucene Document from an object accessed remotely. */
    public Document documentOfDigitalObject(DigitalObject obj, Collection<Runnable> cleanupActions) throws DOException, IOException;
}
