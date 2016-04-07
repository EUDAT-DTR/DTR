/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;

import java.io.*;
import java.util.*;

/**
 * ConcreteStorageProxy objects provide an interface to the hash-based storage
 * layer of the DO server.
 */
public final class ConcreteStorageProxy
  implements RangeRequestStorageProxy
{
  private static final String PERMISSIONS_ELEMENT = "internal.element_perms";
  
  private String repoID = null;    // the identity of this repository
  private Storage storage = null;  // reference to the server storage
  private String objectID = null;  // the object upon which this operation is taking place
  private HeaderSet txnMetadata;   // metadata about the transaction, possibly to be stored in the log
  
  /**
   * Construct a storage accessor/proxy object that uses the given
   * storage mechanism and restricts access to the given objectID based
   * on the permissions granted to the given callerIDs.
   */
  public ConcreteStorageProxy(Storage storage, String repositoryID, String objectID, HeaderSet txnMetadata) {
    if(objectID==null || repositoryID==null) throw new NullPointerException();
    this.repoID = repositoryID;
    this.objectID = objectID;
    this.storage = storage;
    this.txnMetadata = txnMetadata;
  }
  
  /**
   * Get the object identifier of the given object.
   */
  public String getObjectID() {
      return objectID;
  }

  /**
   * Get the repository identifier of the repository for the given object.
   */
  public String getRepoID() {
      return repoID;
  }
    

  
  /**
   * Returns a StorageProxy for a different object in this Repository.  The
   * new StorageProxy will have the same access restrictions and parameters
   * as this StorageProxy for the new object.
   */
  public StorageProxy getObjectAccessor(String objectID) {
    return new ConcreteStorageProxy(this.storage, this.repoID, objectID, this.txnMetadata);
  }

  /**
   * Returns true if the given digital object exists.
   */
  public boolean doesObjectExist()
    throws DOException
  {
    return storage.doesObjectExist(objectID);
  }

  /**
   * Returns true if the given data element exists
   */
  public boolean doesDataElementExist(String elementID)
    throws DOException
  {
    return storage.doesDataElementExist(objectID, elementID);
  }
  
  /**
   * Returns the File in which the given data element is stored, if any.
   * This can return null on servers where data elements are not stored
   * in files.  This is used where operators need to do more with a data
   * element than simple read and write operations.  Examples inlude
   * indexes, databases, etc.
   */
  public File getFileForDataElement(String elementID)
    throws DOException
  {
    return storage.getFileForDataElement(objectID, elementID);
  }
  
  /**
    * Creates a new digital object with the given ID, if one does not already exist.
   * The caller must have permission to create a digital object, and the current
   * object must represent the current data storage.  This returns the ID of the
   * newly created object.
   */
  public String createObject(String newObjectID)
    throws DOException
  {
    return storage.createObject(newObjectID, null, txnMetadata, 0);
  }
  
  /**
   * Deletes the digital object along with all data elements or files that are associated
   * with it.
   */
  public void deleteObject()
    throws DOException
  {
    storage.deleteObject(objectID, txnMetadata, 0);
  }
  
  /**
   * Creates a new digital object with the given ID, if one does not already exist.
   * The caller must have permission to create a digital object, and the current
   * object must represent the current data storage.  This returns the ID of the
   * newly created object.
   */
  public String createObject(String newObjectID, String newObjectName)
    throws DOException
  {
    return storage.createObject(newObjectID, newObjectName, txnMetadata, 0);
  }
  
  /**
   * Returns an Enumeration of all of the objects in the repository.
   */
  public Enumeration listObjects()
    throws DOException
  {
    return storage.listObjects();
  }
  
  
  /**
   * Returns a list of the data elements associated with the current object.
   */
  public Enumeration listDataElements()
    throws DOException
  {
    return storage.listDataElements(objectID);
  }
  
  /**
   * Returns the identified data element for this object.  If the caller does
   * not have permission to read this stream, a DOException is thrown with code
   * PERMISSION_DENIED_ERROR.
   */
  public InputStream getDataElement(String elementID)
    throws DOException
  {
    return storage.getDataElement(objectID, elementID);
  }

  @Override
  public InputStream getDataElement(String elementID, long start, long len) throws DOException {
      if (storage instanceof RangeRequestStorage) {
          return ((RangeRequestStorage)storage).getDataElement(objectID, elementID, start, len);
      } else {
          InputStream in = storage.getDataElement(objectID, elementID);
          return new LimitedInputStream(in, start, len);
      }
  }
    
  /** 
   * Stores the bytes from the given input stream into the given data element 
   * for the object.
   */
  public void storeDataElement(String elementID, InputStream input)
    throws Exception
  {
    storage.storeDataElement(objectID, elementID, input, txnMetadata, false, 0);
  }
  
  /** 
   * Appends the bytes from the given input stream to the given data element
   * for the object.
   */
  public void appendDataElement(String elementID, InputStream input)
    throws Exception
  {
    storage.storeDataElement(objectID, elementID, input, txnMetadata, true, 0);
  }
  
  /** 
   * Deletes the specified data element from the current object.  Returns
   * true if the specified data element ever existed in the first place.
   */
  public boolean deleteDataElement(String elementID)
    throws DOException
  { 
    return storage.deleteDataElement(objectID, elementID, txnMetadata, 0);
  }

  
  
  
  
  /** Get all of the key-value attributes that are associated with the object.
    * If the given container is non-null then the attributes are put into it in
    * order to avoid instantiating a new HeaderSet.
    *
    * @return a HeaderSet containing all of the attributes that are associated
    * with the object
    */
  public HeaderSet getAttributes(HeaderSet container)
    throws DOException
  {
    return storage.getAttributes(objectID, null, container);
  }
  
  
  /** Get all of the key-value attributes that are associated with the given
    * element in the object.  If the given container is non-null then the
    * attributes are put into it in order to avoid instantiating a new HeaderSet.
    *
    * @return a HeaderSet containing all of the attributes that are associated
    * with the element
    */
  public HeaderSet getElementAttributes(String elementID, HeaderSet container)
    throws DOException
  {
    if(elementID==null) throw new NullPointerException();
    return storage.getAttributes(objectID, elementID, container);
  }
  
  
  /** Set the given key-value attributes in the object, replacing any existing
    * attributes that have the same keys.  This will not remove any attributes
    * from the object.
    */
  public void setAttributes(HeaderSet attributes)
    throws DOException
  {
    storage.setAttributes(objectID, null, attributes, txnMetadata, 0);
  }
  
  
  /** Set the given key-value attributes for the given element in the object, 
    * replacing any existing attributes that have the same keys.  This will 
    * not remove any attributes from the element
    */
  public void setElementAttributes(String elementID, HeaderSet attributes)
    throws DOException
  {
    if(elementID==null) throw new NullPointerException();
    storage.setAttributes(objectID, elementID, attributes, txnMetadata, 0);
  }

  /** Remove the attributes with the given keys from the object.
    */
  public void deleteAttributes(String attributeKeys[])
    throws DOException
  {
    storage.deleteAttributes(objectID, null, attributeKeys, txnMetadata, 0);
  }
  
  /** Remove the attributes with the given keys from the element.
    */
  public void deleteElementAttributes(String elementID, String attributeKeys[])
    throws DOException
  {
    if(elementID==null) throw new NullPointerException();
    storage.deleteAttributes(objectID, elementID, attributeKeys, txnMetadata, 0);
  }
  
  
  /** Serialize the entire object using the given format to the given stream */
  public void serializeObject(String format, OutputStream out) 
  throws DOException
  {
    storage.serializeObject(objectID, format, out);
  }

  
  /** Reset the object's state and re-initialize it with the data in the 
   *  given InputStream in the given format. */
  public void deserializeObject(String format, InputStream in)
  throws DOException
  {
    storage.deserializeObject(objectID, format, in);
  }


  

}

