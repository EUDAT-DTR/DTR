/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.networked;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.cnri.repository.AbstractDigitalObject;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.util.StringUtils;
import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;

/**
 * Wraps a searchResult allowing it to be treated as if it were a real DigitalObject.
 * This provides a considerable speed improvement as you can interact the the search result locally
 * without having to make additional network calls unless they are necessary.
 * Any reads that can be made without a network call will be made from the local search result. 
 * Any writes will be made to the remote object.
 * Once a modification is made all further reads will be made from the remote object.
 * These objects are created transparently to the user after they call NetworkedRepository.search() and iterates over the 
 * results.
 */
public class NetworkedSearchResultDigitalObject extends AbstractDigitalObject implements DigitalObject{

	private final NetworkedRepository repo;
	final HeaderSet searchResult;
	volatile boolean modified = false;
	private NetworkedDigitalObject dobj = null;
	
	public NetworkedSearchResultDigitalObject(NetworkedRepository repo, HeaderSet searchResult){
		this.repo = repo;
		this.searchResult = searchResult;
	}

	NetworkedDigitalObject getAsNetworkedDigitalObject() throws RepositoryException {
	    instantiate();
	    return dobj;
	}
	
	@Override
	public Repository getRepository() {
		return repo;
	}

	@Override
	public String getHandle() {
		return searchResult.getStringHeader("objectid",null);
	}

	@Override
	public void delete() throws RepositoryException {
	    if(dobj!=null) dobj.delete();
	    else repo.deleteDigitalObject(getHandle());
	}

	@Override
	public Map<String, String> getAttributes() throws RepositoryException {
	    if(modified) return dobj.getAttributes();
	    else return new HeaderSetPrefixMap(searchResult,"field:objatt_");
	}

	@Override
	public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
	    if(modified) return dobj.listAttributes();
	    else return new CloseableIteratorFromIterator<Entry<String, String>>(new HeaderSetPrefixMap(searchResult,"field:objatt_").entrySet().iterator());
	}

	@Override
	public String getAttribute(String attname) throws RepositoryException {
        if(modified) return dobj.getAttribute(attname);
        else return searchResult.getStringHeader("field:objatt_" + attname,null);
	}
	
	public String getSearchScore() {
		return searchResult.getStringHeader("score", null);
	}

	synchronized void instantiate() throws RepositoryException {
	    if(dobj!=null) return;
	    dobj = (NetworkedDigitalObject)repo.getDigitalObject(getHandle());
	    if(dobj==null) throw new InternalException("Digital object " + getHandle() + " no longer exists");
	}
	
	@Override
	public void setAttributes(Map<String, String> attributes) throws RepositoryException {
	    instantiate();
	    modified = true;
	    dobj.setAttributes(attributes);
	}

	@Override
	public void setAttribute(String attname, String attvalue) throws RepositoryException {
	    instantiate();
	    modified = true;
	    dobj.setAttribute(attname,attvalue);
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
	    instantiate();
        modified = true;
	    dobj.deleteAttributes(names);
	}

	@Override
	public void deleteAttribute(String name) throws RepositoryException {
	    instantiate();
        modified = true;
	    dobj.deleteAttribute(name);
	}

	@Override
	public boolean verifyDataElement(String elementID) throws RepositoryException {
	    if(modified) return dobj.verifyDataElement(elementID);
	    else {
	        for(HeaderItem header : searchResult) {
	            if(header.getName().startsWith("field:elatt_" + escapeElementID(elementID))) return true;
	        }
	        return false;
	    }
	}

	@Override
	public DataElement createDataElement(String elementID) throws CreationException, RepositoryException {
	    instantiate();
	    modified = true;
	    return dobj.createDataElement(elementID);
	}

	@Override
	public DataElement getDataElement(String elementID) throws RepositoryException {
	    if(modified) return dobj.getDataElement(elementID);
	    else {
	        if(!verifyDataElement(elementID)) {
	            return null;
	        }
	        return new NetworkedSearchResultDataElement(this,elementID);
	    }
	}

	@Override
	public void deleteDataElement(String elname) throws RepositoryException {
        instantiate();
        modified = true;
        dobj.deleteDataElement(elname);
	}

	@Override
	public CloseableIterator<String> listDataElementNames() throws RepositoryException {
	    if(modified) return dobj.listDataElementNames();
	    else return new ListDataElementNamesIterator();
	}

	@Override
	public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
        if(modified) return dobj.listDataElements();
        else return new ListDataElementsIterator();
	}
	
    static String escapeElementID(String elementID) {
        return elementID.replace("%","%25").replace("_","%5F");
    }
    
    private class ListDataElementNamesIterator extends AbstractCloseableIterator<String> {
        @SuppressWarnings("unchecked")
        Iterator<HeaderItem> iter = searchResult.iterator();
        List<String> seen = new ArrayList<String>();
        
        @Override
        protected String computeNext() {
            while(iter.hasNext()) {
                String nextHeaderName = iter.next().getName();
                if(nextHeaderName.startsWith("field:elatt_")) {
                    int underscore = nextHeaderName.indexOf('_',12);
                    String nextName = nextHeaderName.substring(12,underscore);
                    nextName = StringUtils.decodeURLIgnorePlus(nextName);
                    if(seen.contains(nextName)) continue;
                    seen.add(nextName);
                    return nextName;
                }
            }
            return null;
        }
    }
    
    private class ListDataElementsIterator extends AbstractCloseableIterator<DataElement> {
        CloseableIterator<String> iter = new ListDataElementNamesIterator();
        
        @Override
        protected DataElement computeNext() {
            if(!iter.hasNext()) return null;
            return new NetworkedSearchResultDataElement(NetworkedSearchResultDigitalObject.this,iter.next());
        }
        
        @Override
        public void closeOnlyOnce() {
            iter.close();
        }
    }
}
