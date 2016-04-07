/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.wrapper;

import java.util.Map;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;
import net.cnri.repository.util.AbstractCloseableIterator;

/**
 * A general wrapper delegating behavior to another repository.  Override the {@link #wrap(DigitalObject)} 
 * and {@link #wrap(DigitalObjectWrapper,DataElement)} methods to ensure the objects and data elements have 
 * any desired new functionality.  The {@link #wrap(DigitalObject)} return value must extend {@link DigitalObjectWrapper}
 * in order for DataElement functionality to go through {@link #wrap(DigitalObjectWrapper,DataElement)}.
 * Even if only {@link #wrap(DigitalObjectWrapper,DataElement)} adds functionality,
 * {@link #wrap(DigitalObject)} must also be overridden, minimally by {@code return new DigitalObjectWrapper(this,dobj);}.
 */
public class RepositoryWrapper implements Repository {
    protected final Repository originalRepository;
    
    public RepositoryWrapper(Repository originalRepository) {
        this.originalRepository = originalRepository;
    }

    protected DigitalObject wrap(DigitalObject dobj) {
        return dobj;
//        return new DigitalObjectWrapper(this,dobj);
    }

    protected DataElement wrap(@SuppressWarnings("unused") DigitalObjectWrapper dobj, DataElement el) {
        return el;
//        return new DataElementWrapper(dobj,el);
    }

    private CloseableIterator<DigitalObject> wrap(final CloseableIterator<DigitalObject> iter) {
        return new AbstractCloseableIterator<DigitalObject>() {
            @Override
            protected DigitalObject computeNext() {
                if(!iter.hasNext()) return null;
                return wrap(iter.next());
            }
            @Override
            protected void closeOnlyOnce() {
                iter.close();
            }
        };
    }

    public String getHandle() {
        return originalRepository.getHandle();
    }
    
    public boolean verifyDigitalObject(String handle) throws RepositoryException {
        return originalRepository.verifyDigitalObject(handle);
    }

    public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        return wrap(originalRepository.createDigitalObject(handle));
    }

    public DigitalObject getDigitalObject(String handle) throws RepositoryException {
        DigitalObject dobj = originalRepository.getDigitalObject(handle);
        if(dobj==null) return null;
        return wrap(dobj);
    }

    public DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
        return wrap(originalRepository.getOrCreateDigitalObject(handle));
    }

    public void deleteDigitalObject(String handle) throws RepositoryException {
        originalRepository.deleteDigitalObject(handle);
    }

    public CloseableIterator<String> listHandles() throws RepositoryException {
        return originalRepository.listHandles();
    }

    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        return wrap(originalRepository.listObjects());
    }

    @Deprecated
    public CloseableIterator<DigitalObject> search(String query) throws RepositoryException {
        return wrap(originalRepository.search(query));
    }

    public CloseableIterator<DigitalObject> search(Query query) throws RepositoryException {
        return wrap(originalRepository.search(query));
    }

    @Deprecated
    public CloseableIterator<String> searchHandles(String query) throws RepositoryException {
        return originalRepository.searchHandles(query);
    }

    public CloseableIterator<String> searchHandles(Query query) throws RepositoryException {
        return originalRepository.searchHandles(query);
    }

    public CloseableIterator<DigitalObject> search(Query query, QueryParams queryParams) throws RepositoryException {
        return wrap(originalRepository.search(query,queryParams));
    }

    public CloseableIterator<String> searchHandles(Query query, QueryParams queryParams) throws RepositoryException {
        return originalRepository.searchHandles(query,queryParams);
    }

    @Deprecated
    public CloseableIterator<Map<String,String>> searchMapping(String query) throws RepositoryException {
        return originalRepository.searchMapping(query);
    }

    public void close() {
        originalRepository.close();
    }
}
