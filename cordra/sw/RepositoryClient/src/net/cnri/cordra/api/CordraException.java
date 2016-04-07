/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.cordra.api;

public class CordraException extends Exception {
    public CordraException(String message) {
        super(message);
    }

    public CordraException(Throwable cause) {
        super(cause);
    }
    
    public CordraException(String message, Throwable cause) {
        super(message, cause);
    }
}
