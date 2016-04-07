/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.io.Closeable;
import java.util.Iterator;

import net.cnri.repository.util.CollectionUtil;

/** Iterator which allows a {@code close()} operation. Note that the iterators in this API may throw {@link UncheckedRepositoryException}.  Example use: 
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
 * 
 * @see CollectionUtil#forEach(Iterator)
 * */
public interface CloseableIterator<T> extends Iterator<T>, Closeable {
    void close();
}
