/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package  net.cnri.apps.cmdline;

import net.cnri.dobj.DOClient;
import net.cnri.dobj.DOConstants;
import net.cnri.dobj.PKAuthentication;
import net.cnri.dobj.StreamPair;
import net.handle.hdllib.*;
import org.bouncycastle.asn1.DERBMPString;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x509.X509Name;
import org.bouncycastle.jce.X509Principal;
import org.bouncycastle.jce.interfaces.PKCS12BagAttributeCarrier;
import org.bouncycastle.x509.X509V1CertificateGenerator;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Hashtable;


public class CertGenerator {
  private static X509V1CertificateGenerator v1CertGen = new X509V1CertificateGenerator();
  private static X509V3CertificateGenerator v3CertGen = new X509V3CertificateGenerator();
  
  private PrivateKey privKey = null;
  private PublicKey pubKey = null;
  private PKAuthentication pkAuth = null;
  private String myID = null;
  private DOClient doClient = null;
  private long expirationDays = 30;


  static {
    try {
      Security.addProvider((java.security.Provider)Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider").newInstance());
    } catch (Exception e) {
      System.err.println("unable to add BouncyCastleProvider to provider list");
      e.printStackTrace();
    }
  }
  
  /**
   * Construct a CertGenerator
   */
  public CertGenerator(String myID, PrivateKey myPrivateKey)
    throws Exception
  {
    this(myID, myPrivateKey, null);
  }

  /**
   * Construct a CertGenerator
   */
  public CertGenerator(String myID, PrivateKey myPrivateKey, PublicKey myPublicKey)
    throws Exception
  {
    this.privKey = myPrivateKey;
    this.pubKey = myPublicKey;
    this.myID = myID;
    this.pkAuth = new PKAuthentication(myID, myPrivateKey);
    this.doClient = new DOClient(pkAuth);

    if(pubKey==null) {
      pubKey = resolvePublicKey(myID);
      System.err.println("resolved public key: "+myID+": "+pubKey);
    }
    
    if(pubKey!=null) {
      pubKey = (PublicKey)KeyFactory.getInstance(pubKey.getAlgorithm()).translateKey(pubKey);
    }
  }


  public void setCertExpirationInDays(long numDays) {
    this.expirationDays = numDays;
  }

  /**
   * Generate a certificate that certifies the given subject (and their associated public key)
   * as a delegate of this generator's identity
   */
  public Certificate createCert(String subjectID)
    throws Exception
  {
    PublicKey subjectPubKey = resolvePublicKey(subjectID);

    if(subjectPubKey!=null) {
      subjectPubKey = (PublicKey)KeyFactory.getInstance(subjectPubKey.getAlgorithm()).translateKey(subjectPubKey);
    }
    return createCert(subjectID, subjectPubKey);
  }

  /**
   * Generate a certificate that certifies the given subject (and their associated public key)
   * as a delegate of this generator's identity
   */
  public Certificate createCert(String subjectID, PublicKey subjectPubKey)
    throws Exception
  {
    Hashtable myAttributes = new Hashtable();
    //myAttributes.put("o", DERObjectIdentifier.getInstance(myID));
    myAttributes.put(X509Name.UID, myID);
    //myAttributes.put(X509Name.OU, "DO Certificate");
    
    Hashtable subjectAttributes = new Hashtable();
    subjectAttributes.put(X509Name.UID, subjectID);
    //subjectAttributes.put("ou", "DO Certificate");
    
    // create the certificate - version 1
    v3CertGen.setSerialNumber(BigInteger.valueOf(1));
    v3CertGen.setIssuerDN(new X509Principal(myAttributes));
    v3CertGen.setNotBefore(new Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24 * 2));
    v3CertGen.setNotAfter(new Date(System.currentTimeMillis() + (1000L * 60 * 60 * 24 * expirationDays)));
    v3CertGen.setSubjectDN(new X509Principal(subjectAttributes));
    v3CertGen.setPublicKey(subjectPubKey);
    v3CertGen.setSignatureAlgorithm("SHA1With"+pubKey.getAlgorithm());
    
    X509Certificate cert = v3CertGen.generateX509Certificate(privKey);
    
    cert.checkValidity(new Date());
    
    //cert.verify(myPubKey);
    
    // this is actually optional - but if you want to have control
    // over setting the friendly name this is the way to do it...
    PKCS12BagAttributeCarrier bagAttr = (PKCS12BagAttributeCarrier)cert;
    bagAttr.setBagAttribute(PKCSObjectIdentifiers.pkcs_9_at_friendlyName,
                            new DERBMPString("DO Delegation Certificate"));
    return cert;
  }

  public void publishCert(String subjectID, Certificate cert)
    throws Exception
  {
    byte certBytes[] = cert.getEncoded();
    StreamPair pair = doClient.performOperation(subjectID,
                                                DOConstants.STORE_CREDENTIAL_OP_ID,
                                                null);
    pair.getOutputStream().write(certBytes);
    pair.getOutputStream().close();
    pair.getInputStream().close();
  }
  
  
  /** Securely resolve a public key for the given handle */
  public synchronized PublicKey resolvePublicKey(String clientID) {
    try {
      // first retrieve the public key (checking server signatures, of course)
      ResolutionRequest req = new ResolutionRequest(Util.encodeString(clientID),
                                                    new byte[][] { Common.STD_TYPE_HSPUBKEY},
                                                    null,
                                                    null);
      req.certify = true;
      AbstractResponse response = new HandleResolver().processRequest(req);
      
      if(response instanceof ResolutionResponse) {
        ResolutionResponse rresponse = (ResolutionResponse)response;
        HandleValue values[] = rresponse.getHandleValues();
        if(values==null || values.length < 1) {
          System.err.println("No public key associated with ID "+clientID);
          return null;
        }

        // decode the public key
        return Util.getPublicKeyFromBytes(values[0].getData(), 0);
      }
    } catch (Exception e) {
      System.err.println("error looking up public key for "+clientID+": "+e);
    }
    return null;
  }
  
  private static final void printUsage() {
    System.err.println("usage: do-certgen <certID> <certPrivKeyFile> <granteeID>");
  }
  
  public static void main(String argv[])
    throws Exception
  {
    if(argv.length!=3) {
      printUsage();
      System.exit(1);
    }
    
    String myID = argv[0];
    String subjectID = argv[2];
    File privKeyFile = new File(argv[1]);
    
    if(!privKeyFile.exists() || !privKeyFile.canRead()) {
      System.err.println("Error:  cannot read private key file: "+privKeyFile.getAbsolutePath());
      System.exit(1);
    }
    
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    FileInputStream fin = new FileInputStream(privKeyFile);
    byte buf[] = new byte[1024];
    int r;
    while((r=fin.read(buf))>=0)
      bout.write(buf, 0, r);
    buf = bout.toByteArray();
    byte secKey[] = null;
    if(Util.requiresSecretKey(buf)) {
      secKey = Util.getPassphrase("Enter the passphrase to decrypt the private key in "+
                                  privKeyFile.getAbsolutePath());
    }
    buf = Util.decrypt(buf, secKey);
    PrivateKey privKey = Util.getPrivateKeyFromBytes(buf, 0);
    
    CertGenerator certGen = new CertGenerator(myID, privKey);
    Certificate cert = certGen.createCert(subjectID);
    certGen.publishCert(subjectID, cert);
    System.exit(0);
  }

}


