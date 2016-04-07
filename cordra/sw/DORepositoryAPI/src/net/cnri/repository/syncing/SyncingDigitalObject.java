/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.syncing;

import java.util.List;
import java.util.Map;

import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.wrapper.DigitalObjectWrapper;

public class SyncingDigitalObject extends DigitalObjectWrapper implements DigitalObject {

    private SyncingRepository repo;
    private DigitalObject localObject;
    
    public SyncingDigitalObject(SyncingRepository repo, DigitalObject localObject) {
        super(repo, localObject);
        this.repo = repo;
        this.localObject = localObject;
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
        if (!name.startsWith("internal.")) {
            repo.setInternalMetaData(localObject);
        }
    }
    
    @Override
    public void deleteAttributes(List<String> names) throws RepositoryException {
        super.deleteAttributes(names);
        repo.setInternalMetaData(localObject);
    }

    @Override
    public DataElement createDataElement(String name) throws CreationException, RepositoryException {
        DataElement result = super.createDataElement(name);
        repo.setInternalMetaData(localObject);
        return result;
    }
    
    @Override
    public void deleteDataElement(String name) throws RepositoryException {
        super.deleteDataElement(name);
        repo.setInternalMetaData(localObject);
    }
}
