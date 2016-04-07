/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

/** Exception thrown by Repository create methods when the object or element to be created already exists. */
public class CreationException extends net.cnri.repository.RepositoryException {
	public CreationException() { 
		super();
	}
	
	public CreationException(String message) {
	    super(message);
	}
}
