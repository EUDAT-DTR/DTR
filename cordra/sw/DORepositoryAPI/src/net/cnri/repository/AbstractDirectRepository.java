/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public abstract class AbstractDirectRepository extends AbstractRepository implements DirectRepository {

    @Override
    public Map<String,String> getAttributes(String handle, String elementName) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) return dobj.getAttributes();
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        return el.getAttributes();
    }

    @Override
    public CloseableIterator<Entry<String,String>> listAttributes(String handle, String elementName) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) return dobj.listAttributes();
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        return el.listAttributes();
    }

    @Override
    public String getAttribute(String handle, String elementName, String name) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) return dobj.getAttribute(name);
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        return el.getAttribute(name);
    }

    @Override
    public void setAttributes(String handle, String elementName, Map<String,String> attributes) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) {
            dobj.setAttributes(attributes);
            return;
        }
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        el.setAttributes(attributes);
    }

    @Override
    public void setAttribute(String handle, String elementName, String name, String value) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) {
            dobj.setAttribute(name,value);
            return;
        }
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        el.setAttribute(name,value);
    }

    @Override
    public void deleteAttributes(String handle, String elementName, List<String> names) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) {
            dobj.deleteAttributes(names);
            return;
        }
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        el.deleteAttributes(names);
    }

    @Override
    public void deleteAttribute(String handle, String elementName, String name) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) {
            dobj.deleteAttribute(name);
            return;
        }
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        el.deleteAttribute(name);
    }

    @Override
    public boolean verifyDataElement(String handle, String name) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        return dobj.verifyDataElement(name);
    }

    @Override
    public void createDataElement(String handle, String name) throws CreationException, RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        dobj.createDataElement(name);
    }

    @Override
    public void deleteDataElement(String handle, String name) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        dobj.deleteDataElement(name);
    }

    @Override
    public CloseableIterator<String> listDataElementNames(String handle) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        return dobj.listDataElementNames();
    }

    @Override
    public InputStream read(String handle, String elementName) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) throw new NullPointerException();
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        return el.read();
    }

    @Override
    public long write(String handle, String elementName, InputStream data, boolean append) throws IOException, RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) throw new NullPointerException();
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        return el.write(data,append);
    }

    @Override
    public long getSize(String handle, String elementName) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) throw new NullPointerException();
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        return el.getSize();
    }

    @Override
    public File getFile(String handle, String elementName) throws RepositoryException {
        DigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        if(elementName==null) throw new NullPointerException();
        DataElement el = dobj.getDataElement(elementName);
        if(el==null) throw new NoSuchDataElementException(handle,elementName);
        if(el instanceof FileDataElement) return ((FileDataElement) el).getFile();
        throw new UnsupportedOperationException();
    }

}
