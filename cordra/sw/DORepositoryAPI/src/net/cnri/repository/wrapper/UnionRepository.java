/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.wrapper;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;
import net.cnri.repository.util.AbstractCloseableIterator;

public class UnionRepository implements Repository {
    private final List<Repository> repos;
    
    public UnionRepository(Repository... repos) {
        this.repos = java.util.Arrays.asList(repos);
    }

    public UnionRepository(List<Repository> repos) {
        this.repos = repos;
    }

    @Override
    public String getHandle() {
        for(Repository repo : repos) {
            String res = repo.getHandle();
            if(res!=null) return res;
        }
        return null;
    }

    @Override
    public boolean verifyDigitalObject(String handle) throws RepositoryException {
        for(Repository repo : repos) {
            if(repo.verifyDigitalObject(handle)) return true;
        }
        return false;
    }

    @Override
    public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        return repos.get(0).createDigitalObject(handle);
    }

    @Override
    public DigitalObject getDigitalObject(String handle) throws RepositoryException {
        for(Repository repo : repos) {
            DigitalObject res = repo.getDigitalObject(handle);
            if(res!=null) return res;
        }
        return null;
    }

    @Override
    public DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
        for(Repository repo : repos) {
            DigitalObject res = repo.getDigitalObject(handle);
            if(res!=null) return res;
        }
        return repos.get(0).createDigitalObject(handle);
    }

    @Override
    public void deleteDigitalObject(String handle) throws RepositoryException {
        boolean found = false;
        for(Repository repo : repos) {
            try {
                repo.deleteDigitalObject(handle);
                found = true;
            } catch(NoSuchDigitalObjectException e) {
                // ignore
            }
        }
        if(!found) throw new NoSuchDigitalObjectException(handle);
    }

    @Override
    public CloseableIterator<String> listHandles() throws RepositoryException {
        return new JoiningIterator<String>() {
            @Override
            protected CloseableIterator<String> process(Repository repo) throws RepositoryException {
                return repo.listHandles();
            }  
        };
    }

    @Override
    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        return new JoiningIterator<DigitalObject>() {
            @Override
            protected CloseableIterator<DigitalObject> process(Repository repo) throws RepositoryException {
                return repo.listObjects();
            }
        };
    }

    @Override
    public CloseableIterator<DigitalObject> search(final Query query, final QueryParams queryParams) throws RepositoryException {
        return new JoiningIterator<DigitalObject>() {
            @Override
            protected CloseableIterator<DigitalObject> process(Repository repo) throws RepositoryException {
                return repo.search(query,queryParams);
            }
        };
    }

    @Override
    public CloseableIterator<String> searchHandles(final Query query, final QueryParams queryParams) throws RepositoryException {
        return new JoiningIterator<String>() {
            @Override
            protected CloseableIterator<String> process(Repository repo) throws RepositoryException {
                return repo.searchHandles(query,queryParams);
            }  
        };
    }

    @Override
    public CloseableIterator<DigitalObject> search(final Query query) throws RepositoryException {
        return new JoiningIterator<DigitalObject>() {
            @Override
            protected CloseableIterator<DigitalObject> process(Repository repo) throws RepositoryException {
                return repo.search(query);
            }
        };
    }

    @Override
    public CloseableIterator<String> searchHandles(final Query query) throws RepositoryException {
        return new JoiningIterator<String>() {
            @Override
            protected CloseableIterator<String> process(Repository repo) throws RepositoryException {
                return repo.searchHandles(query);
            }  
        };
    }

    @Override
    @Deprecated
    public CloseableIterator<DigitalObject> search(final String query) throws RepositoryException {
        return new JoiningIterator<DigitalObject>() {
            @Override
            protected CloseableIterator<DigitalObject> process(Repository repo) throws RepositoryException {
                return repo.search(query);
            }
        };
    }

    @Override
    @Deprecated
    public CloseableIterator<String> searchHandles(final String query) throws RepositoryException {
        return new JoiningIterator<String>() {
            @Override
            protected CloseableIterator<String> process(Repository repo) throws RepositoryException {
                return repo.searchHandles(query);
            }  
        };
    }

    @Override
    @Deprecated
    public CloseableIterator<Map<String,String>> searchMapping(final String query) throws RepositoryException {
        return new JoiningIterator<Map<String,String>>() {
            @Override
            protected CloseableIterator<Map<String,String>> process(Repository repo) throws RepositoryException {
                return repo.searchMapping(query);
            }
        };
    }

    @Override
    public void close() {
        for(Repository repo : repos) repo.close();
    }

    abstract class JoiningIterator<T> extends AbstractCloseableIterator<T> { 
        private final Iterator<Repository> repoIter = repos.iterator();
        private CloseableIterator<T> iter = null;
        
        abstract protected CloseableIterator<T> process(Repository repo) throws RepositoryException;
        
        @Override
        protected T computeNext() {
            try {
                while(iter==null || !iter.hasNext()) {
                    if(repoIter.hasNext()) {
                        iter = process(repoIter.next());
                    } else {
                        return null;
                    }
                }
                return iter.next();
            } catch(RepositoryException e) {
                throw new UncheckedRepositoryException(e);
            }
        }
        
        @Override
        protected void closeOnlyOnce() {
            if(iter!=null) iter.close();
        }
    }
    
}
