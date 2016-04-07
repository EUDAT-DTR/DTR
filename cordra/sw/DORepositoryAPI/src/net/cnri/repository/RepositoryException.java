/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

/** Base class for exceptions thrown by Repository operations. */
public class RepositoryException extends Exception {

    private final String message;
    
    RepositoryException() {
        this((String)null);
    }

    RepositoryException(Throwable cause) {
        this(cause==null ? null : cause.toString(),cause);
    }
    
    RepositoryException(String message) {
        super(message);
        this.message = message;
    }

    RepositoryException(String message, Throwable cause) {
        super(message,cause);
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}
