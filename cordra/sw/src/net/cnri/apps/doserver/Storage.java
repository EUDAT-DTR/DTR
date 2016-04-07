/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.apps.doserver.txnlog.AbstractTransactionQueue;
import net.cnri.dobj.*;

import java.io.*;
import java.util.*;

/**
 * This interface specifies an interface to the back-end storage system
 * for a registry.
 */
public interface Storage {
    
  /** Initializes the storage for use with server based in the given storage directory. */
  public void initWithDirectory(Main server, File baseDirectory)
    throws DOException;

  public void close();
  
  /** Returns the transaction queue set up by this storage */
  public AbstractTransactionQueue getTransactionQueue();
  
  /** Set the transaction queue (used e.g. by Multi-Storage to arrange for a unique queue) */
  public void setTransactionQueue(AbstractTransactionQueue txnQueue);
  
  /** Initialize the transaction queue in the given directory---server calls this after initializing storage */
  public void initTransactionQueue(File txnDir) throws Exception;
  
  /**
   * Creates a new digital object with the given ID, if one does not already exist.
   * Returns the object identifier.  If logTxn is set, this change will be 
   * recorded in the transaction log, and therefore replicated to the other
   * servers.
   */
  public String createObject(String objectID, String objectName, boolean logTxn)
    throws DOException;
    
  /**
   * Creates a new digital object with the given ID, if one does not already exist.
   * Returns the object identifier.  If logTxn is set, this change will be 
   * recorded in the transaction log, and therefore replicated to the other
   * servers.  The timestamp parameter refers to the date/time that this
   * create operation was supposed to have happened.  The timestamp parameter
   * should only be used by the replication manager.
   */
  public String createObject(String objectID, String objectName, boolean logTxn, long timestamp)
    throws DOException;
    
  /** Returns any known metadata for the digital object with the given identifier.
  * If the given DOMetadata object is non-null then the metadata is stored in
  * that object which is also returned.  Otherwise, a new DOMetadata instance
  * is constructed and returned.  Note that this method returns a non-null value
  * whether or not the object exists.
  */
  public DOMetadata getObjectInfo(String objectID, DOMetadata metadata)
    throws DOException;
  
  
  /** Sets the metadata for the object specified by the given DOMetadata. */
  public void setObjectInfo(DOMetadata metadata)
    throws DOException;
  
  
  /**
   * Deletes the digital object along with all data elements or files that 
   * are associated with it.  If logTxn is set, this change will be recorded 
   * in the transaction log, and therefore replicated to the other servers.
   */
  public void deleteObject(String objectID, boolean logTxn)
    throws DOException;
  
  
  /**
   * Deletes the digital object along with all data elements or files that 
   * are associated with it.  If logTxn is set, this change will be recorded 
   * in the transaction log, and therefore replicated to the other servers.
   * The asOfTimestamp parameter is provided in order to delete the given
   * object as of a certain date/time for replication purposes.
   */
  public void deleteObject(String objectID, boolean logTxn, long asOfTimestamp)
    throws DOException;
  

  /**
   * Returns true if the given digital object exists.
   */
  public boolean doesObjectExist(String objectID)
    throws DOException;
  
  /**
   * Returns an Enumeration of all of the object IDs in the repository.
   */
  public Enumeration listObjects()
    throws DOException;
  
  /**
   * Returns an Enumeration (so that not all elements have to be loaded at once)
   */
  public Enumeration listDataElements(String objectID)
    throws DOException;
  
  /**
   * Returns true if the given data element exists
   */
  public boolean doesDataElementExist(String objectID, String elementID)
    throws DOException;
  
  /**
   * Returns the File in which the given data element is stored, if any.
   * This can return null on servers where data elements are not stored
   * in files.  This is used where operators need to do more with a data
   * element than simple read and write operations.  Examples inlude
   * indexes, databases, etc.
   */
  public File getFileForDataElement(String objectID, String elementID)
    throws DOException;
  
  /**
   * Returns the identified data element for the given object.
   */
  public InputStream getDataElement(String objectID, String elementID)
    throws DOException;
  
  /** 
   * Stores the data read from the given InputStream into the given data element for
   * the object identified by objectID.  This reads from the InputStream until the
   * end of the stream has been reached. If logTxn is set, this change will be 
   * recorded in the transaction log, and therefore replicated to the other servers.
   * If append is true, then this will append the bytes from the given input stream
   * to the existing data element.
   */
  public void storeDataElement(String objectID, String elementID, 
                               InputStream input, boolean logTxn,
                               boolean append)
    throws DOException;

  /** 
   * Stores the data read from the given InputStream into the given data element for
   * the object identified by objectID.  This reads from the InputStream until the
   * end of the stream has been reached. If logTxn is set, this change will be 
   * recorded in the transaction log, and therefore replicated to the other servers.
   * If append is true, then this will append the bytes from the given input stream
   * to the existing data element.  The given timestamp is used to record the date
   * that the data element was updated, if not now.  The timestamp parameter should
   * only be used by the replication manager.
   */
  public void storeDataElement(String objectID, String elementID, 
                               InputStream input, boolean logTxn,
                               boolean append, long timestamp)
    throws DOException;

  /** 
    * Deletes the specified data element from the given object.  Returns true 
    * if the specified data element ever existed in the first place.  If 
    * logTxn is set, this change will be recorded in the transaction log, 
    * and therefore replicated to the other servers.
    */
  public boolean deleteDataElement(String objectID, String elementID,
                                   boolean logTxn)
    throws DOException;

  /** 
    * Deletes the specified data element from the given object.  Returns true 
    * if the specified data element ever existed in the first place.  If 
    * logTxn is set, this change will be recorded in the transaction log, 
    * and therefore replicated to the other servers.  The given timestamp is 
    * used to record the date that the data element was deleted, if not now.
    * The timestamp parameter should only be used by the replication manager.
    */
  public boolean deleteDataElement(String objectID, String elementID,
                                   boolean logTxn, long timestamp)
    throws DOException;
  
  
  
  /** Get the attributes for the given object or element within the object.
   *  If elementID is null then this will get the attributes of the object.
   *  If the given container is non-null then the attributes will be added to
   *  it and returned.
   */
  public HeaderSet getAttributes(String objectID, String elementID, HeaderSet container) 
    throws DOException;

  

  /** Add the given key-value attribute to the object, replacing any existing
    * attribute that has the same key.  If the elementID is non-null then the
    * attribute is associated with the identified element within the object.
    */
  public void setAttributes(String objectID, String elementID, 
                            HeaderSet attributes,
                            boolean logTxn, long timestamp)
    throws DOException;

  
  /** Remove the attributes with the given keys from the object or data element.
    * If the elementID is null then the attributes are removed from the object,
    * otherwise they are removed from the element. */
  public void deleteAttributes(String objectID, String elementID,
                               String attributeKeys[], boolean logTxn,
                               long timestamp)
    throws DOException;
 

  /** Write the entire object into the given OutputStream in the given format */
  public void serializeObject(String objectID, String format, OutputStream out)
    throws DOException;
  
  /** Reset the object's state and re-initialize the object with the data in the 
   *  given InputStream in the given format. */
  public void deserializeObject(String objectID, String format, InputStream in) 
  throws DOException;

  /** Version with enhanced logging (stores txnMetadata in transaction) */
  public String createObject(String objectID, String objectName, HeaderSet txnMetadata, long timestamp)
  throws DOException;

  /** Version with enhanced logging (stores txnMetadata in transaction) */
  public void deleteObject(String objectID, HeaderSet txnMetadata, long asOfTimestamp)
  throws DOException;

  /** Version with enhanced logging (stores txnMetadata in transaction) */
  public void storeDataElement(String objectID, String elementID, 
          InputStream input, HeaderSet txnMetadata,
          boolean append, long timestamp)
  throws DOException;

  /** Version with enhanced logging (stores txnMetadata in transaction) */
  public boolean deleteDataElement(String objectID, String elementID,
          HeaderSet txnMetadata, long timestamp)
  throws DOException;

  /** Version with enhanced logging (stores txnMetadata in transaction) */
  public void setAttributes(String objectID, String elementID, 
          HeaderSet attributes,
          HeaderSet txnMetadata, long timestamp)
  throws DOException;

  /** Version with enhanced logging (stores txnMetadata in transaction) */
  public void deleteAttributes(String objectID, String elementID,
          String attributeKeys[], HeaderSet txnMetadata,
          long timestamp)
  throws DOException;
}
