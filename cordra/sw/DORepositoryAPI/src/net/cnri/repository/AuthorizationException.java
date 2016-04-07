/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

/** Repository exception thrown when an operation is unauthorized. */
public class AuthorizationException extends net.cnri.repository.RepositoryException {
	public AuthorizationException() { 
		super();
	}
	
	public AuthorizationException(String message) {
	    super(message);
	}
}
