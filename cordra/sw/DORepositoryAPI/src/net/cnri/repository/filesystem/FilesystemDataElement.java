/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.filesystem;

import java.io.*;
import java.util.*;

import net.cnri.repository.AbstractFileDataElement;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.dobj.DOConstants;

public class FilesystemDataElement extends AbstractFileDataElement implements DataElement{
    private static final String DEFAULT_MIME_TYPE_ATTRIBUTE = "text/plain";
    
    private final FilesystemDigitalObject dobj;
    private final File file;
    private final String elName;
    
	public FilesystemDataElement(FilesystemDigitalObject dobj, File file, String elName) {
        this.dobj = dobj;
        this.file = file;
        this.elName = elName;
    }
	
    @Override
	public DigitalObject getDigitalObject() {
        return dobj;
    }

	@Override
	public String getName() {
	    return elName;
	}

    @Override
    public Map<String, String> getAttributes() throws RepositoryException {
        dobj.loadAttributes();
        Map<String,String> res = dobj.elAttributes.get(elName);
        return copyAttributes(res);
    }

    @Override
    public CloseableIterator<Map.Entry<String, String>> listAttributes() throws RepositoryException {
        dobj.loadAttributes();
        Map<String,String> res = dobj.elAttributes.get(elName);
        return new CloseableIteratorFromIterator<Map.Entry<String,String>>(copyAttributes(res).entrySet().iterator());
    }

    private static Map<String,String> copyAttributes(Map<String,String> atts) {
        if(atts==null) return Collections.emptyMap();
        else return new HashMap<String,String>(atts);
    }
    
    @Override
    public String getAttribute(String name) throws RepositoryException {
        dobj.loadAttributes();
        Map<String,String> res = dobj.elAttributes.get(elName);
        if(res==null) return null;
        return dobj.elAttributes.get(elName).get(name);
    }

	@Override
	public void setAttributes(Map<String, String> attributes) throws RepositoryException {
		Map<String,String> elAtt = dobj.elAttributes.get(elName);
		for (String name : attributes.keySet()) {
			String value = attributes.get(name);
			if(value==null) elAtt.remove(name);
			else elAtt.put(name, value);
		}		
		dobj.writeAttributesFile();
	}

	@Override
	public void setAttribute(String name, String value) throws RepositoryException {
		Map<String,String> elAtt = dobj.elAttributes.get(elName);
		if(value==null) elAtt.remove(name);
		else elAtt.put(name, value);
		dobj.writeAttributesFile();
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
		Map<String,String> elAtt = dobj.elAttributes.get(elName);
		for (String name : names) {
			elAtt.remove(name);
		}
		dobj.writeAttributesFile();        
	}

	@Override
	public void deleteAttribute(String name) throws RepositoryException {
		Map<String,String> elAtt = dobj.elAttributes.get(elName);
		elAtt.remove(name);
		dobj.writeAttributesFile();
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
	public File getFile() {
	    return file;
	}
}
