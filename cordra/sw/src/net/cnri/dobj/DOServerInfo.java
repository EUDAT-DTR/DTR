/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.*;
import java.security.*;

public class DOServerInfo {
  private DOServiceInfo service;
  private String hostAddress;
  private int port;
  private int sslPort = -1;
  private byte pubKeyBytes[];
  private PublicKey publicKey = null;
  private String serverID = null;
  private String serviceID;
  
  DOServerInfo(DOServiceInfo service, String hostAddress, int port, byte pubKeyBytes[]) {
    this.service = service;
    if(this.service!=null) this.serviceID = this.service.getServiceID();
    this.hostAddress = hostAddress;
    this.port = port;
    this.pubKeyBytes = pubKeyBytes;
  }

  DOServerInfo(String serviceID, String hostAddress, int port, byte pubKeyBytes[]) {
      this.serviceID = serviceID;
      this.hostAddress = hostAddress;
      this.port = port;
      this.pubKeyBytes = pubKeyBytes;
    }

  public String toString() {
    return "DO Service "+this.service.getServiceID()+"; server "+hostAddress+":"+port;
  }
  
  /** Returns a string that differentiates this server from the others
    * in the same service.  This ID only needs to be unique within the
    * service, and is used to identify servers for purposes such as
    * replication.
    */
  public String getServerID() {
    return serverID;
  }
  
  public String getServiceID() {
      return serviceID;
  }
  
  /** Return the service that contains this server. */
  public DOServiceInfo getService() {
    return service;
  }
  
  /** Sets the string that differentiates this server from the others
    * in the same service.  This ID only needs to be unique within the
    * service, and is used to identify servers for purposes such as
    * replication.
    */
  void setServerID(String newServerID) {
    this.serverID = newServerID;
  }
  
  
  /** Get the port that should be used for DOP-over-SSL communication.  Returns a number 
    * less than or equal to zero if no DOP-over-SSL communication should occur. */
  int getSSLPort() {
    return sslPort;
  }
  
  /** Set the port that should be used for DOP-over-SSL communication or a number less
    * than or equal to zero if no DOP-over-SSL communication should occur. */
  void setSSLPort(int sslPort) {
    this.sslPort = sslPort;
  }
  
  
  public String getHostAddress() { return hostAddress; }
  
  public int getPort() { return port; }
  
  public PublicKey getPublicKey()
    throws Exception
  {
    if(publicKey!=null) return publicKey;
    if(pubKeyBytes==null) return null;
    synchronized(this) {
      if(publicKey==null) {
        publicKey = Util.getPublicKeyFromBytes(pubKeyBytes, 0);
      }
    }
    return publicKey;
  }
  
}
