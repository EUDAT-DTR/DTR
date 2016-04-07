/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android.sqlite;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import net.cnri.repository.AbstractDigitalObject;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.android.CursorIteratorImpl;
import net.cnri.repository.util.CollectionUtil;

public class SQLiteDigitalObject extends AbstractDigitalObject {

	private final SQLiteRepository repository;
	private final SQLiteDatabase db;
	private final String handle;
	
	public SQLiteDigitalObject(SQLiteRepository repository, SQLiteDatabase db, String handle) {
		this.repository = repository;
		this.db = db;
		this.handle = handle;
	}
	
	
	@Override
	public Repository getRepository() {
		return repository;
	}

	@Override
	public String getHandle() {
		return handle;
	}

	@Override
	public void delete() throws RepositoryException {
		repository.deleteDigitalObject(handle);
	}

	@Override
	public Map<String, String> getAttributes() throws RepositoryException {
		return repository.getAttributes(handle, null); 
	}

	@Override
	public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
		return repository.listAttributes(handle, null);
	}

	@Override
	public String getAttribute(String name) throws RepositoryException {
		return repository.getAttribute(handle, null, name);
	}

	@Override
	public void setAttributes(Map<String, String> attributes) throws RepositoryException {
		repository.setAttributes(handle, null, attributes);
	}

	@Override
	public void setAttribute(String name, String value) throws RepositoryException {
		repository.setAttribute(handle, null, name, value);
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
		repository.deleteAttributes(handle, null, names);
	}

	@Override
	public void deleteAttribute(String name) throws RepositoryException {
		repository.deleteAttribute(handle, null, name);
	}

	@Override
	public boolean verifyDataElement(String name) throws RepositoryException {
		return repository.verifyDataElement(handle, name);
	}

	@Override
	public DataElement createDataElement(String name) throws CreationException, RepositoryException {
		repository.createDataElement(handle, name);
		DataElement result = new SQLiteDataElement(this, name, repository);
		return result;
	}

	@Override
	public DataElement getDataElement(String name) throws RepositoryException {
		if (this.verifyDataElement(name)) {
			return new SQLiteDataElement(this, name, repository);
		} else {
			return null;
		}
		
	}

	@Override
	public DataElement getOrCreateDataElement(String name) 	throws RepositoryException {
	    db.beginTransaction();
	    try {
	        if (this.verifyDataElement(name)) {
	            db.setTransactionSuccessful();
	            return new SQLiteDataElement(this, name, repository);
	        } else {
	            repository.createDataElement(handle, name);
                db.setTransactionSuccessful();
	            return new SQLiteDataElement(this, name, repository);
	        }
	    } finally {
	        db.endTransaction();
	    }
	}

	@Override
	public void deleteDataElement(String name) throws RepositoryException {
		repository.deleteDataElement(handle, name);
	}

	@Override
	public List<String> getDataElementNames() throws RepositoryException {
		return CollectionUtil.asList(repository.listDataElementNames(handle));
	}

	@Override
	public List<DataElement> getDataElements() throws RepositoryException {
		return CollectionUtil.asList(listDataElements());
	}

	@Override
	public CloseableIterator<String> listDataElementNames() throws RepositoryException {
		return repository.listDataElementNames(handle);
	}

	@Override
	public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
	    String NULL = SQLiteRepository.NULL;
		String where = SQLiteRepository.HANDLE + " =? AND " + SQLiteRepository.ATTRIBUTE_NAME + " =? AND " + SQLiteRepository.ELEMENT_NAME + " !=?"; 
		final Cursor cursor = db.query(SQLiteRepository.OBJECTS, 
				SQLiteRepository.elNameOnlyColumns, 
				where, 
				new String[] {handle, NULL, NULL}, null, null, null);

		return new CursorIteratorImpl<DataElement> (cursor, new CursorIteratorImpl.OfCursor<DataElement>() {
            public DataElement ofCursor(Cursor c) {
            	String elementName = c.getString(0);
            	DataElement result = new SQLiteDataElement(SQLiteDigitalObject.this, elementName, repository);
            	return result;
            }
        });
	}

}
