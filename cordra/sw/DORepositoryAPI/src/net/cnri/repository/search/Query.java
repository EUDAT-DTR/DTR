/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import net.cnri.repository.RepositoryException;

/**
 * A query for a repository.  A query is defined by which objects match the query.
 */
public interface Query {
    /**
     * Returns the result of applying a QueryVisitor to this query.  A QueryVisitor implementation will return different results based on the runtime type of the query.
     * This allows the emulation of structural pattern matching over possible Query types.
     * @param visitor the visitor
     * @return the result of the visit
     * @throws RepositoryException
     */
    <T> T accept(QueryVisitor<T> visitor) throws RepositoryException;
}
