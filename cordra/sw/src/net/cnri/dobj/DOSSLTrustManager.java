/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.util.X509HSTrustManager;

/** This object is used to determine whether the other side of a secure socket 
  * connection is authenticated or not based on their Handle and Digital Object
  * authentication.
  */
public class DOSSLTrustManager extends X509HSTrustManager {
    public DOSSLTrustManager() {
        super(DOClient.getResolver().getResolver());
    }
}


