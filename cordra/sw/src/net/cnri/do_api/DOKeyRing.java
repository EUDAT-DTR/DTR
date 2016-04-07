/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.do_api;

import net.cnri.dobj.*;

import net.handle.hdllib.Util;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.security.*;
import java.util.*;
import java.io.*;

// TODO: Long term:
// TODO: - create "permission sets" that can be easily selected
// TODO: - keep track of frequent recipients of encryption key in order to provide a shorter "encrypt for" pick-list


/** This is an interface to a set of encrypted keys that can be used to open
  * encrypted documents in digital objects.  The keys are stored in the digital
  * object identified by the user's handle.
  *
  * All keys will be granted in a new data element (or attribute) named
  * with granted_key.<GUID> where <GUID> is assigned by the server.  
  * The implementation consolidates the keys by adding all granted keys to the 
  * main keychain and removing all expired keys.
  **/
public class DOKeyRing {
  public static final String KEYRING_ELEMENT_ID = "do.object_keys";
  public static final String GRANTED_KEY_PREFIX = "do.granted_key";
  
  // the attribute key used to indicate that an element is encrypted and
  // the key ID that will decrypt it.
  public static final String KEY_ID_ATTRIBUTE = "do.enc_key_id";
  public static final String KEY_ALG_ATTRIBUTE = "do.enc_key_alg";
  public static final String CIPHER_ALG_ATTRIBUTE = "do.enc_alg";
  public static final String ELEMENT_KEY_FORMAT = "do.enc_key_format";
  
  private static final int SECRET_KEY_SIZE = 16; // size in bytes
  private static final String DEFAULT_KEY_ALG = "AES";
  private static final String DEFAULT_ENC_ALG = "AES/ECB/PKCS5Padding";
  private static final String DEFAULT_ENC_ALG_PARAMS = "/ECB/PKCS5Padding";
  private static final String DEFAULT_KEY_ENC_ALG_PARAMS = "/ECB/PKCS1Padding";
  private static final String KEY_ALG = "keyalg";
  
  // the algorithm used to encrypt the secret key
  private static final String KEY_ENC_ALG = "keyencalg";
  
  // the algorithm used to encrypt the data
  private static final String ENC_ALG = "encalg";
  
  // the format of the secret key
  private static final String KEY_FORMAT = "format";
  
  private static final String KEY_ID = "id";
  private static final String KEY_CIPHER_BYTES = "enckey";

  
  // the private key used to encrypt/decrypt keys
  private PrivateKey myPrivateKey;
  
  // the user object in which our keychain is stored
  private DigitalObject userObject;
  
  // secure RNG for use in generating keys
  private SecureRandom secRand;
  
  // a copy of the XML structure containing the keychain
  private ArrayList keyRing = new ArrayList();
  
  private boolean loadedKeys = false;
  
  public DOKeyRing(PrivateKey key, DigitalObject userObj) 
    throws Exception
  {
    this.myPrivateKey = key;
    this.userObject = userObj;
    this.secRand = new SecureRandom();
  }
  
  /** Loads the user's keychain from their digital object, consolidating any granted keys
    * into the main keyring
    */
  public synchronized void loadKeys() 
    throws Exception
  {
    // take in up-to-the-minute changes
    userObject.clearAttributes();
    // if the main keyring exists, load it
    DataElement keyringElement = userObject.getDataElement(KEYRING_ELEMENT_ID);
    if(userObject.verifyDataElement(KEYRING_ELEMENT_ID)) {
      InputStream in = null;
      try {
        addKeysFromStream(in = keyringElement.read());
        loadedKeys = true;
      } finally {
        try { in.close(); } catch (Exception e) {}
      }
    }
    
    ArrayList consolidatedKeyElements = new ArrayList();
    String elementIDs[] = userObject.listDataElements();
    for(int i=0; elementIDs!=null && i<elementIDs.length; i++) {
      if(elementIDs[i]==null) continue;
      if(elementIDs[i].startsWith(GRANTED_KEY_PREFIX)) {
        // read the key and add it to the main keyring
        InputStream in = null;
        try {
          DataElement element = userObject.getDataElement(elementIDs[i]);
          addKeysFromStream(in = element.read());
          consolidatedKeyElements.add(element);
        } catch (Exception e) {
          System.err.println("Error reading key element: "+elementIDs[i]+"; error: "+e);
        } finally {
          try { in.close(); } catch (Exception e) {}
        }
      }
    }
    
    if(consolidatedKeyElements.size()>0 && keyringElement!=null) {
      ByteArrayOutputStream bout = new ByteArrayOutputStream();
      for(Iterator it=keyRing.iterator(); it.hasNext(); ) {
        HeaderSet keyInfo = (HeaderSet)it.next();
        keyInfo.writeHeaders(bout);
      }
      // store the consolidated list of keys
      keyringElement.write(new ByteArrayInputStream(bout.toByteArray()));
      
      // delete the elements that contained the recently accepted keys
      for(Iterator it=consolidatedKeyElements.iterator(); it.hasNext(); ) {
        DataElement element = (DataElement)it.next();
        try {
          element.getDigitalObject().deleteDataElement(element.getDataElementID());
        } catch (Exception e) {
          System.err.println("Error deleting granted key element: "+element+"; error: "+e);
        }
      }
    }
    
  }
  
  
  private void addKeysFromStream(InputStream in) 
  throws Exception
  {
    HeaderSet keyInfo = new HeaderSet();
    while(keyInfo.readHeaders(in)) {
      keyRing.add(keyInfo);
      keyInfo = new HeaderSet();
    }
  }
  
  
  /** Encrypt and add the given secret key, which was used to encrypt an object, 
    * to the given recipient's keychain.  The key will be encrypted using the
    * given entity's public key and then deposited in their keychain.  */
  public static void grantKeyTo(DigitalObject granteeUserObject, SecretKey key)
    throws Exception
  {
    HeaderSet keyInfo = buildKeyInfo(key, granteeUserObject.getID());
    StreamPair io = null;
    try {
      io = granteeUserObject.performOperation(DOConstants.GRANT_KEY_OP_ID, null);
      keyInfo.writeHeaders(io.getOutputStream());
      io.getOutputStream().close();
      DigitalObject.checkStatus(io.getInputStream());
    } finally {
      try { io.close(); } catch (Throwable t) {}
    }
    granteeUserObject.clearAttributes();
  }
  

  private static HeaderSet buildKeyInfo(SecretKey key, String recipientID) 
    throws Exception
  {
    HeaderSet keyInfo = new HeaderSet("doseckey");
    keyInfo.addHeader(KEY_ALG, key.getAlgorithm());
    keyInfo.addHeader(KEY_FORMAT, key.getFormat());
    byte encodedKey[] = key.getEncoded();
    if(encodedKey==null) throw new Exception("Key "+key+" has no encoding");
    keyInfo.addHeader(KEY_ID, Util.doSHA1Digest(encodedKey));
    keyInfo.addHeader(ENC_ALG, DEFAULT_ENC_ALG);
    PublicKey pubKeys[] = DOClient.getResolver().resolvePublicKeys(recipientID);
    for(int i=0; pubKeys!=null && i<pubKeys.length; i++) {
      try {
        // if the key is for signing only, ignore it
        if(pubKeys[i].getAlgorithm().equalsIgnoreCase("DSA")) continue;
        
        // encrypt the key for the recipient....
        String encAlg = pubKeys[i].getAlgorithm() + DEFAULT_KEY_ENC_ALG_PARAMS;
        Cipher pubKeyCipher = Cipher.getInstance(encAlg);
        pubKeyCipher.init(Cipher.ENCRYPT_MODE, pubKeys[i]);
        byte encryptedKey[] = pubKeyCipher.doFinal(encodedKey);
        keyInfo.addHeader(KEY_CIPHER_BYTES, encryptedKey);
        keyInfo.addHeader(KEY_ENC_ALG, encAlg);
        break;
      } catch (Exception e) {
        // only throw an exception only if we're on the last key
        if(i==pubKeys.length-1) throw e;
        else System.err.println("Note: unable to encrypt object key using "+
                                "public key "+pubKeys[i]+" for recipient "+
                                recipientID);
      }
    }
    if(!keyInfo.hasHeader(KEY_CIPHER_BYTES)) {
      throw new Exception("object recipient does not have a public key that can be used for encryption");
    }
    return keyInfo;
  }
  
  /** This will encrypt the given key using the recipient's public key and encode
    * it into a block of text that can be emailed to an individual.
    */
  public static String buildKeyGrant(String keyRecipient, SecretKey key) 
    throws Exception
  {
    HeaderSet keyInfo = buildKeyInfo(key, keyRecipient);
    return keyInfo.toString().trim();
  }
  
  /** Create and return a SecretKey suitable for object encryption */
  public SecretKey generateEncryptionKey() 
    throws java.security.spec.InvalidKeySpecException
  {
    byte secKeyBytes[] = new byte[SECRET_KEY_SIZE];
    secRand.nextBytes(secKeyBytes);
    return new SecretKeySpec(secKeyBytes, DEFAULT_KEY_ALG);
  }
  
 
  public InputStream decryptDataElementUsingKey(int i,String keyID,String algorithm,DataElement element) {
      HeaderSet tmp = (HeaderSet)keyRing.get(i);
      if(tmp.getStringHeader(KEY_ID, "").equalsIgnoreCase(keyID)) {
        try {
          byte encryptedKey[] = tmp.getHexByteArrayHeader(KEY_CIPHER_BYTES, null);
          if(encryptedKey==null) return null; // no encrypted key!
          String cipherAlg = tmp.getStringHeader(KEY_ENC_ALG, "");
          Cipher keyCipher = Cipher.getInstance(cipherAlg);
          keyCipher.init(Cipher.DECRYPT_MODE, myPrivateKey);
          byte decryptedKey[] = keyCipher.doFinal(encryptedKey);
          String alg = tmp.getStringHeader(KEY_ALG, "");
          SecretKey secKey = new SecretKeySpec(decryptedKey, alg);
          Cipher cipher = Cipher.getInstance(algorithm);
          cipher.init(Cipher.DECRYPT_MODE, secKey);
          
          return new CipherInputStream(element.read(), cipher);
        } catch (Exception e) {
          System.err.println("Unable to decrypt key: "+tmp+"; error: "+e);
        }
      }
      return null;
  }
  
  /** 
   * If the given data element is encrypted, check our keychain for a key that will
   * decrypt it and use that key to return an InputStream from which the unencrypted
   * data element bytes can be returned.
   */
  public InputStream decryptDataElement(DataElement element) 
    throws Exception
  {
    String keyID = element.getAttribute(KEY_ID_ATTRIBUTE, null);
    
    if(keyID==null) { // there is no encryption
      return element.read();
    }
    
    // there is encryption... look for a key with a matching algorithm
    String algorithm = element.getAttribute(CIPHER_ALG_ATTRIBUTE, DEFAULT_ENC_ALG);
    
    for(int i=keyRing.size()-1; i>=0; i--) {
        InputStream res = decryptDataElementUsingKey(i,keyID,algorithm,element);
        if (res!=null) return res;
    }
    
    // Check and see if there are new keys
    int size = keyRing.size();
    loadKeys();
    for(int i=keyRing.size()-1; i>=size; i--) {
        InputStream res = decryptDataElementUsingKey(i,keyID,algorithm,element);
        if (res!=null) return res;
    }
    
    throw new DOException(DOException.CRYPTO_ERROR,
                          "No key to decrypt element: "+element);
  }
  
  
  /** Encrypts and writes the data from the given source to the DataElement using the 
   *  secret key to encrypt the data while recording the key identifier in the data element's
   *  attributes. */
  public long writeEncryptedElement(DataElement element, SecretKey secKey, InputStream source) 
    throws Exception
  {
    String keyAlg = secKey.getAlgorithm();
    String keyID = Util.decodeHexString(Util.doSHA1Digest(secKey.getEncoded()), false);
    String encAlg = keyAlg + DEFAULT_ENC_ALG_PARAMS;
    Cipher cipher = Cipher.getInstance(encAlg);
    cipher.init(Cipher.ENCRYPT_MODE, secKey);
    CipherInputStream cin = new CipherInputStream(source, cipher);
    element.setAttribute(KEY_ALG_ATTRIBUTE, keyAlg);
    element.setAttribute(KEY_ID_ATTRIBUTE, keyID);
    element.setAttribute(CIPHER_ALG_ATTRIBUTE, encAlg);
    return element.write(cin);
  }
  
}
