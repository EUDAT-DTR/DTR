/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;
import net.cnri.util.ThreadSafeDateFormat;

import java.net.InetAddress;

public abstract class DOServerOperationContext 
  implements DOOperationContext
{
  private StringBuffer logEntryStr = new StringBuffer();
  
  public abstract InetAddress getClientAddress();
  
  
  /** Return a line of text that represents this request in the log file */
  public final synchronized String getLogEntry(ThreadSafeDateFormat dateFmt, boolean authenticated) {
    logEntryStr.setLength(0);
    logEntryStr.append(dateFmt.format(new java.util.Date()));
    logEntryStr.append(' ');
    InetAddress addr = getClientAddress();
    if(addr!=null) {
      logEntryStr.append(addr.getHostAddress());
    } else {
      logEntryStr.append('-');
    }
    logEntryStr.append(' ');
    logEntryStr.append(getCallerID());
    logEntryStr.append(' ');
    logEntryStr.append(authenticateCaller()?'y':'n');
    logEntryStr.append(' ');
    logEntryStr.append(getTargetObjectID());
    logEntryStr.append(' ');
    logEntryStr.append(getOperationID());
    logEntryStr.append(' ');
    HeaderSet params = getOperationHeaders();
    if(params!=null) 
      logEntryStr.append(params.toString().trim());
    return logEntryStr.toString();
  }
  
  /** Return a DOClient instance that forwards any authentication challenges back to
   * the DOP client on the other end of this connection.
   */
  public abstract DOClient getDOClientWithForwardedAuthentication(String clientID);
  
  
  /**
   * Return a String description of this operation
   */
  public String toString() {
    return ("operation: caller="+getCallerID()+"; op="+getOperationID()+
            "; obj="+getTargetObjectID()+"; params="+getOperationHeaders());
    //+"; connection="+doServer).trim();
  }
  
}
