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


/** This is a high level interface to an arbitrary digital object. */
public class DigitalObject {
  private static final String DATE_CREATED = "internal.created";
  private static final String DATE_MODIFIED = "internal.modified";
  private static final String DATA_SIZE = "internal.size";

  private Repository repo; // the client being used to access this object
  private String objectID;     // the identifier of the object itself
  
  private HashMap<String,HeaderSet> elAtts = new HashMap<String,HeaderSet>();
  private HeaderSet attributes = new HeaderSet();
  private boolean loadedAttributes = false;
  
  DigitalObject(Repository repo, String objID) {
    this.repo = repo;
    this.objectID = objID;
  }
  
  /** Return the repository server through which this object is accessed */
  public Repository getRepository() {
    return repo;
  }
  
  /** Returns the identifier for this digital object */
  public String getObjectID() {
    return this.objectID;
  }
 
  public String getID() {
    return this.objectID;
  }
  
  /** Set the repository server through which we access this object */
  void setRepository(Repository client) {
    this.repo = client;
    clearAttributes();
  }
   
  /** Verifies whether or not the data element with the given name exists
    * within this object */
  public boolean verifyDataElement(String elementID) 
    throws DOException, IOException
  {
    loadAttributes();
    return elAtts.containsKey(elementID);
  }
  
  public void deleteDataElement(String elementID) 
    throws DOException, IOException
  {
    StreamPair io = null;
    try {
      HeaderSet params = new HeaderSet();
      params.addHeader("elementid", elementID);
      io = performOperation(DOConstants.DELETE_DATA_OP_ID, params);
    } finally {
      try { io.close(); } catch (Throwable t) {}
    }
    clearAttributes();
  }
  
  /** Deletes this digital object */
  public void deleteObject()
    throws DOException, IOException
  {
    StreamPair io = null;
    try {
      io = performOperation(DOConstants.DELETE_OBJ_OP_ID, null);
    } finally {
      try { io.close(); } catch (Throwable t) {}
    }
  }
  
  
  /** Returns a nicely formatted label for this object */
  public String toString() {
    return objectID + " through " + (repo==null ? "<global client>" : repo.getObjectID());
  }
  
  /** List the elements in this digital object */
  public String[] listDataElements() 
    throws DOException, IOException
  {
    loadAttributes();
    return (String[])elAtts.keySet().toArray(new String[0]);
  }
  
  /** Get the serialized version of the entire digital object */
  InputStream getSerializedForm(String format) 
    throws DOException, IOException
  {
    HeaderSet params = new HeaderSet();
    if(format!=null) params.addHeader("encoding", format);
    StreamPair io = performOperation(DOConstants.GET_SERIALIZED_FORM_OP_ID, params);
    io.getOutputStream().close();
    return io.getInputStream();
  }
  
  /** Return a reference to the given data element within this object */
  public DataElement getDataElement(String elementID) 
    throws DOException, IOException
  {
    loadAttributes();
    return new DataElement(this, elementID);
  }
  
  /** Invoke a low-level operation on this object, returning the input and output
    * streams for the operation in a StreamPair.  The caller must be careful to
    * close both streams of the StreamPair when they are no longer used.  For some
    * operations the caller should close the output stream before any input will
    * be received.  */
  public StreamPair performOperation(String operationID, HeaderSet parameters) 
    throws DOException, IOException
  {
    return repo.performOperation(objectID, operationID, parameters);
  }
  
  /** Determine whether a user in some groups is authorized to perform an operation with certain parameters on this object. 
   * @param userID If null, presume the caller.
   * @param delegatedids If null, generate the list of all groups by querying the member's digital object.
   * @param operationID May not be null.
   * @param parameters If null, no operation parameters.
   */
  public boolean checkAuthorization(String userID, String[] delegatedids, String operationID, HeaderSet parameters) throws DOException, IOException {
      HeaderSet checkAuthParams = new HeaderSet();
      if(userID!=null) checkAuthParams.addHeader("userid",userID);
      if(delegatedids!=null) checkAuthParams.addHeader("delegatedids",delegatedids);
      checkAuthParams.addHeader("operationid",operationID);
      if(parameters!=null) checkAuthParams.addHeader("params",parameters);
      StreamPair pair = repo.performOperation(objectID, DOConstants.CHECK_AUTHORIZATION_OP_ID, checkAuthParams);
      try {
          pair.getOutputStream().close();
          HeaderSet result = new HeaderSet();
          result.readHeaders(pair.getInputStream());
          return result.getBooleanHeader("result",false);
      }
      finally {
          try { pair.close(); } catch (Throwable e) {}
      }
  }
  
  /** Read the DO server's response from the given InputStream and throw an
    * exception if there was a non-success status message. */
  static void checkStatus(InputStream in) 
    throws DOException, IOException
  {
    HeaderSet ack = new HeaderSet();
    ack.readHeaders(in);
    if(!ack.getStringHeader("status", "").equalsIgnoreCase("success")) {
      throw new DOException(ack.getIntHeader("code", DOException.SERVER_ERROR),
                            ack.getStringHeader("message", 
                                                "Unknown Server Error from response: "+ack));
    }
  }
    
  private void loadAttributes() 
    throws DOException, IOException
  {
    synchronized(attributes) {
      if(loadedAttributes) return;
      StreamPair io = null;
      try {
        io = performOperation(DOConstants.GET_ATTRIBUTES_OP_ID, null);
        io.getOutputStream().close();
        
        InputStream in = io.getInputStream();
        HeaderSet objAtts = null;
        HashMap newElAtts = new HashMap();
        while(true) {
          HeaderSet hdrs = new HeaderSet();
          if(!hdrs.readHeaders(in)) break;
          String msgType = hdrs.getMessageType();
          if(msgType.equals(DOConstants.OBJECT_ATTS_MSGTYPE)) {
            objAtts = hdrs.getHeaderSubset(DOConstants.PARAM_ATTRIBUTES);
          } else if(msgType.equals(DOConstants.ELEMENT_ATTS_MSGTYPE)) {
            String elementID = hdrs.getStringHeader("elementid", "");
            if(elementID.length()>=0) {
              HeaderSet thisElAtts = hdrs.getHeaderSubset(DOConstants.PARAM_ATTRIBUTES);
              newElAtts.put(elementID,thisElAtts);
            }
          } else if(msgType.equals("response") && "error".equals(hdrs.getStringHeader("status", null))) {
              String codeString = hdrs.getStringHeader("code", null);
              int code = DOException.INTERNAL_ERROR;
              if (codeString != null) {
                  try {
                      code = Integer.parseInt(codeString);
                  } catch (NumberFormatException e) {
                      // nothing
                  }
              }
              String message = hdrs.getStringHeader("message", null);
              if (message == null) {
                  message = "Extra info in response to list-attributes: "+hdrs;
              }
              throw new DOException(code, message);
          } else {
              // Example message: Extra info in response to list-attributes:response:status=error&message=Object '10.5240/1064-6784-6F6B-04BD-D04C-Y' does not exist&code=103
              // (this occurred when the object existed in the storage, but not the directory containing its elements)
              throw new DOException(DOException.INTERNAL_ERROR, "Extra info in response to list-attributes: "+hdrs);
          }
        }
        if(objAtts==null) {
          throw new DOException(DOException.SERVER_ERROR,
                                "No object attributes returned from list-attributes operation");
        }
        
        attributes = objAtts;
        elAtts = newElAtts;
        loadedAttributes = true;
      } finally {
        try { io.close(); } catch (Throwable t) {}
      }
    }
  }  
 
  /** Refresh information about Data Elements or Attributes.  
   * Called automatically by methods that are known to change the object, but this is needed if changes are made outside the object.
   * Also potentially needed after use of the generic "performOperation".
   */
  public void refresh() {
      clearAttributes();
  }
  
  void clearAttributes() {
      synchronized(attributes) {
          attributes = new HeaderSet();
          loadedAttributes = false;
      }
  }

  /** Return the date that this object was created */
  public java.util.Date getDateCreated() 
  throws DOException, IOException
  {
      return this.getDateCreated(null);
  }

  /** Return the date that this object was last modified */
  public java.util.Date getDateLastModified() 
  throws DOException, IOException
  {
      return this.getDateLastModified(null);
  }

  /** Gets the size in bytes of the data element, not including attributes */
  public long getSize()  throws DOException, IOException {
      return this.getSize(null);
  }
  
  /** Associates the given attributeValue with the given attributeName for this
   * object.  */
  public void setAttribute(String attributeName, String attributeValue)
  throws DOException, IOException
  {
      this.setAttribute(null,attributeName,attributeValue);
  }

  /** Atomically set more than one attribute */
  public void setAttributes(HeaderSet atts)
  throws DOException, IOException
  {
      this.setAttributes(null,atts);
  }

  /** Delete the given attributeName for this
   * object.  */
  public void deleteAttribute(String attributeName)
  throws DOException, IOException
  {
      this.deleteAttribute(null,attributeName);
  }

  /** Atomically delete more than one attribute */
  public void deleteAttributes(String[] atts)
  throws DOException, IOException
  {
      this.deleteAttributes(null,atts);
  }


  /** Associates the given attributeValue with the given attributeName for this
   * object.  */
  public String getAttribute(String attributeName, String defaultValue)
  throws DOException, IOException
  {
      return this.getAttribute(null,attributeName,defaultValue);
  }

  /** Return all attributes */
  public HeaderSet getAttributes() 
  throws DOException, IOException
  {
      return this.getAttributes(null);
  }

  /** Return the list of attribute names for this object */
  public String[] listAttributes() 
  throws DOException, IOException
  {
      return this.listAttributes(null);
  }


  /** Return the date that this object was created */
  java.util.Date getDateCreated(String elementID) 
  throws DOException, IOException
  {
      synchronized(attributes) {
          loadAttributes();
          HeaderSet attributes = elementID==null ? this.attributes : this.elAtts.get(elementID);
          return new java.util.Date(attributes==null ? 0 : attributes.getLongHeader(DATE_CREATED, 0));
      }
  }

  /** Return the date that this object was last modified */
  java.util.Date getDateLastModified(String elementID) 
  throws DOException, IOException
  {
      synchronized(attributes) {
          loadAttributes();
          HeaderSet attributes = elementID==null ? this.attributes : this.elAtts.get(elementID);
          return new java.util.Date(attributes==null ? 0 : attributes.getLongHeader(DATE_MODIFIED, 0));
      }
  }

  /** Gets the size in bytes of the data element, not including attributes */
  long getSize(String elementID)  throws DOException, IOException {
      synchronized(attributes) {
          loadAttributes();
          HeaderSet attributes = elementID==null ? this.attributes : this.elAtts.get(elementID);
          return attributes==null ? 0 : attributes.getLongHeader(DATA_SIZE, 0);
      }
  }
  
  /** Associates the given attributeValue with the given attributeName for this
   * object.  */
  void setAttribute(String elementID, String attributeName, String attributeValue)
  throws DOException, IOException
  {
      HeaderSet params = new HeaderSet();
      if(elementID!=null) params.addHeader(DOConstants.PARAM_ELEMENT_ID, elementID);

      StreamPair io = null;
      try {
          if(attributeValue!=null) {
              HeaderSet atts = new HeaderSet();
              atts.addHeader(attributeName, attributeValue);
              params.addHeader(DOConstants.PARAM_ATTRIBUTES, atts);
              io = performOperation(DOConstants.SET_ATTRIBUTES_OP_ID, params);
          } else {
              params.addHeader(DOConstants.PARAM_ATTRIBUTES, new String[] {attributeName});
              io = performOperation(DOConstants.DEL_ATTRIBUTES_OP_ID, params);
          }
      } finally {
          try { io.close(); } catch (Throwable t) {}
      }

      // could speed things up by updating the cached attribute list directly
      clearAttributes();
  }

  /** Atomically set more than one attribute */
  void setAttributes(String elementID, HeaderSet atts)
  throws DOException, IOException
  {
      HeaderSet params = new HeaderSet();
      if(elementID!=null) params.addHeader(DOConstants.PARAM_ELEMENT_ID, elementID);

      StreamPair io = null;
      try {
          params.addHeader(DOConstants.PARAM_ATTRIBUTES, atts);
          io = performOperation(DOConstants.SET_ATTRIBUTES_OP_ID, params);
      } finally {
          try { io.close(); } catch (Throwable t) {}
      }

      // could speed things up by updating the cached attribute list directly
      clearAttributes(); 
  }

  /** Delete the given attributeName for this
   * object.  */
  void deleteAttribute(String elementID, String attributeName)
  throws DOException, IOException
  {
      deleteAttributes(elementID, new String[]{attributeName});
  }

  /** Atomically delete more than one attribute */
  void deleteAttributes(String elementID, String[] atts)
  throws DOException, IOException
  {
      HeaderSet params = new HeaderSet();
      if(elementID!=null) params.addHeader(DOConstants.PARAM_ELEMENT_ID, elementID);

      StreamPair io = null;
      try {
          params.addHeader(DOConstants.PARAM_ATTRIBUTES, atts);
          io = performOperation(DOConstants.DEL_ATTRIBUTES_OP_ID, params);            
      } finally {
          try { io.close(); } catch (Throwable t) {}
      }

      // could speed things up by updating the cached attribute list directly
      clearAttributes(); 
  }

  /** Associates the given attributeValue with the given attributeName for this
   * object.  */
  String getAttribute(String elementID, String attributeName, String defaultValue)
  throws DOException, IOException
  {
      synchronized(attributes) {
          loadAttributes();
          HeaderSet attributes = this.attributes;
          if(elementID!=null) {
            attributes = this.elAtts.get(elementID);
            if(attributes==null) return defaultValue;
          }
          
          return attributes.getStringHeader(attributeName, defaultValue);
      }
  }

  /** Return all attributes */
  HeaderSet getAttributes(String elementID) 
  throws DOException, IOException
  {
      synchronized(attributes) {
          loadAttributes();
          HeaderSet attributes = elementID==null ? this.attributes : this.elAtts.get(elementID);
          if(attributes==null) return new HeaderSet();
          return new HeaderSet(attributes);
      }
  }

  /** Return the list of attribute names for this object */
  String[] listAttributes(String elementID) 
  throws DOException, IOException
  {
      int n;
      ArrayList keys;
      synchronized(attributes) {
          loadAttributes();

          HeaderSet attributes = elementID==null ? this.attributes : this.elAtts.get(elementID);
          if(attributes==null) return new String[0];
          n = attributes.size();
          keys = new ArrayList(n);
          for(HeaderItem item: attributes) {
              keys.add(item.getName());
          }
      }
      return (String[])keys.toArray(new String[n]);
  }
}
