/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.cordra.api;

/** Unchecked wrapper for Cordra exceptions.  This is thrown by iterators which are not allowed to throw checked exceptions.  Example use: 
 * <code><pre>
    CloseableIterator&lt;DataElement> iter = dobj.listDataElements();
    try {   
        while (iter.hasNext()) {
            DataElement element = iter.next();
            // ...
        }
    } catch (UncheckedCordraException e) {
        e.throwCause();
    }
    finally {
        iter.close();
    }
 * </pre></code>
 * */
public class UncheckedCordraException extends RuntimeException {

	public UncheckedCordraException(CordraException cause) {
		super(cause);
	}
	
	public void throwCause() throws CordraException {
		throw getCause();
	}
	
	@Override
	public CordraException getCause() {
		return ((CordraException)super.getCause());
	}
}
