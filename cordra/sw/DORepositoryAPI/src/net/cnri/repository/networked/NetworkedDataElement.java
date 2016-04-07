/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.networked;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
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
import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StreamPair;

public class NetworkedDataElement extends AbstractDataElement implements DataElement{
	private static final String DEFAULT_MIME_TYPE_ATTRIBUTE = "text/plain";

	private net.cnri.repository.networked.NetworkedDigitalObject netdobj;
	private net.cnri.do_api.DataElement datael;
	
	public NetworkedDataElement(NetworkedDigitalObject netdobj, net.cnri.do_api.DataElement datael){
		this.netdobj = netdobj;
		this.datael = datael;
	}
	
	@Override
	public DigitalObject getDigitalObject() {
		return netdobj;
	}

	@Override
	public String getName() {
		return datael.getDataElementID();
		}

	@Override
	public void delete() throws RepositoryException {
		netdobj.deleteDataElement(datael.getDataElementID());
	}

	@SuppressWarnings("rawtypes")
	@Override
	public Map<String, String> getAttributes() throws RepositoryException {
		Map<String, String> attMap = new HashMap<String, String>();
		HeaderSet attHeader;
			try {
				attHeader = datael.getAttributes();
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
	public String getAttribute(String name) throws RepositoryException {
		try {
			return datael.getAttribute(name, null);
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
			datael.setAttributes(headers);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void setAttribute(String attname, String attvalue) throws RepositoryException {
		try {
			datael.setAttribute(attname, attvalue);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
		String[] attArray = names.toArray(new String[names.size()]);
		try {
			datael.deleteAttributes(attArray);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void deleteAttribute(String name) throws RepositoryException {
		try {
			datael.deleteAttribute(name);
		} catch (IOException e) {
			throw new InternalException(e);
		}
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
	    try {
	        return datael.read();
	    } catch (IOException e) {
	        throw new InternalException(e);
	    }
	}
	
	@Override
	public InputStream read(long start, long len) throws RepositoryException {
	    HeaderSet parameters = new HeaderSet();
	    parameters.addHeader(DOConstants.PARAM_ELEMENT_ID,getName());
	    parameters.addHeader("start",start);
	    parameters.addHeader("len",len);
	    StreamPair io = netdobj.performOperation(DOConstants.GET_DATA_OP_ID,parameters);
	    try { io.getOutputStream().close(); } catch(IOException e) { throw new InternalException(e); }
	    return io.getInputStream();
	}
	
	@Override
	public long write(InputStream data, boolean append) throws IOException {
		return datael.write(data, append);
	}

	@Override
	public long getSize() throws RepositoryException {
		try {
			return datael.getSize();
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}
}
