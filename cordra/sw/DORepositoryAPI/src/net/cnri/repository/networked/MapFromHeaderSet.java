/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.networked;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;

public class MapFromHeaderSet implements Map<String,String>{

	private HeaderSet headerset;
	
	public MapFromHeaderSet(HeaderSet headerset){
		this.headerset = headerset;
	}
	
	@Override
	public void clear() {
		headerset.removeAllHeaders();
	}

	@Override
	public boolean containsKey(Object key) {
		String k = (String)key;
		return headerset.hasHeader(k);
	}

	@SuppressWarnings("rawtypes")
	@Override
	public boolean containsValue(Object value) {
		String val = value.toString();
		Iterator iter = headerset.iterator();
		while (iter.hasNext()){
			HeaderItem item = (HeaderItem)iter.next();
			if (item.getValue().equals(val)){
				return true;
			} 
		}
		return false;
	}

	@Override
	public String get(Object key) {
		String k = (String)key;
		String value = headerset.getStringHeader(k, null);
		return value;
	}

	@Override
	public boolean isEmpty() {
		return headerset.size()==0;
	}

	@Override
	public String put(String key, String value) {
		headerset.addHeader(key, value);
		return value;
	}

	@Override
	public void putAll(Map<? extends String, ? extends String> map) {
		for(java.util.Map.Entry<? extends String, ? extends String> entry : map.entrySet()) {
			headerset.addHeader(entry.getKey(), entry.getValue());
		}
	}

	@Override
	public String remove(Object key) {
		String k = (String)key;
		String value = headerset.getStringHeader(k, null);
		headerset.removeHeadersWithKey(k);
		return value;
	}

	@Override
	public int size() {
		return headerset.size();
	}

	@Override
	public Collection<String> values() {
		return new Values();
	}

	@Override
	public Set<String> keySet() {
		return new KeySet();
	}

	@Override
	public Set<java.util.Map.Entry<String, String>> entrySet() {
		return new SetEntry();
	}

	class Values implements Collection<String> {

		@Override
		public boolean add(String e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends String> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			MapFromHeaderSet.this.clear();
		}

		@Override
		public boolean contains(Object o) {
			return MapFromHeaderSet.this.containsValue(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object value : c) {
				if(!this.contains(value)) return false;
			}
			return true;
		}

		@Override
		public boolean isEmpty() {
			return MapFromHeaderSet.this.isEmpty();
		}

		@Override
		public Iterator<String> iterator() {
			Iterator<String> iter = new Iterator<String>() {
				@SuppressWarnings("unchecked")
				Iterator<HeaderItem> headerIter = headerset.iterator();
				
				@Override
				public boolean hasNext() {
					return headerIter.hasNext();
				}

				@Override
				public String next() {
					return headerIter.next().getValue();
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
			return iter;
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int size() {
			return MapFromHeaderSet.this.size();
		}

		@Override
		public Object[] toArray() {
			return toArray(new Object[0]);
		}

		@SuppressWarnings({ "unchecked" })
		@Override
		public <T> T[] toArray(T[] a) {
			Object[] array;
			if(this.size()<=a.length) {
				array = a;
			} 
			else {
				array = new Object[this.size()];
			}
			int count = 0;
			Iterator<HeaderItem> iter = MapFromHeaderSet.this.headerset.iterator();
			while (iter.hasNext()){
				HeaderItem item = iter.next();
				String value = item.getValue();
				array[count] = value;
				count++;
			}
			if(count < array.length) {
				array[count] = null;
			}
			return (T[])array;
		}
	}
	
	class KeySet implements Set<String> {

		@Override
		public boolean add(String e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends String> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			MapFromHeaderSet.this.clear();
		}

		@Override
		public boolean contains(Object o) {
			return MapFromHeaderSet.this.containsKey(o);
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object key : c) {
				if(!this.contains(key)) return false;
			}
			return true;
		}

		@Override
		public boolean isEmpty() {
			return MapFromHeaderSet.this.isEmpty();
		}



		@Override
		public Iterator<String> iterator() {
			Iterator<String> iter = new Iterator<String>() {

				@SuppressWarnings("unchecked")
				Iterator<HeaderItem> headerIter = headerset.iterator();

				@Override
				public boolean hasNext() {
					return headerIter.hasNext();
				}

				@Override
				public String next() {
					if(hasNext()) {
						return headerIter.next().getName();
					} else {
						throw new NoSuchElementException();
					}
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
			return iter;
		}

		@Override
		public boolean remove(Object o) {
			return MapFromHeaderSet.this.remove(o) != null;
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int size() {
			return MapFromHeaderSet.this.size();
		}

		@Override
		public Object[] toArray() {
			return toArray(new Object[0]);
		}

		@SuppressWarnings({ "unchecked" })
		@Override
		public <T> T[] toArray(T[] a) {
			Object[] array;
			if(this.size()<=a.length) {
				array = a;
			} 
			else {
				array = new Object[this.size()];
			}
			int count = 0;
			Iterator<HeaderItem> iter = MapFromHeaderSet.this.headerset.iterator();
			while (iter.hasNext()){
				HeaderItem item = iter.next();
				String key = item.getName();
				array[count] = key;
				count++;
			}
			if(count < array.length) {
				array[count] = null;
			}
			return (T[])array;
		}
	}
	
	class Entry implements Map.Entry<String, String> {
		private String key;
		private String value;

		public Entry(String key, String value) {
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
			String res = this.value;
			this.value = value;
			MapFromHeaderSet.this.put(key,value);
			return res;
		}
	}
	
	class SetEntry implements Set<Map.Entry<String,String>> {

		@Override
		public boolean add(Map.Entry<String,String> e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean addAll(Collection<? extends Map.Entry<String,String>> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void clear() {
			MapFromHeaderSet.this.clear();
		}

		@SuppressWarnings("unchecked")
		@Override
		public boolean contains(Object o) {
			Map.Entry<String, String> map = (Map.Entry<String, String>) o;
			String key = map.getKey();
			String value = map.getValue();
			if(MapFromHeaderSet.this.get(key).equals(value)) {
				return true;
			}
			return false;
		}

		@Override
		public boolean containsAll(Collection<?> c) {
			for(Object entry : c) {
				if(!this.contains(entry)) return false;
			}
			return true;
		}

		@Override
		public boolean isEmpty() {
			return MapFromHeaderSet.this.isEmpty();
		}

		@SuppressWarnings({ "rawtypes", "unchecked" })
		@Override
		public Iterator<Map.Entry<String,String>> iterator() {
			Iterator<Map.Entry<String, String>> iter = new Iterator() {
				Iterator<HeaderItem> headerIter = headerset.iterator();
				@Override
				public boolean hasNext() {
					return headerIter.hasNext();
				}

				@Override
				public Map.Entry<String, String> next() {
					if(hasNext()) {
						HeaderItem item = headerIter.next();
						String key = item.getName();
						String value = item.getValue();
						Map.Entry<String, String> entry = new Entry(key, value);
						return entry;
					} else {
						throw new NoSuchElementException();
					}
				}

				@Override
				public void remove() {
					throw new UnsupportedOperationException();
				}
			};
			return iter;
		}

		@Override
		public boolean remove(Object o) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean removeAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean retainAll(Collection<?> c) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int size() {
			return MapFromHeaderSet.this.size();
		}

		@Override
		public Object[] toArray() {
			return toArray(new Object[0]);
		}

		@SuppressWarnings({ "unchecked" })
		@Override
		public <T> T[] toArray(T[] a) {
			Object[] array;
			if(this.size()<=a.length) {
				array = a;
			} 
			else {
				array = new Object[this.size()];
			}
			int count = 0;
			Iterator<HeaderItem> iter = MapFromHeaderSet.this.headerset.iterator();
			while (iter.hasNext()){
				HeaderItem item = iter.next();
				String key = item.getName();
				String value = item.getValue();
				T entry = (T) new Entry(key, value);
				array[count] = entry;
				count++;
			}
			if(count < array.length) {
				array[count] = null;
			}
			return (T[])array;
		}
	}
}
