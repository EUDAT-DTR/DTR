/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.client.ClientProtocolException;

import net.cnri.repository.AbstractDigitalObject;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.memory.MemoryDigitalObject;
import net.cnri.repository.memory.MemoryDataElement;
import net.cnri.repository.util.MappingCloseableIteratorWrapper;
import net.cnri.repository.util.RepositoryJsonSerializerV2;
import net.cnri.util.StringUtils;

public class RestDigitalObject extends AbstractDigitalObject {
    private RestRepository repo;
    private MemoryDigitalObject memDobj;
    
    public RestDigitalObject(RestRepository repo, MemoryDigitalObject memDobj) {
        this.repo = repo;
        this.memDobj = memDobj;
    }

    public Map<String, String> getAttributes() {
        return memDobj.getAttributes();
    }

    public CloseableIterator<Entry<String, String>> listAttributes() {
        return memDobj.listAttributes();
    }

    public String getAttribute(String id) {
        return memDobj.getAttribute(id);
    }

    public boolean verifyDataElement(String id) {
        return memDobj.verifyDataElement(id);
    }

    public DataElement getDataElement(String id) {
        MemoryDataElement el = (MemoryDataElement) memDobj.getDataElement(id);
        if (el != null) {
            return new RestDataElement(this, el);
        } else {
            return null;
        }
    }

    public CloseableIterator<String> listDataElementNames() {
        return memDobj.listDataElementNames();
    }

    public CloseableIterator<DataElement> listDataElements() {
        return new MappingCloseableIteratorWrapper<DataElement, DataElement>(memDobj.listDataElements()) {
            @Override
            protected DataElement map(DataElement from) {
                return new RestDataElement(RestDigitalObject.this, (MemoryDataElement) from);
            }
        };
    }

    @Override
    public Repository getRepository() {
        return repo;
    }

    @Override
    public String getHandle() {
        return memDobj.getHandle();
    }

    @Override
    public void delete() throws RepositoryException {
        repo.deleteDigitalObject(this.getHandle());
    }

    @Override
    public void setAttributes(Map<String, String> attributes) throws RepositoryException {
        Map<String, String> oldAttrubutes = getAttributes();
        memDobj.setAttributes(attributes);
        String json = RepositoryJsonSerializerV2.toJSON(memDobj);
        try {
            repo.postToURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(memDobj.getHandle()), json);
        } catch (ClientProtocolException e) {
            memDobj.setAttributes(oldAttrubutes);
            e.printStackTrace();
        } catch (IOException e) {
            memDobj.setAttributes(oldAttrubutes);
            e.printStackTrace();
        }
    }

    @Override
    public void setAttribute(String name, String value) throws RepositoryException {
        try {
            repo.postToURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(memDobj.getHandle()) + "/att/" + StringUtils.encodeURLComponent(name), value);
            memDobj.setAttribute(name, value);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public void deleteAttributes(List<String> names) throws RepositoryException {
        Map<String, String> oldAttrubutes = getAttributes();
        memDobj.deleteAttributes(names);
        String json = RepositoryJsonSerializerV2.toJSON(memDobj);
        try {
            repo.postToURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(memDobj.getHandle()), json);
        } catch (ClientProtocolException e) {
            memDobj.setAttributes(oldAttrubutes);
            e.printStackTrace();
        } catch (IOException e) {
            memDobj.setAttributes(oldAttrubutes);
            e.printStackTrace();
        }
    }

    @Override
    public void deleteAttribute(String name) throws RepositoryException {
        try {
            repo.deleteFromURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(memDobj.getHandle()) + "/att/" + StringUtils.encodeURLComponent(name));
            memDobj.deleteAttribute(name);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public DataElement createDataElement(String name) throws CreationException, RepositoryException {
        memDobj.createDataElement(name);
        String json = RepositoryJsonSerializerV2.toJSON(memDobj);
        try {
            repo.postToURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(memDobj.getHandle()), json);
            MemoryDataElement memEl = (MemoryDataElement) memDobj.getDataElement(name);
            memEl.setAttribute(net.cnri.repository.Constants.ELEMENT_ATTRIBUTE_DATA_NOT_PRESENT, "1");
            RestDataElement resultElement = new RestDataElement(this, memEl);
            return resultElement;
        } catch (ClientProtocolException e) {
            memDobj.deleteDataElement(name);
            e.printStackTrace();
            throw new CreationException(e.getMessage());
        } catch (IOException e) {
            memDobj.deleteDataElement(name);
            e.printStackTrace();
            throw new CreationException(e.getMessage());
        }
    }

    @Override
    public void deleteDataElement(String name) throws RepositoryException {
        try {
            repo.deleteFromURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(memDobj.getHandle()) + "/el/" + StringUtils.encodeURLComponent(name));
            memDobj.deleteDataElement(name);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public List<String> getDataElementNames() throws RepositoryException {
        return memDobj.getDataElementNames();
    }

    @Override
    public List<DataElement> getDataElements() throws RepositoryException {
        return memDobj.getDataElements();
    }

}
