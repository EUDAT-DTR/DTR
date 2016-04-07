/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;

/**
 * Convenience utilities for Collections, especially Iterators and {@link CloseableIterator}s.
 */
public class CollectionUtil {
	/**
	 * @param a
	 * @param b
	 * @return a - b
	 */
	public static Set<String> difference(Set<String> a, Set<String> b) {
		Set<String> difference = new HashSet<String>(a);
		difference.removeAll(b);
		return difference;
	}
	
	public static Set<String> union(List<Set<String>> sets) {
		Set<String> union = new HashSet<String>();
		for (Set<String> set : sets) {
			union.addAll(set);
		}
		return union;
	}
	
	public static Set<String> intersection(List<Set<String>> sets) {
		if (sets.size() == 0) {
			return new HashSet<String>();
		} else if (sets.size() == 1) {
			return new HashSet<String>(sets.get(0));
		}
		Set<String> result = new HashSet<String>(sets.get(0));
		for (int i = 1; i < sets.size(); i++) {
			result = intersection(result, sets.get(i));
		}
		return result;
	}
	
	public static Set<String> intersection(Set<String> a, Set<String> b) {
		Set<String> intersection = new HashSet<String>(a);
		intersection.retainAll(b);
		return intersection;
	}

	/**
	 * Add all the elements of a given CloseableIterator to a given Collection.  The CloseableIterator will be closed, and unchecked
	 * exceptions will be properly propagated. 
	 * 
	 * @param collection a collection to be added to
	 * @param iter an iterator, the elements of which will be added to the collection
	 * @throws RepositoryException
	 */
    public static <T> void addAllFromIterator(Collection<T> collection, CloseableIterator<T> iter) throws RepositoryException {
        try {
            while(iter.hasNext()) {
                collection.add(iter.next());
            }
        }
        catch(UncheckedRepositoryException e) { e.throwCause(); }
        finally { iter.close(); }
    }
    
    /**
     * Return the elements of a given CloseableIterator as a List.  The CloseableIterator will be closed, and unchecked
     * exceptions will be properly propagated. 
     * 
     * @param iter an iterator
     * @throws RepositoryException
     */
    public static <T> List<T> asList(CloseableIterator<T> iter) throws RepositoryException {
        List<T> res = new ArrayList<T>();
        addAllFromIterator(res,iter);
        return res;
    }
    
    /**
     * Return the elements of a given CloseableIterator as a Set.  The CloseableIterator will be closed, and unchecked
     * exceptions will be properly propagated. 
     * 
     * @param iter an iterator
     * @throws RepositoryException
     */
    public static <T> Set<T> asSet(CloseableIterator<T> iter) throws RepositoryException {
        Set<T> res = new HashSet<T>();
        addAllFromIterator(res,iter);
        return res;
    }

    /**
     * A convenience method to use an iterator in a for-each loop.  To correctly use with a {@link CloseableIterator}:
     * <code><pre>
        CloseableIterator&lt;DataElement> iter = dobj.listDataElements();
        try {   
            for(DataElement element : CollectionUtil.forEach(iter)) {
                // ...
            }
        } catch (UncheckedRepositoryException e) {
            e.throwCause();
        } finally {
            iter.close();
        }
     * </pre></code>
     * 
     * Note that the returned Iterable does not allow its {@code iterator()} method to return a fresh iterator each time; the iteration is once-only.
     * 
     * @param iter an iterator
     */
    public static <T> Iterable<T> forEach(final Iterator<T> iter) {
        return new Iterable<T>() {
            @Override
            public Iterator<T> iterator() {
                return iter;
            }
        };
    }
}
