/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.layered;

import java.util.Collection;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.Query;

public interface SupportsSearchExcluding {
    CloseableIterator<DigitalObject> searchExcluding(Query query, Collection<String> handles) throws RepositoryException;
    CloseableIterator<String> searchHandlesExcluding(Query query, Collection<String> handles) throws RepositoryException;
}
