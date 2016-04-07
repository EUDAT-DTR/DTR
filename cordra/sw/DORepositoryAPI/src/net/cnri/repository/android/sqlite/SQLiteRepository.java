/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android.sqlite;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;

import net.cnri.repository.AbstractRepository;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.DirectRepository;
import net.cnri.repository.InternalException;
import net.cnri.repository.NoSuchDataElementException;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.Repositories;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.android.CursorIteratorImpl;
import net.cnri.repository.search.AbstractQueryVisitorForSearch;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.search.ElementAttributeQuery;
import net.cnri.repository.search.Query;
import net.cnri.repository.util.Pair;

public class SQLiteRepository extends AbstractRepository implements DirectRepository  {
	public static final String NULL = "";
	
    public static final String DATABASE_NAME = DatabaseHelper.DATABASE_NAME;
    public static final String OBJECTS = "OBJECTS";
    private final Context context;
    private final DatabaseHelper dbHelper;
    private final SQLiteDatabase db;

    public static final String HANDLE = "HANDLE";
    public static final String ELEMENT_NAME = "ELEMENT_NAME";
    public static final String ATTRIBUTE_NAME = "ATTRIBUTE_NAME";
    public static final String VALUE = "VALUE";
    
    public static final String[] attColumns = {ATTRIBUTE_NAME, VALUE};
    public static final String[] valueOnlyColumns = {VALUE};
    public static final String[] elNameOnlyColumns = {ELEMENT_NAME};
    public static final String[] handleOnlyColumns = {HANDLE};

	private static final String ELEMENT_FILE_DIR = "element-files"; 
    
	public SQLiteRepository(Context context) {
		this.context = context;
		dbHelper = new DatabaseHelper(context);
		db = dbHelper.getWritableDatabase();
	}
	
    public SQLiteDatabase getDb() {
        return db;
    }
    
	private static ContentValues createObjectRow(String handle) {
        ContentValues row = new ContentValues();
        row.put(HANDLE, handle);
        row.put(ELEMENT_NAME, NULL);
        row.put(ATTRIBUTE_NAME, NULL);
        row.put(VALUE, NULL);
        return row;
	}
	
	private static ContentValues createAttributeRow(String handle, String attributeName, String value) {
        ContentValues row = new ContentValues();
        row.put(HANDLE, handle);
        row.put(ELEMENT_NAME, NULL);
        row.put(ATTRIBUTE_NAME, attributeName);
        row.put(VALUE, value);
        return row;
	}
	
	private static ContentValues createElementRow(String handle, String elementName) {
        ContentValues row = new ContentValues();
        row.put(HANDLE, handle);
        row.put(ELEMENT_NAME, elementName);
        row.put(ATTRIBUTE_NAME, NULL);
        row.put(VALUE, NULL);
        return row;
	}

	private static ContentValues createRow(String handle, String elementName, String attributeName, String value) {
	    ContentValues row = new ContentValues();
	    row.put(HANDLE, handle);
	    row.put(ELEMENT_NAME, elementName);
	    row.put(ATTRIBUTE_NAME, attributeName);
	    row.put(VALUE, value);
	    return row;
	}

	@Override
	public boolean verifyDigitalObject(String handle) throws RepositoryException {
	    Cursor cursor = db.rawQuery("SELECT 1 FROM OBJECTS WHERE HANDLE=? LIMIT 1", new String[] {handle});
	    try {
	        return cursor.moveToFirst();
	    } finally {
	        cursor.close();
	    }
	}

	@Override
	public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
		db.beginTransaction();
		try {
		    if(verifyDigitalObject(handle)) throw new CreationException();
		    ContentValues row = createObjectRow(handle);
			db.insertOrThrow(OBJECTS, null, row);
			String now = String.valueOf(System.currentTimeMillis());
			ContentValues internalCreatedRow = createAttributeRow(handle, Repositories.INTERNAL_CREATED, now);
			ContentValues internalModifiedRow = createAttributeRow(handle, Repositories.INTERNAL_MODIFIED, now);
			db.insertOrThrow(OBJECTS, null, internalCreatedRow);
			db.insertOrThrow(OBJECTS, null, internalModifiedRow);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
		DigitalObject result = new SQLiteDigitalObject(this, db, handle);
		return result;
	}
	
	@Override
	public DigitalObject getDigitalObject(String handle) throws RepositoryException {
        if (verifyDigitalObject(handle)) {
            return new SQLiteDigitalObject(this, db, handle);
        } else {
            return null;
        }
	}

	@Override
	public DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
	    db.beginTransaction();
        try {
            DigitalObject res = super.getOrCreateDigitalObject(handle);
            db.setTransactionSuccessful();
            return res;
        } finally {
            db.endTransaction();
        }
	}
	
	
	@Override
	public void close() {
		db.close();
	}

	@Override
	public Map<String, String> getAttributes(String handle, String elementName) throws RepositoryException {
		if (elementName == null) elementName = NULL;
		String where = HANDLE + "=? AND " + ELEMENT_NAME + "=? AND " + ATTRIBUTE_NAME + " !=?";
		Cursor cursor = db.query(OBJECTS, 
		        attColumns, 
		        where, 
		        new String[] {handle, elementName, NULL}, null, null, null);
		try {
	        Map<String, String> results = new HashMap<String, String>();
		    if (cursor.moveToFirst()) {
		        while (!cursor.isAfterLast()) {
		            String attributeName = cursor.getString(0);
		            String value = cursor.getString(1);
		            results.put(attributeName, value);
		            cursor.moveToNext();
		        }
		    }
		    return results;
		} finally {
		    cursor.close();
		}
	}

	@Override
	public CloseableIterator<Entry<String, String>> listAttributes(String handle, String elementName) throws RepositoryException {
		if (!verifyDigitalObject(handle)) {
			throw new NoSuchDigitalObjectException(handle);
		}
		if (elementName!=null && !verifyDataElement(handle, elementName)) {
			throw new NoSuchDataElementException(elementName);
		}
        if (elementName == null) elementName = NULL;
		String where = HANDLE + "=? AND " + ELEMENT_NAME + "=? AND " + ATTRIBUTE_NAME + " !=?";
		final Cursor cursor = db.query(OBJECTS, 
				attColumns, 
				where, 
				new String[] {handle, elementName, NULL}, null, null, null);

		return new CursorIteratorImpl<Entry<String, String>> (cursor, new CursorIteratorImpl.OfCursor<Entry<String, String>>() {
            public Entry<String, String> ofCursor(Cursor c) {
            	String name = c.getString(0);
            	String value = c.getString(1);
            	return new Pair(name, value);
            }
        });
	}	
	
	@Override
	public String getAttribute(String handle, String elementName, String name) throws RepositoryException {
		Cursor cursor = getCursorForAttributeQuery(handle, elementName, name);
		try {
		    if (cursor.moveToFirst()) {
		        return cursor.getString(0);
		    } else {
		        return null;
		    }
		} finally {
		    cursor.close();
		}
	}
	
	private Cursor getCursorForAttributeQuery(String handle, String elementName, String name) {
        if (elementName == null) elementName = NULL;
        String where = HANDLE + "=? AND " + ELEMENT_NAME + "=? AND " + ATTRIBUTE_NAME + "=?";
        return db.query(OBJECTS, 
                valueOnlyColumns, 
                where, 
                new String[] {handle, elementName, name}, null, null, null);
	}

	@Override
	public void setAttributes(String handle, String elementName, Map<String, String> attributes) throws RepositoryException {
	    db.beginTransaction();
	    try {
	        if (elementName == null) elementName = NULL;
	        DatabaseUtils.InsertHelper ih = new DatabaseUtils.InsertHelper(db, OBJECTS);
	        List<String> toDelete = new ArrayList<String>();
	        for (Map.Entry<String,String> entry : attributes.entrySet()) {
	            if(entry.getValue()==null) toDelete.add(entry.getKey());
	            else ih.replace(createRow(handle,elementName,entry.getKey(),entry.getValue()));
	        }
	        if(!toDelete.isEmpty()) deleteAttributesInner(handle,elementName,toDelete);
            setModifiedAttribute(ih,handle);
	        ih.close();
	        db.setTransactionSuccessful();
	    } finally {
	        db.endTransaction();
	    }
	}

	@Override
	public void setAttribute(String handle, String elementName, String name, String value) throws RepositoryException {
        if (elementName == null) elementName = NULL;
        if(value == null) {
            deleteAttribute(handle, elementName, name);
            return;
        }
        db.beginTransaction();
        try {
        	if (!verifyDigitalObject(handle)) {
        		throw new NoSuchDigitalObjectException(handle);
        	}
            db.replace(OBJECTS,null,createRow(handle,elementName,name,value));
            setModifiedAttribute(handle);
        	db.setTransactionSuccessful();
        } finally {
        	db.endTransaction();
        }
	}
	
	//Note this method does not use a transaction as it is expected to be used inside another methods transaction
    protected void setModifiedAttribute(String handle) throws InternalException {
        String now = String.valueOf(System.currentTimeMillis());
        db.replace(OBJECTS, null, createRow(handle,NULL,Repositories.INTERNAL_MODIFIED,now));
    }	

    private static void setModifiedAttribute(DatabaseUtils.InsertHelper ih, String handle) throws InternalException {
        String now = String.valueOf(System.currentTimeMillis());
        ih.replace(createRow(handle,NULL,Repositories.INTERNAL_MODIFIED,now));
    }
    
	@Override
	public void deleteAttributes(String handle, String elementName, List<String> names) throws RepositoryException {
		if (names.size() == 0) return;
		if (elementName == null) elementName = NULL;
		db.beginTransaction();
		try {
		    deleteAttributesInner(handle,elementName,names);
			setModifiedAttribute(handle);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}
	
	private void deleteAttributesInner(String handle, String elementName, List<String> names) throws RepositoryException {
        List<String> whereArgsList = new ArrayList<String>();
        whereArgsList.add(handle);
        whereArgsList.add(elementName);
        whereArgsList.addAll(names);
        String [] whereArgs = whereArgsList.toArray(new String[whereArgsList.size()]);
        String where = HANDLE + "=? AND " + ELEMENT_NAME +"=? AND " + ATTRIBUTE_NAME + " IN (";
        for (int i = 0; i < names.size(); i++) {
            where += "?";
            if (i != names.size()-1) {
                where += ",";
            } 
        }
        where += ")";
        db.delete(OBJECTS, where, whereArgs);
	}
	
	@Override
	public void deleteAttribute(String handle, String elementName, String name)	throws RepositoryException {
		db.beginTransaction();
        try {
        	if (!verifyDigitalObject(handle)) {
        		throw new NoSuchDigitalObjectException(handle);
        	}
        	if (elementName == null) elementName = NULL;
        	String where = HANDLE + "=? AND " + ELEMENT_NAME + "=? AND " + ATTRIBUTE_NAME + "=?";
        	db.delete(OBJECTS,
        	        where, 
        	        new String[] {handle, elementName, name});
        	setModifiedAttribute(handle);
        	db.setTransactionSuccessful();
        } finally {
        	db.endTransaction();
        }
	}

	@Override
	public boolean verifyDataElement(String handle, String name) throws RepositoryException {
	    Cursor cursor = db.rawQuery("SELECT 1 FROM OBJECTS WHERE " + HANDLE + "=? AND " + ELEMENT_NAME  + "=? LIMIT 1", new String[] {handle, name});
	    try {
	        return cursor.moveToFirst();
	    } finally {
	        cursor.close();
	    }
	}

	@Override
	public void createDataElement(String handle, String name) throws CreationException, RepositoryException {
		db.beginTransaction();
		try {
			if (verifyDataElement(handle, name)) {
				throw new CreationException();
			}
			ContentValues row = createElementRow(handle, name);
			db.insertOrThrow(OBJECTS, null, row);
			setModifiedAttribute(handle);
//			getElementFile(handle, name, true);
			db.setTransactionSuccessful();
		} finally {
			db.endTransaction();
		}
	}

	@Override
	public void deleteDataElement(String handle, String name) throws RepositoryException {
	    db.beginTransaction();
	    try {
	        File elementFile = getElementFile(handle, name, false);
	        elementFile.delete();
	        String where = HANDLE + " =? AND " + ELEMENT_NAME + " =?"; 
	        db.delete(OBJECTS, where, new String[] {handle, name});
	        setModifiedAttribute(handle);
	        db.setTransactionSuccessful();
	    } finally {
	        db.endTransaction();
	    }
	}
	
    @Override
    public void deleteDigitalObject(String handle) throws RepositoryException {
    	File objectDir = getObjectDir(handle, false);
    	deleteRecursive(objectDir);
		String where = HANDLE + " =?" ; 
		db.delete(OBJECTS, where, new String[] {handle});
    }
    
    private boolean deleteRecursive(File file) {
        if (!file.exists()) return true;
        boolean ret = true;
        if (file.isDirectory()){
            for (File f : file.listFiles()){
                ret = ret && deleteRecursive(f);
            }
        }
        return ret && file.delete();
    }

	@Override
	public CloseableIterator<String> listDataElementNames(String handle) throws RepositoryException {
		String where = HANDLE + " =? AND " + ELEMENT_NAME + " !=? AND " + ATTRIBUTE_NAME + " =?"; 
		final Cursor cursor = db.query(OBJECTS, 
		        elNameOnlyColumns, 
				where, 
				new String[] {handle, NULL, NULL}, null, null, null);

		return new CursorIteratorImpl<String> (cursor, new CursorIteratorImpl.OfCursor<String>() {
            public String ofCursor(Cursor c) {
            	return c.getString(0);
            }
        });
	}
	
	private File getObjectDir(String handle, boolean create) {
		String objectDirName = encodeFilename(handle);
		File elementsDir = new File(context.getFilesDir(), ELEMENT_FILE_DIR);
		if(create) elementsDir.mkdirs();
		File objectDir = new File(elementsDir, objectDirName);
		if(create) objectDir.mkdirs();
		return objectDir;
	}
	
	public File getElementFile(String handle, String name, boolean create) throws InternalException {
		String fileName = encodeFilename(name);
		File objectDir = getObjectDir(handle, create);
		File file = new File(objectDir, fileName);
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
	
	private static String encodeFilename(String name) {
	    StringBuilder sb = new StringBuilder();
	    appendEncoded(sb, name);
	    return sb.toString();
	}
	
	//TODO extract into util method
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

    //=========================
    
	@Override
	public InputStream read(String handle, String elementName) throws RepositoryException {
        if(!verifyDataElement(handle,elementName)) throw new NoSuchDataElementException(handle,elementName);
        try {
            return new FileInputStream(getElementFile(handle, elementName, true));
        }
        catch(FileNotFoundException e) {
            throw new InternalException(e);
        }
	}

	@Override
	public long write(String handle, String elementName, InputStream data, boolean append) throws IOException, RepositoryException {
        if(!verifyDataElement(handle, elementName)) throw new NoSuchDataElementException(handle,elementName);
        File file = getElementFile(handle, elementName, true);
        long totalBytesWritten = 0;
        OutputStream out = new BufferedOutputStream(new FileOutputStream(file, append),4096);
        try {
            byte[] buffer = new byte[4096];
            int len;
            while ((len = data.read(buffer)) > 0) {
                out.write(buffer, 0, len);
                totalBytesWritten += len;
            }
        }
        finally {
            out.close();
        }
        //TODO set the modified attribute
        return totalBytesWritten;
	}

	@Override
	public long getSize(String handle, String elementName) throws RepositoryException {
        if(!verifyDataElement(handle,elementName)) throw new NoSuchDataElementException(handle,elementName);
        File file = getElementFile(handle, elementName, false);
        return file.length();
	}

	@Override
	public File getFile(String handle, String elementName) throws RepositoryException {
        if(!verifyDataElement(handle, elementName)) throw new NoSuchDataElementException(handle, elementName);
        return getElementFile(handle, elementName, true);
	}
	
    @Override
    public CloseableIterator<String> listHandles() throws RepositoryException {
		String where = HANDLE + " !=? AND " + ELEMENT_NAME + " =? AND " + ATTRIBUTE_NAME + " =?"; 
		final Cursor cursor = db.query(OBJECTS, 
				handleOnlyColumns, 
				where, 
				new String[] {NULL, NULL, NULL}, null, null, null);

		return new CursorIteratorImpl<String> (cursor, new CursorIteratorImpl.OfCursor<String>() {
            public String ofCursor(Cursor c) {
            	return c.getString(0);
            }
        });
    }

    @Override
    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        String where = HANDLE + " !=? AND " + ELEMENT_NAME + " =? AND " + ATTRIBUTE_NAME + " =?"; 
        final Cursor cursor = db.query(OBJECTS, 
                handleOnlyColumns, 
                where, 
                new String[] {NULL, NULL, NULL}, null, null, null);

        return new CursorIteratorImpl<DigitalObject> (cursor, new CursorIteratorImpl.OfCursor<DigitalObject>() {
            public DigitalObject ofCursor(Cursor c) {
                String handle = c.getString(0);
                DigitalObject result = null;
                try {
                    result = getDigitalObject(handle);
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
                return result;
            }
        });
    }
    
    @Override
    public CloseableIterator<DigitalObject> search(Query query) throws RepositoryException {
        return query.accept(new AbstractQueryVisitorForSearch.SearchObjects(this) {
            @Override
            public CloseableIterator<DigitalObject> visitAttributeQuery(AttributeQuery attQuery) throws RepositoryException {
                return searchObjectsAttributeQuery(attQuery);
            }
            @Override
            public CloseableIterator<DigitalObject> visitElementAttributeQuery(ElementAttributeQuery elattQuery) throws RepositoryException {
                return searchElementAttributeQuery(elattQuery);
            }
        });
    }
    
    private CloseableIterator<DigitalObject> searchElementAttributeQuery(ElementAttributeQuery query) {
		String where = ELEMENT_NAME + " =? AND " + ATTRIBUTE_NAME + " =? AND " + VALUE + " =?"; 
		String elementName = query.getElementName();
		String elementAttributeName = query.getAttributeName();
		String value = query.getValue();
		final Cursor cursor = db.query(OBJECTS, 
				handleOnlyColumns, 
				where, 
				new String[] {elementName, elementAttributeName, value}, null, null, null);

		return new CursorIteratorImpl<DigitalObject> (cursor, new CursorIteratorImpl.OfCursor<DigitalObject>() {
            public DigitalObject ofCursor(Cursor c) {
            	String handle = c.getString(0);
            	DigitalObject result = null;
            	try {
					result = getDigitalObject(handle);
				} catch (RepositoryException e) {
					throw new UncheckedRepositoryException(e);
				}
            	return result;
            }
        });
    }    
    
    private CloseableIterator<DigitalObject> searchObjectsAttributeQuery(AttributeQuery attQuery) {
		String where = ELEMENT_NAME + "=? AND " + ATTRIBUTE_NAME + " =? AND " + VALUE + " =?"; 
		String attributeName = attQuery.getAttributeName();
		String value = attQuery.getValue();
		final Cursor cursor = db.query(OBJECTS, 
		        handleOnlyColumns,
		        where, 
				new String[] {NULL, attributeName, value}, null, null, null);

		return new CursorIteratorImpl<DigitalObject> (cursor, new CursorIteratorImpl.OfCursor<DigitalObject>() {
            public DigitalObject ofCursor(Cursor c) {
            	String handle = c.getString(0);
            	DigitalObject result = null;
            	try {
					result = getDigitalObject(handle);
				} catch (RepositoryException e) {
					throw new UncheckedRepositoryException(e);
				}
            	return result;
            }
        });
    }
    
    @Override
    public CloseableIterator<String> searchHandles(Query query) throws RepositoryException {
        return query.accept(new AbstractQueryVisitorForSearch.SearchHandles(this) {
            @Override
            public CloseableIterator<String> visitAttributeQuery(AttributeQuery attQuery) throws RepositoryException {
                return searchHandlesAttributeQuery(attQuery);
            }
            @Override
            public CloseableIterator<String> visitElementAttributeQuery(ElementAttributeQuery elattQuery) throws RepositoryException {
                return searchHandlesElementAttributeQuery(elattQuery);
            }
        });
    }
    
    private CloseableIterator<String> searchHandlesElementAttributeQuery(ElementAttributeQuery query) {
		String where = ELEMENT_NAME + " =? AND " + ATTRIBUTE_NAME + " =? AND " + VALUE + " =?"; 
		String elementName = query.getElementName();
		String elementAttributeName = query.getAttributeName();
		String value = query.getValue();
		final Cursor cursor = db.query(OBJECTS, 
				handleOnlyColumns, 
				where, 
				new String[] {elementName, elementAttributeName, value}, null, null, null);

		return new CursorIteratorImpl<String> (cursor, new CursorIteratorImpl.OfCursor<String>() {
            public String ofCursor(Cursor c) {
            	return c.getString(0);
            }
        });
    }

    private CloseableIterator<String> searchHandlesAttributeQuery(AttributeQuery attQuery) {
		String where = ELEMENT_NAME + "=? AND " + ATTRIBUTE_NAME + " =? AND " + VALUE + " =?"; 
		String attributeName = attQuery.getAttributeName();
		String value = attQuery.getValue();
		final Cursor cursor = db.query(OBJECTS, 
				handleOnlyColumns, 
				where, 
				new String[] {NULL, attributeName, value}, null, null, null);

		return new CursorIteratorImpl<String> (cursor, new CursorIteratorImpl.OfCursor<String>() {
            public String ofCursor(Cursor c) {
            	return c.getString(0);
            }
        });
    }
}
