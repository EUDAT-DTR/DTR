/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;

abstract class AttributeHolder {
    private final Uri attUri;
    private final String where;
    private Map<String,String> cachedAtts;
    
    public AttributeHolder(Uri attUri, String where) {
        this.attUri = attUri;
        this.where = where;
    }

    abstract ProviderClientProxy getClient();
    
    public synchronized Map<String,String> getAttributes() throws RepositoryException {
        if(cachedAtts!=null) return cachedAtts;
        Map<String,String> res = new HashMap<String,String>();
        Cursor cursor = getClient().query(attUri,Provider.AttributeColumns.ATTRIBUTE_PROJECTION,null,null,null);
        try {
            if(cursor.moveToFirst()) do {
                res.put(cursor.getString(0),cursor.getString(1));
            } while(cursor.moveToNext());
        }
        finally {
            cursor.close();
        }
        cachedAtts = res;
        return Collections.unmodifiableMap(res);
    }

    public synchronized CloseableIterator<Map.Entry<String,String>> listAttributes() throws RepositoryException {
        if(cachedAtts==null) getAttributes();
        return new CloseableIteratorFromIterator<Map.Entry<String,String>>(cachedAtts.entrySet().iterator());
//        Cursor cursor = getClient().query(attUri,Provider.AttributeColumns.ATTRIBUTE_PROJECTION,null,null,null);
//        return new CursorIteratorImpl<Map.Entry<String,String>>(cursor, new CursorIteratorImpl.OfCursor<Map.Entry<String,String>>() {
//            public Map.Entry<String,String> ofCursor(Cursor c) {
//                return new AttributeEntry(c.getString(0),c.getString(1));
//            }
//        });
    }

    public synchronized String getAttribute(String name) throws RepositoryException {
        if(cachedAtts==null) getAttributes();
        return cachedAtts.get(name);
//        Cursor cursor = getClient().query(attUri,Provider.AttributeColumns.VALUE_PROJECTION,
//                where,new String[] { name },null);
//        try {
//            if(cursor.moveToFirst()) return cursor.getString(0);
//            else return null;
//        }
//        finally {
//            cursor.close();
//        }
    }

    public synchronized void setAttributes(Map<String,String> attributes) throws RepositoryException {
        cachedAtts = null;
        List<ContentValues> valuesToInsert = new ArrayList<ContentValues>();
        for(Map.Entry<String,String> pair : attributes.entrySet()) {
            String name = pair.getKey();
            String value = pair.getValue();
            if(value==null) {
                getClient().delete(attUri,where,new String[] { name });
                continue;
            }
            else {
                ContentValues values = new ContentValues();
                values.put(Provider.AttributeColumns.NAME,name);
                values.put(Provider.AttributeColumns.VALUE,value);
                valuesToInsert.add(values);
            }
        }
        if(!valuesToInsert.isEmpty()) getClient().bulkInsert(attUri,valuesToInsert.toArray(new ContentValues[valuesToInsert.size()]));
    }

    public synchronized void setAttribute(String name, String value) throws RepositoryException {
        cachedAtts = null;
        if(value==null) {
            getClient().delete(attUri,where,new String[] { name });
            return;
        }
        ContentValues values = new ContentValues();
        values.put(Provider.AttributeColumns.NAME,name);
        values.put(Provider.AttributeColumns.VALUE,value);
        getClient().insert(attUri,values);
    }

    public synchronized void deleteAttributes(List<String> names) throws RepositoryException {
        cachedAtts = null;
        for(String name : names) {
            deleteAttribute(name);
        }
    }

    public synchronized void deleteAttribute(String name) throws RepositoryException {
        cachedAtts = null;
        setAttribute(name,null);
    }
    
    static class AttributeEntry implements Map.Entry<String,String> {
        private String name;
        private String value;
        
        public AttributeEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }
        
        @Override
        public String getKey() {
            return name;
        }
        
        @Override
        public String getValue() {
            return value;
        }

        @Override
        public String setValue(String value) {
            String res = this.value;
            this.value = value;
            return res;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((name == null) ? 0 : name.hashCode());
            result = prime * result + ((value == null) ? 0 : value.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            AttributeEntry other = (AttributeEntry) obj;
            if (name == null) {
                if (other.name != null) return false;
            } else if (!name.equals(other.name)) return false;
            if (value == null) {
                if (other.value != null) return false;
            } else if (!value.equals(other.value)) return false;
            return true;
        }
    }
}
