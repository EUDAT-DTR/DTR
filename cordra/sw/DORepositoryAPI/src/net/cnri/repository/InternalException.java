/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

/** Exception thrown by a Repository operation when an unexpected error has occurred. */
public class InternalException extends net.cnri.repository.RepositoryException {
    public InternalException(Throwable cause) {
        super(cause);
    }

    public InternalException(String message) {
        super(message);
    }    
    
    public InternalException(String message, Throwable cause) {
        super(message, cause);
    }
}
