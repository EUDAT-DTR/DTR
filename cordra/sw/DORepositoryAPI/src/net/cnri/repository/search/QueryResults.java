/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import net.cnri.repository.CloseableIterator;

/**
 * A CloseableIterator which carries a size and an optional boolean indicating whether there are more results.  
 * Used as the result of a search when the repository can know the total number of matches.
 * The size might be greater than the number of elements in the iterator, for instance in the case of a paged query.
 * If #isMore() is true, then this iterator has all results or the last page of results.
 * If #isMore() is false, then there are more results available past the page shown.
 * If #isMore() is null, either is possible.
 *
 * @param <T> the type of each result
 */
public class QueryResults<T> implements CloseableIterator<T> {
	private int size;
	private CloseableIterator<T> iter;
	private Boolean more;
	
	public QueryResults(int size, CloseableIterator<T> iter) {
		this.size = size;
		this.iter = iter;
	}

	public QueryResults(int size, Boolean more, CloseableIterator<T> iter) {
	    this.size = size;
	    this.more = more;
	    this.iter = iter;
	}

	public void close() {
		iter.close();
	}

	public boolean hasNext() {
		return iter.hasNext();
	}

	public T next() {
		return iter.next();
	}

	public void remove() {
		iter.remove();
	}

	public int size() { 
		return size; 
	}
	
	public Boolean isMore() {
	    return more;
	}
}
