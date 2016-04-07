/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

/**
 * Objects implementing DOOperationContext provide access to the "system"
 * so that operators can perform their jobs.
 */
public interface DOOperationContext {
  
  /**
   * Requests verification of the callers identity.  Returns true iff the
   * caller's identity has been verified.
   */
  public boolean authenticateCaller();

  /**
   * Returns a list of unverified IDs that the client claims as credentials.
   * Note: These IDs are not yet verified and the caller should call
   * authenticateCredential() with any credential IDs for whom they
   * assign any meaning.
   * Note2: This should only be called after authenticateClient returns.
   */
  public String[] getCredentialIDs();

  /**
   * Verify that this client has been granted a credential by the identified entity.
   * The given credentialID is expected to have come from the list returned by
   * getCredentialIDs().  Returns true iff a verified credential from credentialID
   * was granted to this client.
   */
  public boolean authenticateCredential(String credentialID);
  
  /**
   * Returns the identity of the caller.
   */
  public String getCallerID();

  /**
   * Returns the operation that the caller attempted to invoke.
   */
  public String getOperationID();
  
  
  /**
   * Returns the object on which the caller is invoke the operation.
   */
  public String getTargetObjectID();

  /**
   * Returns the identity of this repository service
   */
  public String getServerID();
  
  /**
   * Returns the set of headers that were included with the operation request
   */
  public HeaderSet getOperationHeaders();


  /**
   * Returns on object that allows operators to access the storage
   * system for the current object.
   */
  public StorageProxy getStorage();

  
  /**
   * Inserts an object into the connection-level information table.  This
   * causes subsequent calls to getConnectionMapping() on the same connection
   * (but not necessarily the same operation) with the same key to return
   * the given data value.
   */
  public void setConnectionMapping(Object mappingKey, Object mappingData);

  
  /**
   * Returns the object from the connection-level information table that had
   * previously been used as the mappingData parameter for a call to
   * setConnectionMapping with the given mappingKey on the same connection.
   * If no such object had been inserted into the table then this will return
   * null.
   */
  public Object getConnectionMapping(Object mappingKey);
  
  
  /**
   * Performs the specified operation with the identity of the caller, or as 
   * the container repository if the forwarding operations are configured to use
   * the repository's own identity for forwarded operations.
   * If the serverID is null then the DO client that performs this request
   * will resolve the objectID to find the server.
   */
  public void performOperation(String serverID, String objectID,
                               String operationID, HeaderSet params,
                               java.io.InputStream input, java.io.OutputStream output)
  throws DOException;
  
}
