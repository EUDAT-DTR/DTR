/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

/**
 * A specification of a field to be sorted.
 * 
 * @see QueryParams
 */
public class SortField {
    private final String name;
    private final boolean reverse;
    
    public SortField(String name, boolean reverse) {
        this.name = name;
        this.reverse = reverse;
    }

    public String getName() {
        return name;
    }

    public boolean isReverse() {
        return reverse;
    }
}
