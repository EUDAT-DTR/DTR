/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.repository.util.LimitedInputStream;

public abstract class AbstractDataElement implements DataElement {
    @Override
    public void delete() throws RepositoryException {
        getDigitalObject().deleteDataElement(getName());
    }

    @Override
    public Map<String,String> getAttributes() throws RepositoryException {
        Map<String, String> result = new HashMap<String, String>();
        CloseableIterator<Entry<String,String>> iter = listAttributes();
        try {
            while(iter.hasNext()) {
                Entry<String,String> entry = iter.next();
                result.put(entry.getKey(),entry.getValue());
            }
        }
        catch(UncheckedRepositoryException e) {
            e.throwCause();
        }
        finally {
            iter.close();
        }
        return result;
    }

    @Override
    public CloseableIterator<Entry<String,String>> listAttributes() throws RepositoryException {
        return new CloseableIteratorFromIterator<Map.Entry<String,String>>(getAttributes().entrySet().iterator());
    }

    @Override
    public String getAttribute(String name) throws RepositoryException {
        return getAttributes().get(name);
    }

    @Override
    public void setAttributes(Map<String,String> attributes) throws RepositoryException {
        for(Map.Entry<String,String> entry : attributes.entrySet()) {
            setAttribute(entry.getKey(),entry.getValue());
        }
    }

    @Override
    public void deleteAttributes(List<String> names) throws RepositoryException {
        for(String name : names) {
            deleteAttribute(name);
        }
    }

    @Override
    public String getType() throws RepositoryException {
        String res = getAttribute(Constants.ELEMENT_ATTRIBUTE_TYPE);
        return res;
    }

    @Override
    public void setType(String type) throws RepositoryException {
        setAttribute(Constants.ELEMENT_ATTRIBUTE_TYPE,type);
    }

    @Override
    public long write(InputStream data) throws IOException, RepositoryException {
        return write(data,false);
    }
    
    @Override
    public InputStream read(long start, long len) throws RepositoryException {
        InputStream in = read();
        if(start<=0 && len<0) return in;
        else return new LimitedInputStream(in,start,len);
    }
}
