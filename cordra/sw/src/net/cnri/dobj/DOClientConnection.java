/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.DHParameterSpec;

import java.io.*;
import java.math.BigInteger;
import java.security.*;
import java.security.spec.*;

/**
 * This class provides the interface for client software to
 * communicate using the DO protocol with a specific digital
 * object server.
 */
public class DOClientConnection 
  extends DOConnection 
{
  private static final String LOG_OPERATIONS_PROPERTY = "do.log_all_ops_dir";
  private static final int NONCE_RANDOM_SIZE = 20;
  
  // client-specific fields
  private String serverHandle = null;
  private DOServiceInfo service = null;
  private DOServerInfo connectedServer = null;
  private boolean encryptConnection = true;
  private static DHParameterSpec dhParams; 
  
  private static KeyPairGenerator keyExchangePairGen = null;
  private static String SESSION_KEY_PAIR_LOCK = "sessionKeyExchangePair";
  
  private static File logDir = null;
  private static OutputStream logOut = null;
  private static long logIDCounter = 1;
  
  static {
    try {
      String logDirStr = System.getProperty(LOG_OPERATIONS_PROPERTY, null);
      if(logDirStr!=null) {
        File tmp = new File(logDirStr);
        if(tmp.isDirectory()) {
          logOut = new FileOutputStream(new File(tmp, "op_log"));
          logDir = tmp;
          System.err.println("Writing connection logs to directory: "+logDirStr);
        }
      }
      
    } catch (Exception t) {
      t.printStackTrace(System.err);
    }
    
    // initialize the session key exchange pair
    // RS: This is way too slow on Android; so let us let users initialize when they want
//    new Thread(new Runnable() {
//      public void run() {
//        try {
//          getKeyExchangePairGenerator().generateKeyPair();
//        } catch (Exception e) {
//          System.err.println("Unable to initialize DH key pair");
//          e.printStackTrace(System.err);
//        }
//      }
//    }).start();
  }
  
  /**
    * Instantiate a new DOClientConnection with the specified identity and
    * authentication information.
    */
  public DOClientConnection(DOAuthentication authentication)
    throws DOException
  {
    super(authentication);
  }
  
  /** If connected to a service by identifier, return that identifier */
  public String getServiceID() {
    return this.serverHandle;
  }
  
  /** If connected to ta service, return the service information */
  public DOServiceInfo getServiceInfo() {
    return this.service;
  }
  
  /** If connected to a server, return the information for the server to which
    * we are connected */
  public DOServerInfo getServerInfo() {
    return this.connectedServer;
  }
  
  /** Sets whether or not this connection will be encrypted.  This has no effect
    * on the current connection if the encryption/authentication handshake has
    * already taken place. */
  public void setUseEncryption(boolean encrypt) {
    this.encryptConnection = encrypt;
  }
  
  /** Sets the Diffie-Hellman parameters to be used for encryption.  This has no effect
   * on any current connections if the encryption/authentication handshake has
   * already taken place. */
  public static void setEncryptionParameters(DHParameterSpec dhParams) {
      synchronized(SESSION_KEY_PAIR_LOCK) {
          DOClientConnection.dhParams = dhParams;
          keyExchangePairGen = null;
      }
  }
  
  /** Pre-computed parameters for use with {@link #setEncryptionParameters(DHParameterSpec) setEncryptionParameters}
   * on slow clients. */
  public static final DHParameterSpec RFC_2539_WELL_KNOWN_GROUP_2;
  static {
      StringBuilder sb = new StringBuilder();
      sb.append("FFFFFFFFFFFFFFFFC90FDAA22168C234C4C6628B80DC1CD1");
      sb.append("29024E088A67CC74020BBEA63B139B22514A08798E3404DD");
      sb.append("EF9519B3CD3A431B302B0A6DF25F14374FE1356D6D51C245");
      sb.append("E485B576625E7EC6F44C42E9A637ED6B0BFF5CB6F406B7ED");
      sb.append("EE386BFB5A899FA5AE9F24117C4B1FE649286651ECE65381");
      sb.append("FFFFFFFFFFFFFFFF");

      BigInteger p = new BigInteger(sb.toString(), 16);
      BigInteger g = BigInteger.valueOf(2);
      RFC_2539_WELL_KNOWN_GROUP_2 = new DHParameterSpec(p,g);
  }

 /** Connect directly to one of a the servers in the service identified
    * by the given identifier, if we aren't already connected. */
  public synchronized final void reconnect(String serverHandle)
    throws DOException
  {
    if(isOpen()) return;
    try { close(); } catch (Exception e) { }
    connect(serverHandle);
  }
  
  /** Connect directly to one of a the servers in the service */
  public synchronized final void reconnect(DOServiceInfo service)
  throws DOException
  {
      if(isOpen()) return;
      try { close(); } catch (Exception e) { }
      connect(service);
  }

  /** Connect directly to one of the servers in the service identified
    * by the given identifier. */
  public synchronized final void connect(String serverHandle)
    throws DOException
  {
    DOServiceInfo connService = new DOServiceInfo(serverHandle);
    
    int currentServerNum = 0;
    for(int i=0; i<connService.getServerCount(); i++) {
      try {
        connect(connService.getServer(i));
        this.service = connService;
      } catch (DOException e) {
        // if this was the last server, re-throw the exception
        if(i >= connService.getServerCount()-1) {
          throw e;
        }
      }
      
      if(connectedServer!=null) break;
    }
    
    if(connectedServer==null) { // no servers were found for this handle...
      throw new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR,
                            "No servers left to contact");
    }
    
    this.serverHandle = serverHandle;
  }
  
  /** Connect directly to one of the servers in the service */
 public synchronized final void connect(DOServiceInfo connService)
   throws DOException
 {
   int currentServerNum = 0;
   for(int i=0; i<connService.getServerCount(); i++) {
     try {
       connect(connService.getServer(i));
       this.service = connService;
     } catch (DOException e) {
       // if this was the last server, re-throw the exception
       if(i >= connService.getServerCount()-1) {
         throw e;
       }
     }
     
     if(connectedServer!=null) break;
   }
   
   if(connectedServer==null) { // no servers were found for this handle...
     throw new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR,
                           "No servers left to contact");
   }
   
   this.serverHandle = connService.getServiceID();
 }
  
  /** Connect directly to the given server.  This is used when a connection to a
    * specific server is necessary.  Clients should generally use the other
    * connect() methods that only specify the service ID. */
  public synchronized final void connect(DOServerInfo server) 
    throws DOException
  {
    if(DEBUG) System.err.println("connecting to server: "+server);
    super.connectAsClient(server);
    
    if(DEBUG) System.err.println("authenticating server");
    authenticateServer(server, encryptConnection);
    
    if(DEBUG) System.err.println("done authenticating server");
    this.connectedServer = server;
  }
  
  private static KeyPairGenerator getKeyExchangePairGenerator()
    throws Exception
  {
    if(keyExchangePairGen!=null)
      return keyExchangePairGen;
    
    synchronized(SESSION_KEY_PAIR_LOCK) {
      if(keyExchangePairGen==null) {
        // Some central authority creates new DH parameters
        //System.err.println("Creating Diffie-Hellman parameters (go get some coffee)");
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("DH");
        if(dhParams==null) kpg.initialize(512);
        else kpg.initialize(dhParams);
        kpg.generateKeyPair(); // It seems some implementations don't actually initialize until this point
        keyExchangePairGen = kpg;
      }
      return keyExchangePairGen;
    }
  }
  
  /** 
   * Verifies the identity of the server and uses that identity to set
   * up encryption for the connection (if requested)
   */
  protected void authenticateServer(DOServerInfo server, boolean encryptConnection)
    throws DOException
    {
      if(getSocket() instanceof javax.net.ssl.SSLSocket) {
          // if the socket is an SSLSocket then the server has already been authenticated
          // and encryption established using the DOSSLTrustManager
          return;
      }

      synchronized(controlOut) {
          synchronized(sockOut) {
              // lock the output stream so that no other messages can be sent
              // while we are authenticating and establishing encryption

              HeaderSet authRequest = new HeaderSet(AUTHENTICATE_COMMAND);
              KeyPair sessExchangePair = null;
              try {

                  authRequest.addHeader(ENTITY_ID_HEADER, this.auth.getID());
                  authRequest.addHeader(SETUP_ENCRYPTION_FLAG, encryptConnection);

                  DHPrivateKey privKey = null;
                  DHPublicKey pubKey = null;
                  if(encryptConnection) {
                      // if the connection should be encrypted, send the DH public key
                      // that the server can use to encrypt and return a session key
                      sessExchangePair = getKeyExchangePairGenerator().generateKeyPair();
                      privKey = (DHPrivateKey)sessExchangePair.getPrivate();
                      pubKey = (DHPublicKey)sessExchangePair.getPublic();
                      authRequest.addHeader("public_key", pubKey.getEncoded());
                      authRequest.addHeader("public_key_alg", "DH");
                  }

                  byte nonceBytes[] = new byte[NONCE_RANDOM_SIZE + 8];
                  ConnectionEncryption.getRandom().nextBytes(nonceBytes);    
                  net.handle.hdllib.Encoder.writeLong(nonceBytes, NONCE_RANDOM_SIZE, 
                          System.currentTimeMillis()/1000);
                  authRequest.addHeader("nonce", nonceBytes);

                  // send the authentication request
                  HeaderSet authResponse = super.sendControlMessage(authRequest, true);
                  if(authResponse==null) {
                      throw new DOException(DOException.PROTOCOL_ERROR,
                      "Server did not respond to authentication request");
                  }

                  // extract the session key for encryption and tell the connection
                  // to start encrypting and decrypting every message
                  if(encryptConnection) {
                      // decrypt the session key using the DH exchange private key

                      KeyAgreement ka = KeyAgreement.getInstance("DH");
                      ka.init(privKey);

                      // get the DH public key from the other side and combine it with our
                      // public DH key to create the secret key
                      KeyFactory kf = KeyFactory.getInstance("DH");
                      byte otherPubKeyBytes[] = 
                          authResponse.getHexByteArrayHeader("public_key", null);
                      java.security.spec.X509EncodedKeySpec x509KeySpec = 
                          new X509EncodedKeySpec(otherPubKeyBytes);
                      PublicKey otherPubKey = kf.generatePublic(x509KeySpec);
                      ka.doPhase(otherPubKey, true);
                      byte sessionKey[] = ka.generateSecret();
                      GenericEncryption encryptor = new GenericEncryption(this);
                      authResponse.addHeader("cryptsecretkey", sessionKey);
                      encryptor.setParameters(authResponse);
                      super.setEncryption(encryptor);
                  }

                  // verify the authenticity of the signature in the server's response
                  byte responseSigBytes[] = authResponse.getHexByteArrayHeader("auth_response", null);
                  PublicKey[] serverPubKeys = { server.getPublicKey() };

                  if(serverPubKeys[0]==null) {
                      serverPubKeys = DOConnection.getResolver().resolvePublicKeys(server.getServiceID());
                  }

                  boolean verified = false;
                  for(PublicKey serverPubKey : serverPubKeys) {
                      //String alg = authResponse.getStringHeader("auth_alg", "SHA1withDSA");
                      Signature sig = Signature.getInstance(PKAuthentication.getSigAlgForKeyAlg(serverPubKey.getAlgorithm()));
                      sig.initVerify(serverPubKey);

                      sig.update(nonceBytes);
                      if(sig.verify(responseSigBytes)) {
                          verified = true;
                          break;
                      }
                  }
                  if(!verified) {
                      throw new DOException(DOException.CRYPTO_ERROR, "Server authentication failed");
                  }
              } catch (Exception e) {
                  e.printStackTrace(System.err);
                  if(e instanceof DOException)
                      throw (DOException)e;
                  else
                      throw new DOException(DOException.CRYPTO_ERROR, 
                              "Error creating authentication request: "+e);
              }
          }
      }
    }
  
  /** 
   * Submit a new operation on a new channel and return an InputStream from which
   * the results of the operation can be read.
   */ 
  public StreamPair performOperation(String objectID, String operationID)
    throws DOException
  {
    return performOperation(objectID, operationID, null);
  }
    
  /** 
   * Submit a new operation on a new channel and return an InputStream from which
   * the results of the operation can be read.
   */ 
  public StreamPair performOperation(String objectID, String operationID,
                                     HeaderSet operationParameters) 
    throws DOException
  {
    if(connectedServer==null) {
      throw new DOException(DOException.APPLICATION_ERROR,
                            "DOClientConnection must connect to a service "+
                            "before performing an operation");
    }
    try {
      if(DEBUG) System.err.println("opening new channel");
      StreamPair newPair = super.getNewChannel();
      if(DEBUG) System.err.println("got new channel: "+newPair);
      OutputStream out = newPair.getOutputStream();
      InputStream in = newPair.getInputStream();
      HeaderSet reqHdrs = new HeaderSet("do");
      reqHdrs.addHeader("callerid", auth.getID());
      reqHdrs.addHeader("objectid", objectID);
      reqHdrs.addHeader("operationid", operationID);
      reqHdrs.addHeader("params", operationParameters);
      if(DEBUG) System.err.println("sending op request: "+reqHdrs);
      reqHdrs.writeHeaders(out);
      out.flush();
      if(DEBUG) System.err.println("reading op response...");
      HeaderSet headers = new HeaderSet();
      headers.readHeaders(in);
      if(DEBUG) System.err.println("got operation response: "+headers);
      
      if(logOut!=null && serverHandle!=null) {
        try { // try to write the log entry, but don't worry if it fails
          synchronized (logOut) {
            long logID = logIDCounter++;
            logOut.write(String.valueOf(logID).getBytes());
            logOut.write((byte)'$');
            logOut.write(serverHandle.getBytes());
            logOut.write((byte)'$');
            reqHdrs.writeHeaders(logOut);
            logOut.flush();
            FileOutputStream echoIn = 
              new FileOutputStream(new File(logDir, ""+logID+".input"));
            FileOutputStream echoOut = 
              new FileOutputStream(new File(logDir, ""+logID+".output"));
            in = new net.cnri.io.EchoInputStream(in, echoIn);
            out = new net.cnri.io.EchoOutputStream(out, echoOut);
            newPair = new StreamPair(in, out);
          }
        } catch (Exception t) {
          System.err.println("Error writing DO operation log entry: "+t);
        }
      }
      
      // check the headers...
      if(headers.getStringHeader("status", "").equalsIgnoreCase("error")) {
        try { out.close(); } catch (Exception e) {}
        try { in.close(); } catch (Exception e) {}
        throw new DOException(headers.getIntHeader("code", DOException.PROTOCOL_ERROR),
                              headers.getStringHeader("message",""));
      }
      
      newPair.setName("op:obj="+objectID+"; op="+operationID+"; params="+operationParameters);
      
      return newPair;
    } catch (IOException e) {
      if(e instanceof DOException) {
        throw (DOException)e;
      } else {
        throw new DOException(DOException.NETWORK_ERROR, "Error invoking operation obj="+objectID+"; op="+operationID+"; error="+e);
      }
    }
  }
  
  /** 
   * Sets up a connection with the given server.  This verifies the 
   * authenticity of the server using the public key contained in
   * the given DOServerInfo object.
   * 
   * This method will:
   *    1) Authenticate the repository of the object being operated upon,
   *	2) establish an encrypted connection to that repository,
   *	3) provide our authentication to the repository, 
   *	4) forward everything that was written to the "input" stream to the 
   *	repository (in digitally signed "chunks"), and 
   *	5) Verify the repository's signature of any bytes that are recieved and 
   *	forward them to the InputStream that is returned from this operation.
   */
  /*
  private synchronized boolean setupConnection() 
    throws Exception
  {
    // get the control channel
    StreamPair controlChannel = super.getNewChannel();
    
    OutputStream out = controlChannel.getOutputStream();
    OutputStreamWriter writer = null;
    try {
      writer = new OutputStreamWriter(out, "UTF8");
    } catch (UnsupportedEncodingException e) {
      writer = new OutputStreamWriter(out);
    }
    
    HeaderSet reqHeaders = new HeaderSet("setupconnection");
     
    reqHeaders.addHeader(SERVER_ID_HEADER, this.serverHandle);
    reqHeaders.addHeader(MY_ID_HEADER, getAuth().getID());
    
    HeaderSet response = sendControlMessage(reqHeaders, true);
    System.err.println("Need to check the result of setupconnection: "+response);
    
    // TODO: check the result!
    
    return true;
  }
  */
}




