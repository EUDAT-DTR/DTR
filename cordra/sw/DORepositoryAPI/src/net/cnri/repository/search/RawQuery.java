/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import net.cnri.repository.RepositoryException;

/**
 * A query based on a raw String to be interpreted in a potentially repository-specific way. 
 */
public class RawQuery implements Query {
    protected String query;
    
    public RawQuery(String query) {
        this.query = query;
    }
    
    public String getQueryString() {
        return query;
    }
    
    public <T> T accept(QueryVisitor<T> visitor) throws RepositoryException {
        return visitor.visitRawQuery(this);
    }
}
