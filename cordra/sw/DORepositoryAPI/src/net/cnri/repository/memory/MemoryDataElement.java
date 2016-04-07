/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.memory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import net.cnri.repository.AbstractDataElement;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.dobj.DOConstants;

public class MemoryDataElement extends AbstractDataElement implements DataElement{
	private static final String DEFAULT_MIME_TYPE_ATTRIBUTE = "text/plain";
	
	
	private final MemoryDigitalObject dobj;
	private final String name;
	private ConcurrentHashMap<String, String> elementAtts = new ConcurrentHashMap<String, String>();
	private byte[] data = new byte[0];
	
	public MemoryDataElement(MemoryDigitalObject dobj, String name){
		this.dobj = dobj;
		this.name = name;
		dobj.setModifiedAttribute();
	}
	
	@Override
	public DigitalObject getDigitalObject() {
		return dobj;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public void delete() {
		dobj.deleteDataElement(name);
	}

	@Override
	public Map<String, String> getAttributes() {
		return copyOfAttributes();
	}
	
	private Map<String,String> copyOfAttributes() {
	    return new HashMap<String,String>(elementAtts);
	}

	@Override
	public CloseableIterator<Entry<String, String>> listAttributes() {
		final Iterator<Entry<String, String>> iter = copyOfAttributes().entrySet().iterator();
		return new CloseableIteratorFromIterator<Entry<String, String>>(iter);
	}

	@Override
	public String getAttribute(String aname) {
		return elementAtts.get(aname);
	}

	@Override
	public void setAttributes(Map<String, String> attributes) {
        for(Map.Entry<String,String> entry : attributes.entrySet()) {
            if(entry.getValue()==null) elementAtts.remove(entry.getKey());
            else elementAtts.put(entry.getKey(),entry.getValue());
        }
        dobj.setModifiedAttribute();
	}

	@Override
	public void setAttribute(String name, String value) {
	    if(value==null) elementAtts.remove(name);
	    else elementAtts.put(name, value);
	    dobj.setModifiedAttribute();
	}

	@Override
	public void deleteAttributes(List<String> names) {
		for(String aname : names){
			elementAtts.remove(aname);
		}
		dobj.setModifiedAttribute();
	}

	@Override
	public void deleteAttribute(String aname) {
		elementAtts.remove(aname);
		dobj.setModifiedAttribute();
	}

	@Override
	public String getType() {
		String res = getAttribute(DOConstants.MIME_TYPE_ATTRIBUTE);
		if (res == null) {
			return DEFAULT_MIME_TYPE_ATTRIBUTE;
		}
		else {
			return res;
		}
	}

	@Override
	public void setType(String type) {
		elementAtts.put(DOConstants.MIME_TYPE_ATTRIBUTE, type);
		dobj.setModifiedAttribute();
	}

	@Override
	public InputStream read() {
		return new ByteArrayInputStream(data);
	}

	@Override
	public long write(InputStream in, boolean append) throws IOException {
		long res = 0;
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buf = new byte[4096];
		int r;
        while((r = in.read(buf))>0) {
            out.write(buf,0,r);
            res += r;
        }
        if(append) {
            byte[] incoming = out.toByteArray();
            byte[] oldData = data;
            data = new byte[oldData.length + incoming.length];
            System.arraycopy(oldData,0,data,0,oldData.length);
            System.arraycopy(incoming,0,data,oldData.length,incoming.length);
        }
        else {
            data = out.toByteArray();
        }
        dobj.setModifiedAttribute();
		return res;
	}

	@Override
	public long getSize() {
		return data.length;
	}
}
