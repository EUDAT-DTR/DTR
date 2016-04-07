/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import net.cnri.repository.RepositoryException;

/**
 * A query that matches all objects.
 */
public class MatchAllObjectsQuery implements Query {
	public MatchAllObjectsQuery() {}
	
    public <T> T accept(QueryVisitor<T> visitor) throws RepositoryException {
        return visitor.visitMatchAllObjectsQuery(this);
    }
}
