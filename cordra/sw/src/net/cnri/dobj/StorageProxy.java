/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj;

import java.io.*;
import java.util.*;

/**
 * StorageProxy objects provide an interface to the storage layer of
 * a registry for a single object.
 */
public interface StorageProxy {

  /**
   * Get the object identifier of the given object.
   */
  public String getObjectID();

  /**
   * Get the repository identifier of the repository for the given object.
   */
  public String getRepoID();
    
  /**
   * Returns a StorageProxy for a different object in this Repository.
   */
  public StorageProxy getObjectAccessor(String objectID);

  
  /**
   * Returns true if the given digital object exists.
   */
  public boolean doesObjectExist()
    throws DOException;

  
  /**
   * Returns true if the given data element exists
   */
  public boolean doesDataElementExist(String elementID)
    throws DOException;

  
  /**
   * Returns the File in which the given data element is stored, if any.
   * This can return null on servers where data elements are not stored
   * in files.  This is used where operators need to do more with a data
   * element than simple read and write operations.  Examples inlude
   * indexes, databases, etc.
   */
  public File getFileForDataElement(String elementID)
    throws DOException;

  
  /**
   * Creates a new digital object with the given ID, if one does not already exist.
   * The caller must have permission to create a digital object, and this can only
   * be called on the StorageProxy for the repository object itself.
   * This returns the ID of the newly created object.
   */
  public String createObject(String newObjectID)
    throws DOException;

  
  /**
   * Creates a new digital object with the given ID, if one does not already exist.
   * This can only be ccalled on the StorageProxy for the repository object itself.
   * This returns the ID of the newly created object.
   */
  public String createObject(String newObjectID, String newObjectName)
    throws DOException;

  
  /**
   * Deletes the current object object along with all data elements or files that
   * are associated with it.
   */
  public void deleteObject()
    throws DOException;
  
  /**
   * Returns an Enumeration of all of the objects in the repository.
   */
  public Enumeration listObjects()
    throws DOException;
  

  /**
   * Returns a list of the data elements associated with the current object.
   */
  public Enumeration listDataElements()
    throws DOException;

  
  /**
   * Returns a stream for reading the identified data element for this object.
   */
  public InputStream getDataElement(String elementID)
    throws DOException;

  
  /** 
   * Writes data from the given InputStream as the data element for the given object ID.
   */
  public void storeDataElement(String elementID, InputStream input)
    throws Exception;
  
  /** 
    * Appends data from the given InputStream to the data element for the given object ID.
    */
  public void appendDataElement(String elementID, InputStream input)
    throws Exception;
  
  
  /** 
   * Deletes the specified data element from the current object.  Returns
   * true if the specified data element ever existed in the first place.
   */
  public boolean deleteDataElement(String elementID)
    throws DOException;
  
  /** Get all of the key-value attributes that are associated with the object.
    * If the given container is non-null then the attributes are put into it in
    * order to avoid instantiating a new HeaderSet.
    *
    * @return a HeaderSet containing all of the attributes that are associated
    * with the object
    */
  public HeaderSet getAttributes(HeaderSet container)
    throws DOException;
  
  
  /** Get all of the key-value attributes that are associated with the given
    * element in the object.  If the given container is non-null then the
    * attributes are put into it in order to avoid instantiating a new HeaderSet.
    *
    * @return a HeaderSet containing all of the attributes that are associated
    * with the element
    */
  public HeaderSet getElementAttributes(String elementID, HeaderSet container)
    throws DOException;
  
  
  /** Set the given key-value attributes in the object, replacing any existing
    * attributes that have the same keys.  This will not remove any attributes
    * from the object.
    */
  public void setAttributes(HeaderSet attributes)
    throws DOException;
  
  
  /** Set the given key-value attributes for the given element in the object, 
    * replacing any existing attributes that have the same keys.  This will 
    * not remove any attributes from the element
    */
  public void setElementAttributes(String elementID, HeaderSet attributes)
    throws DOException;
  
  
  /** Remove the attributes with the given keys from the object.
    */
  public void deleteAttributes(String attributeKeys[])
    throws DOException;
  
  /** Remove the attributes with the given keys from the element.
    */
  public void deleteElementAttributes(String elementID, String attributeKeys[])
    throws DOException;
  
  
  /** Serialize the entire object using the given format to the given stream */
  public void serializeObject(String format, OutputStream out) 
    throws DOException;
  
  
  /** Reset the object's state and re-initialize the object with the data in the 
   *  given InputStream in the given format. */
  public void deserializeObject(String format, InputStream in)
    throws DOException;

}

