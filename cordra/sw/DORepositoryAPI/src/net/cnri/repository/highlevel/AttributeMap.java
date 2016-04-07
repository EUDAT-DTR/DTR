/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.highlevel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;

public class AttributeMap implements Map<String, String> {

	private DigitalObject dobj;
	private String name;
	private String prefix;
	
	public AttributeMap(DigitalObject dobj, String name) {
		this.dobj = dobj;
		this.name = name;
		this.prefix = "map."+name+".";
	}
	
	public String getName() {
        return name;
    }
	
	private String nameForKey(String key) {
		return prefix+key;
	}
	
	private String removePrefix(String key) {
		return key.substring(prefix.length());
	}

	@Override
	public void clear() {
		List<String> attributesToDelete = new ArrayList<String>();
		try {
			CloseableIterator<Map.Entry<String, String>> iter = dobj.listAttributes();
			while (iter.hasNext()) {
				Map.Entry<String, String> current = iter.next();
				if (current.getKey().startsWith(prefix)) {
					attributesToDelete.add(current.getKey());
				}
			}
			dobj.deleteAttributes(attributesToDelete);
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean containsKey(Object key) {
		String attributeName = nameForKey((String) key);
		boolean result = false;
		try {
			result = dobj.getAttribute(attributeName)!=null;
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public boolean containsValue(Object value) {
		try {
			CloseableIterator<Map.Entry<String, String>> iter = dobj.listAttributes();
			while (iter.hasNext()) {
				Map.Entry<String, String> current = iter.next();
				if (current.getKey().startsWith(prefix)) {
					String currentValue = dobj.getAttribute(current.getKey());
					if (value.equals(currentValue)) {
						return true;
					}
				}
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public Set<Map.Entry<String, String>> entrySet() {
		Set<Map.Entry<String, String>> result = new HashSet<Map.Entry<String, String>>();
		try {
			CloseableIterator<Map.Entry<String, String>> iter = dobj.listAttributes();
			while (iter.hasNext()) {
				Map.Entry<String, String> current = iter.next();
				String currentKey = current.getKey();
				if (currentKey.startsWith(prefix)) {
					result.add(new net.cnri.repository.util.Pair(removePrefix(currentKey), dobj.getAttribute(currentKey)));
				}
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		
		return null;
	}

	@Override
	public String get(Object key) {
		String attname = nameForKey((String)key);
		String result = null;
		try {
			result = dobj.getAttribute(attname);
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public boolean isEmpty() {
		try {
			CloseableIterator<Map.Entry<String, String>> iter = dobj.listAttributes();
			while (iter.hasNext()) {
				Map.Entry<String, String> current = iter.next();
				if (current.getKey().startsWith(prefix)) {
					return false;
				}
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return true;
	}

	@Override
	public Set<String> keySet() {
		Set<String> result = new HashSet<String>();
		try {
			CloseableIterator<Map.Entry<String, String>> iter = dobj.listAttributes();
			while (iter.hasNext()) {
				Map.Entry<String, String> current = iter.next();
				String currentKey = current.getKey();
				if (currentKey.startsWith(prefix)) {
					result.add(removePrefix(currentKey));
				}
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public String put(String key, String value) {
		String attname = nameForKey(key);
		String result = null;
		try {
			result = dobj.getAttribute(attname);
			dobj.setAttribute(attname, value);
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public void putAll(Map<? extends String, ? extends String> m) {
		Iterator<? extends String> iter =  m.keySet().iterator();
		while (iter.hasNext()) {
			String key = iter.next();
			String attname = nameForKey(key);
			String value = m.get(key);
			try {
				dobj.setAttribute(attname, value);
			} catch (RepositoryException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public String remove(Object key) {
		String result = null;
		try {
			result = dobj.getAttribute((String) key);
			dobj.deleteAttribute(nameForKey((String) key));
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public int size() {
		int result = 0;
		try {
			CloseableIterator<Map.Entry<String, String>> iter = dobj.listAttributes();
			while (iter.hasNext()) {
				Map.Entry<String, String> current = iter.next();
				if (current.getKey().startsWith(prefix)) {
					result++;
				}
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return result;
	}

	@Override
	public Collection<String> values() {
		Collection<String> result = new ArrayList<String>();
		try {
			CloseableIterator<Map.Entry<String, String>> iter = dobj.listAttributes();
			while (iter.hasNext()) {
				Map.Entry<String, String> current = iter.next();
				if (current.getKey().startsWith(prefix)) {
					result.add(dobj.getAttribute(current.getKey()));
				}
			}
		} catch (RepositoryException e) {
			e.printStackTrace();
		}
		return result;
	}
}
