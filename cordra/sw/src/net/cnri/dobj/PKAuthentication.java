/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.PublicKeyAuthenticationInfo;
import net.handle.hdllib.Util;

import java.security.Signature;
import java.security.*;
import java.io.*;


/**
 * A PKAuthentication object uses a private key to prove our identity.
 * The certificate credentials are handled as part of the AbstractAuthentication
 * parent class.
 */
public class PKAuthentication 
  extends AbstractAuthentication
{
  private PrivateKey myKey = null;
  private String myID = null;
  private Signature sig = null;
  private Signature rsaSig = null;
  private Signature dsaSig = null;

  /**
   * Construct an authentication object that uses the given
   * private key to prove that we are the entity identified
   * by myID.
   */
  public PKAuthentication(String myID, PrivateKey myKey) {
    this.myID = myID;
    this.myKey = myKey;
  }
  
  
  /** Return the private key that is used for authentication.  This is used when DOP 
    * is tunneled over SSL/TLS sockets that use public/private key certificates for
    * authentication. */
  PrivateKey getPrivateKey() {
    return this.myKey;
  }
  
  /** 
   * Returns this authentication in a form that will work with handle system
   * administration. */
  public net.handle.hdllib.AuthenticationInfo toHandleAuth() {
    return new PublicKeyAuthenticationInfo(Util.encodeString(myID), 300, myKey);
  }
  
  
  /** Returns the identifier representing the entity that is supposed
   * to be authenticated by this object.
   * @see net.cnri.dobj.DOAuthentication#getID()
   */
  public String getID() {
    return myID;
  }

  
  /** The following must be implemented by subclasses of AbstractAuthentication
   * in order to provide a copy of themselves for the purposes of 
   * authenticating when retrieving their credentials.  The returned object 
   * will have the autoRetrieveCredentials option turned off when retrieving 
   * the credentials in order to avoid a recursive loop when authenticating 
   * with the server that holds the credentials.
   */
  public AbstractAuthentication cloneAuthentication() {
    return new PKAuthentication(myID, myKey);
  }
  

  /**
   * Convenience method to read a private key from a file, decrypt that key with the given passphrase and
   * return a PKAuthentication object for the given ID.
   */
  public static final PKAuthentication readPKAuthenticationFromFile(String myID, String privateKeyFile, String passphrase)
    throws Exception
  {
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    FileInputStream fin = new FileInputStream(privateKeyFile);
    byte buf[] = new byte[256];
    int r;
    while((r=fin.read(buf))>=0) bout.write(buf, 0, r);
    buf = bout.toByteArray();
    buf = Util.decrypt(buf, passphrase==null ? null : Util.encodeString(passphrase));
    return new PKAuthentication(myID, Util.getPrivateKeyFromBytes(buf, 0));
  }


  public synchronized void initialize()
    throws Exception
  {
    getSig();
  }
  
  
  /* Signs the appropriate part (the nonce) of the given challenge message
   * using our private key, and puts the signature in the given response
   * message.
   * @see net.cnri.dobj.DOAuthentication#signChallenge(net.cnri.dobj.HeaderSet, net.cnri.dobj.HeaderSet)
   */
  public synchronized void signChallenge(HeaderSet challenge, HeaderSet response)
    throws Exception
  {
    byte nonceBytes[] = challenge.getHexByteArrayHeader("nonce", null);
    Signature s = getSig();
    s.update(nonceBytes);
    response.addHeader("auth_type", DOConstants.CLIENT_AUTH_TYPE_HSPUBKEY);
    response.addHeader("auth_response", s.sign());
    response.addHeader("auth_alg", s.getAlgorithm());
  }
  
  /** 
   * Get a (clone of) the singleton Signature object that has been initialized
   * with the private key of this object.
   */
  private synchronized Signature getSig()
    throws Exception
  {
    if(sig==null) {

      Signature tmpSig = Signature.getInstance(getSigAlgForKeyAlg(myKey.getAlgorithm()));
      tmpSig.initSign(myKey);
      sig = tmpSig;
      return tmpSig;
    }

    try {
      return (Signature)sig.clone();
    } catch (Exception e) {
      Signature tmpSig = Signature.getInstance(getSigAlgForKeyAlg(myKey.getAlgorithm()));
      tmpSig.initSign(myKey);
      return tmpSig;
    }
  }

  public static final String getSigAlgForKeyAlg(String keyAlg) {
    if(keyAlg.equalsIgnoreCase("RSA"))
      return "SHA1withRSA";
    else if(keyAlg.equalsIgnoreCase("DSA"))
      return "SHA1withDSA";
    return keyAlg;  // shouldn't get here
  }

  public String toString() {
    return getID()+" <private key>";
  }
  
}
