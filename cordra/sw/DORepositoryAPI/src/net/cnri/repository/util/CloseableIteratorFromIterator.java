/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.io.Closeable;
import java.io.IOException;
import java.util.Iterator;

import net.cnri.repository.CloseableIterator;

/** Wraps a standard java.util.Iterator as a {@link CloseableIterator}. */
public class CloseableIteratorFromIterator<T> implements CloseableIterator<T> {
	
	private Iterator<? extends T> iter;
	
	public CloseableIteratorFromIterator(Iterator<? extends T> iter) {
		this.iter = iter;
	}
	
	@Override
	public boolean hasNext() {
		return iter.hasNext();
	}

	@Override
	public T next() {
		return iter.next();
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException();
	}

    /** If constructed from an iterator which is an instance of java.io.Closeable, closes it.  Otherwise does nothing. */
	@Override
	public void close() {
		if(iter instanceof Closeable) try { ((Closeable)iter).close(); } catch(IOException e) {}
	}
}
