/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.wrapper;

import java.io.IOException;
import java.util.AbstractList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.layered.LayeredRepository;
import net.cnri.repository.layered.SupportsFastCopyForLayeredRepo;
import net.cnri.repository.util.AbstractCloseableIterator;

public class DigitalObjectWrapper implements DigitalObject, SupportsFastCopyForLayeredRepo {
    private final RepositoryWrapper repositoryWrapper;
    protected final DigitalObject originalDigitalObject;
    
    public DigitalObjectWrapper(RepositoryWrapper repositoryWrapper, DigitalObject originalDigitalObject) {
        this.repositoryWrapper = repositoryWrapper;
        this.originalDigitalObject = originalDigitalObject;
    }

    public Repository getRepository() {
        return repositoryWrapper;
    }

    protected DataElement wrap(DataElement el) {
        return repositoryWrapper.wrap(this,el);
    }

    private List<DataElement> wrap(final List<DataElement> list) {
        return new AbstractList<DataElement>() {
            @Override
            public DataElement get(int index) {
                return wrap(list.get(index));
            }
            @Override
            public int size() {
                return list.size();
            }
        };
    }

    private CloseableIterator<DataElement> wrap(final CloseableIterator<DataElement> iter) {
        return new AbstractCloseableIterator<DataElement>() {
            @Override
            protected DataElement computeNext() {
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
        return originalDigitalObject.getHandle();
    }

    public void delete() throws RepositoryException {
        originalDigitalObject.delete();
    }

    public Map<String,String> getAttributes() throws RepositoryException {
        return originalDigitalObject.getAttributes();
    }

    public CloseableIterator<Entry<String,String>> listAttributes() throws RepositoryException {
        return originalDigitalObject.listAttributes();
    }

    public String getAttribute(String name) throws RepositoryException {
        return originalDigitalObject.getAttribute(name);
    }

    public void setAttributes(Map<String,String> attributes) throws RepositoryException {
        originalDigitalObject.setAttributes(attributes);
    }

    public void setAttribute(String name, String value) throws RepositoryException {
        originalDigitalObject.setAttribute(name,value);
    }

    public void deleteAttributes(List<String> names) throws RepositoryException {
        originalDigitalObject.deleteAttributes(names);
    }

    public void deleteAttribute(String name) throws RepositoryException {
        originalDigitalObject.deleteAttribute(name);
    }

    public boolean verifyDataElement(String name) throws RepositoryException {
        return originalDigitalObject.verifyDataElement(name);
    }

    public DataElement createDataElement(String name) throws CreationException, RepositoryException {
        return wrap(originalDigitalObject.createDataElement(name));
    }

    public DataElement getDataElement(String name) throws RepositoryException {
        DataElement el = originalDigitalObject.getDataElement(name);
        if(el==null) return null;
        return wrap(el);
    }

    public DataElement getOrCreateDataElement(String name) throws RepositoryException {
        return wrap(originalDigitalObject.getOrCreateDataElement(name));
    }

    public void deleteDataElement(String name) throws RepositoryException {
        originalDigitalObject.deleteDataElement(name);
    }

    public List<String> getDataElementNames() throws RepositoryException {
        return originalDigitalObject.getDataElementNames();
    }

    public List<DataElement> getDataElements() throws RepositoryException {
        return wrap(originalDigitalObject.getDataElements());
    }

    public CloseableIterator<String> listDataElementNames() throws RepositoryException {
        return originalDigitalObject.listDataElementNames();
    }

    public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
        return wrap(originalDigitalObject.listDataElements());
    }
    
    @Override
    public void copyTo(DigitalObject target) throws RepositoryException, IOException {
        LayeredRepository.copyForLayeredRepo(originalDigitalObject,target);
    }
}
