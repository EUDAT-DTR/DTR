/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

/**
 * Objects implementing the Authorizor interface can be used by a server
 * to decide who can perform what operations on which objects.
 */
public interface Authorizer {

  public boolean checkAuthorization(String caller, String operation, String object)
    throws DOException;
}
