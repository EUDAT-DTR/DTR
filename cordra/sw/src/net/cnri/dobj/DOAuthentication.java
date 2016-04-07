/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.security.cert.Certificate;

/**
 * Interface for objects that can be used to authenticate themselves to the 
 * other side of a DOConnection link.
 */
public interface DOAuthentication {
  
  /** Returns the identifier of the calling code. */
  public String getID();
  
  /**
   * Signs the given challenge message and puts the result (including any required
   * parameters) into the given HeaderSet object.
   */
  public void signChallenge(HeaderSet challenge, HeaderSet response)
    throws Exception;
  
  /** Returns any certificates that supplement this entities authentication */
  public Certificate[] getCredentials();
  
  /** 
   * Returns this authentication in a form that will work with handle system
   * administration. */
  public net.handle.hdllib.AuthenticationInfo toHandleAuth();
}

