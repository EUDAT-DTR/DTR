/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.CloseableIteratorFromIterator;

public abstract class AbstractDigitalObject implements DigitalObject {
	@Override
	public void delete() throws RepositoryException {
		getRepository().deleteDigitalObject(getHandle());
	}

	@Override
	public Map<String,String> getAttributes() throws RepositoryException {
		Map<String, String> result = new HashMap<String, String>();
		CloseableIterator<Entry<String,String>> iter = listAttributes();
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
	public CloseableIterator<Entry<String,String>> listAttributes() throws RepositoryException {
		return new CloseableIteratorFromIterator<Map.Entry<String,String>>(getAttributes().entrySet().iterator());
	}

	@Override
	public String getAttribute(String name) throws RepositoryException {
		return getAttributes().get(name);
	}

	@Override
	public void setAttributes(Map<String,String> attributes) throws RepositoryException {
		for(Map.Entry<String,String> entry : attributes.entrySet()) {
			setAttribute(entry.getKey(),entry.getValue());
		}
	}

	@Override
	public void deleteAttributes(List<String> names) throws RepositoryException {
		for(String name : names) {
			deleteAttribute(name);
		}
	}

	@Override
	public boolean verifyDataElement(String name) throws RepositoryException {
		return getDataElement(name)!=null;
	}

	@Override
	public DataElement getOrCreateDataElement(String name) throws RepositoryException {
		DataElement res = getDataElement(name);
		if(res==null) return createDataElement(name);
		return res;
	}

	@Override
	public void deleteDataElement(String name) throws RepositoryException {
		DataElement el = getDataElement(name);
		if(el==null) throw new NoSuchDataElementException(getHandle(),name);
		el.delete();
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
		final CloseableIterator<DataElement> iter = listDataElements();
		return new AbstractCloseableIterator<String>() {
			@Override
			protected String computeNext() {
				if(iter.hasNext()) return iter.next().getName();
				else return null;
			}
			@Override
			protected void closeOnlyOnce() {
				iter.close();
			}
		};
	}

	@Override
	public CloseableIterator<DataElement> listDataElements() throws RepositoryException {
		final CloseableIterator<String> iter = listDataElementNames();
		return new AbstractCloseableIterator<DataElement>() {
			@Override
			protected DataElement computeNext() {
				try {
					if(iter.hasNext()) return getDataElement(iter.next());
					else return null;
				}
				catch(RepositoryException e) { throw new UncheckedRepositoryException(e); }
			}
			@Override
			protected void closeOnlyOnce() {
				iter.close();
			}
		};
	}

	@Override
	public int hashCode() {
		return getHandle().hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) return true;
		if (obj == null) return false;
		if (getClass() != obj.getClass()) return false;
		DigitalObject other = (DigitalObject) obj;
		if (getHandle() == null) {
			if (other.getHandle() != null) return false;
		} else if (!getHandle().equals(other.getHandle())) return false;
		return true;
	}
}
