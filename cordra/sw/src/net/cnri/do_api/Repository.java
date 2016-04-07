/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.do_api;

import net.cnri.dobj.*;

import java.io.*;
import java.util.*;


  /** This object provides high level access to a digital object repository. */
public class Repository 
  extends DigitalObject
{
  public static final String SERIALIZED_ENCODING = "basiczip";
  
  private DOClientConnection doClient;
  private DOServiceInfo service;
  private boolean encryptConnection = true;
  
  /** Constructor for a repository that interfaces  an object that provides
    * an interface to the with the given authentication information */
  public Repository(DOAuthentication auth, String repoID) 
    throws DOException
  {
    super(null, repoID);
    this.doClient = new DOClientConnection(auth);
    setRepository(this);
  }
  
  /** Constructor for a repository that interfaces  an object that provides
   * an interface to the with the given authentication information */
  public Repository(DOAuthentication auth, DOServiceInfo service) 
  throws DOException
  {
      super(null, service.getServiceID());
      this.service = service;
      this.doClient = new DOClientConnection(auth);
      setRepository(this);
  }

  public void close() {
      try {
          getConnection().close();
      } catch(IOException e) {}
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer(super.toString());
    sb.append(" conn: "+doClient);
    return sb.toString();
  }
  
  /** Construct a Repository instance which can be used to talk over existing
    * connections to DO servers. */
  public Repository(String repoID, DOClientConnection conn) {
    super(null, repoID);
    this.doClient = conn;
  }
  
  
  /** Sets whether or not the connection to this repository will be encrypted.  
    * This has no effect on the current connection if the authentication 
    * handshake has already taken occurred. */
  public void setUseEncryption(boolean encrypt) {
    this.encryptConnection = encrypt;
  }
  
  
  /** Internal method to return the underlying connection that is used to 
    * communicate with this Repository. */
  public DOClientConnection getConnection() 
    throws DOException
  {
    doClient.setUseEncryption(encryptConnection);
    if(this.service!=null) {
        doClient.reconnect(this.service);
    }
    else {
        doClient.reconnect(getID());
    }
    return doClient;
  }
  
  
  /** Verifies that the specified digital object exists in the current repository */
  public boolean verifyDigitalObject(String objectID) 
    throws DOException, IOException
  {
    StreamPair io = null;
    try {
      try {
          io = performOperation(objectID, DOConstants.DOES_OBJ_EXIST_OP_ID, null);
      }
      catch (DOException e) {
          if(e.getErrorCode()==DOException.NO_SUCH_OBJECT_ERROR) return false;
          else throw e;
      }
      HeaderSet response = new HeaderSet();
      if(response.readHeaders(io.getInputStream())) {
        if(response.hasHeader("result")) {
          return response.getBooleanHeader("result", false);
        } else {
          throw new DOException(DOException.SERVER_ERROR,
                                "result attribute missing from response to "+
                                "verify object (object='"+objectID+"') request: "+
                                response);
        }
      }
      throw new DOException(DOException.SERVER_ERROR,
                            "Incomplete response to verify object (object='"+
                            objectID+"'): "+response);
    } finally {
      try { io.close(); } catch (Throwable t) {}
    }
  }
  
  
  /** Invoke a low-level operation on the given object, returning the input and output
   * streams for the operation in a StreamPair.  The caller must be careful to
   * close both streams of the StreamPair when they are no longer used.  For some
   * operations the caller should close the output stream before any input will
   * be received.  */
  public StreamPair performOperation(String objectID, String operationID, HeaderSet parameters) 
  throws DOException, IOException
  {
    return getConnection().performOperation(objectID, operationID, parameters);
  }
    
  
  
  /** Create a new digital object in this repository and return an interface
    * to that object.  The objectID parameter indicates the identifier for the
    * object.  If the given objectID is null then the repository should choose
    * a unique identifier and create the object.  If an objectID is provided 
    * then the client should have permission to register that identifier in the
    * handle system - otherwise the object will be created but will not be 
    * referenceable from outside of the repository. */
  public DigitalObject createDigitalObject(String objectID) 
    throws DOException
  {
    HeaderSet params = null;
    if(objectID!=null) {
      params = new HeaderSet();
      params.addHeader("objectid", objectID);
    }
    StreamPair io = null;
    try {
      io = performOperation(getID(), DOConstants.CREATE_OBJ_OP_ID, params);
      io.getOutputStream().close();
      
      HeaderSet info = new HeaderSet();
      if(!info.readHeaders(io.getInputStream())) {
        throw new DOException(DOException.SERVER_ERROR, 
                              "No response was received from the server");
      }
      
      String objID = info.getStringHeader("objectid", null);
      if(objID==null) {
        throw new DOException(DOException.SERVER_ERROR, 
                              "No object ID was received from the server");
      }
      
      return new DigitalObject(this, objID);
    } catch (DOException e) {
      throw e;
    } catch (IOException e) {
      DOException de = new DOException(DOException.NETWORK_ERROR, "I/O Error creating object: "+e);
      de.initCause(e);
      throw(de);
    } finally {
      //try { objIn.close(); } catch (Exception e2) {}
      try { io.close(); } catch (Exception e2) {}
    }
  }
  
  
  /** Deletes a specified digital object from the repository */
  public void deleteDigitalObject(String objectID)
    throws DOException, IOException
  {
    StreamPair io = null;
    try {
      io = performOperation(objectID, DOConstants.DELETE_OBJ_OP_ID, null);
    } finally {
      try { io.close(); } catch (Throwable t) {}
    }
  }
  
  
  /** If the specified digital object exists in the repository, this method 
    * will return an instance of the DigitalObject class corresponding to that 
    * object. */
  public DigitalObject getDigitalObject(String objectID) 
    throws DOException, IOException
  {
    if(verifyDigitalObject(objectID))
      return new DigitalObject(this, objectID);
    else
      throw new DOException(DOException.NO_SUCH_OBJECT_ERROR,
                            "Object <"+objectID+
                            "> does not exist in repository <"+getID()+">");
  }
  
  
  
  /** Return an iterator of object IDs that are contained in this repository. */
  public CloseableIterator listObjects() 
    throws DOException, IOException
  {
    return new DOIDIterator(performOperation(getID(), DOConstants.LIST_OBJECTS_OP_ID, null));
  }
  
  
  /** Iterator over the object IDs in a repository. */
  private class DOIDIterator
    implements CloseableIterator
  {
    private StreamPair io;
    private boolean hasNextItem;
    private HeaderSet nextItem = new HeaderSet();
    
    public DOIDIterator(StreamPair io) 
      throws IOException
    {
      this.io = io;
      hasNextItem = nextItem.readHeaders(io.getInputStream());
    }
    
    public boolean hasNext() {
      return hasNextItem;
    }
    
    public void remove() {
      throw new UnsupportedOperationException();
    }
    
    public Object next() {
      if(!hasNextItem) throw new NoSuchElementException();
      String objectID = nextItem.getStringHeader("objectid", "");
      
      try {
        hasNextItem = nextItem.readHeaders(io.getInputStream());
      } catch (Exception e) {
        hasNextItem = false;
        try { io.close(); } catch (Throwable t) {}
        throw new RuntimeException("Error listing objects: "+e);
      }
      if(!hasNextItem) { // clean up
        try { io.close(); } catch (Throwable t) {}
      }
      return objectID;
    }
    
    public void close() throws IOException {
        io.close();
    }
    
    public void finalize()
      throws Throwable
    {
      try { close(); } catch (IOException e) {}
      super.finalize();
    }
    
  }
  
  
  /** Create a new object on this repository with the given identifier and 
    * return the ID of the new object.  The object is copied from the given
    * source repository into this repository.  */
  public String copyObjectFrom(Repository source, String objectID) 
    throws DOException
  {
    
    InputStream objIn = null;
    StreamPair io = null;
    try {
      DigitalObject srcObj = source.getDigitalObject(objectID);
      objIn = srcObj.getSerializedForm(SERIALIZED_ENCODING);
      
      HeaderSet params = null;
      if(objectID!=null) {
        params = new HeaderSet();
        params.addHeader("objectid", objectID);
        params.addHeader("initencoding", SERIALIZED_ENCODING);
      }
      io = performOperation(getID(), DOConstants.CREATE_OBJ_OP_ID, params);
      
      net.cnri.util.IOForwarder.forwardStream(objIn, io.getOutputStream());
      io.getOutputStream().close();
      
      HeaderSet info = new HeaderSet();
      if(!info.readHeaders(io.getInputStream())) {
        throw new DOException(DOException.SERVER_ERROR, 
                              "No response was received from the server");
      }
      
      String objID = info.getStringHeader("objectid", null);
      if(objID==null) {
        throw new DOException(DOException.SERVER_ERROR, 
                              "No object ID was received from the server");
      }
      return objID;
    } catch (IOException e) {
      throw new DOException(DOException.NETWORK_ERROR, "I/O Error creating object: "+e);
    } finally {
      try { objIn.close(); } catch (Exception e2) {}
      try { io.close(); } catch (Exception e2) {}
    }
  }
  
  public CloseableIterator<HeaderSet> search(String query) throws DOException, IOException {
	  QueryResults results = search(query, null, null, null, 0, 0);
	  return results.getIterator();
  }
  
  public QueryResults search(String query, List<String> returnedFields, List<String> sortFields, int pageSize, int pageOffset) throws DOException, IOException {
      return search(query,returnedFields,sortFields,null,pageSize,pageOffset);
  }

  public QueryResults search(String query, List<String> returnedFields, List<String> sortFields, int pageSize, int pageOffset, boolean indexUpToDate) throws DOException, IOException {
      return search(query,returnedFields,sortFields,null,pageSize,pageOffset,indexUpToDate);
  }

  public QueryResults search(String query, List<String> returnedFields, List<String> sortFields, int pageSize, int pageOffset, HeaderSet params) throws DOException, IOException {
      return search(query,returnedFields,sortFields,null,pageSize,pageOffset,params);
  }

  /**
   * Deprecated: uses a single {@code sortOrder} for all fields.  Instead use parameter {@code sortFields} and use sort fields of the form "field ASC" or "field DESC".
   */
  @Deprecated
  public QueryResults search(String query, List<String> returnedFields, List<String> sortFields, String sortOrder, int pageSize, int pageOffset) throws DOException, IOException {
      return search(query,returnedFields,sortFields,null,pageSize,pageOffset,false);
  }
  /**
   * Deprecated: uses a single {@code sortOrder} for all fields.  Instead use parameter {@code sortFields} and use sort fields of the form "field ASC" or "field DESC".
   */
  @Deprecated
  public QueryResults search(String query, List<String> returnedFields, List<String> sortFields, String sortOrder, int pageSize, int pageOffset, boolean indexUpToDate) throws DOException, IOException {
      HeaderSet params = null;
      if (indexUpToDate) {
          params = new HeaderSet();
          params.addHeader("indexUpToDate", true);
      }
      return search(query, returnedFields, sortFields, pageSize, pageOffset, params);
  }

  /**
   * Deprecated: uses a single {@code sortOrder} for all fields.  Instead use parameter {@code sortFields} and use sort fields of the form "field ASC" or "field DESC".
   */
  @Deprecated
  public QueryResults search(String query, List<String> returnedFields, List<String> sortFields, String sortOrder, int pageSize, int pageOffset, HeaderSet params) throws DOException, IOException {
      HeaderSet doparams = new HeaderSet();
      doparams.addHeader("query", query);
      if(returnedFields!=null) doparams.addHeader("returnedFields", concatenate(returnedFields, ","));
      if(sortFields!=null) doparams.addHeader("sortFields", concatenate(sortFields, ","));
      if(sortOrder!=null) doparams.addHeader("sortOrder", sortOrder);
      if(pageSize>0) doparams.addHeader("pageSize", pageSize);
      doparams.addHeader("pageOffset", pageOffset);
      if (params != null) {
          for (HeaderItem item : params) {
              doparams.addHeader(item.getName(), item.getValue());
          }
      }
      return searchInternal(doparams);
  }

  public QueryResults search(String query, HeaderSet params) throws DOException, IOException {
      HeaderSet doparams = new HeaderSet();
      doparams.addHeader("query", query);
      if (params != null) {
          for (HeaderItem item : params) {
              doparams.addHeader(item.getName(), item.getValue());
          }
      }
      return searchInternal(doparams);
  }
  
  private QueryResults searchInternal(HeaderSet doparams) throws DOException, IOException {
      StreamPair io = performOperation(DOConstants.SEARCH_OP_ID, doparams);
      HeaderSet firstHeaderSetFromServer = new HeaderSet();
      firstHeaderSetFromServer.readHeaders(io.getInputStream());
      boolean isActuallyResult = "result".equals(firstHeaderSetFromServer.getMessageType());
      int totalMatches = -1;
      Boolean more = null;
      if(!isActuallyResult) {
          totalMatches = firstHeaderSetFromServer.getIntHeader("totalMatches", -1);
          String moreString = firstHeaderSetFromServer.getStringHeader("more", null);
          if (moreString != null) {
              more = Boolean.valueOf(moreString);
          }
      }
      CloseableIterator<HeaderSet> iter = new ResultsIterator(io, isActuallyResult, firstHeaderSetFromServer);
      return new QueryResults(iter, totalMatches, more);
  }   
  
  private static String concatenate(List<String> list, String delimeter) {
	  if (list == null) return null;
	  String result = "";
	  for (int i = 0; i < list.size(); i++) {
		  result += list.get(i);
		  if (i != (list.size()-1)) { //don't put a delimiter after the last item
			  result += delimeter;
		  }
	  }
	  return result;
  }
  
  //The above works for this process connecting to an old server but what about the case of an old client connecting to this new server.
  //It will assume that the first header set is a result and it won't be!
  
  private class ResultsIterator implements CloseableIterator<HeaderSet> {
      private StreamPair io;
	  private BufferedInputStream in = null; 
      private HeaderSet next = null;
      private boolean closed;
      
      public ResultsIterator(StreamPair io, boolean isActuallyResult, HeaderSet firstHeaderSetFromServer) {
    	  this.io = io;
    	  in = new BufferedInputStream(io.getInputStream());
    	  next = isActuallyResult ? firstHeaderSetFromServer : null;
      }
      
      public boolean hasNext() {
          if(closed) return false;
          if(next!=null) return true;
          next = new HeaderSet();
          try {
              if(!next.readHeaders(in)) {
                  close();
                  return false;
              }
          }
          catch(Exception e) { 
              try { close(); } catch(IOException ie) {}
              if(e instanceof RuntimeException) throw (RuntimeException)e;
              throw new RuntimeException(e);
          }
          return true;
      }
      
      public HeaderSet next() {
          if(hasNext()) {
              HeaderSet res = next;
              next = null;
              return res;
          }
          return null;
      }
      
      public void remove() { throw new UnsupportedOperationException(); }
      
      public void close() throws IOException {
          closed = true;
          io.close();
      }	  
  }
  
  public class QueryResults {
	  private CloseableIterator<HeaderSet> iter; 
	  private int totalMatches;
	  private Boolean more = null;
	  
      public QueryResults(CloseableIterator<HeaderSet> iter, int totalMatches) {
          this.iter = iter;
          this.totalMatches = totalMatches;
      }

      public QueryResults(CloseableIterator<HeaderSet> iter, int totalMatches, Boolean more) {
		  this.iter = iter;
		  this.totalMatches = totalMatches;
		  this.more = more;
	  }
	  public CloseableIterator<HeaderSet> getIterator() { return iter; }
	  public int getTotalMatches() { return totalMatches; }
	  public Boolean isMore() { return more; }
  }
  
  public static interface CloseableIterator<T> extends Iterator<T>, Closeable {}
}
