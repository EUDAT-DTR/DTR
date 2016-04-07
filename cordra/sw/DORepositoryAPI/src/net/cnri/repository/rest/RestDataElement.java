/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.http.client.ClientProtocolException;

import net.cnri.repository.AbstractDataElement;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.memory.MemoryDataElement;
import net.cnri.repository.util.RepositoryJsonSerializerV2;
import net.cnri.util.StringUtils;

public class RestDataElement extends AbstractDataElement {

    private RestDigitalObject dobj;
    private MemoryDataElement dataElement;
    
    public RestDataElement(RestDigitalObject dobj, MemoryDataElement dataElement) {
        this.dobj = dobj;
        this.dataElement = dataElement;
    }
    
    @Override
    public Map<String, String> getAttributes() {
        return dataElement.getAttributes();
    }

    @Override
    public CloseableIterator<Entry<String, String>> listAttributes() {
        return dataElement.listAttributes();
    }

    @Override
    public String getAttribute(String aname) {
        return dataElement.getAttribute(aname);
    }

    @Override
    public String getType() {
        return dataElement.getType();
    }

    @Override
    public InputStream read() {
        return readDataElementFromServlet();
    }
    
    private InputStream readDataElementFromServlet() {
        RestRepository repo = (RestRepository) dobj.getRepository();  
        String url = repo.baseUri + "/" + StringUtils.encodeURLComponent(dobj.getHandle()) + "/el/" + StringUtils.encodeURLComponent(getName());
        InputStream result = null;
        try {
            result = repo.getInputStreamFromURLPrememptiveBasicAuth(url);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public long getSize() {
        RestRepository repo = (RestRepository) dobj.getRepository();  
        String url = repo.baseUri + "/" + StringUtils.encodeURLComponent(dobj.getHandle()) + "/el/" + StringUtils.encodeURLComponent(getName()) + "?getSize=true";
        String sizeString = "0";
        try {
            sizeString = repo.getJSONFromURLPrememptiveBasicAuth(url);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return Long.parseLong(sizeString);
    }

    @Override
    public DigitalObject getDigitalObject() {
        return dobj;
    }

    @Override
    public String getName() {
        return dataElement.getName();
    }

    @Override
    public void delete() throws RepositoryException {
        dobj.deleteDataElement(getName());
    }

    @Override
    public void setAttributes(Map<String, String> attributes) throws RepositoryException {
        RestRepository repo = (RestRepository) dobj.getRepository();
        Map<String, String> oldAttrubutes = getAttributes();
        dataElement.setAttributes(attributes);
        String json = RepositoryJsonSerializerV2.toJSON(dobj);
        try {
            repo.postToURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(dobj.getHandle()), json);
        } catch (ClientProtocolException e) {
            dataElement.setAttributes(oldAttrubutes);
            e.printStackTrace();
        } catch (IOException e) {
            dataElement.setAttributes(oldAttrubutes);
            e.printStackTrace();
        }
        
    }

    @Override
    public void setAttribute(String name, String value) throws RepositoryException {
        try {
            RestRepository repo = (RestRepository) dobj.getRepository();
            repo.postToURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(dobj.getHandle()) + "/el/" + StringUtils.encodeURLComponent(getName()) + "/att/" + StringUtils.encodeURLComponent(name), value);
            //TODO deal with response code
            dataElement.setAttribute(name, value);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public void deleteAttributes(List<String> names) throws RepositoryException {
        RestRepository repo = (RestRepository) dobj.getRepository();
        Map<String, String> oldAttrubutes = getAttributes();
        dataElement.deleteAttributes(names);
        String json = RepositoryJsonSerializerV2.toJSON(dobj);
        try {
            repo.postToURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(dobj.getHandle()), json);
        } catch (ClientProtocolException e) {
            dataElement.setAttributes(oldAttrubutes);
            e.printStackTrace();
        } catch (IOException e) {
            dataElement.setAttributes(oldAttrubutes);
            e.printStackTrace();
        }
    }

    @Override
    public void deleteAttribute(String name) throws RepositoryException {
        try {
            RestRepository repo = (RestRepository) dobj.getRepository();
            repo.deleteFromURLPrememptiveBasicAuth(repo.baseUri + "/" + StringUtils.encodeURLComponent(dobj.getHandle()) + "/el/" + StringUtils.encodeURLComponent(getName()) + "/att/" + StringUtils.encodeURLComponent(name));
            dataElement.deleteAttribute(name);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public long write(InputStream data, boolean append) throws IOException, RepositoryException {
        try {
            RestRepository repo = (RestRepository) dobj.getRepository();
            String url = repo.baseUri + "/" + StringUtils.encodeURLComponent(dobj.getHandle()) + "/el/" + StringUtils.encodeURLComponent(getName());
            if (append) {
                url += "?append=true";
            }
            repo.postInputStreamToURLPrememptiveBasicAuth(url, data);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
        return 0;
    }

}
