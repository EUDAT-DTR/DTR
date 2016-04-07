/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.syncing;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.wrapper.DataElementWrapper;

public class SyncingDataElement extends DataElementWrapper implements DataElement {

    private SyncingRepository repo;
    private DigitalObject localObject;
    
    public SyncingDataElement(DigitalObject syncingObject, DataElement localElement) {
        super(syncingObject, localElement);
        this.repo = (SyncingRepository) syncingObject.getRepository();
        this.localObject = localElement.getDigitalObject();
    }
    
    @Override
    public void setAttribute(String name, String value) throws RepositoryException {
        super.setAttribute(name, value);
        repo.setInternalMetaData(localObject);
    }
    
    @Override
    public void setAttributes(Map<String,String> attributes) throws RepositoryException {
        super.setAttributes(attributes);
        repo.setInternalMetaData(localObject);
    }

    @Override
    public void deleteAttribute(String name) throws RepositoryException {
        super.deleteAttribute(name);
        repo.setInternalMetaData(localObject);
    }

    @Override
    public void deleteAttributes(List<String> names) throws RepositoryException {
        super.deleteAttributes(names);
        repo.setInternalMetaData(localObject);
    }
    
    @Override
    public void setType(String type) throws RepositoryException {
        super.setType(type);
        repo.setInternalMetaData(localObject);
    }
    
    @Override
    public long write(InputStream data) throws IOException, RepositoryException {
        long result = super.write(data);
        repo.setInternalMetaData(localObject);
        return result;
    }
    
    @Override
    public long write(InputStream data, boolean append) throws IOException, RepositoryException {
        long result = super.write(data, append);
        repo.setInternalMetaData(localObject);
        return result;
    }
}
