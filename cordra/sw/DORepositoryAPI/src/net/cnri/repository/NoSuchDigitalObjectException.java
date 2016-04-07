/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository;

public class NoSuchDigitalObjectException extends RepositoryException {
    public NoSuchDigitalObjectException(String handle) {
        super("No such object: " + handle);
    }
}
