/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.bdbje;

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

import com.sleepycat.je.Cursor;
import com.sleepycat.je.CursorConfig;
import com.sleepycat.je.Database;
import com.sleepycat.je.DatabaseConfig;
import com.sleepycat.je.DatabaseEntry;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.Durability;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.LockMode;
import com.sleepycat.je.OperationStatus;
import com.sleepycat.je.SecondaryConfig;
import com.sleepycat.je.SecondaryCursor;
import com.sleepycat.je.SecondaryDatabase;
import com.sleepycat.je.Transaction;
import com.sleepycat.je.TransactionConfig;

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
import net.cnri.repository.search.AbstractQueryVisitorForSearch;
import net.cnri.repository.search.ElementAttributeQuery;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.Pair;


/**
 * Digital Object repository built using a Berkeley DB JE environment. 
 */
public class BdbjeRepository extends AbstractRepository implements DirectRepository {

    private Environment dbEnvironment = null;
    private Database db = null;
    private SecondaryDatabase secondaryDb = null;

    static final byte[] HANDLE_DATA = {};

    public BdbjeRepository(File base) throws InternalException {
        this(base, Durability.COMMIT_NO_SYNC);
    }
    
    public BdbjeRepository(File base, Durability durability) throws InternalException {
        this(base,envConfigForDurability(durability));
    }
    
    private static EnvironmentConfig envConfigForDurability(Durability durability) {
        EnvironmentConfig envConfig = new EnvironmentConfig();
        envConfig.setDurability(durability);
        return envConfig;
    }

    public BdbjeRepository(File base, EnvironmentConfig envConfig) throws InternalException {
        File dbDir = new File(base,"bdbje");
        if(!dbDir.exists()) {
            dbDir.mkdirs();
        }
        try {
            if(envConfig==null) envConfig = new EnvironmentConfig();
            envConfig.setAllowCreate(true);
            envConfig.setSharedCache(true);
            envConfig.setTransactional(true); 
            envConfig.setCachePercent(10);
            dbEnvironment = new Environment(dbDir, envConfig);
            DatabaseConfig dbConfig = new DatabaseConfig();
            dbConfig.setAllowCreate(true);
            dbConfig.setTransactional(true); 
            db = dbEnvironment.openDatabase(null, "repositoryDatabase", dbConfig);

            NameValueKeyCreator nameValueKeyCreator = new NameValueKeyCreator();
            SecondaryConfig secondaryConfig = new SecondaryConfig();
            secondaryConfig.setAllowCreate(true);
            secondaryConfig.setSortedDuplicates(true);
            secondaryConfig.setTransactional(true); 
            secondaryConfig.setKeyCreator(nameValueKeyCreator);
            secondaryDb = dbEnvironment.openSecondaryDatabase(null, "index", db,  secondaryConfig);
        } catch (DatabaseException dbe) {
            throw new InternalException(dbe);
        }       
    }

    //test method
    void purgeAllData() {
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, null);
        try {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            while (cursor.getNext(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
                cursor.delete();
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
            e.printStackTrace();
        }    	
    }

    //test method
    void printAllData() {
        Cursor cursor = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        while (cursor.getNext(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            byte[] keyBytes = key.getData();
            byte[] dataBytes = data.getData();
            System.out.println("Key: "+ KeyUtil.keyToString(keyBytes) + "  value: " + BdbUtil.decode(dataBytes));
        }
        cursor.close();
    }    

    public String toString() {
        Cursor cursor = db.openCursor(null, null);
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        String result = "";
        while (cursor.getNext(key, data, LockMode.DEFAULT) == OperationStatus.SUCCESS) {
            byte[] keyBytes = key.getData();
            byte[] dataBytes = data.getData();
            result +="Key: "+  KeyUtil.keyToString(keyBytes) + "  value: " + BdbUtil.decode(dataBytes) + " /n";
        }
        cursor.close();
        return result;
    } 

    @Override
    public boolean verifyDigitalObject(String handle) throws RepositoryException {
        DatabaseEntry key = new DatabaseEntry(KeyUtil.getHandleKey(handle));
        DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);
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
    public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        if(handle==null) throw new UnsupportedOperationException();
        byte[] handleBytes = KeyUtil.getHandleKey(handle);
        OperationStatus status;
        try {
            status = db.putNoOverwrite(null, new DatabaseEntry(handleBytes), new DatabaseEntry(HANDLE_DATA));
        } catch (Exception e) {
            throw new InternalException(e);
        }
        if(status==OperationStatus.KEYEXIST) throw new CreationException();
        else if(status!=OperationStatus.SUCCESS) throw new InternalException("Unexpected status " + status.toString());
        return new BdbjeDigitalObject(this, dbEnvironment, db, handle, handleBytes);
    }

    @Override
    public DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
        byte[] handleBytes = KeyUtil.getHandleKey(handle);
        OperationStatus status;
        try {
            status = db.putNoOverwrite(null, new DatabaseEntry(handleBytes), new DatabaseEntry(HANDLE_DATA));
        } catch (Exception e) {
            throw new InternalException(e);
        }
        if(status!=OperationStatus.SUCCESS && status!=OperationStatus.KEYEXIST) throw new InternalException("Unexpected status " + status.toString());
        return new BdbjeDigitalObject(this, dbEnvironment, db, handle, handleBytes);
    }

    @Override
    public DigitalObject getDigitalObject(String handle) throws RepositoryException {
        byte[] handleBytes = KeyUtil.getHandleKey(handle);
        DatabaseEntry key = new DatabaseEntry(handleBytes);
        DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);
        OperationStatus status = db.get(null, key, data, LockMode.READ_UNCOMMITTED);
        if (status == OperationStatus.SUCCESS) {
            return new BdbjeDigitalObject(this, dbEnvironment, db, handle, handleBytes);
        } else if(status == OperationStatus.NOTFOUND) {
            return null;
        } else {
            throw new InternalException("Unexpected status " + status.toString());
        }
    }

    List<String> getHandles() throws RepositoryException {
        List<String> result = new ArrayList<String>();
        DatabaseEntry key = new DatabaseEntry();
        DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);
        Cursor cursor = db.openCursor(null, null);
        try {
            while(cursor.getNext(key, data, LockMode.READ_UNCOMMITTED) == OperationStatus.SUCCESS) {
                byte[] keyBytes = key.getData();
                if (KeyUtil.isHandleKey(keyBytes)) {
                    String name = KeyUtil.extractHandle(keyBytes);
                    result.add(name);
                } 
            }
        } catch (Exception e) {
            throw new InternalException(e);
        } finally {
            cursor.close();
        }
        return result;		
    }

    @Override
    public CloseableIterator<String> listHandles() throws RepositoryException {
        final Cursor cursor = db.openCursor(null, null);
        return new AbstractCloseableIterator<String>() {
            @Override
            protected String computeNext() {
                DatabaseEntry key = new DatabaseEntry();
                DatabaseEntry data = new DatabaseEntry();
                data.setPartial(0, 0, true);
                while(cursor.getNext(key, data, LockMode.READ_UNCOMMITTED) == OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (KeyUtil.isHandleKey(keyBytes)) {
                        return KeyUtil.extractHandle(keyBytes);
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
    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        final Cursor cursor = db.openCursor(null, null);
        return new AbstractCloseableIterator<DigitalObject>() {
            DatabaseEntry key = new DatabaseEntry();
            DatabaseEntry data = new DatabaseEntry();
            {
                data.setPartial(0, 0, true);
            }
            @Override
            protected DigitalObject computeNext() {
                while(cursor.getNext(key, data, LockMode.READ_UNCOMMITTED) == OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (KeyUtil.isHandleKey(keyBytes)) {
                        String handle = KeyUtil.extractHandle(keyBytes);
                        try {
                            return new BdbjeDigitalObject(BdbjeRepository.this, dbEnvironment, db, handle, keyBytes);
                        } catch (RepositoryException e) {
                            throw new UncheckedRepositoryException(e);
                        }
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
    public CloseableIterator<DigitalObject> search(Query q) throws RepositoryException {
        return q.accept(new AbstractQueryVisitorForSearch.SearchObjects(this) {
            @Override
            public CloseableIterator<DigitalObject> visitAttributeQuery(AttributeQuery query) throws RepositoryException {
                final SecondaryCursor cursor = secondaryDb.openCursor(null, null);
                String attributeName = query.getAttributeName();
                String attributeValue = query.getValue();
                final byte[] secondaryKey = NameValueKeyCreator.generateKey(attributeName,attributeValue);
                return new AbstractCloseableIterator<DigitalObject>() {
                    DatabaseEntry searchKey = new DatabaseEntry(secondaryKey);
                    DatabaseEntry primaryKey = new DatabaseEntry();
                    DatabaseEntry primaryData = new DatabaseEntry();
                    OperationStatus status;
                    {
                        primaryData.setPartial(0, 0, true);
                        status = cursor.getSearchKey(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
                    }
                    @Override
                    protected DigitalObject computeNext() {
                        if (status == OperationStatus.SUCCESS) {
                            byte[] primaryKeyBytes = primaryKey.getData();
                            String handle = KeyUtil.extractHandle(primaryKeyBytes);
                            DigitalObject res;
                            try {
                                res = new BdbjeDigitalObject(BdbjeRepository.this, dbEnvironment, db, handle, KeyUtil.extractHandleBytes(primaryKeyBytes));
                            } catch (RepositoryException e) {
                                throw new UncheckedRepositoryException(e);
                            }
                            status = cursor.getNextDup(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
                            return res;
                        }
                        else return null;
                    }
                    @Override
                    protected void closeOnlyOnce() {
                        cursor.close();
                    }
                };
            }
            @Override
            public CloseableIterator<DigitalObject> visitElementAttributeQuery(ElementAttributeQuery query) throws RepositoryException {
                return searchElementAttributeQuery(query);
            }
        });
    }

    private CloseableIterator<DigitalObject> searchElementAttributeQuery(ElementAttributeQuery query) {
        final SecondaryCursor cursor = secondaryDb.openCursor(null, null);
        String elementName = query.getElementName();
        String elementAttributeName = query.getAttributeName();
        String elementAttributeValue = query.getValue();
        final byte[] secondaryKey = NameValueKeyCreator.generateElementAttributKey(elementName, elementAttributeName, elementAttributeValue);
        return new AbstractCloseableIterator<DigitalObject>() {
            DatabaseEntry searchKey = new DatabaseEntry(secondaryKey);
            DatabaseEntry primaryKey = new DatabaseEntry();
            DatabaseEntry primaryData = new DatabaseEntry();
            OperationStatus status;
            {
                primaryData.setPartial(0, 0, true);
                status = cursor.getSearchKey(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
            }
            @Override
            protected DigitalObject computeNext() {
                if (status == OperationStatus.SUCCESS) {
                    byte[] primaryKeyBytes = primaryKey.getData();
                    String handle = KeyUtil.extractHandle(primaryKeyBytes);
                    DigitalObject res;
                    try {
                        res = new BdbjeDigitalObject(BdbjeRepository.this, dbEnvironment, db, handle, KeyUtil.extractHandleBytes(primaryKeyBytes));
                    } catch (RepositoryException e) {
                        throw new UncheckedRepositoryException(e);
                    }
                    status = cursor.getNextDup(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
                    return res;
                }
                else return null;
            }
            @Override
            protected void closeOnlyOnce() {
                cursor.close();
            }
        };
    }

    private CloseableIterator<String> searchHandlesElementAttributeQuery(ElementAttributeQuery query) throws RepositoryException { 
        final SecondaryCursor cursor = secondaryDb.openCursor(null, null);
        String elementName = query.getElementName();
        String elementAttributeName = query.getAttributeName();
        String elementAttributeValue = query.getValue();
        final byte[] secondaryKey = NameValueKeyCreator.generateElementAttributKey(elementName, elementAttributeName, elementAttributeValue);
        return new AbstractCloseableIterator<String>() {
            DatabaseEntry searchKey = new DatabaseEntry(secondaryKey);
            DatabaseEntry primaryKey = new DatabaseEntry();
            DatabaseEntry primaryData = new DatabaseEntry();
            OperationStatus status;
            {
                primaryData.setPartial(0, 0, true);
                status = cursor.getSearchKey(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
            }
            @Override
            protected String computeNext() {
                if (status == OperationStatus.SUCCESS) {
                    byte[] primaryKeyBytes = primaryKey.getData();
                    String handle = KeyUtil.extractHandle(primaryKeyBytes);
                    status = cursor.getNextDup(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
                    return handle;
                }
                else return null;
            }
            @Override
            protected void closeOnlyOnce() {
                cursor.close();
            }
        };
    }

    @Override   
    public CloseableIterator<String> searchHandles(Query q) throws RepositoryException {
        return q.accept(new AbstractQueryVisitorForSearch.SearchHandles(this) {
            @Override
            public CloseableIterator<String> visitAttributeQuery(AttributeQuery query) throws RepositoryException {
                final SecondaryCursor cursor = secondaryDb.openCursor(null, null);
                String attributeName = query.getAttributeName();
                String attributeValue = query.getValue();
                final byte[] secondaryKey = NameValueKeyCreator.generateKey(attributeName,attributeValue);
                return new AbstractCloseableIterator<String>() {
                    DatabaseEntry searchKey = new DatabaseEntry(secondaryKey);
                    DatabaseEntry primaryKey = new DatabaseEntry();
                    DatabaseEntry primaryData = new DatabaseEntry();
                    OperationStatus status;
                    {
                        primaryData.setPartial(0, 0, true);
                        status = cursor.getSearchKey(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
                    }
                    @Override
                    protected String computeNext() {
                        if (status == OperationStatus.SUCCESS) {
                            byte[] primaryKeyBytes = primaryKey.getData();
                            String handle = KeyUtil.extractHandle(primaryKeyBytes);
                            status = cursor.getNextDup(searchKey, primaryKey, primaryData, LockMode.READ_UNCOMMITTED);
                            return handle;
                        }
                        else return null;
                    }
                    @Override
                    protected void closeOnlyOnce() {
                        cursor.close();
                    }
                };
            }
            @Override
            public CloseableIterator<String> visitElementAttributeQuery(ElementAttributeQuery query) throws RepositoryException {
                return searchHandlesElementAttributeQuery(query);
            }
        });
    }   

    //	private List<DigitalObject> superSlowHackSearch(Query query) throws RepositoryException {
    //		List<DigitalObject> results = new ArrayList<DigitalObject>();
    //		CloseableIterator<DigitalObject> iter = listObjects();
    //		while(iter.hasNext()) {
    //			DigitalObject current = iter.next();
    //			if (objectMatchesQuery(current, query)) {
    //				results.add(current);
    //			}
    //		}
    //		iter.close();
    //		return results;
    //	}
    //	
    //	private static boolean objectHasTerm(DigitalObject dobj, Term term) throws RepositoryException {
    //		String field = term.getField();
    //		String text = term.getText();
    //		String value = dobj.getAttribute(field);
    //		if (value != null) {
    //			if (value.equals(text)) { 
    //				return true;
    //			}
    //		}
    //		return false;
    //	}
    //	
    //	private static boolean objectMatchesQuery(DigitalObject dobj, TermQuery query) throws RepositoryException {
    //		return objectHasTerm(dobj, query.getTerm());
    //	}
    //	
    //	private static boolean objectMatchesQuery(DigitalObject dobj, Query query) throws RepositoryException {
    //		if (query instanceof TermQuery) {
    //			return objectMatchesQuery(dobj, (TermQuery)query);
    //		}
    //		throw new UnsupportedOperationException();
    //	}	

    @Override
    public void close() {
        try {
            if (secondaryDb != null) {
                secondaryDb.close();
            }
            if (db != null) {
                db.close();
            }
            if (dbEnvironment != null) {
                dbEnvironment.close();
            } 
        } catch (DatabaseException dbe) {
            System.err.println("Error closing environment" + dbe.toString());
        }
    }

    @Override
    public Map<String,String> getAttributes(String handle,String elementName) throws RepositoryException {
        Map<String, String> result = new HashMap<String, String>();
        CloseableIterator<Entry<String,String>> iter = listAttributes(handle,elementName);
        try {
            while(iter.hasNext()) {
                Entry<String,String> entry = iter.next();
                result.put(entry.getKey(),entry.getValue());
            }
        }
        catch(UncheckedRepositoryException e) {
            e.throwCause();
        }
        finally {
            iter.close();
        }
        return result;
    }

    @Override
    public CloseableIterator<Entry<String,String>> listAttributes(String handle, String elementName) throws RepositoryException {
        byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
        final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
        final DatabaseEntry data = new DatabaseEntry();
        final Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        final byte[] keyPrefix;
        try {
            OperationStatus status = cursor.getSearchKey(key, data, null); //moves cursor to the record at the handle 
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            if(elementName!=null) {
                byte[] elementKey = KeyUtil.getElementKey(handleKeyBytes, elementName);
                key.setData(elementKey);
                status = cursor.getSearchKey(key, data, null); //moves cursor to the record at the handle 
                if(status!=OperationStatus.SUCCESS) throw new NoSuchDataElementException(handle,elementName);
                keyPrefix = elementKey;
                elementKey[handleKeyBytes.length] = KeyUtil.ELEMENT_ATTRIBUTE_DELIMETER;
            }
            else {
                keyPrefix = BdbUtil.concatBytes(handleKeyBytes,KeyUtil.ATTRIBUTE_DELIMETER_ARRAY);
            }
            key.setData(keyPrefix);
        }
        catch(RuntimeException e) {
            cursor.close();
            throw e;
        }
        return new AbstractCloseableIterator<Map.Entry<String,String>>() {
            OperationStatus status = cursor.getSearchKeyRange(key, data, null); //moves cursor to the record at the handle 
            @Override
            protected Entry<String,String> computeNext() {
                if(status==null) status = cursor.getNext(key,data,null);
                if(status==OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (BdbUtil.startsWith(keyBytes,keyPrefix)) {
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
    public String getAttribute(String handle, String elementName, String name) throws RepositoryException {
        byte[] keyBytes;
        if(elementName==null) {
            keyBytes = KeyUtil.getAttributeKey(handle, name);
        }
        else {
            keyBytes = KeyUtil.getElementAttributeKey(handle,elementName,name);
        }
        Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        try {
            DatabaseEntry key = new DatabaseEntry(keyBytes);
            DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = cursor.getSearchKey(key, data, null);      
            if (status == OperationStatus.SUCCESS) {
                return BdbUtil.decode(data.getData());
            } else {
                key.setData(keyBytes,0,4+2*handle.length());
                status = cursor.getSearchKey(key,data,null);
                if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
                if(elementName!=null) {
                    keyBytes[4+2*handle.length()] = KeyUtil.ELEMENT_DELIMETER;
                    key.setData(keyBytes,0,9+2*handle.length()+2*elementName.length());
                    status = cursor.getSearchKey(key,data,null);
                    if(status!=OperationStatus.SUCCESS) throw new NoSuchDataElementException(handle,elementName);
                }
                return null;
            }
        }
        finally {
            cursor.close();
        }
    }

    @Override
    public void setAttributes(String handle, String elementName, Map<String,String> attributes) throws RepositoryException {
        final byte[] keyPrefix;
        if(elementName!=null) {
            keyPrefix = KeyUtil.getElementAttributeSearchKey(handle,elementName);
        }
        else {
            keyPrefix = KeyUtil.getAttributeSearchKey(handle);
        }
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, CursorConfig.READ_COMMITTED);
        try {
            byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
            final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
            final DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = cursor.getSearchKey(key,data,null);
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            if(elementName!=null) {
                key.setData(KeyUtil.getElementKey(handleKeyBytes,elementName));
                status = cursor.getSearchKey(key,data,null);
                if(status!=OperationStatus.SUCCESS) throw new NoSuchDataElementException(handle,elementName);
            }
            for (Entry<String,String> entry : attributes.entrySet()) {
                String name = entry.getKey();
                String value = entry.getValue();
                if (value == null) {
                    deleteAttributeWithCursor(keyPrefix,cursor,name);
                } else {
                    setAttributeWithCursor(keyPrefix, cursor, name, value);
                }
            }
            setModifiedAttribute(handleKeyBytes, cursor);
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
    }

    private static void setAttributeWithCursor(byte[] keyPrefix, Cursor cursor, String name, String value) throws RepositoryException {
        byte[] keyBytes = BdbUtil.concatBytes(keyPrefix,BdbUtil.encodeAsPascalString(name));
        byte[] dataBytes = BdbUtil.encode(value);
        try {
            cursor.put(new DatabaseEntry(keyBytes), new DatabaseEntry(dataBytes));
        } catch (Exception e) {
            throw new InternalException(e);
        }       
    }

    private static void deleteAttributeWithCursor(byte[] keyPrefix, Cursor cursor,String name) throws RepositoryException {
        byte[] keyBytes = BdbUtil.concatBytes(keyPrefix,BdbUtil.encodeAsPascalString(name));
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
    public void setAttribute(String handle, String elementName, String name, String value) throws RepositoryException {
        if(value==null) {
            deleteAttribute(handle,elementName,name);
            return;
        }
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, CursorConfig.READ_COMMITTED);
        try {
            byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
            final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
            final DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = cursor.getSearchKey(key,data,null);
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            byte[] keyPrefix;
            if(elementName!=null) {
                byte[] elementKey = KeyUtil.getElementKey(handleKeyBytes,elementName);
                key.setData(elementKey);
                status = cursor.getSearchKey(key,data,null);
                if(status!=OperationStatus.SUCCESS) throw new NoSuchDataElementException(handle,elementName);
                keyPrefix = elementKey;
                keyPrefix[handleKeyBytes.length] = KeyUtil.ELEMENT_ATTRIBUTE_DELIMETER;
            }
            else {
                keyPrefix = BdbUtil.concatBytes(handleKeyBytes,KeyUtil.ATTRIBUTE_DELIMETER_ARRAY);
            }
            key.setData(BdbUtil.concatBytes(keyPrefix,BdbUtil.encodeAsPascalString(name)));
            data.setData(BdbUtil.encode(value));
            cursor.put(key,data);
            setModifiedAttribute(handleKeyBytes, cursor);
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
    }
    
    private static void setModifiedAttribute(byte[] handleKeyBytes, Cursor cursor) throws RepositoryException {
        byte[] keyPrefix = BdbUtil.concatBytes(handleKeyBytes,KeyUtil.ATTRIBUTE_DELIMETER_ARRAY);
        String now = String.valueOf(System.currentTimeMillis());
        setAttributeWithCursor(keyPrefix, cursor, Repositories.INTERNAL_MODIFIED, now);
    }
    
    @Override
    public void deleteAttributes(String handle, String elementName, List<String> names) throws RepositoryException {
        final byte[] keyPrefix;
        if(elementName!=null) {
            keyPrefix = KeyUtil.getElementAttributeSearchKey(handle,elementName);
        }
        else {
            keyPrefix = KeyUtil.getAttributeSearchKey(handle);
        }
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, CursorConfig.READ_COMMITTED);
        try {
            byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
            final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
            final DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = cursor.getSearchKey(key,data,null);
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            if(elementName!=null) {
                key.setData(KeyUtil.getElementKey(handleKeyBytes,elementName));
                status = cursor.getSearchKey(key,data,null);
                if(status!=OperationStatus.SUCCESS) throw new NoSuchDataElementException(handle,elementName);
            }
            for (String name : names) {
                deleteAttributeWithCursor(keyPrefix,cursor,name);
            }
            setModifiedAttribute(handleKeyBytes, cursor);
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
    }

    @Override
    public void deleteAttribute(String handle, String elementName, String name) throws RepositoryException {
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, CursorConfig.READ_COMMITTED);
        try {
            byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
            final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
            final DatabaseEntry data = new DatabaseEntry();
            OperationStatus status = cursor.getSearchKey(key,data,null);
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            byte[] keyPrefix;
            if(elementName!=null) {
                byte[] elementKey = KeyUtil.getElementKey(handleKeyBytes,elementName);
                key.setData(elementKey);
                status = cursor.getSearchKey(key,data,null);
                if(status!=OperationStatus.SUCCESS) throw new NoSuchDataElementException(handle,elementName);
                keyPrefix = elementKey;
                keyPrefix[handleKeyBytes.length] = KeyUtil.ELEMENT_ATTRIBUTE_DELIMETER;
            }
            else {
                keyPrefix = BdbUtil.concatBytes(handleKeyBytes,KeyUtil.ATTRIBUTE_DELIMETER_ARRAY);
            }
            key.setData(BdbUtil.concatBytes(keyPrefix,BdbUtil.encodeAsPascalString(name)));
            data.setPartial(0,0,true);
            if(OperationStatus.SUCCESS == cursor.getSearchKey(key,data,LockMode.RMW)) cursor.delete();
            setModifiedAttribute(handleKeyBytes, cursor);
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
    }

    @Override
    public boolean verifyDataElement(String handle, String name) throws RepositoryException {
        Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        try {
            byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
            final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
            final DatabaseEntry data = new DatabaseEntry();
            data.setPartial(0,0,true);
            OperationStatus status = cursor.getSearchKey(key,data,null);
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            byte[] elementKey = KeyUtil.getElementKey(handleKeyBytes,name);
            key.setData(elementKey);
            status = cursor.getSearchKey(key,data,null);
            return status==OperationStatus.SUCCESS;
        }
        finally {
            cursor.close();
            
        }
    }

    @Override
    public void createDataElement(String handle, String name) throws CreationException, RepositoryException {
        Transaction dbtxn = dbEnvironment.beginTransaction(null, null);
        Cursor cursor = db.openCursor(dbtxn, CursorConfig.READ_COMMITTED);
        try {
            byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
            final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
            final DatabaseEntry data = new DatabaseEntry();
            data.setPartial(0,0,true);
            OperationStatus status = cursor.getSearchKey(key,data,null);
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            byte[] elementKey = KeyUtil.getElementKey(handleKeyBytes,name);
            key.setData(elementKey);
            data.setData(BdbjeDigitalObject.ELEMENT_DATA);
            status = db.putNoOverwrite(null, key, data);
            if(status==OperationStatus.KEYEXIST) throw new CreationException();
            else if(status!=OperationStatus.SUCCESS) throw new InternalException("Unexpected status " + status.toString());

            BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),handle,name,elementKey,true);
            setModifiedAttribute(handleKeyBytes, cursor);
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
    public void deleteDataElement(String handle, String name) throws RepositoryException {
        byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
        byte[] elementKeyBytes = KeyUtil.getElementKey(handleKeyBytes,name);
        byte[] elementAttributeStartKeyBytes = elementKeyBytes.clone();
        elementAttributeStartKeyBytes[handleKeyBytes.length] = KeyUtil.ELEMENT_ATTRIBUTE_DELIMETER;
        DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
        DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0,0,true);
        TransactionConfig config = new TransactionConfig();
        config.setSerializableIsolation(true);
        Transaction dbtxn = dbEnvironment.beginTransaction(null, config);
        Cursor cursor = db.openCursor(dbtxn, CursorConfig.READ_COMMITTED);
        try {
            OperationStatus status = cursor.getSearchKey(key, data, null);
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            key.setData(elementKeyBytes);
            status = cursor.getSearchKey(key, data, LockMode.RMW); //moves cursor to the record at the start of this element 
            if(status == OperationStatus.SUCCESS) cursor.delete();
            else {
                throw new NoSuchDataElementException(handle,name);
            }
            key = new DatabaseEntry(elementAttributeStartKeyBytes);
            status = cursor.getSearchKeyRange(key, data, LockMode.RMW); //moves cursor to the record at the start of this element 
            while (status == OperationStatus.SUCCESS) {
                byte[] keyBytes = key.getData();
                if(BdbUtil.startsWith(keyBytes,elementAttributeStartKeyBytes)) {
                    cursor.delete();
                } else {
                    break;
                }
                status = cursor.getNext(key, data, LockMode.RMW);
            }
            setModifiedAttribute(handleKeyBytes, cursor);
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
        BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),handle,name,elementKeyBytes,false).delete();
    }

    @Override
    public CloseableIterator<String> listDataElementNames(String handle) throws RepositoryException {
        byte[] handleKeyBytes = KeyUtil.getHandleKey(handle);
        final DatabaseEntry key = new DatabaseEntry(handleKeyBytes);
        final DatabaseEntry data = new DatabaseEntry();
        data.setPartial(0, 0, true);
        final Cursor cursor = db.openCursor(null, CursorConfig.READ_COMMITTED);
        final byte[] attributePrefix;
        final byte[] elementPrefix;
        try {
            OperationStatus status = cursor.getSearchKey(key, data, null); //moves cursor to the record at the handle
            if(status!=OperationStatus.SUCCESS) throw new NoSuchDigitalObjectException(handle);
            attributePrefix = BdbUtil.concatBytes(handleKeyBytes,KeyUtil.ATTRIBUTE_DELIMETER_ARRAY);
            elementPrefix = BdbUtil.concatBytes(handleKeyBytes,KeyUtil.ELEMENT_DELIMETER_ARRAY);
        }
        catch(RuntimeException e) {
            cursor.close();
            throw e;
        }
        return new AbstractCloseableIterator<String>() {
            @Override
            protected String computeNext() {
                while(cursor.getNext(key, data, null) == OperationStatus.SUCCESS) {
                    byte[] keyBytes = key.getData();
                    if (BdbUtil.startsWith(keyBytes,attributePrefix)) {
                        continue;
                    }
                    else if(BdbUtil.startsWith(keyBytes,elementPrefix)) {
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

    @Override
    public InputStream read(String handle, String elementName) throws RepositoryException {
        if(!verifyDataElement(handle,elementName)) throw new NoSuchDataElementException(handle,elementName);
        try {
            return new FileInputStream(BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),handle,elementName,null,false));
        }
        catch(FileNotFoundException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public long write(String handle, String elementName, InputStream data, boolean append) throws IOException, RepositoryException {
        if(!verifyDataElement(handle,elementName)) throw new NoSuchDataElementException(handle,elementName);
        File file = BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),handle,elementName,null,false);
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
        File file = BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),handle,elementName,null,false);
        return file.length();
    }

    @Override
    public File getFile(String handle, String elementName) throws RepositoryException {
        if(!verifyDataElement(handle,elementName)) throw new NoSuchDataElementException(handle,elementName);
        return BdbjeDigitalObject.getElementFile(dbEnvironment.getHome(),handle,elementName,null,false);
    }
}
