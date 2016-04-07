/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.GeneralSecurityException;

/**
 * Objects of class ConnectionEncryption are capable of encrypting and 
 * decrypting traffic on the connection.
 */
public abstract class ConnectionEncryption {
  private static SecureRandom srand = null;
  private static Long RANDOM_LOCK = new Long(12340125l);
  
  /**
   * Set up the encryption mechanism and put the parameters into the given
   * HeaderSet so that they can be communicated to the other side.
   * The "cryptsecretkey" parameter is encrypted using the public key
   * that is included in the request argument.
   */
  public abstract void initParameters(HeaderSet request, HeaderSet parameters)
    throws Exception;
  

  /** Decrypts the incoming chunk of bytes and puts the result back into the
    * given ByteBuffer for reading.
    */
  public abstract void processIncomingChunk(ByteBuffer buf)
    throws GeneralSecurityException;
  
  /** Processes the outgoing chunk of bytes and returns the processed version.
    *  Note: This may process the bytes in-line, or re-use the returned buffer
    *  in order to avoid allocating a new buffer for every invocation.
    */
  public abstract ByteBuffer processOutgoingChunk(ByteBuffer buf)
    throws GeneralSecurityException;
  
  
  /** 
    * Constructs a ConnectionEncryption instance that conforms to the given
    * parameters.  This is called by DOConnection when the other side of a
    * connection sends a request to establish encryption on a connection.
    * After this method returns, the given response will be sent to the
    * other side of the connection immediately before the connection becomes
    * encrypted.
    * 
    * @param request The parameter set containing input for the encryption setup
    * @param response The parameter set where the encryption details will be stored
    * @return a ConnectionEncryption object conforming to the parameters
    */
  public static ConnectionEncryption constructInstance(DOConnection conn,
                                                       HeaderSet request,
                                                       HeaderSet response)
    throws Exception
  {
    // TODO: Examine a list of supported algorithms provided by the client
    // to determine which ConnectionEncryption subclass to use.
    GenericEncryption enc = new GenericEncryption(conn);
    enc.initParameters(request, response);
    return enc;
  }
  
  /** Return a singleton SecureRandom object. */
  public static final SecureRandom getRandom() {
    if(srand!=null) return srand;
    synchronized (RANDOM_LOCK) {
      if(srand!=null) return srand;
      srand = new SecureRandom();
      srand.setSeed(srand.generateSeed(10));
    }
    return srand;
  }


}
