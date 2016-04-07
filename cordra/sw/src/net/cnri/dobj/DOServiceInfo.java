/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.*;
import net.cnri.simplexml.*;
import java.util.Vector;
import java.io.StringReader;

public class DOServiceInfo {
  private static final byte ALIAS_TYPE[] = Util.encodeString("HS_ALIAS");
  private static final byte OBJ_SVR_TYPE[] = Util.encodeString(DOConstants.OBJECT_SVRINFO_HDL_TYPE);
  private static final byte REPO_URL_QUERY[][] = { OBJ_SVR_TYPE, ALIAS_TYPE, Common.STD_TYPE_HSPUBKEY };
  private static final String REPO_URL_TYPES[] = { 
    DOConstants.OBJECT_SVRINFO_HDL_TYPE,
    "HS_ALIAS", "HS_PUBKEY"
  };
  
  private String serviceHandle;
  private String aliasedHandle = null;
  private DOServerInfo servers[] = null;
  
  public DOServiceInfo(String serviceHandle)
    throws DOException
  {
    this.serviceHandle = serviceHandle;
    resolve();
  }

  public DOServiceInfo(String serviceHandle, DOServerInfo[] servers)
  {
      this.serviceHandle = serviceHandle;
      this.servers = servers;
  }

  /** Convenience constructor for a service with a single server */
  public DOServiceInfo(String serviceHandle, String host, int port, byte[] pubKeyBytes)
  {
      this.serviceHandle = serviceHandle;
      this.servers = new DOServerInfo[] { new DOServerInfo(this,host,port,pubKeyBytes) };
  }
  
  /** Return the identifier that was resolved to get this service */
  public String getServiceID() {
    return this.serviceHandle;
  }
  
  private void resolve()
    throws DOException
  {
    if(servers!=null) return;
    synchronized(this) {
      if(servers!=null) return;
      
      Resolver resolver = DOConnection.getResolver();
      aliasedHandle = serviceHandle;
      int aliasCount = 10; // the maximum number of alias-redirects allowed
      boolean resolveAlias = true;
      while(resolveAlias) {
        resolveAlias = false;
        try {
          HandleValue values[] = DOConnection.getResolver().resolveHandle(serviceHandle,
                                                                          REPO_URL_TYPES,
                                                                          true);
          // check for aliases
          for(int i=0; aliasCount>=0 && values!=null && i<values.length; i++) {
            if(values[i].hasType(ALIAS_TYPE)) {
              aliasedHandle = Util.decodeString(values[i].getData());
              aliasCount--;
              resolveAlias = true;
              break;
            }
          }
          if(resolveAlias) continue;  // break into the outer while loop
          
          // if no more aliases, get the service info
          for(int i=0; values!=null && i<values.length; i++) {
            if(values[i].hasType(OBJ_SVR_TYPE)) {
              servers = decodeServerInfo(values[i].getData());
            }
          }
        } catch (Exception e) {
          //System.err.println("Error locating site '"+serviceHandle+"': "+e);
          //e.printStackTrace(System.err);
          throw new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR, 
                                "Unable to find object server info for: "+serviceHandle+
                                "; error: ", e);
        }
      }
    }
  }
  
    
  /**
   * Parses the server information from an byte array containing UTF-8 encoded
   * XML with the following form.
   * TODO: Create DTD and document this format
   * 
   * <serverinfo>
   *  <server>
   *   <id>cnri-1</id>
   *   <label>CNRI Repository</label>
   *   <publickey>128927398472398749823749872983749823742348237423</publickey>
   *   <hostaddress>132.151.9.17</hostaddress>
   *   <port>9901</port>
   *  </server>
   * </serverinfo>
   */    
  private DOServerInfo[] decodeServerInfo(byte data[])
    throws Exception
  {
    XTag serverInfoTag = new XParser().parse(new StringReader(Util.decodeString(data)), false);
    
    Vector serverList = new Vector();
    for(int i=0; i<serverInfoTag.getSubTagCount(); i++) {
      XTag svrTag = serverInfoTag.getSubTag(i);
      if(!svrTag.getName().equalsIgnoreCase("SERVER")) continue;
      byte pubKeyBytes[] = null;
      String pubKeyString = svrTag.getStrSubTag("publickey", null);
      if(pubKeyString!=null) pubKeyBytes = Util.encodeHexString(pubKeyString);
      String address = svrTag.getStrSubTag("hostaddress", "");
      int port = svrTag.getIntSubTag("port", 9901);
      DOServerInfo serverInfo = new DOServerInfo(this, address, port, pubKeyBytes);
      serverInfo.setServerID(svrTag.getStrSubTag("id", null));
      serverInfo.setSSLPort(svrTag.getIntSubTag("ssl-port", -1));
      serverList.addElement(serverInfo);
    }
    DOServerInfo servers[] = new DOServerInfo[serverList.size()];
    serverList.copyInto(servers);
    return servers;
  }
  
  
  public int getServerCount() {
    return servers==null ? 0 : servers.length;
  }
  
  public DOServerInfo getServer(int serverNum) {
    if(serverNum<0 || servers==null || serverNum >= servers.length)
      return null;
    return servers[serverNum];
  }
  
  
}
