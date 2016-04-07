/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.layered.LayeredRepository;
import net.cnri.repository.layered.SupportsFastCopyForLayeredRepo;
import net.cnri.repository.memory.MemoryDigitalObject;

public class EmbeddedDigitalObject extends AttributeHolder implements DigitalObject, SupportsFastCopyForLayeredRepo {
    private final EmbeddedRepository repository;
    private final Uri uri;
    private final Uri elUri;
    private final String handle;
    
    public EmbeddedDigitalObject(EmbeddedRepository repository, Uri uri, String handle) {
        super(Uri.withAppendedPath(uri,Provider.AttributeColumns.ATTRIBUTE_PATH_SEGMENT), 
                Provider.AttributeColumns.ATTRIBUTE_WHERE_NAME);
        this.repository = repository;
        this.uri = uri;
        this.elUri = Uri.withAppendedPath(uri,Provider.ElementColumns.ELEMENT_PATH_SEGMENT);
        this.handle = handle;
    }

    public void setContext(Context context) throws RepositoryException {
        repository.setContext(context);
    }

    ProviderClientProxy getClient() {
        return repository.getClient();
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
        getClient().delete(uri,null,null);
    }

    @Override
    public boolean verifyDataElement(String name) throws RepositoryException {
        if(name==null) return false;
        Cursor cursor = getClient().query(elUri,
                Provider.ElementColumns.ID_PROJECTION,Provider.ElementColumns.ELEMENT_WHERE_NAME,new String[] { name },null);
        try {
            return cursor.moveToFirst();
        }
        finally {
            cursor.close();
        }
    }

    @Override
    public DataElement createDataElement(String name) throws CreationException, RepositoryException {
        if(name==null) throw new NullPointerException();
        if(verifyDataElement(name)) throw new CreationException();
        ContentValues values = new ContentValues();
        values.put(Provider.ElementColumns.NAME,name);
        Uri elementUri = getClient().insert(elUri,values);
        return new EmbeddedDataElement(this,elementUri,name);
    }
    
    @Override
    public DataElement getOrCreateDataElement(String name) throws RepositoryException {
        DataElement res = getDataElement(name);
        if(res==null) return createDataElement(name);
        return res;
    }

    @Override
    public DataElement getDataElement(String name) throws RepositoryException {
        if(name==null) return null;
        Cursor cursor = getClient().query(elUri,
                Provider.ElementColumns.ID_PROJECTION,Provider.ElementColumns.ELEMENT_WHERE_NAME,new String[] { name },null);
        try {
            if(!cursor.moveToFirst()) return null;
            return getDataElementWithIdAndName(cursor.getString(0),name);
        }
        finally {
            cursor.close();
        }
    }
    
    private DataElement getDataElementWithIdAndName(String id, String name) {
        Uri elementUri = Uri.withAppendedPath(elUri,id);
        return new EmbeddedDataElement(this,elementUri,name);
    }

    @Override
    public void deleteDataElement(String name) throws RepositoryException {
        getClient().delete(elUri,Provider.ElementColumns.ELEMENT_WHERE_NAME,new String[] { name });
    }

    @Override
    public List<String> getDataElementNames() throws RepositoryException {
        List<String> result = new ArrayList<String>();
        CloseableIterator<String> iter = listDataElementNames();
        try {
            while(iter.hasNext()) {
                result.add(iter.next());
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
    public List<DataElement> getDataElements() throws RepositoryException {
        List<DataElement> result = new ArrayList<DataElement>();
        CloseableIterator<DataElement> iter = listDataElements();
        try {
            while(iter.hasNext()) {
                result.add(iter.next());
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
    public CloseableIterator<String> listDataElementNames() throws RepositoryException {
        Cursor cursor = getClient().query(elUri,Provider.ElementColumns.ID_NAME_PROJECTION,null,null,null);
        return new CursorIteratorImpl<String>(cursor,new CursorIteratorImpl.OfCursor<String>() {
           public String ofCursor(Cursor c) {
               return c.getString(1);
           }
        });
    }

    @Override
    public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
        Cursor cursor = getClient().query(elUri,Provider.ElementColumns.ID_NAME_PROJECTION,null,null,null);
        return new CursorIteratorImpl<DataElement>(cursor,new CursorIteratorImpl.OfCursor<DataElement>() {
           public DataElement ofCursor(Cursor c) {
               Uri elementUri = Uri.withAppendedPath(elUri,c.getString(0));
               return new EmbeddedDataElement(EmbeddedDigitalObject.this,elementUri,c.getString(1));
           }
        });
    }

    public void copyTo(DigitalObject dobj) throws RepositoryException, IOException {
        boolean isMemory = dobj instanceof MemoryDigitalObject;
        MemoryDigitalObject memdobj;
        if(isMemory) memdobj = (MemoryDigitalObject)dobj;
        else memdobj = new MemoryDigitalObject(null,handle);
        
        Cursor cursor = getClient().query(uri,Provider.ObjectColumns.ENTIRE_OBJECT_PROJECTION,null,null,null);
        try {
            if(cursor.moveToFirst()) do {
                if(cursor.isNull(0)) {
                    memdobj.setAttribute(cursor.getString(1),cursor.getString(2));
                }
                else if(cursor.isNull(1)) {
                    DataElement sourceEl = getDataElementWithIdAndName(cursor.getString(3),cursor.getString(0));
                    DataElement targetEl = memdobj.createDataElement(cursor.getString(0));
                    LayeredRepository.copyDataOrSetAttributeForMissing(sourceEl,targetEl);
                }
                else {
                    memdobj.getDataElement(cursor.getString(0)).setAttribute(cursor.getString(1),cursor.getString(2));
                }
            } while(cursor.moveToNext());
        }
        finally {
            cursor.close();
        }
        
        if(!isMemory) {
            LayeredRepository.copyForLayeredRepo(memdobj,dobj);
        }
    }

}
