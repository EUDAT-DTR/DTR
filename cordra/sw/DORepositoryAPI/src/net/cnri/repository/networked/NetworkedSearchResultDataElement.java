/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.networked;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.cnri.repository.AbstractDataElement;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.dobj.DOConstants;
import net.handle.hdllib.Util;

/**
 * Allows you to interact with the attributes on a DataElement search result locally without making unnecessary network calls.
 */
public class NetworkedSearchResultDataElement extends AbstractDataElement implements DataElement{
    private static final String DEFAULT_MIME_TYPE_ATTRIBUTE = "text/plain";

    private final net.cnri.repository.networked.NetworkedSearchResultDigitalObject netdobj;
    private final String elementName;
    private final String prefix;
    
    private NetworkedDataElement elem;
    
    public NetworkedSearchResultDataElement(NetworkedSearchResultDigitalObject netdobj, String elementName){
        this.netdobj = netdobj;
        this.elementName = elementName;
        this.prefix = "field:elatt_" + NetworkedSearchResultDigitalObject.escapeElementID(elementName) + "_";
    }

    @Override
    public DigitalObject getDigitalObject() {
        return netdobj;
    }

    @Override
    public String getName() {
        return elementName;
    }

    synchronized void instantiate() throws RepositoryException {
        if(elem!=null) return;
        elem = (NetworkedDataElement)netdobj.getAsNetworkedDigitalObject().getDataElement(elementName);
        if(elem==null) throw new InternalException("Data element " + elementName + " no longer exists");
    }
    
    @Override
    public void delete() throws RepositoryException {
        instantiate();
        netdobj.modified = true;
        elem.delete();
    }

    @Override
    public Map<String, String> getAttributes() throws RepositoryException {
        if(netdobj.modified) {
            instantiate();
            return elem.getAttributes();
        }
        else return new HeaderSetPrefixMap(netdobj.searchResult,prefix);
    }

    @Override
    public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
        if(netdobj.modified) {
            instantiate();
            return elem.listAttributes();
        }
        else return new CloseableIteratorFromIterator<Map.Entry<String,String>>(new HeaderSetPrefixMap(netdobj.searchResult,prefix).entrySet().iterator());
    }

    @Override
    public String getAttribute(String name) throws RepositoryException {
        if(netdobj.modified) {
            instantiate();
            return elem.getAttribute(name);
        }
        else return netdobj.searchResult.getStringHeader(prefix + name,null);
    }	

    @Override
    public void setAttributes(Map<String, String> attributes) throws RepositoryException {
        instantiate();
        netdobj.modified = true;
        elem.setAttributes(attributes);
    }

    @Override
    public void setAttribute(String attName, String attValue) throws RepositoryException {
        instantiate();
        netdobj.modified = true;
        elem.setAttribute(attName,attValue);
    }

    @Override
    public void deleteAttributes(List<String> names) throws RepositoryException {
        instantiate();
        netdobj.modified = true;
        elem.deleteAttributes(names);
    }

    @Override
    public void deleteAttribute(String name) throws RepositoryException {
        instantiate();
        netdobj.modified = true;
        elem.deleteAttribute(name);
    }

    @Override
    public String getType() throws RepositoryException {
        String res = getAttribute(DOConstants.MIME_TYPE_ATTRIBUTE);
        if (res == null) {
            return DEFAULT_MIME_TYPE_ATTRIBUTE;
        }
        else {
            return res;
        }
    }

    @Override
    public void setType(String type) throws RepositoryException {
        setAttribute(DOConstants.MIME_TYPE_ATTRIBUTE, type);
    }

    @Override
    public InputStream read() throws RepositoryException {
        if(netdobj.modified) {
            instantiate();
            return elem.read();
        }
        String contentFieldName = "field:eltext_" + elementName;
        String content = netdobj.searchResult.getStringHeader(contentFieldName, null);
        if (content == null) {
            instantiate();
            return elem.read();
        }
        return new ByteArrayInputStream(Util.encodeString(content));
    }

    @Override
    public long write(InputStream data, boolean append) throws RepositoryException, IOException {
        instantiate();
        netdobj.modified = true;
        return elem.write(data,append);
    }

    @Override
    public long getSize() throws RepositoryException {
        String sizeAtt = getAttribute("internal.size");
        if(sizeAtt==null) return -1;
        try {
            return Long.parseLong(sizeAtt);
        }
        catch(NumberFormatException e) {
            return -1;
        }
    }
}
