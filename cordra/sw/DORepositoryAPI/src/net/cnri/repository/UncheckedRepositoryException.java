/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

/** Unchecked wrapper for Repository exceptions.  This is thrown by iterators which are not allowed to throw checked exceptions.  Example use: 
 * <code><pre>
    CloseableIterator&lt;DataElement> iter = dobj.listDataElements();
    try {   
        while (iter.hasNext()) {
            DataElement element = iter.next();
            // ...
        }
    } catch (UncheckedRepositoryException e) {
        e.throwCause();
    }
    finally {
        iter.close();
    }
 * </pre></code>
 * */
@SuppressWarnings("serial")
public class UncheckedRepositoryException extends RuntimeException {

	public UncheckedRepositoryException(RepositoryException cause) {
		super(cause);
	}
	
	public void throwCause() throws RepositoryException {
		throw getCause();
	}
	
	@Override
	public RepositoryException getCause() {
		return ((RepositoryException)super.getCause());
	}
}
