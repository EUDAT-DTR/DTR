/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import net.cnri.repository.CloseableIterator;

/**
 * {@link CloseableIterator} which returns the result of returning only some of the objects returned by a given CloseableIterator.  
 * Use by subclassing and implementing {@link #retain(Object)}.
 */
public abstract class FilteringCloseableIteratorWrapper<T> extends AbstractCloseableIterator<T> {
    private final CloseableIterator<T> iter;
    
    public FilteringCloseableIteratorWrapper(CloseableIterator<T> iter) {
        this.iter = iter;
    }
    
    /**
     * Abstract method, to be overridden by subclasses, specifying whether to return a given object returned by the source CloseableIterator.
     * 
     * @param candidate the object produced by the source CloseableIterator.
     * @return whether to return candidate from this CloseableIterator.
     */
    abstract protected boolean retain(T candidate);
    
    @Override
    protected T computeNext() {
        while(iter.hasNext()) {
            T next = iter.next();
            if(retain(next)) return next;
        }
        return null;
    }
    
    @Override
    protected void closeOnlyOnce() {
        iter.close();
    }

}
