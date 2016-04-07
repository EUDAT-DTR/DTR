/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.memory;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import net.cnri.repository.AbstractDigitalObject;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repositories;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.repository.util.Pair;

public class MemoryDigitalObject extends AbstractDigitalObject implements DigitalObject{

    private final MemoryRepository repo;
    private final String handle;
    ConcurrentHashMap<String, String> attributes = new ConcurrentHashMap<String, String>();
    private ConcurrentHashMap<String, DataElement> dataElements = new ConcurrentHashMap<String, DataElement>();

    public MemoryDigitalObject(MemoryRepository repo, String handle){
        this.repo = repo;
        this.handle = handle;
        setCreatedAndModifiedAttribute();
    }
    
    public void setCreatedAndModifiedAttribute() {
        String now = String.valueOf(System.currentTimeMillis());
        attributes.put(Repositories.INTERNAL_CREATED, now);
        indexAttribute(Repositories.INTERNAL_CREATED, now);
        attributes.put(Repositories.INTERNAL_MODIFIED, now);
        indexAttribute(Repositories.INTERNAL_MODIFIED, now);
    }
    
    public void setModifiedAttribute() {
        String now = String.valueOf(System.currentTimeMillis());
        attributes.put(Repositories.INTERNAL_MODIFIED, now);
        indexAttribute(Repositories.INTERNAL_MODIFIED, now);
    }
    
    @Override
    public Repository getRepository() {
        return repo;
    }

    @Override
    public String getHandle() {
        return handle;
    }

    @Override
    public void delete() throws RepositoryException {
        if(repo==null) return;
        repo.deleteDigitalObject(handle);   
    }

    @Override
    public Map<String, String> getAttributes() {
        return getCopyOfAttributes();
    }

    @Override
    public CloseableIterator<Entry<String, String>> listAttributes() {
        Iterator<Entry<String, String>> iter = getCopyOfAttributes().entrySet().iterator();
        return new CloseableIteratorFromIterator<Entry<String, String>>(iter);
    }

    private Map<String, String> getCopyOfAttributes() {
        return new HashMap<String, String>(attributes);
    }

    @Override
    public String getAttribute(String id) {
        return attributes.get(id);
    }

    @Override
    public void setAttributes(Map<String, String> givenAttributes) {
        boolean includedModified = false;
        for(Map.Entry<String,String> entry : givenAttributes.entrySet()) {
            if (Repositories.INTERNAL_MODIFIED.equals(entry.getKey())) {
                includedModified = true;
            }
            indexAttribute(entry.getKey(),entry.getValue());
            if(entry.getValue()==null) attributes.remove(entry.getKey());
            else attributes.put(entry.getKey(),entry.getValue());
        }
        if (!includedModified) setModifiedAttribute();
    }

    @Override
    public void setAttribute(String id, String value) {
        indexAttribute(id, value);
        if(value==null) {
            attributes.remove(id);
        }
        else {
            attributes.put(id, value);
        }
        if(!Repositories.INTERNAL_CREATED.equals(id) && !Repositories.INTERNAL_MODIFIED.equals(id)) setModifiedAttribute();
    }

    // TODO this won't crash when accessed from multiple threads, but multiple threads setting the same attribute might get the index and the attributes out of sync.
    void indexAttribute(String id, String value) {
        if(repo==null) return;
        String oldValue = this.getAttribute(id);
        if (oldValue != null) {
            Pair key = new Pair(id, oldValue);
            Set<String> objects = repo.index.get(key);
            if (objects != null) {
                objects.remove(handle);
            }
        }
        if (value != null) {
            Pair key = new Pair(id, value);
            Set<String> objects = repo.index.get(key);
            if (objects == null) {
                objects = new SetFromMap<String>(new ConcurrentHashMap<String,Boolean>());
                repo.index.putIfAbsent(key, objects);
            }
            objects.add(handle);
        }
    }

    @Override
    public void deleteAttributes(List<String> ids) {
        for(String id : ids){
            indexAttribute(id,null);
            attributes.remove(id);
        }
        setModifiedAttribute();
    }

    @Override
    public void deleteAttribute(String id) {
        indexAttribute(id,null);
        attributes.remove(id);
        setModifiedAttribute();
    }

    @Override
    public boolean verifyDataElement(String id) {
        return dataElements.containsKey(id);
    }

    @Override
    public DataElement createDataElement(String id) throws CreationException {
        MemoryDataElement el = new MemoryDataElement(this,id);
        DataElement res = dataElements.putIfAbsent(id,el);
        if(res!=null) throw new CreationException();
        return el;
    }

    @Override
    public DataElement getDataElement(String id) {
        return dataElements.get(id);
    }

    @Override
    public DataElement getOrCreateDataElement(String id) throws CreationException {
        DataElement res = dataElements.get(id);
        if(res!=null) return res;
        MemoryDataElement el = new MemoryDataElement(this,id);
        res = dataElements.putIfAbsent(id,el);
        if(res!=null) return res;
        return dataElements.get(id);
    }

    @Override
    public void deleteDataElement(String id) {
        dataElements.remove(id);
        setModifiedAttribute();
    }

    @Override
    public CloseableIterator<String> listDataElementNames() {
        Iterator<String> iter = dataElements.keySet().iterator();
        return new CloseableIteratorFromIterator<String>(iter);
    }

    @Override
    public CloseableIterator<DataElement> listDataElements() {
        Iterator<DataElement> iter = dataElements.values().iterator();
        return new CloseableIteratorFromIterator<DataElement>(iter);
    }

    /* workaround to missing Collections.newSetFromMap.  Apache License 2.0, taken from Android source */
    private static class SetFromMap<E> extends java.util.AbstractSet<E> {
        private Map<E, Boolean> m;
        private transient Set<E> backingSet;

        SetFromMap(final Map<E, Boolean> map) {
            m = map;
            backingSet = map.keySet();
        }

        @Override
        public boolean equals(Object object) {
            return backingSet.equals(object);
        }

        @Override
        public int hashCode() {
            return backingSet.hashCode();
        }

        @Override
        public boolean add(E object) {
            return m.put(object, Boolean.TRUE) == null;
        }

        @Override
        public void clear() {
            m.clear();
        }

        @Override
        public String toString() {
            return backingSet.toString();
        }

        @Override
        public boolean contains(Object object) {
            return backingSet.contains(object);
        }

        @Override
        public boolean containsAll(java.util.Collection<?> collection) {
            return backingSet.containsAll(collection);
        }

        @Override
        public boolean isEmpty() {
            return m.isEmpty();
        }

        @Override
        public boolean remove(Object object) {
            return m.remove(object) != null;
        }

        @Override
        public boolean retainAll(java.util.Collection<?> collection) {
            return backingSet.retainAll(collection);
        }

        @Override
        public Object[] toArray() {
            return backingSet.toArray();
        }

        @Override
        public <T> T[] toArray(T[] contents) {
            return backingSet.toArray(contents);
        }

        @Override
        public Iterator<E> iterator() {
            return backingSet.iterator();
        }

        @Override
        public int size() {
            return m.size();
        }
    }
}
