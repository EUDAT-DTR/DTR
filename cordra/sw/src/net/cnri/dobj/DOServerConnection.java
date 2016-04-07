/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.cnri.dobj.delegation.DelegationClient;
import net.handle.hdllib.*;
import net.handle.util.X509HSTrustManager;

import java.io.*;
import java.net.*;
import javax.net.ssl.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.*;
import java.security.cert.Certificate;
import java.security.cert.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


/**
 * This class provides the interface for server software to
 * communicate using the DO protocol with a digital object
 * client.
 */
public class DOServerConnection 
  extends DOConnection 
{
  private static Logger logger = LoggerFactory.getLogger(DOServerConnection.class);
  private static final int NONCE_RANDOM_SIZE = 20;
  private static final Object DIGEST_LOCK = new Object();
  private static MessageDigest md5Digest; // lazily initialized
  private static MessageDigest sha1Digest; // lazily initialized
  private static volatile boolean initializedDigests = false;

  private final Map<String,PublicKey[]> cachedPubkeys = new HashMap<String,PublicKey[]>(); // never cleared, perhaps harmless

  // client-specific fields
  private final String serverHandle = null;
  private final int port = 9901;
  private final Map<String,Client> cachedAuthIDs = new ConcurrentHashMap<String,Client>();
  private CertificateFactory certFactory = null; // lazily initialized
  private final Hashtable connectionLocalData = new Hashtable();
  private final boolean usingSSL;
  private final DelegationClient delegationClient;

  private transient Client currentClient = null;

  private static class Client {
      final String clientID;
      final Map<String,Certificate> credentialCache;
      final List<String> delegatedIDsList;
      
      Client(String clientID, Map<String,Certificate> credentialCache, List<String> delegatedIDsList) {
          this.clientID = clientID;
          this.credentialCache = credentialCache;
          this.delegatedIDsList = delegatedIDsList;
      }
  }
  
  public DOServerConnection(DOAuthentication authentication, Socket connection)
    throws Exception
  {
      this(authentication, connection, null);
  }

  /** Construct a server-side connection handler using the given socket.  This 
    * also sets the given listeners for the connection before any authentication
    * is done in order to avoid missing any open-channel events. */
  public DOServerConnection(DOAuthentication authentication, Socket connection,
                            DOConnectionListener listener)
    throws Exception
  {
      this(authentication, connection, listener, null);
  }
   
  public DOServerConnection(DOAuthentication authentication, Socket connection,
              DOConnectionListener listener, DelegationClient delegationClient)
      throws Exception
  {
    super(authentication);
    if(listener!=null) setListener(listener);
    
    // if the connection is an SSLSocket then use that for authentication and encryption
    this.usingSSL = connection instanceof javax.net.ssl.SSLSocket;
   
    if(delegationClient==null) this.delegationClient = new DelegationClient(new DOClient(authentication), true, authentication.getID(), true);
    else this.delegationClient = delegationClient;
    
    super.connectAsServer(connection);
  }
  
  
  /** Initialize the MessageDigest objects.  In this case we use static digests
    * because they are only used once per connection, and even then only for
    * secret-key authentication.  Rather than use a new digest object for every
    * connection, we re-use them and risk a bit of slowdown if many threads
    * need to authenticate secret-key clients at the same time.
    */
  private static void initDigests() 
    throws Exception
  {
    if(!initializedDigests) {
      synchronized(DIGEST_LOCK) {
        md5Digest = MessageDigest.getInstance("MD5");
        sha1Digest = MessageDigest.getInstance("SHA1");
        initializedDigests = true;
      }
    }
  }
        

  
  /**
   * Returns a list of unverified IDs that the client claims as credentials.
   * Note: These IDs are not yet verified and the caller should call
   * authenticateCredential() with any credential IDs for whom they
   * assign any meaning.
   * Note2: This should only be called after authenticateClient returns.
   */
  public synchronized String[] getCredentialIDs() {
    if(currentClient==null) return new String[0];
    int numcreds = currentClient.credentialCache==null ? 0 : currentClient.credentialCache.size();
    int numdelegators = currentClient.delegatedIDsList==null ? 0 : currentClient.delegatedIDsList.size();
    
    if(numcreds==0 && numdelegators==0 && !delegationClient.getAutoDiscoverDelegation()) return null;
    
    ArrayList<String> resList = new ArrayList<String>();
    if(numcreds > 0) {
        resList.addAll(currentClient.credentialCache.keySet());
    }
    if(numdelegators > 0 || delegationClient.getAutoDiscoverDelegation()) {
        try {
            resList.addAll(delegationClient.allImplicitDelegators(currentClient.clientID,currentClient.delegatedIDsList));
        }
        catch(DOException e) {
            logger.warn("Exception finding delegators", e);
        }
    }
    return resList.toArray(new String[0]);
  }


  private boolean checkExplicitCredential(String credentialID) {
      if(currentClient==null) return false;
      if(currentClient.credentialCache==null) {
          return false;
        }
        Certificate cert = currentClient.credentialCache.get(credentialID);
        if(cert==null) {
          return false;
        }
        
        try {
          PublicKey credAuthKeys[] = resolvePublicKeys(credentialID);
          if(credAuthKeys==null || credAuthKeys.length<=0) {
            return false;
          }
          
          Exception verifyException = null;
          for(int i=0; i<credAuthKeys.length; i++) {
            try {
              cert.verify(credAuthKeys[i]);
              return true;
            } catch (Exception e) {
              verifyException = e;
            }
          }
          if(DEBUG) {
            System.err.println("Unable to verify credential for "+
                               credentialID+": "+verifyException);
          }
          return false;
        } catch (Exception e) {
          System.err.println("Unable to verify credential for "+credentialID+": "+e);
          return false;
        }
  }
  
  /**
   * Verify that this client has been granted a credential by the identified entity.
   * The given credentialID is expected to have come from the list returned by
   * getCredentialIDs().  Returns true iff a verified credential from credentialID
   * was granted to this client.
   */
  public boolean authenticateCredential(String credentialID) {
      if(currentClient==null) return false;
      if(checkExplicitCredential(credentialID)) return true;
      else {
          try {
              return delegationClient.checkImplicitDelegation(currentClient.clientID,currentClient.delegatedIDsList,credentialID);
          }
          catch(DOException e) {
              // ignore
              return false;
          }
      }
  }
  
  
  
  /** This method returns an SSLServerSocketFactory that can be used to create SSLSockets
   * that authenticate their side of the connection using handle public key authentication. */
  public static SSLServerSocketFactory getSecureServerSocketFactory(PKAuthentication authentication, PublicKey pubKey) 
    throws Exception
  {
    System.err.println("Setting Up ssl Context");
    SSLContext sslContext = SSLContext.getInstance("TLS");
    sslContext.init(new KeyManager[] { new DOSSLKeyManager(authentication, pubKey) },
                    new TrustManager[] { new DOSSLTrustManager() }, 
                    ConnectionEncryption.getRandom());
    System.err.println("Initialize ssl Context");
    return sslContext.getServerSocketFactory();
  }
  
  /**
   * This method is called by the digital object server to verify the
   * identity of the client using the public key assigned to the ID
   * of the client..
   */
  public boolean authenticateClient(String clientID, String storedPassword) throws DOException {
      return authenticateClient(clientID,storedPassword,null);
  }
 
  /**
   * This method is called by the digital object server to verify the
   * identity of the client using the public key assigned to the ID
   * of the client..
   */
  public boolean authenticateClient(String clientID, String storedPassword, PublicKey storedPublicKey)
    throws DOException
    {
      // shortcut the authentication if the client is the standard anonymous ID
      if(DOConstants.ANONYMOUS_ID.equalsIgnoreCase(clientID)) {
          return true;
      }

      // if the connection is not encrypted then authentication is susceptible
      // to man-in-the-middle attacks (attacker taking over after authentication
      // is performed) and therefore cannot be trusted
      //if(!isEncrypted()) {
      //  return false;
      //}

      Client authCacheVal = cachedAuthIDs.get(clientID);
      if(authCacheVal!=null) {
          currentClient = authCacheVal;
          return true;
      }


      boolean isAuthenticated = false;
      /*
    if(usingSSL) {
      SSLSocket sslSock = (SSLSocket)getSocket();
      SSLSession sslSession = sslSock.getSession();
      try {
        // we'll get a list of the peer certificates from the connection.  The DOSSLTrustManager
        // must have already verified the signatures on all certificates, so we just need to make
        // sure that the peer certificate belongs to the client
        Hashtable localCreds = new Hashtable();
        Certificate peerCerts[] = sslSession.getPeerCertificates();
        if(peerCerts!=null && peerCerts.length>0 && peerCerts[0] instanceof X509Certificate) {
          X509Certificate peerCert = (X509Certificate)peerCerts[0];
          String subject = CertUtil.extractHandleFromPrincipal(peerCert.getSubjectDN().getName());
          String issuer = CertUtil.extractHandleFromPrincipal(peerCert.getIssuerDN().getName());
          if(subject.equals(issuer) && subject.equals(clientID)) {
            isAuthenticated = true;
          }
        }
      } catch (Exception e) {
        System.err.println("Exception checking peer certificates: "+e+"; falling back to DOP authentication");
        e.printStackTrace(System.err);
      }
    }
       */

      if(!isAuthenticated) {
          HeaderSet authRequest = new HeaderSet(AUTHENTICATE_COMMAND);
          HeaderSet authResponse = null;
          try {
              authRequest.addHeader("entity_id", this.auth.getID());
              authRequest.addHeader("entityid", this.auth.getID());

              // generate an unpredictable random number for the client to sign
              byte nonceBytes[] = new byte[NONCE_RANDOM_SIZE]; //  + 8
              ConnectionEncryption.getRandom().nextBytes(nonceBytes);
              authRequest.addHeader("nonce", nonceBytes);

              // tell the client which digest algorithm to use for secret key auth
              String digestAlgStr = "sha1";
              authRequest.addHeader("digest_alg", digestAlgStr);

              authResponse = super.sendControlMessage(authRequest, true);
              if(authResponse==null) {
                  System.err.println("client timed out during authentication");
                  return false;
              }

              String authType = 
                  authResponse.getStringHeader("auth_type",
                          DOConstants.CLIENT_AUTH_TYPE_HSPUBKEY);
              if(authType.equalsIgnoreCase(DOConstants.CLIENT_AUTH_TYPE_HSPUBKEY)) {
                  // verify the authenticity of the signature in the server's response
                  byte responseSigBytes[] = authResponse.getHexByteArrayHeader("auth_response", null);

                  if(storedPublicKey!=null) {
                      String keyAlg = storedPublicKey.getAlgorithm();
                      Signature sig =
                          Signature.getInstance(PKAuthentication.getSigAlgForKeyAlg(keyAlg));
                      sig.initVerify(storedPublicKey);

                      sig.update(nonceBytes);
                      isAuthenticated = sig.verify(responseSigBytes);
                  }
                  if(!isAuthenticated) {
                      PublicKey[] clientPubKeys = resolvePublicKeys(clientID);
                      if(clientPubKeys==null || clientPubKeys.length<=0) return false;

                      for(int i=0; i<clientPubKeys.length; i++) {
                          String keyAlg = clientPubKeys[i].getAlgorithm();
                          Signature sig =
                                  Signature.getInstance(PKAuthentication.getSigAlgForKeyAlg(keyAlg));
                          sig.initVerify(clientPubKeys[i]);
                          
                          sig.update(nonceBytes);
                          isAuthenticated = sig.verify(responseSigBytes);
                          if(isAuthenticated) break;
                      }
                  }
              } else if(authType.equalsIgnoreCase(DOConstants.CLIENT_AUTH_TYPE_HSSECKEY)) {
                  String clientDigestAlg = authResponse.getStringHeader("digest_alg", digestAlgStr);
                  if(!clientDigestAlg.equalsIgnoreCase(digestAlgStr)) {
                      throw new DOException(DOException.CRYPTO_ERROR,
                      "Client used different digest algorithm than requested");
                  }
                  byte digestAlg = Common.HASH_CODE_SHA1;
                  if(digestAlgStr.equalsIgnoreCase("sha1")) {
                      digestAlg = Common.HASH_CODE_SHA1;
                  } else if(digestAlgStr.equalsIgnoreCase("md5")) {
                      digestAlg = Common.HASH_CODE_MD5;
                  } else {
                      throw new DOException(DOException.CRYPTO_ERROR,
                              "Unrecognized digest algorithm: "+digestAlgStr);
                  }
                  initDigests();
                  byte clientNonce[] = authResponse.getHexByteArrayHeader("client_nonce", null);
                  byte clientDigest[] = authResponse.getHexByteArrayHeader("auth_response", null);
                  if(clientDigest.length==Common.MD5_DIGEST_SIZE && getProtocolMajorVersion()==1 && getProtocolMinorVersion()<4) {
                      // compensate for buggy clients
                      digestAlg = Common.HASH_CODE_MD5;
                  }
                  byte signedResponse[] = new byte[clientDigest.length + 1];
                  signedResponse[0] = digestAlg;
                  System.arraycopy(clientDigest,0,signedResponse,1,clientDigest.length);
                  
                  VerifyAuthRequest vaReq = new VerifyAuthRequest(Util.encodeString(clientID),
                          nonceBytes, // nonce[]
                          clientNonce, // origreqdigest[]
                          digestAlg, // origDigestAlg
                          signedResponse, // signedResponse[]
                          0, // client handle index
                          null // authentication
                  );
                  if(storedPassword!=null) {
                      isAuthenticated = verifyPassword(storedPassword,vaReq);
                  }
                  else {
                      vaReq.certify = true;
                      AbstractResponse vaResponse = getResolver().getResolver().processRequest(vaReq);
                      if(vaResponse!=null && vaResponse instanceof VerifyAuthResponse) {
                          isAuthenticated = ((VerifyAuthResponse)vaResponse).isValid;
                      } else {
                          throw new DOException(DOException.NETWORK_ERROR,
                                  "Unable to verify secret key authentication; "+
                                  "Invalid verification response: "+vaResponse);
                      }
                  }
              } else {
                  throw new DOException(DOException.PROTOCOL_ERROR,
                          "Unrecognized authentication type: "+authType);
              }

              if(!isAuthenticated) {
                  // if we could not verify the client's identity then don't bother with
                  // any of their credentials
                  return false;
              }

              // read and verify any credentials that are supplied
              Hashtable localCreds = new Hashtable();
              int numCreds = authResponse.getIntHeader("numcreds", 0);
              for(int i=0; i<numCreds; i++) {
                  // read the credential from the input stream
                  Certificate newCert = null;
                  byte certBytes[] = authResponse.getHexByteArrayHeader("cred"+i, null);
                  if(certBytes==null) continue;
                  try {
                      newCert = getCertFactory().generateCertificate(new ByteArrayInputStream(certBytes));
                      if(newCert!=null && newCert instanceof X509Certificate) {
                          X509Certificate x509Cert = (X509Certificate)newCert;
                          String subject = X509HSTrustManager.parseIdentityHandle(x509Cert);
                          String issuer = X509HSTrustManager.parseIdentityHandle(x509Cert);

                          //System.err.println("Got cert: issuer='"+issuer+"'; subject='"+subject+"'; issuerobj='"+x509Cert.getIssuerDN().getClass()+"'");

                          if(subject.equals(clientID)) {
                              localCreds.put(issuer, newCert);
                          } else {
                              System.err.println("info: client "+clientID+" sent a certificate meant for "+
                                      x509Cert.getSubjectDN().getName());
                          }
                      }
                  } catch (Exception e) {
                      System.err.println("error reading credential: "+e);
                  }
              }

              String[] delegatedIDs = authResponse.getStringArrayHeader("delegatedids",null);

              this.currentClient = new Client(clientID,localCreds,delegatedIDs==null ? null : Arrays.asList(delegatedIDs));
          } catch (Exception e) {
//              System.err.println("Error authenticating: "+e+"; auth request: "+
//                      authRequest+"; authResponse: "+authResponse);
//
//              e.printStackTrace(System.err);
              if(e instanceof DOException)
                  throw (DOException)e;
              else
                  throw new DOException(DOException.CRYPTO_ERROR, 
                          "Error creating authentication request: ",e);
          }
      }

      if(isAuthenticated) {
          cachedAuthIDs.put(clientID, currentClient);
      }
      else {
          cachedAuthIDs.remove(clientID);
      }

      return isAuthenticated;
    }
  
  
  /**
   * Inserts an object into the connection-level information table.  This
   * causes subsequent calls to getConnectionMapping() on the same connection
   * (but not necessarily the same operation) with the same key to return
   * the given data value.
   */
  public void setConnectionMapping(Object mappingKey, Object mappingData) {
    connectionLocalData.put(mappingKey, mappingData);
  }
    
  /**
   * Returns the object from the connection-level information table that had
   * previously been used as the mappingData parameter for a call to
   * setConnectionMapping with the given mappingKey.  If no such object
   * had been inserted into the table then this will return null.
   */
  public Object getConnectionMapping(Object mappingKey) {
    return connectionLocalData.get(mappingKey);
  }

  protected void socketWasDisconnected() {
    connectionLocalData.clear();
//    System.gc();
//    System.runFinalization();
  }

  private synchronized CertificateFactory getCertFactory()
    throws CertificateException
  {
    if(certFactory!=null) return certFactory;
    certFactory = CertificateFactory.getInstance("X.509");
    return certFactory;
  }

  
  /** Securely resolve a public key for the given handle */
  public synchronized PublicKey[] resolvePublicKeys(String clientID) {
    if(cachedPubkeys.containsKey(clientID)) {
      return (PublicKey[])cachedPubkeys.get(clientID);
    }
    try {
      PublicKey keys[] = DOConnection.getResolver().resolvePublicKeys(clientID);
      
      if(keys==null || keys.length<=0) {
        System.err.println("No VALID public key is associated with ID "+clientID);
        return null;
      }
      
      cachedPubkeys.put(clientID, keys);
      return keys;
    } catch (Exception e) {
      System.err.println("error looking up public key for "+clientID+": "+e);
    }
    return null;
  }
  
  

  public static boolean verifyPassword(String storedPassword, VerifyAuthRequest req) throws HandleException {
      // Get the encoding type (hash algorithm) of the signature
      // and use that to get the signature.
      byte authSignature[];
      byte digestAlg = req.signedResponse[0];

      boolean oldFormat = ((req.majorProtocolVersion==5 && req.minorProtocolVersion==0) ||
              (req.majorProtocolVersion==2 && req.minorProtocolVersion==0));

      if(oldFormat) { // the old format - just the md5 digest, no length or alg ID
          digestAlg = Common.HASH_CODE_MD5;
          authSignature = req.signedResponse;

      } else { // new format w/ one byte md5 or sha1 identifiers
          switch(digestAlg) {
          case Common.HASH_CODE_MD5:
              authSignature = new byte[Common.MD5_DIGEST_SIZE];
              System.arraycopy(req.signedResponse, 1, authSignature, 0, Common.MD5_DIGEST_SIZE);
              break;
          case Common.HASH_CODE_SHA1:
              authSignature = new byte[Common.SHA1_DIGEST_SIZE];
              System.arraycopy(req.signedResponse, 1, authSignature, 0, Common.SHA1_DIGEST_SIZE);
              break;
          default:
              // could be an invalid hash type, but for now we'll assume that it is
              // just the old format - no hash type, just 16 bytes of md5
              ////// this should be changed to throw an exception once the old
              ////// clients are phased out.
              authSignature = req.signedResponse;
              digestAlg = Common.HASH_CODE_MD5;
              /**
               throw new HandleException(HandleException.MESSAGE_FORMAT_ERROR,
               "Invalid hash type in secret key signature: "+((int)digestAlg));
               */
          }
      }

      byte realSignature[] =
          Util.doDigest(digestAlg, Util.encodeString(storedPassword), req.nonce,
                  req.origRequestDigest, Util.encodeString(storedPassword));

      if(realSignature!=null && realSignature.length>0 && Util.equals(realSignature, authSignature)) {
          return true;
      }

      return false;

  }


}


