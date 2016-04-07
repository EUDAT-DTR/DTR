/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.bdbje;

import java.io.File;
import java.io.IOException;
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

import net.cnri.repository.AbstractDigitalObject;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repositories;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.layered.LayeredRepository;
import net.cnri.repository.layered.SupportsFastCopyForLayeredRepo;
import net.cnri.repository.memory.MemoryDigitalObject;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.Pair;
import net.handle.hdllib.Util;

/**
 * 
 * The digital object is stored using the berkeleydb base API. Which is to say it is stored as a series on name value pairs.
 * The attribute keys are composed of the object handle a delimiter and the element and attribute names. This key is byte encoded.
 * See KeyUtil.
 * 
 */
public class BdbjeDigitalObject extends AbstractDigitalObject implements DigitalObject, SupportsFastCopyForLayeredRepo {

	private final String handle;
    private final BdbjeRepository repository;
    private final Environment dbEnvironment;
    private final Database db;
    private final byte[] handleKeyBytes;
    
    private static final String ELEMENT_FILE_DIR = "elements";
    static final byte[] ELEMENT_DATA = {};
	
	public BdbjeDigitalObject(BdbjeRepository respository, Environment dbEnvironment, Database db, String handle, byte[] handleBytes) throws RepositoryException {
		this.repository = respository;
		this.dbEnvironment = dbEnvironment;
		this.db = db;
		this.handle = handle;
		this.handleKeyBytes = handleBytes;
		if (getAttribute(Repositories.INTERNAL_CREATED) == null) {
		    setCreatedAndModifiedAttribute();  
		}
	}
	
	@Override
	public Repository getRepository() {
		return repository;
	}

	@Override
	public String getHandle() {
		return handle;
	}
	
    public void setCreatedAndModifiedAttribute() throws InternalException {
        String now = String.valueOf(System.currentTimeMillis());
        byte[] dataBytes = BdbUtil.encode(now);
        byte[] createdKeyBytes = KeyUtil.getAttributeKey(handleKeyBytes, Repositories.INTERNAL_CREATED);
        try {
            db.put(null, new DatabaseEntry(createdKeyBytes), new DatabaseEntry(dataBytes));
        } catch (Exception e) {
            throw new InternalException(e);
        }
        byte[] modifiedKeyBytes = KeyUtil.getAttributeKey(handleKeyBytes, Repositories.INTERNAL_MODIFIED);
        try {
            db.put(null, new DatabaseEntry(modifiedKeyBytes), new DatabaseEntry(dataBytes));
        } catch (Exception e) {
            throw new InternalException(e);
        }
    }
    
    public void setModifiedAttribute() throws InternalException {
        String now = String.valueOf(System.currentTimeMillis());
        byte[] keyBytes = KeyUtil.getAttributeKey(handleKeyBytes, Repositories.INTERNAL_MODIFIED);
        byte[] dataBytes = BdbUtil.encode(now);
        try {
            db.put(null, new DatabaseEntry(keyBytes), new DatabaseEntry(dataBytes));
        } catch (Exception e) {
            throw new InternalException(e);
        }
    }

	@Override
	public void delete() throws RepositoryException {
    	DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
    	DatabaseEntry data = new DatabaseEntry();
    	data.setPartial(0,0,true);
    	TransactionConfig config = new TransactionConfig();
    	config.setSerializableIsolation(true);
    	Transaction dbtxn = dbEnvironment.beginTransaction(null, config);
    	Cursor cursor = db.openCursor(dbtxn, null);
		try {
			OperationStatus status = cursor.getSearchKey(key, data, LockMode.RMW); //moves cursor to the record at the handle 
			if(status == OperationStatus.SUCCESS) {
			    cursor.delete();
			    while (cursor.getNext(key, data, LockMode.RMW) == OperationStatus.SUCCESS) {
			        byte[] keyBytes = key.getData();
			        if(isKeyElementOfThisObject(keyBytes)) {
			            getElementFile(null,keyBytes,false).delete();
			            cursor.delete();
			        }
			        else if (BdbUtil.startsWith(keyBytes,handleKeyBytes)) {
			            cursor.delete();
			        } else {
			            break;
			        }
			    }
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
	}
	
    @Override
    public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
        final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
        final DatabaseEntry data = new DatabaseEntry();
        final Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        cursor.getSearchKey(key, data, null); //moves cursor to the record at the handle 
        return new AbstractCloseableIterator<Map.Entry<String,String>>() {
            @Override
            protected Entry<String,String> computeNext() {
                OperationStatus status = cursor.getNext(key,data,null);
                if(status==OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (isKeyAttributeOfThisObject(keyBytes)) {
                        String name = KeyUtil.extractAttributeName(keyBytes);
                        String value = BdbUtil.decode(data.getData());
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
		byte[] keyBytes = KeyUtil.getAttributeKey(handleKeyBytes, name);
    	DatabaseEntry key = new DatabaseEntry(keyBytes);
    	DatabaseEntry data = new DatabaseEntry();
    	OperationStatus status = db.get(null, key, data, LockMode.READ_COMMITTED);		
		if (status == OperationStatus.SUCCESS) {
			return BdbUtil.decode(data.getData());
		} else {
			return null;
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
		setModifiedAttribute();
	}
	
	/**
	 * 
	 * This a faster version of setAttribute used when setting lots of attributes on the same object
	 */
	private void setAttributeWithCursor(Cursor cursor, String name, String value) throws RepositoryException {
		byte[] keyBytes = KeyUtil.getAttributeKey(handleKeyBytes, name);
		byte[] dataBytes = BdbUtil.encode(value);
		try {
			cursor.put(new DatabaseEntry(keyBytes), new DatabaseEntry(dataBytes));
		} catch (Exception e) {
			throw new InternalException(e);
		}		
	}

	@Override
	public void setAttribute(String name, String value) throws RepositoryException {
        if(value==null) {
            deleteAttribute(name);
            return;
        }
		byte[] keyBytes = KeyUtil.getAttributeKey(handleKeyBytes, name);
		byte[] dataBytes = BdbUtil.encode(value);
		try {
			db.put(null, new DatabaseEntry(keyBytes), new DatabaseEntry(dataBytes));
		} catch (Exception e) {
			throw new InternalException(e);
		}
		setModifiedAttribute();
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
	    setModifiedAttribute();
	}

    private void deleteAttributeWithCursor(Cursor cursor,String name) throws RepositoryException {
        byte[] keyBytes = KeyUtil.getAttributeKey(handleKeyBytes, name);
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
		byte[] keyBytes = KeyUtil.getAttributeKey(handleKeyBytes, name);
		try {
			db.delete(null, new DatabaseEntry(keyBytes));
		} catch (Exception e) {
			throw new InternalException(e);
		}
		setModifiedAttribute();
	}

	@Override
	public boolean verifyDataElement(String name) throws RepositoryException {
		byte[] keyBytes = KeyUtil.getElementKey(handleKeyBytes, name);
    	DatabaseEntry key = new DatabaseEntry(keyBytes);
    	DatabaseEntry data = new DatabaseEntry();
    	data.setPartial(0,0,true);
    	OperationStatus status = db.get(null, key, data, LockMode.READ_UNCOMMITTED);
		if (status == OperationStatus.SUCCESS) {
			return true;
		} else if(status == OperationStatus.NOTFOUND) {
			return false;
		} else {
			throw new InternalException("Unexpected status " + status.toString());
		}
	}

	@Override
	public DataElement createDataElement(String name) throws CreationException, RepositoryException {
		byte[] keyBytes = KeyUtil.getElementKey(handleKeyBytes, name);
		OperationStatus status;
		try {
			status = db.putNoOverwrite(null, new DatabaseEntry(keyBytes), new DatabaseEntry(ELEMENT_DATA));
		} catch (Exception e) {
			throw new InternalException(e);
		}		
		if(status==OperationStatus.KEYEXIST) throw new CreationException();
		else if(status!=OperationStatus.SUCCESS) throw new InternalException("Unexpected status " + status.toString());

		getElementFile(name,keyBytes,true);
		return new BdbjeDataElement(this, dbEnvironment, db, name, keyBytes);
	}
	
	/*
	 * Ridiculously optimized encoding of handle/element name pair into a FAT-safe filename.
	 * Reserved characters are encoded using a comma and one other character. 
	 */
    private static boolean reservedChar(char ch) {
        if((ch&0xFFC0)!=0) return ch=='\\' || ch=='|' || ch==127;
        if((ch&0x20)==0) return true;
        if((ch&0x08)==0) return ch=='"';
        if(ch=='.' || ch=='8' || ch=='9' || ch=='-') return false;
        int nibble = ch&0x0F;
        if(nibble==0xA || nibble==0xC || nibble==0xF) return true; // *,/:<?
        return ch=='>';
    }

    private static void appendEncoded(StringBuilder sb,String s) {
        int last = -1;
        char[] chs = s.toCharArray();
        for(int i = 0; i<chs.length; i++) {
            char ch = chs[i];
            if(reservedChar(ch)) {
                if(last>=0) {
                    sb.append(chs,last,i-last);
                    last = -1;
                }
                sb.append(',');
                int masked = ch&0x1F;
                if(masked!=0x1C && masked!=0x1F) {
                    sb.append((char)((ch&0x3F)|0x40));
                }
                else switch(ch) {
                case 0x1C: sb.append('0'); break;
                case 0x1F: sb.append('1'); break;
                case 0x3C: sb.append('2'); break;
                case 0x3F: sb.append('3'); break;
                case 0x5C: sb.append('4'); break;
                case 0x5F: sb.append('5'); break;
                case 0x7C: sb.append('6'); break;
                case 0x7F: sb.append('7'); break;
                }
            }
            else if(last<0) last = i;
        }
        if(last>=0) {
            sb.append(chs,last,chs.length-last);
        }
    }
    
	private static String encodeFilename(String handle, String elementName) {
	    StringBuilder sb = new StringBuilder((handle.length() + elementName.length() + 1)*2);
	    appendEncoded(sb,handle);
	    sb.append(",,");
	    appendEncoded(sb,elementName);
	    return sb.toString();
	}

	static File getElementFile(File home, String handle, String name,byte[] elementKeyBytes, boolean create) throws InternalException {
	    if(name==null) name = KeyUtil.extractElementName(elementKeyBytes);
	    String fileName = encodeFilename(handle,name);
		File elementsDir = new File(home, ELEMENT_FILE_DIR);
		elementsDir.mkdirs();
		File file = new File(elementsDir, fileName);
		if(!create || file.exists()) {
        	return file;
        } else {
	        try {
	            file.createNewFile();
	        }
	        catch(IOException e) {
	            throw new InternalException(e);
	        }
			return file;	
        }
	}
	
	private File getElementFile(String name, byte[] elementKeyBytes, boolean create) throws InternalException {
	    return getElementFile(dbEnvironment.getHome(),handle,name,elementKeyBytes,create);
	}

	@Override
	public DataElement getDataElement(String name) throws RepositoryException {
		byte[] keyBytes = KeyUtil.getElementKey(handleKeyBytes, name);
    	DatabaseEntry key = new DatabaseEntry(keyBytes);
    	DatabaseEntry data = new DatabaseEntry();
    	data.setPartial(0,0,true);
    	OperationStatus status = db.get(null, key, data, LockMode.READ_UNCOMMITTED);
		if (status == OperationStatus.SUCCESS) {
			return getDataElementFromNameAndKeyBytes(name,keyBytes);
		} else if(status == OperationStatus.NOTFOUND) {
			return null;
		} else {
			throw new InternalException("Unexpected status " + status.toString());
		}
	}

	private DataElement getDataElementFromNameAndKeyBytes(String name, byte[] keyBytes) throws RepositoryException {
	    return new BdbjeDataElement(this, dbEnvironment, db, name, keyBytes);
	}
	
	@Override
	public void deleteDataElement(String name) throws RepositoryException {
		DataElement el = getDataElement(name);
		if (el != null) {
			el.delete();
		}
	}

	@Override
	public CloseableIterator<String> listDataElementNames() throws RepositoryException {
	    final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
        final DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);
        final Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        cursor.getSearchKey(key, data, null); //moves cursor to the record at the handle 
	    return new AbstractCloseableIterator<String>() {
            @Override
	        protected String computeNext() {
                while(cursor.getNext(key, data, null) == OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (isKeyAttributeOfThisObject(keyBytes)) {
                        continue;
                    }
                    else if(isKeyElementOfThisObject(keyBytes)) {
                        String name = KeyUtil.extractElementName(keyBytes);
                        return name;
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
	
	private boolean isKeyElementOfThisObject(byte[] keyBytes) {
		return(Util.startsWith(keyBytes, handleKeyBytes) && keyBytes[handleKeyBytes.length]==KeyUtil.ELEMENT_DELIMETER);
	}

	private boolean isKeyAttributeOfThisObject(byte[] keyBytes) {
		return(Util.startsWith(keyBytes, handleKeyBytes) && keyBytes[handleKeyBytes.length]==KeyUtil.ATTRIBUTE_DELIMETER);
	}

	@Override
	public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
        final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
        final DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);
        final Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        cursor.getSearchKey(key, data, null); //moves cursor to the record at the handle 
        return new AbstractCloseableIterator<DataElement>() {
            @Override
            protected DataElement computeNext() {
                while(cursor.getNext(key, data, null) == OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (isKeyAttributeOfThisObject(keyBytes)) {
                        continue;
                    }
                    else if(isKeyElementOfThisObject(keyBytes)) {
                        String name = KeyUtil.extractElementName(keyBytes);
                        try {
                            return new BdbjeDataElement(BdbjeDigitalObject.this, dbEnvironment, db, name, keyBytes);
                        }
                        catch(RepositoryException e) {
                            throw new UncheckedRepositoryException(e);
                        }
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
    public void copyTo(DigitalObject dobj) throws RepositoryException, IOException {
        boolean isMemory = dobj instanceof MemoryDigitalObject;
        MemoryDigitalObject memdobj;
        if(isMemory) memdobj = (MemoryDigitalObject)dobj;
        else memdobj = new MemoryDigitalObject(null,handle);
        
        final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
        final DatabaseEntry data = new DatabaseEntry();
        final Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        cursor.getSearchKey(key, data, null); //moves cursor to the record at the handle
        while(cursor.getNext(key,data,null)==OperationStatus.SUCCESS) {
            byte[] keyBytes = key.getData();
            if(!BdbUtil.startsWith(keyBytes,handleKeyBytes)) break;
            String elementName = KeyUtil.extractElementName(keyBytes);
            String attributeName = KeyUtil.extractAttributeName(keyBytes);
            if(elementName==null) memdobj.setAttribute(attributeName,BdbUtil.decode(data.getData()));
            else if(attributeName!=null) memdobj.getDataElement(elementName).setAttribute(attributeName,BdbUtil.decode(data.getData()));
            else {
                DataElement el = memdobj.createDataElement(elementName);
                LayeredRepository.copyDataOrSetAttributeForMissing(getDataElementFromNameAndKeyBytes(elementName,keyBytes),el);
            }
        }
        
        if(!isMemory) {
            LayeredRepository.copyForLayeredRepo(memdobj,dobj);
        }
    }

}
