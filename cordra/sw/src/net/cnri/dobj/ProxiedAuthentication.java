/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.security.cert.Certificate;
import java.util.*;
import java.io.*;

public class ProxiedAuthentication 
  extends AbstractAuthentication
{
  private DOConnection conn;
  private String clientID;
  private Certificate certs[] = {};
  
  public ProxiedAuthentication(String clientID, DOConnection conn) {
    this.clientID = clientID;
    this.conn = conn;
  }
  
  
  
  /** Returns the identifier of the entity whose authentication is being proxied */
  public String getID() { return clientID; }
  
  /**
   * Signs the given challenge message and puts the result (including any required
   * parameters) into the given HeaderSet object.
   */
  public void signChallenge(HeaderSet challenge, HeaderSet response)
  throws Exception
  {
    System.err.println("Signing challenge: "+challenge);
    // now we just need to prove our identity by signing the nonce
    HeaderSet challengeFwd = new HeaderSet(challenge.getMessageType());
    challenge.copyInto(challengeFwd);
    HeaderSet authResponse = conn.sendControlMessage(challengeFwd, true);
    System.err.println("Received response, forwarding it to server: "+authResponse);
    
    authResponse.copyInto(response);
    
    // read and cache any credentials that are supplied
    ArrayList credList = new ArrayList();
    int numCreds = authResponse.getIntHeader("numcreds", 0);
    for(int i=0; i<numCreds; i++) {
      // read the credential from the input stream
      Certificate newCert = null;
      byte certBytes[] = authResponse.getHexByteArrayHeader("cred"+i, null);
      if(certBytes==null) continue;
      try {
        credList.add(getCertFactory().generateCertificate(new ByteArrayInputStream(certBytes)));
      } catch (Exception e) {
        System.err.println("error reading credential: "+e);
      }
    }
    this.certs = (Certificate[])credList.toArray(new Certificate[credList.size()]);
    
    // the proxied authentication will copy the certificates into the forwarded
    // authentication response, so we don't need to do that here
  }
  
  
  /**
   * Returns an empty list since the proxied authentication returns a list of credentials
   * in the signChallenge() method.
   */
  public Certificate[] getCredentials() { return certs; }
  
  /** 
   * Returns this authentication in a form that will work with handle system
   * administration. */
  public net.handle.hdllib.AuthenticationInfo toHandleAuth() {
    return null;
  }
  
  
  /** The following must be implemented by subclasses of AbstractAuthentication
   * in order to provide a copy of themselves for the purposes of 
   * authenticating when retrieving their credentials.  The returned object 
   * will have the autoRetrieveCredentials option turned off when retrieving 
   * the credentials in order to avoid a recursive loop when authenticating 
   * with the server that holds the credentials.
   */
  public AbstractAuthentication cloneAuthentication() {
    return new ProxiedAuthentication(this.clientID, this.conn);
  }
  
}
