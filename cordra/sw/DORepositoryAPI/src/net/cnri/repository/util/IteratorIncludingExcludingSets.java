/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.util.Set;

import net.cnri.repository.CloseableIterator;

public class IteratorIncludingExcludingSets<U> extends AbstractCloseableIterator<U> {
    private final CloseableIterator<U> iter;
    private final Set<U> include;
    private final Set<U> exclude;
    
    public IteratorIncludingExcludingSets(CloseableIterator<U> iter, Set<U> include, Set<U> exclude) {
        this.iter = iter;
        this.include = include;
        this.exclude = exclude;
    }

    @Override
    protected U computeNext() {
        while(iter.hasNext()) {
            U res = iter.next();
            if((include==null || include.contains(res)) && (exclude==null || !exclude.contains(res))) return res;
        }
        return null;
    }
    @Override
    protected void closeOnlyOnce() {
        iter.close();
    }
}
