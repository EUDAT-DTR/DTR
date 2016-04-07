/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.networked;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
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
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StreamPair;

public class NetworkedDigitalObject extends AbstractDigitalObject implements DigitalObject{

	private NetworkedRepository repo;
	private net.cnri.do_api.DigitalObject dobj;
	
	public NetworkedDigitalObject(NetworkedRepository repo, net.cnri.do_api.DigitalObject dobj){
		this.repo = repo;
		this.dobj = dobj;
	}

	@Override
	public Repository getRepository() {
		return repo;
	}

	@Override
	public String getHandle() {
		return dobj.getID();
	}

	@Override
	public void delete() throws RepositoryException {
		try {
			dobj.deleteObject();
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map<String, String> getAttributes() throws RepositoryException {
		HeaderSet attHeader;
		Map<String, String> attMap = new HashMap<String, String>();
		try {
			attHeader = dobj.getAttributes();
		} catch (IOException e) {
			throw new InternalException(e);
		}
		Iterator iter = attHeader.iterator();
		while (iter.hasNext()){
			HeaderItem item = (HeaderItem)iter.next();
			attMap.put(item.getName(), item.getValue());
		}
		return attMap;
	}

	@Override
	public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
		Map<String, String> attMap = this.getAttributes();
		return new CloseableIteratorFromIterator<Entry<String, String>>(attMap.entrySet().iterator());
	}

	@Override
	public String getAttribute(String attname) throws RepositoryException {
		try {
			return dobj.getAttribute(attname, null);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void setAttributes(Map<String, String> attributes) throws RepositoryException {
		HeaderSet headers = new HeaderSet();
		for(Map.Entry<String,String> entry : attributes.entrySet()) {
			headers.addHeader(entry.getKey(), entry.getValue());
		}
		try {
			dobj.setAttributes(headers);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void setAttribute(String attname, String attvalue) throws RepositoryException {
		try {
			dobj.setAttribute(attname, attvalue);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
		String[] attHolder = names.toArray(new String[names.size()]);
		try {
			dobj.deleteAttributes(attHolder);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void deleteAttribute(String name) throws RepositoryException {
		try {
			dobj.deleteAttribute(name);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public boolean verifyDataElement(String elementID) throws RepositoryException {
		try{
			return dobj.verifyDataElement(elementID);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public DataElement createDataElement(String elementID) throws CreationException, RepositoryException {
        if(elementID==null) {
        	throw new NullPointerException();
        }
        if(verifyDataElement(elementID)) {
        	throw new CreationException();
        }
        try {
            net.cnri.do_api.DataElement el = dobj.getDataElement(elementID);
            el.append(new ByteArrayInputStream(new byte[0]));
			return new NetworkedDataElement(this,el);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public DataElement getDataElement(String elementID) throws RepositoryException {
        if(!verifyDataElement(elementID)) {
        	return null;
        }
		try {
			return new NetworkedDataElement(this, dobj.getDataElement(elementID));
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void deleteDataElement(String elname) throws RepositoryException {
		try {
			dobj.deleteDataElement(elname);
		} catch (IOException e) {
			throw new InternalException(e);
		}
		
	}

	@Override
	public CloseableIterator<String> listDataElementNames() throws RepositoryException {
		final String[] dataElementList;
		try {
			dataElementList = dobj.listDataElements();
		} catch (IOException e) {
			throw new InternalException(e);
		}
		Iterator<String> iter = java.util.Arrays.asList(dataElementList).iterator();
		return new CloseableIteratorFromIterator<String>(iter);
	}

	@Override
	public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
		final String[] dataElementList;
		try {
			dataElementList = dobj.listDataElements();
		} catch (IOException e) {
			throw new InternalException(e);
		}
		
		final Iterator<String> iter = java.util.Arrays.asList(dataElementList).iterator();
		Iterator<DataElement> el_iter = new Iterator<DataElement>() {
			@Override
			public DataElement next() {
				try {
					return new NetworkedDataElement(NetworkedDigitalObject.this, dobj.getDataElement(iter.next()));
				} catch (IOException e) {
					throw new UncheckedRepositoryException(new InternalException(e));
				}
			}

			@Override
			public boolean hasNext() {
				return iter.hasNext();
			}

			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}
		};
		return new CloseableIteratorFromIterator<DataElement>(el_iter);
	}

    /**
     * Perform an arbitrary operation.
     * 
     * @param operationId the operation to perform.
     * @param parameters parameters of the operation.
     * @return a StreamPair for the input and output of the operation.
     * @throws RepositoryException
     */
    public StreamPair performOperation(String operationId, Map<String,String> parameters) throws RepositoryException {
        HeaderSet headers = new HeaderSet();
        for(Map.Entry<String,String> entry : parameters.entrySet()) {
            headers.addHeader(entry.getKey(),entry.getValue());
        }
        return performOperation(operationId,headers);
    }

    /**
     * Perform an arbitrary operation.
     * 
     * @param operationId the operation to perform.
     * @param parameters parameters of the operation.
     * @return a StreamPair for the input and output of the operation.
     * @throws RepositoryException
     */
    public StreamPair performOperation(String operationId, HeaderSet parameters) throws RepositoryException {
        try {
            return dobj.performOperation(operationId,parameters);
        }
        catch(IOException e) {
            throw new InternalException(e);
        }
    }
}
