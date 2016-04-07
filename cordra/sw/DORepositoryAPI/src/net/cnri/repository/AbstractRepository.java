/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.util.Map;

import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.util.AbstractCloseableIterator;

public abstract class AbstractRepository implements Repository {

    @Override
    public String getHandle() {
        return null;
    }
    
    @Override
    public boolean verifyDigitalObject(String handle) throws RepositoryException {
        return getDigitalObject(handle)!=null;
    }

    @Override
    public DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
        DigitalObject res = getDigitalObject(handle);
        if(res==null) return createDigitalObject(handle);
        return res;
    }

    @Override
    public void deleteDigitalObject(String handle) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        dobj.delete();
    }

    @Override
    public CloseableIterator<String> listHandles() throws RepositoryException {
        final CloseableIterator<DigitalObject> iter = listObjects();
        return new AbstractCloseableIterator<String>() {
            @Override
            protected String computeNext() {
                if(iter.hasNext()) return iter.next().getHandle();
                else return null;
            }
            @Override
            protected void closeOnlyOnce() {
                iter.close();
            }
        };
    }

    @Override
    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        final CloseableIterator<String> iter = listHandles();
        return new AbstractCloseableIterator<DigitalObject>() {
            @Override
            protected DigitalObject computeNext() {
                try {
                    if(iter.hasNext()) return getDigitalObject(iter.next());
                    else return null;
                }
                catch(RepositoryException e) { throw new UncheckedRepositoryException(e); }
            }
            @Override
            protected void closeOnlyOnce() {
                iter.close();
            }
        };
    }
    
    @Override
    public CloseableIterator<DigitalObject> search(Query query, QueryParams queryParams) throws RepositoryException {
        if(queryParams==null || queryParams.equals(QueryParams.DEFAULT)) return search(query);
        else throw new UnsupportedOperationException();
    }

    @Override
    public CloseableIterator<String> searchHandles(Query query, QueryParams queryParams) throws RepositoryException {
        if(queryParams==null || queryParams.equals(QueryParams.DEFAULT)) return searchHandles(query);
        else throw new UnsupportedOperationException();
    }

    @Override
    @Deprecated
    public CloseableIterator<DigitalObject> search(String query) throws RepositoryException {
        return search(new RawQuery(query),null);
    }
    
    @Override
    @Deprecated
    public CloseableIterator<String> searchHandles(String query) throws RepositoryException {
        return searchHandles(new RawQuery(query),null);
    }
    
    @Override
    public CloseableIterator<DigitalObject> search(Query query) throws RepositoryException {
        return search(query,null);
    }

    @Override
    public CloseableIterator<String> searchHandles(Query query) throws RepositoryException {
        return searchHandles(query,null);
    }

    @Override
    @Deprecated
    public CloseableIterator<Map<String,String>> searchMapping(String query) throws RepositoryException {
        throw new UnsupportedOperationException();
    }
}
