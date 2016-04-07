/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;

public class DataElementWrapper implements DataElement {
    private final DigitalObject digitalObjectWrapper;
    protected final DataElement originalDataElement;
    
    public DataElementWrapper(DigitalObject digitalObjectWrapper, DataElement originalDataElement) {
        this.digitalObjectWrapper = digitalObjectWrapper;
        this.originalDataElement = originalDataElement;
    }

    public DigitalObject getDigitalObject() {
        return digitalObjectWrapper;
    }
    public String getName() {
        return originalDataElement.getName();
    }
    public void delete() throws RepositoryException {
        originalDataElement.delete();
    }
    public Map<String,String> getAttributes() throws RepositoryException {
        return originalDataElement.getAttributes();
    }
    public CloseableIterator<Entry<String,String>> listAttributes() throws RepositoryException {
        return originalDataElement.listAttributes();
    }
    public String getAttribute(String name) throws RepositoryException {
        return originalDataElement.getAttribute(name);
    }
    public void setAttributes(Map<String,String> attributes) throws RepositoryException {
        originalDataElement.setAttributes(attributes);
    }
    public void setAttribute(String name, String value) throws RepositoryException {
        originalDataElement.setAttribute(name,value);
    }
    public void deleteAttributes(List<String> names) throws RepositoryException {
        originalDataElement.deleteAttributes(names);
    }
    public void deleteAttribute(String name) throws RepositoryException {
        originalDataElement.deleteAttribute(name);
    }
    public String getType() throws RepositoryException {
        return originalDataElement.getType();
    }
    public void setType(String type) throws RepositoryException {
        originalDataElement.setType(type);
    }
    public InputStream read() throws RepositoryException {
        return originalDataElement.read();
    }
    public InputStream read(long start, long len) throws RepositoryException {
        return originalDataElement.read(start,len);
    }
    public long write(InputStream data) throws IOException, RepositoryException {
        return originalDataElement.write(data);
    }
    public long write(InputStream data, boolean append) throws IOException, RepositoryException {
        return originalDataElement.write(data,append);
    }
    public long getSize() throws RepositoryException {
        return originalDataElement.getSize();
    }
    
}
