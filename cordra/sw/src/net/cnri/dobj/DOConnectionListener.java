/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

/**
 * DOConnectionListener specifies an interface for objects that can receive
 * notification of events on a DO connection, such as a new channel being
 * opened.
 */
public interface DOConnectionListener {

  /** This is called when a new channel has been created on the server. */
  public void channelCreated(StreamPair pair);

}
