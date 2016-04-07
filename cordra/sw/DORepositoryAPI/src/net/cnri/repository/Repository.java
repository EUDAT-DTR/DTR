/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

import java.util.Map;

import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;

/**
 * An object for accessing a Digital Object Repository.  The Repositories class provides factory methods.
 */
public interface Repository {
    /**
     * Returns the handle of the repository.  May return {@code null} for local repositories.
     * @return the handle of the repository, or {@code null} if not available.
     */
    String getHandle();
    
    /**
     * Verifies whether a digital object with the given identifier can be accessed via this repository.
     * @param handle an identifier for a digital object
     * @return whether a digital object with that identifier can be accessed via this repository
     */
    boolean verifyDigitalObject(String handle) throws RepositoryException;
    
    /**
     * Creates a new digital object with the given identifier in this repository.  Passing {@code null} requests that the repository assign a new unique identifier;
     * this method will throw {@code UnsupportedOperationException} if the given repository does not support that functionality.
     * @param handle the identifier for the new digital object, or {@code null} to request that the repository assign a new unique identifier.
     * @return the newly-created digital object
     * @throws CreationException if an object with the given identifier already exists within the repository
     */
    DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException;

    /**
     * Returns the digital object with the given identifier from this repository.
     * @param handle the identifier for the digital object
     * @return the digital object, or null if no such object can be accessed through this repository
     */
    DigitalObject getDigitalObject(String handle) throws RepositoryException;

    /**
     * Returns the digital object with the given identifier from this repository, which is created if it does not exist.
     * @param handle the identifier for the digital object
     * @return the digital object
     */
    DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException;

    /**
     * Deletes a digital object from the repository.
     * @param handle the identifier for the digital object
     */
    void deleteDigitalObject(String handle) throws RepositoryException;
    
    /** Provides a CloseableIterator view of the identifiers for digital objects accessible through this repository. */
    CloseableIterator<String> listHandles() throws RepositoryException;

    /** Provides a CloseableIterator view of the digital objects accessible through this repository. */
    CloseableIterator<DigitalObject> listObjects() throws RepositoryException;
    
    /** Provides a CloseableIterator view of the digital objects accessible through this repository under search bounds defined in the Query object and the parameters defined in the QueryParams object. 
     * @param query the query to perform
     * @param queryParams parameters for the query, or {@code null} for default parameters
     * @return a CloseableIterator over the results.  Some implementations may return {@link net.cnri.repository.search.QueryResults}.
     * */
	CloseableIterator<DigitalObject> search(Query query, QueryParams queryParams) throws RepositoryException;
    
    /** Provides a CloseableIterator view of the digital objects handles accessible through this repository under search bounds defined in the Query object and the parameters defined in the QueryParams object.
    * @param query the query to perform
    * @param queryParams parameters for the query, or {@code null} for default parameters
    * @return a CloseableIterator over the results.  Some implementations may return {@link net.cnri.repository.search.QueryResults}.
    * */
    CloseableIterator<String> searchHandles(Query query, QueryParams queryParams) throws RepositoryException;

    /** Provides a CloseableIterator view of the digital objects accessible through this repository under search bounds defined in the Query object with default parameters.
     * @param query the query to perform
     * @return a CloseableIterator over the results.  Some implementations may return {@link net.cnri.repository.search.QueryResults}.
     * */
    CloseableIterator<DigitalObject> search(Query query) throws RepositoryException;
    
    /** Provides a CloseableIterator view of the digital objects handles accessible through this repository under search bounds defined in the Query object with default parameters.
    * @param query the query to perform
    * @return a CloseableIterator over the results.  Some implementations may return {@link net.cnri.repository.search.QueryResults}.
    * */
    CloseableIterator<String> searchHandles(Query query) throws RepositoryException;

    /** @deprecated Use {@link #search(Query,QueryParams)} with first argument a {@link net.cnri.repository.search.RawQuery} and second argument {@code null}. */
    @Deprecated
    CloseableIterator<DigitalObject> search(String query) throws RepositoryException;
    
    /** @deprecated Use {@link #searchHandles(Query,QueryParams)} with first argument a {@link net.cnri.repository.search.RawQuery} and second argument {@code null}. */
    @Deprecated
    CloseableIterator<String> searchHandles(String query) throws RepositoryException;

    @Deprecated
    /** Provides a CloseableIterator view of the digital objects in a map accessible through this repository under search bounds. 
     * @deprecated Use #search(String), and write implementations to provide fast access to search results. */
    CloseableIterator<Map<String,String>> searchMapping(String query) throws RepositoryException;
    
    /**
     * Close the repository and release all resources.
     */
    void close();
}
