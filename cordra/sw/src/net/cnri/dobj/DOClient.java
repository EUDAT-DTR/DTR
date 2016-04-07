/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import net.handle.hdllib.*;

import java.io.*;
import java.util.*;

/**
 * This class describes an object that provides communication
 * capabilities with Digital Objects.  All operations
 * performed on digital objects can be performed through an instance of
 * this class.
 */
public class DOClient 
  implements DOConstants
{
  private static final String REPO_URL_QUERY[] = { DOConstants.OBJECT_SERVER_HDL_TYPE,
                                                   "HS_ALIAS" };
  private final transient DOAuthentication myID;
  private final transient Hashtable connectionCache = new Hashtable();
  
  public boolean DEBUG = false;
  
  public DOClient(String clientID, DOServerConnection serverConn) {
    if(serverConn==null || clientID==null) {
      throw new NullPointerException("Given null DOServerConnection as proxy auth");
    }
    this.myID = null;
  }
  
  /** Create a DOClient instance set up to authenticate with the given information */
  public DOClient(DOAuthentication id) {
	  if(DEBUG) System.out.println("Querying My ID");
	  this.myID = id;
  }
  
  /**
   * List the operations that can be performed on the given object at the
   * specified repository.  If repositoryID is null, this will return the
   * operations available from the default repository for the object.
   */
  public String[] listOperations(String repositoryID, String objectID)
    throws DOException
  {
    StreamPair io = repositoryID==null ?
      performOperation(objectID, DOConstants.LIST_OPERATIONS_OP_ID, null) :
      performOperation(repositoryID, objectID, DOConstants.LIST_OPERATIONS_OP_ID, null);
    Vector ops = new Vector();
    try {
      io.getOutputStream().close();

      InputStreamReader rdr = new InputStreamReader(io.getInputStream(), "UTF8");
      StringBuffer sb = new StringBuffer();
      int ch;
      while(true) {
        ch = rdr.read();
        if(ch=='\n' || ch==-1) {
          String op = sb.toString().trim();
          if(op.length()>0)
            ops.addElement(op);
          sb.setLength(0);
          if(ch==-1) break;
        } else {
          sb.append((char)ch);
        }
      }
    } catch (Exception e) {
      System.err.println("Error reading operations: "+e);
      e.printStackTrace(System.err);
    }
    String opsArray[] = new String[ops.size()];
    ops.copyInto(opsArray);
    return opsArray;
  }
  
  /**
   * Performs the specified operation on the object identified by 
   * "objectID" with the given input.  This returns a pair of streams
   * to/from which the input/output of the operation can be written.
   * The given parameters are included in the operation request, but
   * should be considered part of the input.
   * 
   * This method will 1) Authenticate the repository of the object being 
   * operated upon, 2) establish an encrypted connection to that repository,
   * 3) provide our authentication to the repository, 4) forward everything
   * that is written to the input stream (in the returned pair) to the repository,
   * and 5) Verify the repository's signature of any bytes that are recieved and
   * forward them to the InputStream that is returned from this operation.
   */
  public StreamPair performOperation(String objectID, String operationID, HeaderSet parameters)
    throws DOException  
  {
    return performOperation(null, objectID, operationID, parameters);
  }

  public StreamPair performOperation(String repositoryID, String objectID,
                                     String operationID, HeaderSet parameters)
    throws DOException
  {    
    if(repositoryID == null) {
      // no repository was given, see if there is a default repository
      // associated with the object in the handle system
      repositoryID = resolveRepositoryID(objectID);
    }
    
    DOClientConnection clientConn = getConnectionToRepository(repositoryID);
    
    try {
      return clientConn.performOperation(objectID, operationID, parameters);
    } catch (Exception e) {
      if(e instanceof DOException) throw (DOException)e;
      else throw new DOException(DOException.PROTOCOL_ERROR, String.valueOf(e));
    }
  }
  
  /** Return a connection to the given repository.  This method will return an
    * open (cached) connection if one is available and if not it will construct
    * a new connection and return it. */
  public synchronized DOClientConnection getConnectionToRepository(String repositoryID) 
    throws DOException
  {
    // find a cached connection, if available, and use it to perform the operation
    DOClientConnection clientConn = (DOClientConnection)connectionCache.get(repositoryID);
    if(clientConn!=null && !clientConn.isOpen())
      clientConn = null;

    if(clientConn==null) {
      try {
        clientConn = new DOClientConnection(myID);
        clientConn.DEBUG = DEBUG;
        clientConn.connect(repositoryID);
        
        connectionCache.put(repositoryID, clientConn);
      } catch (Exception e) {
        if(e instanceof DOException) throw (DOException)e;
        else throw new DOException(DOException.PROTOCOL_ERROR, String.valueOf(e));
      }
    }
    return clientConn;
  }
  
  /**
    * Resolves the given object identifier and returns the repository that 
    * hosts the object.   */
  public static final String resolveRepositoryID(String objectID) 
    throws DOException
  {
    String repositoryID = null;
    HandleValue values[] = null;
    String aliasedID = objectID;
    while(values==null) {
      try {
        values = getResolver().resolveHandle(aliasedID, REPO_URL_QUERY);
        if(values==null) {
          throw new DOException(DOException.NO_SUCH_OBJECT_ERROR, objectID);
        } else if(values.length<=0) {
          throw new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR, objectID);
        }
        boolean wasAliased = false;
        for(int i=0; i<values.length; i++) {
          if(values[i].getTypeAsString().equalsIgnoreCase("HS_ALIAS")) {
            aliasedID = values[i].getDataAsString();
            wasAliased = true;
            break;
          }
        }
        if(wasAliased) { // continue within this loop
          values = null;
        }
      } catch (HandleException e) {
        //e.printStackTrace(System.err);
        throw new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR, String.valueOf(e));
      }
    }
    
    for(int i=0; i<values.length; i++) {
      if(values[i].getTypeAsString().equalsIgnoreCase(DOConstants.OBJECT_SERVER_HDL_TYPE)) {
        repositoryID = values[i].getDataAsString();
        break;
      }
    }
    if(repositoryID==null) {
      throw new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR,
                            "Unable to locate the registry server for "+objectID);
    }
    return repositoryID;
  }
  
  public synchronized void closeAllConnections() 
    throws Exception
  {
    Exception e = null;
    for(Enumeration en = connectionCache.elements(); en.hasMoreElements(); ) {
      try {
        ((DOClientConnection)en.nextElement()).close();
      } catch (Exception err) {
        System.err.println("Error shutting down cached connections: "+err);
        e = err;
      }
    }
  }
  
  public static Resolver getResolver() {
    return DOConnection.getResolver();
  }

}
