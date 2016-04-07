/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.networked;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;

public class HeaderSetPrefixMap extends AbstractMap<String,String> {
    private final HeaderSet headers;
    private final String prefix;
    
    public HeaderSetPrefixMap(HeaderSet headers, String prefix) {
        this.headers = headers;
        this.prefix = prefix;
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        int i = 0;
        for(HeaderItem item : headers) {
            if(item.getName().startsWith(prefix)) i++;
        }
        return i;
    }
    
    @Override
    public boolean containsKey(Object key) {
        if(!(key instanceof String)) return false;
        String s = (String)key;
        return headers.getStringHeader(prefix + s,null) != null;
    }

    @Override
    public String get(Object key) {
        if(!(key instanceof String)) return null;
        String s = (String)key;
        return headers.getStringHeader(prefix + s,null);
    }

    @Override
    public Set<Map.Entry<String,String>> entrySet() {
        return new AbstractSet<Map.Entry<String,String>>() {
            @Override
            public Iterator<Map.Entry<String,String>> iterator() {
                return new Iterator<Map.Entry<String,String>>() {
                    @SuppressWarnings("unchecked")
                    Iterator<HeaderItem> iter = headers.iterator();
                    Map.Entry<String,String> next;
                    
                    private void advanceToNext() {
                        if(next!=null) return;
                        while(iter.hasNext()) {
                            HeaderItem item = iter.next();
                            String nextHeaderName = item.getName();
                            if(nextHeaderName.startsWith(prefix)) {
                                String nextName = nextHeaderName.substring(prefix.length());
                                next = new MapEntry(nextName,item.getValue());
                                return;
                            }
                        }
                    }
                    
                    @Override
                    public boolean hasNext() {
                        advanceToNext();
                        return next!=null;
                    }
                    
                    @Override
                    public Map.Entry<String,String> next() {
                        advanceToNext();
                        Map.Entry<String,String> res = next;
                        next = null;
                        return res;
                    }
                    
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }

            @Override
            public int size() {
                return HeaderSetPrefixMap.this.size();
            }
        };
    }
    
    private static class MapEntry implements Map.Entry<String,String> {
        final String key, value;
        
        MapEntry(String key, String value) {
            this.key = key;
            this.value = value;
        }
        
        @Override
        public String getKey() {
            return key;
        }
        
        @Override
        public String getValue() {
            return value;
        }
        
        @Override
        public String setValue(String value) {
            throw new UnsupportedOperationException();
        }
    }
}
