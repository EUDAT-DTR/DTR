/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.memory;

import java.io.File;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import net.cnri.repository.AbstractDirectRepository;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.AbstractQueryVisitorForSearch;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.repository.util.Pair;

/**
 * A local Digital Object repository entirely in system memory. High performance, no persistence.
 */
public class MemoryRepository extends AbstractDirectRepository {
    private AtomicInteger objectIdCounter = new AtomicInteger();
    private ConcurrentHashMap<String, MemoryDigitalObject> repo = new ConcurrentHashMap<String, MemoryDigitalObject>();
	
	ConcurrentHashMap<Map.Entry<String, String>, Set<String>> index = new ConcurrentHashMap<Map.Entry<String, String>, Set<String>>();
	
	public MemoryRepository() {}
    
	@Override
	public boolean verifyDigitalObject(String handle) {
		return repo.containsKey(handle);
		
	}

	@Override
	public DigitalObject createDigitalObject(String handle) throws CreationException {
        if(handle==null) {
            handle = String.valueOf(objectIdCounter.getAndIncrement());
        }
	    MemoryDigitalObject dobj = new MemoryDigitalObject(this, handle);
	    MemoryDigitalObject res = repo.putIfAbsent(handle, dobj);
		if(res!=null) throw new CreationException();
		return dobj;
	}
	
	@Override
	public DigitalObject getDigitalObject(String handle) {
		return repo.get(handle);
	}

	@Override
	public DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
	    DigitalObject res = repo.get(handle);
	    if(res!=null) return res;
	    MemoryDigitalObject dobj = new MemoryDigitalObject(this, handle);
        res = repo.putIfAbsent(handle,dobj);
        if(res!=null) return res;
        return dobj;
	}
	
	@Override
	public void deleteDigitalObject(String handle) throws NoSuchDigitalObjectException {
	    MemoryDigitalObject dobj = repo.get(handle);
	    if(dobj==null) throw new NoSuchDigitalObjectException(handle);
	    for(String name : dobj.attributes.keySet()) {
            dobj.indexAttribute(name,null);
        }
		repo.remove(handle);
	}

	@Override
	public CloseableIterator<String> listHandles() {
		final Iterator<String> iter= repo.keySet().iterator();
		
		return new CloseableIteratorFromIterator<String>(iter);
	}

	@Override
	public CloseableIterator<DigitalObject> listObjects() {
		final Iterator<? extends DigitalObject> iter = repo.values().iterator();
		
		return new CloseableIteratorFromIterator<DigitalObject>(iter);
	}

	@Override
	public CloseableIterator<DigitalObject> search(Query query) throws RepositoryException {
	    return query.accept(new AbstractQueryVisitorForSearch.SearchObjects(this) {
	        @Override
	        public CloseableIterator<DigitalObject> visitAttributeQuery(AttributeQuery attQuery) throws RepositoryException {
	            return indexSearch(attQuery);
	        }
	    });
	}	

    @Override
	public CloseableIterator<String> searchHandles(Query query) throws RepositoryException {
        return query.accept(new AbstractQueryVisitorForSearch.SearchHandles(this) {
            @Override
            public CloseableIterator<String> visitAttributeQuery(AttributeQuery attQuery) throws RepositoryException {
                return indexSearchHandles(attQuery);
            }
        });
	}   

	CloseableIterator<DigitalObject> indexSearch(AttributeQuery query) {
        final CloseableIterator<String> handleIter = indexSearchHandles(query);
        return new AbstractCloseableIterator<DigitalObject>() {
            @Override
            protected DigitalObject computeNext() {
                if(handleIter.hasNext()) return getDigitalObject(handleIter.next());
                return null;
            }
            @Override
            protected void closeOnlyOnce() {
                handleIter.close();
            }
        };
	}

	CloseableIterator<String> indexSearchHandles(AttributeQuery query) {
	    Pair key = new Pair(query.getAttributeName(), query.getValue());
	    Set<String> handles = index.get(key);
	    if(handles==null) handles = Collections.emptySet();
	    return new CloseableIteratorFromIterator<String>(handles.iterator());
	}

	@Override
	public void close() {
	    // no-op
	}

	@Override
	public File getFile(String handle, String elementName) throws RepositoryException {
		throw new UnsupportedOperationException();
	}
}
