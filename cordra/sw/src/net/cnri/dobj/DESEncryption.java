/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.*;

import javax.crypto.*;
import javax.crypto.interfaces.*;
import javax.crypto.spec.*;
import java.nio.*;
import java.security.*;
import java.security.spec.*;

/**
 * DESEncryption objects are capable of encrypting/decrypting data based
 * on a secret key.
 */
public class DESEncryption extends ConnectionEncryption {
  private static final int DEFAULT_BUF_SIZE = 2100;
  private MessageDigest macDigest = null;
  private DESedeKeySpec keySpec = null;
  private Cipher encryptCipher = null;
  private Cipher decryptCipher = null;
  private byte outgoingBuf[] = new byte[DEFAULT_BUF_SIZE];
  private byte incomingBuf[] = new byte[DEFAULT_BUF_SIZE];
  private DOConnection conn;
  
  public DESEncryption(DOConnection conn) {
    this.conn = conn;
    Security.insertProviderAt(new com.sun.crypto.provider.SunJCE(), 1);
  }
  
  
  /**
   * Set up the encryption mechanism and put the parameters into the given
   * HeaderSet so that they can be communicated to the other side.
   * The "cryptsecretkey" parameter is encrypted using the public key
   * that is included in the request argument.
   * */
  public void initParameters(HeaderSet request, HeaderSet response)
    throws Exception
  {
    
    response.addHeader("cryptmacalg", "SHA1");
    response.addHeader("cryptmode", "ECB");
    response.addHeader("cryptpadding", "PKCS5Padding");
    response.addHeader("cryptalg", "DESede");
    
    // Generate our own DH key pair and use it along with the public key
    // to calculate a secret key
    // get the DH public key from the other side 
    KeyFactory pubKeyFact = KeyFactory.getInstance(request.getStringHeader("public_key_alg","DH"));    
    X509EncodedKeySpec keySpec = 
      new X509EncodedKeySpec(request.getHexByteArrayHeader("public_key", null));
    PublicKey pubKey = pubKeyFact.generatePublic(keySpec);
    DHParameterSpec dhParamSpec = ((DHPublicKey)pubKey).getParams();
    KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance("DH");
    keyPairGen.initialize(dhParamSpec);
    KeyPair myKeyPair = keyPairGen.generateKeyPair();
    
    // we now have our own key pair and can 
    KeyAgreement keyAgreement = KeyAgreement.getInstance("DH");
    keyAgreement.init(myKeyPair.getPrivate());
    keyAgreement.doPhase(pubKey, true);
    
    byte sharedSecret[] = keyAgreement.generateSecret();
    
    // set up the ciphers and then delete the key from the response
    response.addHeader("cryptsecretkey", sharedSecret);
    setParameters(response);
    response.removeHeadersWithKey("cryptsecretkey");
    
    response.addHeader("public_key_alg", myKeyPair.getPublic().getAlgorithm());
    response.addHeader("public_key", myKeyPair.getPublic().getEncoded());
  }
  
  
  /** 
   * Set up the parameters for the encryption/decryption process.
   * The parameters should include a hex-encoded "secretkey" value,
   * as well as an optional "cipherparams" value to indicate the 
   * algorithm  
   */
  public synchronized void setParameters(HeaderSet parameters)
    throws Exception
  {
    byte secretKey[] = parameters.getHexByteArrayHeader("cryptsecretkey", null);
    String digestAlg = parameters.getStringHeader("cryptmacalg", "SHA1");
    String algorithm = parameters.getStringHeader("cryptalg", "DESede");
    String mode = parameters.getStringHeader("cryptmode", "ECB");
    String padding = parameters.getStringHeader("cryptpadding", "PKCS5Padding");
    String cipherParams = algorithm + '/' + mode + '/' + padding;
    
    keySpec = new DESedeKeySpec(secretKey);
    SecretKey key = SecretKeyFactory.getInstance(algorithm).generateSecret(keySpec);
    
    Cipher eCipher = Cipher.getInstance(cipherParams);
    eCipher.init(Cipher.ENCRYPT_MODE, key);
    Cipher dCipher = Cipher.getInstance(cipherParams);
    dCipher.init(Cipher.DECRYPT_MODE, key);

    MessageDigest mDigest = MessageDigest.getInstance(digestAlg);

    encryptCipher = eCipher;
    decryptCipher = dCipher;
    macDigest = mDigest;
  }
  
  
  /* Decrypt the given incoming chunk and put the result back into the given
   * ByteBuffer.
   * 
   * @see net.cnri.dobj.ConnectionEncryption#processIncomingChunk(ByteBuffer)
   */
  public synchronized void processIncomingChunk(ByteBuffer buf)
    throws GeneralSecurityException
  {
    // decrypt the buffer
    int resultLen = decryptCipher.getOutputSize(buf.remaining());
    if(resultLen>incomingBuf.length) incomingBuf = new byte[resultLen];
    
    byte chunkBuf[] = null;
    int chunkOffset = 0;
    int chunkLen = buf.remaining();
    if(buf.hasArray()) {
      chunkBuf = buf.array();
      chunkOffset = buf.position();
    } else {
      chunkBuf = new byte[chunkLen];
      buf.get(chunkBuf);
    }
    
    int inputLen = decryptCipher.doFinal(chunkBuf, chunkOffset, chunkLen, incomingBuf);
    
    if(conn.getProtocolMajorVersion()==1 && conn.getProtocolMinorVersion()==0) {
      // we are talking to an old client/server in which case we should extract
      // and verify the hash of the cleartext so that we can be sure that the data
      // was decrypted properly
      macDigest.reset();
      int readOffset = 0;
      int dataLen = Encoder.readInt(incomingBuf, readOffset);
      readOffset += Encoder.INT_SIZE;
      int dataOffset = readOffset;
      macDigest.update(incomingBuf, readOffset, dataLen);
      readOffset += dataLen;
      
      int hashLen = Encoder.readInt(incomingBuf, readOffset);
      readOffset += Encoder.INT_SIZE;
      int hashOffset = readOffset;
      readOffset += hashLen;
      
      byte digestResult[] = macDigest.digest();
      
      if(digestResult.length!=hashLen)
        throw new DigestException("MAC digest length mismatch");
      
      // verify the MAC code
      for(int i=0; i<hashLen; i++) {
        if(digestResult[i]!=incomingBuf[hashOffset+i])
          throw new DigestException("Invalid MAC digest");
      }
      
      buf.clear();
      buf.put(incomingBuf, dataOffset, dataLen);
      buf.flip();
    } else {
      // the more recent versions of the protocol don't include the hash with each chunk
      buf.clear();
      buf.put(incomingBuf, 0, inputLen);
      buf.flip();
    }
    return;
  }
  
  
  private byte intBuf1[] = new byte[Encoder.INT_SIZE];
  private byte intBuf2[] = new byte[Encoder.INT_SIZE];
  private byte outDigestBuf[] = new byte[128];
  /** Processes the outgoing chunk of bytes and returns the processed version.
   *  Note: This implementation re-uses the returned value so this method should
   *  not be called from multiple threads.
   *
   * @see net.cnri.dobj.ConnectionEncryption#processOutgoingChunk(ByteBuffer)
   */
  public synchronized ByteBuffer processOutgoingChunk(ByteBuffer buf)
    throws GeneralSecurityException
  {
    if(conn.getProtocolMajorVersion()==1 && conn.getProtocolMinorVersion()==0) {
      // we are talking to an old client/server in which case we should include
      // a hash of the cleartext so that the other side can be sure that the data
      // was decrypted properly
      int hashSize = macDigest.getDigestLength();
      if(hashSize<=0) hashSize = 20;
      
      int clearTextLen = 0;
      macDigest.reset();
      Encoder.writeInt(intBuf1, 0, hashSize);
      macDigest.update(intBuf1); // hash the buffer length
      clearTextLen += Encoder.INT_SIZE;
      // note: the line below assumes that the ByteBuffer has a backing array
      // we could call macDigest.update(buf) but that method is only available
      // as of java 1.5
      clearTextLen += buf.remaining();
      buf.mark();
      macDigest.update(buf.array(), buf.position(), buf.remaining()); // hash the buffer itself
      buf.reset();
      
      Encoder.writeInt(intBuf2, 0, hashSize); // hash the hash length?
      macDigest.update(intBuf2);
      clearTextLen += Encoder.INT_SIZE;
      int digestLen = macDigest.digest(outDigestBuf, 0, outDigestBuf.length);
      
      int cryptLen = encryptCipher.getOutputSize(clearTextLen);
      if(outgoingBuf.length<cryptLen) outgoingBuf = new byte[cryptLen+1000];
      
      int cryptOffset = 0;
      cryptOffset += encryptCipher.update(intBuf1, 0, Encoder.INT_SIZE, 
                                          outgoingBuf, cryptOffset);
      cryptOffset += encryptCipher.update(buf.array(), buf.position(), buf.remaining(),
                                          outgoingBuf, cryptOffset);
      cryptOffset += encryptCipher.update(intBuf2, 0, Encoder.INT_SIZE, 
                                          outgoingBuf, cryptOffset);
      cryptOffset += encryptCipher.update(outDigestBuf, 0, digestLen,
                                          outgoingBuf, cryptOffset);
      cryptOffset += encryptCipher.doFinal(outgoingBuf, cryptOffset);
      
      return ByteBuffer.wrap(outgoingBuf, 0, cryptOffset);
    } else {
      // use the updated (hash-less) encryption
      int buflen = buf.remaining();
      int cryptLen = encryptCipher.getOutputSize(buflen);
      if(outgoingBuf.length<cryptLen) outgoingBuf = new byte[cryptLen+1000];
      
      int cryptOffset = 0;
      cryptOffset += encryptCipher.doFinal(buf.array(), buf.position(), buflen,
                                           outgoingBuf, cryptOffset);
      return ByteBuffer.wrap(outgoingBuf, 0, cryptOffset);
    }
  }
  
}