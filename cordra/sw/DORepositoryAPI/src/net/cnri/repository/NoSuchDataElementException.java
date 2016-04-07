/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

public class NoSuchDataElementException extends RepositoryException {
    public NoSuchDataElementException(String message) {
        super(message);
    }
    
    public NoSuchDataElementException(String handle,String elementName) {
        super("No such data element: " + handle + ", " + elementName);
    }
}
