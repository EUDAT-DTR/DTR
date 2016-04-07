/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.net.*;

import java.security.*;
import java.security.cert.*;
import javax.net.ssl.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.handle.hdllib.Resolver;
import net.handle.util.X509HSCertificateGenerator;

/** This object is used to provide a private key for authentication to the other side
 * of a secure socket connection.
 */
public class DOSSLKeyManager extends X509ExtendedKeyManager {
  private static Logger logger = LoggerFactory.getLogger(DOSSLKeyManager.class);  
  
  private static final String DOP_AUTH_ALIAS = "DOP_AUTH_ALIAS";
  private volatile X509Certificate myCert = null;
  private PKAuthentication myAuth = null;
  private PrivateKey privKey = null;
  private PublicKey publicKey = null;
  
  public DOSSLKeyManager(PKAuthentication auth) 
    throws Exception
  {
      this(auth, null);
  }
  

  public DOSSLKeyManager(PKAuthentication auth, PublicKey pubKey)
  throws Exception
  {
    this.myAuth = auth;
    
    privKey = myAuth.getPrivateKey();
    
    if(!auth.getID().equals(DOConstants.ANONYMOUS_ID)) {
      String privKeyAlg = privKey.getAlgorithm();
      // generate a signature that will be used to verify a match with the public key
      Signature signer = Signature.getInstance(PKAuthentication.getSigAlgForKeyAlg(privKeyAlg));
      signer.initSign(privKey);
      byte sigTestData[] = "hello, how are you?".getBytes();
      signer.update(sigTestData);
      byte testSig[] = signer.sign();
        
      PublicKey[] pubKeys;
      if(pubKey==null) {
          Resolver temp = DOClient.getResolver();
          String tempID = auth.getID();
          pubKeys = temp.resolvePublicKeys(tempID);
      }
      else {
          pubKeys = new PublicKey[] { pubKey };
          pubKey = null;
      }
          
      for(int i=0; pubKeys!=null && i<pubKeys.length; i++) {
        if(pubKeys[i]==null) continue;
        if(!pubKeys[i].getAlgorithm().equals(privKeyAlg)) continue;
        
        Signature verifier = Signature.getInstance(PKAuthentication.getSigAlgForKeyAlg(privKeyAlg));
        verifier.initVerify(pubKeys[i]);
        verifier.update(sigTestData);
        if(verifier.verify(testSig)) {
          pubKey = pubKeys[i];
        }
      }
      
      if(pubKey==null) {
        throw new DOException(DOException.CRYPTO_ERROR, "Public keys resolved for "+
                              myAuth.getID()+" do not match supplied private key");
      }
    }
    this.publicKey = pubKey;
  }
  
  
  
  private X509Certificate getMyCert() {
      if(myCert==null) {
          synchronized(this) {
              if(myCert==null) {
                  try {
                      myCert = X509HSCertificateGenerator.generate(myAuth.getID(),publicKey,privKey);
                  } catch(Exception e) {
                      logger.error("Error generating certificate",e);
                      return null;
                  }
              }
          }
      }
      return myCert;
  }
  
  public String chooseClientAlias(String keyTypes[], Principal issuers[], Socket socket) {
      return DOP_AUTH_ALIAS;
  }

  public String chooseServerAlias(String keyType, Principal[] issuers, Socket socket) {
      return DOP_AUTH_ALIAS;
  }
  
  public String[] getServerAliases(String keyType, Principal[] issuers) {
      return new String[] { DOP_AUTH_ALIAS };
  }
  
  public X509Certificate[] getCertificateChain(String alias) {
      if(getMyCert()!=null) {
          return new X509Certificate[] { getMyCert() };
      } else {
          return new X509Certificate[0];
      }
  }
  
  public String[] getClientAliases(String keyType, Principal issuers[]) {
      return new String[] { DOP_AUTH_ALIAS };
  }
  
  public PrivateKey getPrivateKey(String alias) {
      return myAuth.getPrivateKey();
  }

  @Override
  public String chooseEngineClientAlias(String[] as, Principal[] aprincipal, SSLEngine sslengine) {
      return chooseClientAlias(as,aprincipal,null);
  }

  @Override
  public String chooseEngineServerAlias(String s, Principal[] aprincipal, SSLEngine sslengine) {
      return chooseServerAlias(s,aprincipal,null);
  }
}

