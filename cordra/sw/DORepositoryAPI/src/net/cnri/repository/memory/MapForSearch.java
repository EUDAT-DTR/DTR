/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.memory;

import java.util.AbstractCollection;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.Iterator;

import net.cnri.repository.DataElement;

@Deprecated
class MapForSearch extends AbstractMap<String,String> {
    private final MemoryDigitalObject dobj;
    
    public MapForSearch(MemoryDigitalObject dobj) {
        this.dobj = dobj;
    }
    
    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsKey(Object key) {
        if(!(key instanceof String)) return false;
        String s = (String)key;
        if(s.equals("objectid")) return true;
        if(s.startsWith("objatt_")) return dobj.getAttribute(s.substring("objatt_".length()))!=null;
        if(s.startsWith("elatt_")) {
            int start = "elatt_".length();
            int end = s.indexOf('_',start+1);
            while(end>start) {
                String elName = s.substring(start,end);
                MemoryDataElement el = (MemoryDataElement) dobj.getDataElement(elName);
                if(el!=null) {
                    String attName = s.substring(end+1);
                    if(el.getAttribute(attName)!=null) return true;
                }
                end = s.indexOf('_',end+1);
            }
        }
        return false;
    }

    @Override
    public boolean containsValue(Object value) {
        if(!(value instanceof String)) return false;
        String s = (String)value;
        if(s.equals(dobj.getHandle())) return true;
        if(dobj.getAttribute(s)!=null) return true;
        Iterator<DataElement> iter = dobj.listDataElements();
        while(iter.hasNext()) {
        	MemoryDataElement el = (MemoryDataElement) iter.next();
            if(el.getAttribute(s)!=null) return true;
        }
        return false;
    }

    @Override
    public String get(Object key) {
        if(!(key instanceof String)) return null;
        String s = (String)key;
        if(s.equals("objectid")) return dobj.getHandle();
        if(s.startsWith("objatt_")) return dobj.getAttribute(s.substring("objatt_".length()));
        if(s.startsWith("elatt_")) {
            int start = "elatt_".length();
            int end = s.indexOf('_',start+1);
            while(end>start) {
                String elName = s.substring(start,end);
                MemoryDataElement el = (MemoryDataElement) dobj.getDataElement(elName);
                if(el!=null) {
                    String attName = s.substring(end+1);
                    String res = el.getAttribute(attName);
                    if(res!=null) return null;
                }
                end = s.indexOf('_',end+1);
            }
        }
        return null;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public String put(String key, String value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void putAll(Map<? extends String,? extends String> m) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String remove(Object key) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int size() {
        int res = 1 + dobj.getAttributes().size();
        Iterator<DataElement> iter = dobj.listDataElements();
        while(iter.hasNext()) {
            MemoryDataElement el = (MemoryDataElement) iter.next();
            res += el.getAttributes().size();
        }
        return res;
    }

    @Override
    public Collection<String> values() {
        return new AbstractCollection<String>() {
            @Override
            public Iterator<String> iterator() {
                return new AttributeIterator<String>() {
                    @Override
                    String getHandle() {
                        return dobj.getHandle();
                    }
                    
                    @Override
                    Iterator<String> objattIter() {
                        return new MapIterator<Map.Entry<String,String>,String>(dobj.listAttributes()) {
                            @Override
                            String map(java.util.Map.Entry<String,String> x) {
                                return x.getValue();
                            }
                        };
                    }
                    
                    @Override
                    Iterator<String> elattIter(MemoryDataElement el) {
                        return new MapIterator<Map.Entry<String,String>,String>(el.listAttributes()) {
                            @Override
                            String map(java.util.Map.Entry<String,String> x) {
                                return x.getValue();
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return MapForSearch.this.size();
            }
        };
    }
    
    @Override
    public Set<String> keySet() {
        return new AbstractSet<String>() {
            @Override
            public Iterator<String> iterator() {
                return new AttributeIterator<String>() {
                    @Override
                    String getHandle() {
                        return "objectid";
                    }
                    
                    @Override
                    Iterator<String> objattIter() {
                        return new MapIterator<Map.Entry<String,String>,String>(dobj.listAttributes()) {
                            String map(Map.Entry<String,String> x) {
                                return "objatt_" + x.getKey();
                            }
                        };
                    }
                    
                    @Override
                    Iterator<String> elattIter(final MemoryDataElement el) {
                        return new MapIterator<Map.Entry<String,String>,String>(el.listAttributes()) {
                            String map(Map.Entry<String,String> x) {
                                return "elatt_" + el.getName() + "_" + x.getKey();
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return MapForSearch.this.size();
            }
        };
    }

    @Override
    public Set<Map.Entry<String,String>> entrySet() {
        return new AbstractSet<Map.Entry<String,String>>() {
            @Override
            public Iterator<Map.Entry<String,String>> iterator() {
                return new AttributeIterator<Map.Entry<String,String>>() {
                    @Override
                    Map.Entry<String,String> getHandle() {
                        return new MapEntry("objectid",dobj.getHandle());
                    }
                    
                    @Override
                    Iterator<Map.Entry<String,String>> objattIter() {
                        return new MapIterator<Map.Entry<String,String>,Map.Entry<String,String>>(dobj.listAttributes()) {
                            Map.Entry<String,String> map(Map.Entry<String,String> x) {
                                return new MapEntry("objatt_" + x.getKey(),x.getValue());
                            }
                        };
                    }
                    
                    @Override
                    Iterator<Map.Entry<String,String>> elattIter(final MemoryDataElement el) {
                        return new MapIterator<Map.Entry<String,String>,Map.Entry<String,String>>(el.listAttributes()) {
                            Map.Entry<String,String> map(Map.Entry<String,String> x) {
                                return new MapEntry("elatt_" + el.getName() + "_" + x.getKey(),x.getValue());
                            }
                        };
                    }
                };
            }

            @Override
            public int size() {
                return MapForSearch.this.size();
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
    
    private abstract static class MapIterator<T1,T2> implements Iterator<T2> {
        final Iterator<T1> iter;
        
        MapIterator(Iterator<T1> iter) {
            this.iter = iter;
        }
        
        @Override
        public boolean hasNext() {
            return iter.hasNext();
        }
        
        @Override
        public T2 next() {
            return map(iter.next());
        }
        
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
        abstract T2 map(T1 x);
    }
    
    private abstract class AttributeIterator<T> implements Iterator<T> {
        T next = getHandle();
        final Iterator<T> objattIter = objattIter();
        final Iterator<DataElement> elIter = dobj.listDataElements();
        Iterator<T> elattIter;
        
        @Override
        public boolean hasNext() {
            return next!=null;
        }
        
        @Override
        public T next() {
            if(next==null) return null;
            T res = next;
            next = null;
            if(objattIter.hasNext()) next = objattIter.next();
            else {
                while((elattIter==null || !elattIter.hasNext()) && elIter.hasNext()) elattIter = elattIter((MemoryDataElement) elIter.next());
                if(elattIter!=null && elattIter.hasNext()) next = elattIter.next();
            }
            return res;
        }
        
        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
        abstract T getHandle();
        abstract Iterator<T> objattIter();
        abstract Iterator<T> elattIter(MemoryDataElement el);
    }
    
}
