/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.util.*;

/**
 * <p>
 * A thin wrapper around an ArrayList of String pairs, allowing it a Map implementation, such as needed for Digital Object attributes.
 * The implementation does not support concurrent use.
 * </p><p>
 * Note that this class disobeys the general Map contract in several way, because a single key can have duplicate values.  The {@code get}
 * method will always return the latest value {@code put}, but the former values will continue to be visible via {@code iterator} and {@code size}.
 * </p><p>
 * The {@code put} method always returns null, even if a mapping for a key already exists.
 * </p>
 */
public class StringPairListMap extends AbstractMap<String,String> {
    private List<Pair> list = new ArrayList<Pair>();
    
    /**
     * {@inheritDoc}
     * The {@code put} method of this class always returns null.
     * @return null.  This implementation always returns null, even if a mapping already exists.
     */
    @Override
    public String put(String key, String value) {
        list.add(new Pair(key,value));
        return null;
    }
    
    @Override
    public void clear() {
        list.clear();
    }
    
    @Override
    public String remove(Object key) {
        String res = null;
        boolean found = false;
        ListIterator<Pair> iter = list.listIterator(list.size());
        while(iter.hasPrevious()) {
            Pair pair = iter.previous();
            if(key==null ? pair.getKey()==null : pair.getKey().equals(key)) {
                if(!found) res = pair.getValue();
                found = true;
                iter.remove();
            }
        }
        return res;
    }
    
    @Override
    public String get(Object key) {
        ListIterator<Pair> iter = list.listIterator(list.size());
        while(iter.hasPrevious()) {
            Pair pair = iter.previous();
            if(key==null ? pair.getKey()==null : pair.getKey().equals(key)) {
                return pair.getValue();
            }
        }
        return null;
    }
    
    @Override
    public Set<Map.Entry<String,String>> entrySet() {
        return new AbstractSet<Map.Entry<String,String>>() {
            @Override
            public int size() {
                return list.size();
            }
            
            @Override
            public Iterator<Map.Entry<String,String>> iterator() {
                return new AbstractCloseableIterator<Map.Entry<String,String>>() {
                    private Iterator<Pair> iter = list.iterator();
                    
                    @Override
                    protected Map.Entry<String,String> computeNext() {
                        if(!iter.hasNext()) return null;
                        return iter.next();
                    }
                    
                    @Override
                    public void remove() {
                        iter.remove();
                    }
                };
            }
        };
    }
}
