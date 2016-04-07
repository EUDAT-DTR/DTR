/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.util.Collection;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import net.cnri.repository.AbstractRepository;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.android.CursorIteratorExcludingImpl.CursorPredicate;
import net.cnri.repository.layered.SupportsSearchExcluding;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.search.RawQuery;

/**
 * 
 * Implements the Repository interface by writing data to an Android ContentProvider backed by an SQLite database.
 * Allows you to create a Repository on an Android device.
 * 
 */
public class EmbeddedRepository extends AbstractRepository implements Repository, SupportsSearchExcluding {
    private final Uri objectUri;
    private final Uri oldSearchUri;
    private final Uri searchUri;
    Context context;
    ProviderClientProxy client;
    
    private final String authority;

    private static EmbeddedRepository embeddedRepository;

    /**
     * Creates a repository used to access the embedded repository on the Android device.
     * @param context the context of the Android application environment in which the repository will be accessed 
     * @return a new Repository instance
     */
    public static synchronized Repository getEmbeddedRepository(Context context) throws RepositoryException {
        if(embeddedRepository==null) embeddedRepository = new EmbeddedRepository(context,Provider.AUTHORITY);
        return embeddedRepository;
    }

    /**
     * Creates an EmbeddedRepository pointing to a specific content provider specified by its authority.
     * Prefer {@link #getEmbeddedRepository(Context)} to use the default provider. 
     * @param authority
     */
    public EmbeddedRepository(Context context,String authority) throws RepositoryException {
        this.authority = authority;
        this.objectUri = Uri.parse("content://" + authority + "/do");
        this.oldSearchUri = Uri.parse("content://" + authority + "/att?groupByObject=true");
        this.searchUri = Uri.parse("content://" + authority + "/search");
        setContext(context);
    }

    ProviderClientProxy getClient() {
        return client;
    }

    public void setContext(Context context) throws RepositoryException {
        if(this.client!=null) this.client.release();
        if(context==null) {
            this.context = null;
            this.client = null;
        }
        else {
            this.context = context.getApplicationContext();
            if(this.context==null) this.context = context;
            ContentResolver cr = this.context.getContentResolver();
            this.client = new ProviderClientProxy(cr.acquireContentProviderClient(authority),authority);
        }
    }
    
    @Override
    public boolean verifyDigitalObject(String handle) throws RepositoryException {
        if(handle==null) return false;
        Cursor cursor = client.query(objectUri,
                Provider.ObjectColumns.ID_PROJECTION,Provider.ObjectColumns.HANDLE + "=?",new String[] { handle },null);
        try {
            return cursor.moveToFirst();
        }
        finally {
            cursor.close();
        }
    }

    @Override
    public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        ContentValues values = new ContentValues();
        if(handle != null) {
            if(verifyDigitalObject(handle)) {
                throw new CreationException();
            }
            values.put(Provider.ObjectColumns.HANDLE,handle);
        }
        Uri uri = client.insert(objectUri,values);
        handle = net.cnri.repository.util.StringEncoding.decodeUriSafe(uri.getLastPathSegment());
        return new EmbeddedDigitalObject(this,uri,handle);
    }

    @Override
    public DigitalObject getDigitalObject(String handle) throws RepositoryException {
        if(handle==null) return null;
        Cursor cursor = client.query(objectUri,
                Provider.ObjectColumns.ID_PROJECTION,Provider.ObjectColumns.HANDLE + "=?",new String[] { handle },null);
        try {
            if(!cursor.moveToFirst()) return null;
            Uri uri = Uri.withAppendedPath(objectUri,cursor.getString(0));
            return new EmbeddedDigitalObject(this,uri,handle);
        }
        finally {
            cursor.close();
        }
    }

    @Override
    public void deleteDigitalObject(String handle) throws RepositoryException {
        client.delete(objectUri,
                Provider.ObjectColumns.HANDLE + "=?",new String[] { handle });
    }

    @Override
    public CloseableIterator<String> listHandles() throws RepositoryException {
        Cursor cursor = client.query(objectUri,Provider.ObjectColumns.ID_HANDLE_PROJECTION,null,null,null);
        return new CursorIteratorImpl<String>(cursor,new CursorIteratorImpl.OfCursor<String>() {
            public String ofCursor(Cursor c) {
                return c.getString(1);
            }
        });
    }

    @Override
    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        Cursor cursor = client.query(objectUri,Provider.ObjectColumns.ID_HANDLE_PROJECTION,null,null,null);
        return new CursorIteratorImpl<DigitalObject>(cursor,new CursorIteratorImpl.OfCursor<DigitalObject>() {
            public DigitalObject ofCursor(Cursor c) {
                Uri uri = Uri.withAppendedPath(objectUri,c.getString(0));
                return new EmbeddedDigitalObject(EmbeddedRepository.this,uri,c.getString(1));
            }
        });
    }

    private static volatile long lastFailedQuery;
    
    private Cursor queryContentProviderClient(Query query) throws RepositoryException {
        if(lastFailedQuery==0 || System.currentTimeMillis() - lastFailedQuery > 86400000) {
            try {
                Cursor cursor = client.query(searchUri,Provider.AttributeColumns.SEARCH_ATTRIBUTE_PROJECTION,ParcelableQuery.writeQueryToString(query),null,null);
                lastFailedQuery = 0;
                return cursor;
            }
            catch(IllegalArgumentException e) {
                lastFailedQuery = System.currentTimeMillis();
            }
        }        
        String queryString;
        if (query instanceof AttributeQuery) {
            AttributeQuery tQuery = (AttributeQuery) query;
            String attributeName = tQuery.getAttributeName();
            String attributeValue = tQuery.getValue();
            queryString = "att.name='"+attributeName+"' AND att.value='"+attributeValue+"'";
        } else if(query instanceof RawQuery) {
            queryString = ((RawQuery)query).getQueryString();
        } else {
            throw new UnsupportedOperationException();
        }
        return client.query(oldSearchUri,Provider.AttributeColumns.SEARCH_ATTRIBUTE_PROJECTION,queryString,null,null);
    }

    @Override
    public CloseableIterator<DigitalObject> searchExcluding(Query query,final Collection<String> handles) throws RepositoryException {
        //String stuff = "att.name='10740/att/object_type' AND att.value='UsageStats'";
        Cursor cursor = queryContentProviderClient(query);
        //Cursor cursor = context.getContentResolver().query(Provider.AttributeColumns.CONTENT_URI,Provider.AttributeColumns.ATTRIBUTE_PROJECTION,AttributeColumns.ATTRIBUTE_WHERE_NAME,queries,null);
        return new CursorIteratorExcludingImpl<DigitalObject>(cursor,new CursorIteratorImpl.OfCursor<DigitalObject>() {
            public DigitalObject ofCursor(Cursor c) {
                Uri uri = Uri.withAppendedPath(objectUri,c.getString(0));
                return new EmbeddedDigitalObject(EmbeddedRepository.this,uri,c.getString(1));
            }
        }, new CursorPredicate() {
           public boolean apply(Cursor c) {
               return handles.contains(c.getString(1));
           }
        });
    }

	@Override
	public CloseableIterator<DigitalObject> search(Query query) throws RepositoryException {
        Cursor cursor = queryContentProviderClient(query);
        //Cursor cursor = context.getContentResolver().query(Provider.AttributeColumns.CONTENT_URI,Provider.AttributeColumns.ATTRIBUTE_PROJECTION,AttributeColumns.ATTRIBUTE_WHERE_NAME,queries,null);
        return new CursorIteratorImpl<DigitalObject>(cursor,new CursorIteratorImpl.OfCursor<DigitalObject>() {
            public DigitalObject ofCursor(Cursor c) {
                Uri uri = Uri.withAppendedPath(objectUri,c.getString(0));
                return new EmbeddedDigitalObject(EmbeddedRepository.this,uri,c.getString(1));
            }
        });
	}

    @Override
    public CloseableIterator<String> searchHandles(Query query) throws RepositoryException {
        Cursor cursor = queryContentProviderClient(query);
        return new CursorIteratorImpl<String>(cursor,new CursorIteratorImpl.OfCursor<String>() {
            public String ofCursor(Cursor c) {
                return c.getString(1);
            }
        });
    }

    @Override
    public CloseableIterator<String> searchHandlesExcluding(Query query, final Collection<String> handles) throws RepositoryException {
        Cursor cursor = queryContentProviderClient(query);
        return new CursorIteratorExcludingImpl<String>(cursor,new CursorIteratorImpl.OfCursor<String>() {
            public String ofCursor(Cursor c) {
                return c.getString(1);
            }
        }, new CursorPredicate() {
            public boolean apply(Cursor c) {
                return handles.contains(c.getString(1));
            }
         });
    }

    @Override
    public void close() {
        client.release();
    }
    
    public Bundle call(String method, String arg, Bundle extras) throws RepositoryException {
        return client.call(method,arg,extras);
    }
}
