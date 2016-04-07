/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import net.cnri.repository.RepositoryException;

/**
 * A specification of some behavior on each kind of query.  The Visitor pattern is OO's crufty answer to structural pattern matching on algebraic types.
 * <p>
 * To use a {@code QueryVisitor}, call the {@link Query#accept} method on a {@code Query}, with the {@code QueryVisitor} as parameter.
 * <p>
 * To implement a {@code QueryVisitor}, implement the {@code visit...} methods for each query type.  Recursive instances (such as {@link BooleanQuery})
 * are handled by passing the {@code QueryVisitor} as {@code this} to each subquery's {@code accept} method.
 *
 * @param <T> the return type for the visitor
 */
public interface QueryVisitor<T> {
    T visitRawQuery(RawQuery query) throws RepositoryException;
    T visitMatchAllObjectsQuery(MatchAllObjectsQuery query) throws RepositoryException;
    T visitBooleanQuery(BooleanQuery query) throws RepositoryException;
    T visitAttributeQuery(AttributeQuery query) throws RepositoryException;
    T visitElementAttributeQuery(ElementAttributeQuery query) throws RepositoryException;
}
