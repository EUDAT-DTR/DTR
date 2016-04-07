/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import net.cnri.repository.CloseableIterator;

/**
 * {@link CloseableIterator} which returns the result of applying a specified function to the objects returned by a given CloseableIterator.  
 * Use by subclassing and implementing {@link #map(Object)}.
 */
public abstract class MappingCloseableIteratorWrapper<From,To> extends AbstractCloseableIterator<To> {
    private final CloseableIterator<From> iter;
    
    public MappingCloseableIteratorWrapper(CloseableIterator<From> iter) {
        this.iter = iter;
    }
    
    /**
     * Abstract method, to be overridden by subclasses, specifying what function to apply to objects returned by the source CloseableIterator.
     * 
     * @param from the object produced by the source CloseableIterator.
     * @return the object to be returned by this CloseableIterator
     */
    abstract protected To map(From from);
    
    @Override
    protected To computeNext() {
        if(!iter.hasNext()) return null;
        return(map(iter.next()));
    }
    
    @Override
    protected void closeOnlyOnce() {
        iter.close();
    }

}
