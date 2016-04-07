/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.Util;
import net.cnri.simplexml.*;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateException;
import java.security.cert.Certificate;
import java.security.*;
import java.util.ArrayList;
import java.io.*;

/**
 * Provides a partial implementation of the DOAuthentication interface that 
 * helps 
 
 Interface for objects that can be used to authenticate themselves to the 
 * other side of a DOConnection link.
 */
public abstract class AbstractAuthentication 
  implements DOAuthentication
{
  private static AbstractAuthentication anonymous = null;
  private static Object ANONYMOUS_LOCK = new Object();

  private static final long CREDENTIAL_CACHE_TIME = 1000*60*60; // one hour
  
  private transient java.security.cert.Certificate credentialCache[] = null;
  private boolean autoRetrieveCredentials = true;
  private transient boolean gettingCredentials = false;
  private transient CertificateFactory certFactory = null;
  private transient long lastCredentialUpdate = System.currentTimeMillis();
  private transient String CRED_CACHE_LOCK = String.valueOf(System.currentTimeMillis());
  

  
  /**
   * Sets whether or not this object will automatically retrieve
   * the client's credentials from the client DO when getCredentials()
   * is called.  If true, the credentials will also be refreshed
   * if the credentials were last retrieved over an hour before the
   * present time.
   */
  public void setAutoRetrieveCredentials(boolean autoRetrieve) {
    this.autoRetrieveCredentials = autoRetrieve;
  }

  /** The following must be implemented by subclasses of AbstractAuthentication
    * in order to provide a copy of themselves for the purposes of 
    * authenticating when retrieving their credentials.  The returned object 
    * will have the autoRetrieveCredentials option turned off when retrieving 
    * the credentials in order to avoid a recursive loop when authenticating 
    * with the server that holds the credentials.
    */
  public abstract AbstractAuthentication cloneAuthentication();
  
  
  public void setCredentials(Certificate[] credentials) {
    this.credentialCache = credentials;
  }
  
  
  /**
   * Retrieve the credentials from this user's digital object so that they can be
   * presented to any servers during authentication.
   */
  public Certificate[] getCredentials() {
    if(!autoRetrieveCredentials)
      return credentialCache;

    synchronized (CRED_CACHE_LOCK) {
      if(gettingCredentials) return credentialCache;
      
      if(System.currentTimeMillis() < lastCredentialUpdate+CREDENTIAL_CACHE_TIME &&
         credentialCache!=null) {
        return credentialCache;
      }
      
      ArrayList newCreds = new ArrayList();
      gettingCredentials = true;
      DOClient doClient = null;
      try {
        AbstractAuthentication altAuth = cloneAuthentication();
        altAuth.setAutoRetrieveCredentials(false);
        doClient = new DOClient(altAuth);
        
        // perform the operation to retrieve the credentials
        StreamPair io = 
          doClient.performOperation(null, getID(), 
                                    DOConstants.GET_CREDENTIALS_OP_ID, null);
        io.getOutputStream().close();
        
        // read the list of credentials from the XML response
        XTag pubXMLCertList =
          new XParser().parse(new InputStreamReader(io.getInputStream(), "UTF8"), false);
        io.getInputStream().close();
        for(int i=0; i<pubXMLCertList.getSubTagCount(); i++) {
          XTag subtag = pubXMLCertList.getSubTag(i);
          if(subtag.getName().equalsIgnoreCase("certificate")) {
            try {
              String encodedCert = subtag.getStrValue();
              InputStream certIn =
                new ByteArrayInputStream(Util.encodeHexString(encodedCert));
              newCreds.add((X509Certificate)getCertFactory().generateCertificate(certIn));
            } catch (Exception e) {
              System.err.println("Invalid certificate error "+e+" on cert: "+subtag);
            }
          }
        }
        credentialCache = (Certificate[])newCreds.toArray(new Certificate[newCreds.size()]);
        lastCredentialUpdate = System.currentTimeMillis();
      } catch (Exception e) {
        System.err.println("Error retrieving credentials: "+e);
      } finally {
        gettingCredentials = false;
        try { doClient.closeAllConnections(); } catch (Exception e) {}
      }
      return credentialCache;
    }
  }
  
  
  public static final DOAuthentication getAnonymousAuth() {
    if(anonymous!=null) return anonymous;
    synchronized(ANONYMOUS_LOCK) {
      if(anonymous==null) {
        InputStream pkIn = 
          PKAuthentication.class.getResourceAsStream("/net/cnri/dobj/etc/anonymous_privkey.bin");
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buf[] = new byte[1024];
        int r;
        byte keyBytes[] = null;
        PrivateKey privKey = null;
        try {
          while((r=pkIn.read(buf))>=0)
            bout.write(buf, 0, r);
          keyBytes = Util.decrypt(bout.toByteArray(), null);
          privKey = Util.getPrivateKeyFromBytes(keyBytes, 0);
        } catch (Exception e) {
          // should never actually happen
          System.err.println("Error:  Unable to initialize anonymous private key: "+e);
        }
        anonymous = new PKAuthentication(DOConstants.ANONYMOUS_ID, privKey);
        anonymous.setAutoRetrieveCredentials(false);
      }
      return anonymous;
    }
  }
  
  
  protected synchronized CertificateFactory getCertFactory()
    throws CertificateException
  {
    if(certFactory!=null) return certFactory;
    certFactory = CertificateFactory.getInstance("X.509");
    return certFactory;
  }


}

