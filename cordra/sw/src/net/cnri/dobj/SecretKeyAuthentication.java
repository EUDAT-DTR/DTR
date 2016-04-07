/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.Util;
import net.handle.hdllib.SecretKeyAuthenticationInfo;
import java.security.*;

/**
 * A PKAuthentication object uses a secret key to prove our identity.
 * The certificate credentials are handled as part of the AbstractAuthentication
 * parent class.
 */
public class SecretKeyAuthentication 
  extends AbstractAuthentication
{
  private static final int CLIENT_NONCE_SIZE = 20;
  
  private byte secretKeyBytes[] = null;
  private String myID = null;
  
  private static MessageDigest md5Digest = null;
  private static MessageDigest sha1Digest = null;
  private static boolean initialized = false;
  private static final Object DIGEST_LOCK = new Object();
  
  
  /**
   * Construct an authentication object that uses the given
   * private key to prove that we are the entity identified
   * by myID.
   */
  public SecretKeyAuthentication(String myID, byte secretKey[]) {
    this.myID = myID;
    this.secretKeyBytes = secretKey;
  }
  
  /** 
   * Returns this authentication in a form that will work with handle system
   * administration. */
  public net.handle.hdllib.AuthenticationInfo toHandleAuth() {
    return new SecretKeyAuthenticationInfo(Util.encodeString(myID), 0, secretKeyBytes);
  }

  /** The following must be implemented by subclasses of AbstractAuthentication
   * in order to provide a copy of themselves for the purposes of 
   * authenticating when retrieving their credentials.  The returned object 
   * will have the autoRetrieveCredentials option turned off when retrieving 
   * the credentials in order to avoid a recursive loop when authenticating 
   * with the server that holds the credentials.
   */
  public AbstractAuthentication cloneAuthentication() {
    return new SecretKeyAuthentication(myID, secretKeyBytes);
  }
  
  
  /** Returns the identifier representing the entity that is supposed
   * to be authenticated by this object.
   * @see net.cnri.dobj.DOAuthentication#getID()
   */
  public String getID() {
    return myID;
  }
  
  /* Signs the appropriate part (the nonce) of the given challenge message
   * using our secret key, and puts the signature in the given response
   * message.
   * @see net.cnri.dobj.DOAuthentication#signChallenge(net.cnri.dobj.HeaderSet, net.cnri.dobj.HeaderSet)
   */
  public synchronized void signChallenge(HeaderSet challenge, HeaderSet response)
    throws Exception
  {
      signChallenge(challenge,response,false);
  }

  /** Use md5 to create the digest regardless of what algorithm is advertised.  Expected by old broken servers. */
  synchronized void oldBrokenSignChallenge(HeaderSet challenge, HeaderSet response) throws Exception {
      signChallenge(challenge,response,true);
  }
  
  private synchronized void signChallenge(HeaderSet challenge, HeaderSet response, boolean oldServer) throws Exception {
      if(!initialized) {
          synchronized(DIGEST_LOCK) {
              md5Digest = MessageDigest.getInstance("MD5");
              sha1Digest = MessageDigest.getInstance("SHA1");
              initialized = true;
          }
      }

      byte nonce[] = challenge.getHexByteArrayHeader("nonce", null);

      // use the same hash algorithm as the handle protocol so that the response
      // can be verified with a VerifyAuthRequest message using the handle protocol

      String digestAlgStr = challenge.getStringHeader("alg", "sha1");

      byte digestAlg;
      MessageDigest digest;
      if(digestAlgStr.equalsIgnoreCase("md5")) {
          digestAlg = net.handle.hdllib.Common.HASH_CODE_MD5;
          digest = md5Digest;
      } else if(digestAlgStr.equalsIgnoreCase("sha1")) {
          digestAlg = net.handle.hdllib.Common.HASH_CODE_SHA1;
          digest = oldServer ? md5Digest : sha1Digest; // work with old broken servers
      } else {
          throw new DOException(DOException.CRYPTO_ERROR,
                  "Unknown digest algorithm: '"+digestAlgStr+"'");
      }

      // put some random bits into the client nonce so that we can't be
      // tricked into authenticating a handle authentication challenge from
      // a potential man-in-the-middle
      byte clientNonce[] = new byte[CLIENT_NONCE_SIZE];
      ConnectionEncryption.getRandom().nextBytes(clientNonce);

      synchronized(digest) {
          digest.reset();
          digest.update(secretKeyBytes);
          digest.update(nonce);

          // would normally add the original request digest here but since the
          // server is verified using its public key, we only need to prevent
          // the server from tricking us into signing a challenge for some other
          // operation, thus the client-generated nonce
          digest.update(clientNonce);

          digest.update(secretKeyBytes);

          response.addHeader("auth_type", DOConstants.CLIENT_AUTH_TYPE_HSSECKEY);
          response.addHeader("digest_alg", digestAlgStr);
          response.addHeader("client_nonce", clientNonce);
          response.addHeader("auth_response", digest.digest());
      }
          }

  public String toString() {
    return getID()+" <secret key>";
  }
  
  
  /**
   * Retrieve the credentials from this user's digital object so that they can be
   * presented to any servers during authentication.
   */
  public java.security.cert.Certificate[] getCredentials() {
    return null;
  }
  
}
