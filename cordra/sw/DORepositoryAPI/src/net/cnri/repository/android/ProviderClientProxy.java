/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.io.FileNotFoundException;
import java.util.ArrayList;

import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.OperationApplicationException;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;

public class ProviderClientProxy {
    private final String authority;
    private final ContentProviderClient delegate;

    public ProviderClientProxy(ContentProviderClient delegate,String authority) throws RepositoryException {
        if(delegate==null) throw new InternalException("Could not find Android repo content provider.  Please ensure that the Android repository is installed.");
        this.delegate = delegate;
        this.authority = authority;
    }

    public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) throws OperationApplicationException, RepositoryException {
        try {
            return delegate.applyBatch(operations);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public int bulkInsert(Uri url, ContentValues[] initialValues) throws RepositoryException {
        try {
            return delegate.bulkInsert(url,initialValues);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public int delete(Uri url, String selection, String[] selectionArgs) throws RepositoryException {
        try {
            return delegate.delete(url,selection,selectionArgs);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public ContentProvider getLocalContentProvider() {
        return delegate.getLocalContentProvider();
    }

    public String getType(Uri url) throws RepositoryException {
        try {
            return delegate.getType(url);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public Uri insert(Uri url, ContentValues initialValues) throws RepositoryException {
        try {
            return delegate.insert(url,initialValues);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public AssetFileDescriptor openAssetFile(Uri url, String mode) throws FileNotFoundException, RepositoryException {
        try {
            return delegate.openAssetFile(url,mode);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public ParcelFileDescriptor openFile(Uri url, String mode) throws FileNotFoundException, RepositoryException {
        try {
            return delegate.openFile(url,mode);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public Cursor query(Uri url, String[] projection, String selection, String[] selectionArgs, String sortOrder) throws RepositoryException {
        try {
            return delegate.query(url,projection,selection,selectionArgs,sortOrder);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }

    public boolean release() {
        return delegate.release();
    }

    public int update(Uri url, ContentValues values, String selection, String[] selectionArgs) throws RepositoryException {
        try {
            return delegate.update(url,values,selection,selectionArgs);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }
    
    public Bundle call(String method, String arg, Bundle extras) throws RepositoryException {
        if(extras==null) extras = Bundle.EMPTY;
//        Cursor cursor = null;
//        try {
//            cursor = delegate.query(this.callUri,null,method,null,arg);
//            if(cursor==null) return Bundle.EMPTY;
//            return cursor.respond(extras);
//        }
//        catch(RemoteException e) {
//            throw new InternalException(e);
//        }
//        catch(IllegalStateException e) {
//            throw ExceptionSerializer.deserialize(e.getMessage());
//        }
//        finally {
//            if(cursor!=null) cursor.close();
//        }
        Uri.Builder uriBuilder = new Uri.Builder().scheme("content").authority(authority).appendPath("call");
        uriBuilder.appendPath(method).appendEncodedPath(Util.serializeBundleAsUriComponent(extras));
        if(arg!=null) uriBuilder.query(arg);
        Uri uri = uriBuilder.build();
        try {
            String type = delegate.getType(uri);
            if(type==null) return Bundle.EMPTY;
            return Util.deserializeBundleFromString(type);
        }
        catch(RemoteException e) {
            throw new InternalException(e);
        }
        catch(IllegalStateException e) {
            throw ExceptionSerializer.deserialize(e.getMessage());
        }
    }
}
