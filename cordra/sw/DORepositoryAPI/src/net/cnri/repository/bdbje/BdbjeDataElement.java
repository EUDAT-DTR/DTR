/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.bdbje;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.Environment;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

import net.cnri.dobj.DOConstants;
import net.cnri.repository.AbstractFileDataElement;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.FileDataElement;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.Pair;
import net.handle.hdllib.Util;

public class BdbjeDataElement extends AbstractFileDataElement implements FileDataElement {

	private static final String DEFAULT_MIME_TYPE_ATTRIBUTE = "text/plain";
	
	private final BdbjeDigitalObject dobj;
	private final String elName;
    private final Environment dbEnvironment;
    private final Database db;
    private final byte[] elementKeyBytes;
    private final byte[] elementAttributeStartKeyBytes;
	
	public BdbjeDataElement(BdbjeDigitalObject dobj, Environment dbEnvironment, Database db, String elName, byte[] elementKeyBytes) throws CreationException, InternalException {
		this.dobj = dobj;
		this.elName = elName;
		this.dbEnvironment = dbEnvironment;
		this.db = db;
		this.elementKeyBytes = elementKeyBytes;
		this.elementAttributeStartKeyBytes = elementKeyBytes.clone();
		elementAttributeStartKeyBytes[4+dobj.getHandle().length()*2] = KeyUtil.ELEMENT_ATTRIBUTE_DELIMETER;
		dobj.setModifiedAttribute();
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
	public void delete() throws RepositoryException {
        DatabaseEntry key = new DatabaseEntry(elementKeyBytes);
        DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0,0,true);
        TransactionConfig config = new TransactionConfig();
        config.setSerializableIsolation(true);
        Transaction dbtxn = dbEnvironment.beginTransaction(null, config);
        Cursor cursor = db.openCursor(dbtxn, null);
        try {
            OperationStatus status = cursor.getSearchKey(key, data, LockMode.RMW); //moves cursor to the record at the start of this element 
            if(status == OperationStatus.SUCCESS) cursor.delete();
            key = new DatabaseEntry(elementAttributeStartKeyBytes);
            status = cursor.getSearchKeyRange(key, data, LockMode.RMW); //moves cursor to the record at the start of this element 
            while (status == OperationStatus.SUCCESS) {
                byte[] keyBytes = key.getData();
                if(isKeyAttributeOfThisElement(keyBytes)) {
                    cursor.delete();
                } else {
                    break;
                }
                status = cursor.getNext(key, data, LockMode.RMW);
            }
            cursor.close();
            cursor = null;
            dbtxn.commit();
            dbtxn = null;
        } catch (Exception e) {
            if (cursor != null) {
                cursor.close();
            }
            if (dbtxn != null) {
                dbtxn.abort();
                dbtxn = null;
            }
            if(e instanceof RepositoryException) throw (RepositoryException)e;
            else throw new InternalException(e);
        }
        BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),dobj.getHandle(),elName,elementKeyBytes,false).delete();
        dobj.setModifiedAttribute();
    }
	
	/**
	 * Is the given key an attribute on this element. 
	 * Returns true if the key starts with elementKeyBytesPlusDelimeter
	 */
	public boolean isKeyAttributeOfThisElement(byte[] keyBytes) {
		return Util.startsWith(keyBytes, elementAttributeStartKeyBytes);
	}

    @Override
    public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
        final DatabaseEntry key = new DatabaseEntry(elementAttributeStartKeyBytes);
        final DatabaseEntry data = new DatabaseEntry();
        final Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        return new AbstractCloseableIterator<Map.Entry<String,String>>() {
            OperationStatus status = cursor.getSearchKeyRange(key, data, null); //moves cursor to the record at the handle 
            @Override
            protected Entry<String,String> computeNext() {
                if(status==null) status = cursor.getNext(key,data,null);
                if(status==OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (isKeyAttributeOfThisElement(keyBytes)) {
                        String name = KeyUtil.extractAttributeName(keyBytes);
                        String value = BdbUtil.decode(data.getData());
                        status = null;
                        return new Pair(name, value);
                    } else {
                        return null;
                    }
                }
                return null;
            }
            @Override
            protected void closeOnlyOnce() {
                cursor.close();
            }
        };
    }

	@Override
	public String getAttribute(String name) throws RepositoryException {
		byte[] keyBytes = KeyUtil.getElementAttributeKey(elementAttributeStartKeyBytes, name);
    	DatabaseEntry key = new DatabaseEntry(keyBytes);
    	DatabaseEntry data = new DatabaseEntry();
    	OperationStatus status = db.get(null, key, data, LockMode.READ_COMMITTED);		
		if (status == OperationStatus.SUCCESS) {
			return BdbUtil.decode(data.getData());
		} else {
			return null;
		}
	}

	private void setAttributeWithCursor(Cursor cursor, String name, String value) throws RepositoryException {
		byte[] keyBytes = KeyUtil.getElementAttributeKey(elementAttributeStartKeyBytes, name);
		byte[] dataBytes = BdbUtil.encode(value);
		try {
			cursor.put(new DatabaseEntry(keyBytes), new DatabaseEntry(dataBytes));
		} catch (Exception e) {
			throw new InternalException(e);
		}		
	}
	
	@Override
	public void setAttributes(Map<String, String> attributes) throws RepositoryException {
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, null);
        try {
            for (Entry<String,String> entry : attributes.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (value == null) {
                    deleteAttributeWithCursor(cursor,name);
                } else {
                    setAttributeWithCursor(cursor, name, value);
                }
            }
            cursor.close();
            cursor = null;
            dbtxn.commit();
        } catch (Exception e) {
            if (cursor != null) {
                cursor.close();
            }
            if (dbtxn != null) {
                dbtxn.abort();
                dbtxn = null;
            }
            if(e instanceof RepositoryException) throw (RepositoryException)e;
            else throw new InternalException(e);
        } 
        dobj.setModifiedAttribute();
    }
	
	@Override
	public void setAttribute(String name, String value) throws RepositoryException {
		if(value==null) {
			deleteAttribute(name);
			return;
		}
		byte[] keyBytes = KeyUtil.getElementAttributeKey(elementAttributeStartKeyBytes, name);
		byte[] dataBytes = BdbUtil.encode(value);
		try {
			db.put(null, new DatabaseEntry(keyBytes), new DatabaseEntry(dataBytes));
		} catch (Exception e) {
            throw new InternalException(e);
		}
		dobj.setModifiedAttribute();
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, null);
        try {
            for (String name : names) {
                deleteAttributeWithCursor(cursor,name);
            }
            cursor.close();
            cursor = null;
            dbtxn.commit();
        } catch (Exception e) {
            if (cursor != null) {
                cursor.close();
            }
            if (dbtxn != null) {
                dbtxn.abort();
                dbtxn = null;
            }
            if(e instanceof RepositoryException) throw (RepositoryException)e;
            else throw new InternalException(e);
        } 
        dobj.setModifiedAttribute();
    }

    private void deleteAttributeWithCursor(Cursor cursor,String name) throws RepositoryException {
        byte[] keyBytes = KeyUtil.getElementAttributeKey(elementAttributeStartKeyBytes, name);
        DatabaseEntry key = new DatabaseEntry(keyBytes);
        DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0,0,true);
        try {
            if(OperationStatus.SUCCESS == cursor.getSearchKey(key,data,LockMode.RMW)) cursor.delete();
        } catch (Exception e) {
            throw new InternalException(e);
        }       
    }

    @Override
	public void deleteAttribute(String name) throws RepositoryException {
		byte[] keyBytes = KeyUtil.getElementAttributeKey(elementAttributeStartKeyBytes, name);
		try {
			db.delete(null, new DatabaseEntry(keyBytes));
		} catch (Exception e) {
            throw new InternalException(e);
		}
		dobj.setModifiedAttribute();
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
	public File getFile() throws RepositoryException { 
	    return BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),dobj.getHandle(),elName,elementKeyBytes,true);
	}
}
