/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android.sqlite;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.cnri.repository.AbstractFileDataElement;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.FileDataElement;
import net.cnri.repository.RepositoryException;

public class SQLiteDataElement extends AbstractFileDataElement implements FileDataElement {

	private SQLiteDigitalObject dobj;
	private String elementName;
	private SQLiteRepository repository;
	private String handle;
	
	public SQLiteDataElement(SQLiteDigitalObject dobj, String elementName, SQLiteRepository repository) {
		this.dobj = dobj;
		this.elementName = elementName;
		this.repository = repository;
		this.handle = dobj.getHandle();
	}
	
	@Override
	public DigitalObject getDigitalObject() {
		return dobj;
	}

	@Override
	public String getName() {
		return elementName;
	}

	@Override
	public void delete() throws RepositoryException {
		repository.deleteDataElement(handle, elementName);
	}

	@Override
	public Map<String, String> getAttributes() throws RepositoryException {
		return repository.getAttributes(handle, elementName);
	}

	@Override
	public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
		return repository.listAttributes(handle, elementName);
	}

	@Override
	public String getAttribute(String name) throws RepositoryException {
		return repository.getAttribute(handle, elementName, name);
	}

	@Override
	public void setAttributes(Map<String, String> attributes) throws RepositoryException {
		repository.setAttributes(handle, elementName, attributes);
	}

	@Override
	public void setAttribute(String name, String value) throws RepositoryException {
		repository.setAttribute(handle, elementName, name, value);
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
		repository.deleteAttributes(handle, elementName, names);
	}

	@Override
	public void deleteAttribute(String name) throws RepositoryException {
		repository.deleteAttribute(handle, elementName, name);
	}

	@Override
	public File getFile() throws RepositoryException {
		return repository.getFile(handle, elementName);
	}
}
