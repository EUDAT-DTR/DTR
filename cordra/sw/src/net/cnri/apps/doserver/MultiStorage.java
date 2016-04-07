/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import com.sleepycat.je.*;

import net.cnri.apps.doserver.txnlog.AbstractTransactionQueue;
import net.cnri.dobj.*;
import net.handle.hdllib.*;
import net.cnri.util.*;

import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * MultiStorage stores digital objects and associated data elements
 * into one of several types of object storage modules.  The default storage
 * module is the HashDirectoryStorage and uses the filesystem to store objects.
 * Setting a attribute on an object will cause the entire object to be transferred
 * from one storage module to another.
 */
class MultiStorage
  extends AbstractStorage
{
  static final Logger logger = LoggerFactory.getLogger(MultiStorage.class);
  
  public static final String TRANSFER_FORMAT = "basiczip";
  public static final String STORAGE_ATTRIBUTE_KEY = "doserver.storagekey";
  
  private static final Object EMPTY_LOCK_VAL = new Object();
  
  private Main mainServer;
  private HashDirectoryStorage fsStorage;
  private HashMap<String, Storage> otherStorage = new HashMap();
  private StreamTable props = new StreamTable();
  private Object storageMUXLock = new Object();
  
  // the Berkeley DB database environment
  private Environment environment = null;
  
  // the Berkeley DB index database
  private Database muxDB;
  

  public MultiStorage() {
    
  }
  
  public void initWithDirectory(Main server, File baseDirectory)
    throws DOException
  {
    this.mainServer = server;
    this.fsStorage = new HashDirectoryStorage();
    this.fsStorage.initWithDirectory(server, new File(baseDirectory, "storage"));
    
    try {
      if(!baseDirectory.exists()) {
        baseDirectory.mkdirs();
      } else {
        try {
          File propsFile = new File(baseDirectory, "multistore.dct");
          if(propsFile.exists()) {
            props.readFromFile(propsFile);
          }
        } catch (Exception e) {
          logger.error("Error loading storage properties",e);
          throw new DOException(DOException.STORAGE_ERROR,
                                "Error reading storage properties: "+e);
        }
      }   
    } catch (Exception e) {
      throw new DOException(DOException.STORAGE_ERROR, "Unable to storage directory: "+e);
    }
    
    for(Iterator skeys=props.keySet().iterator(); skeys.hasNext(); ) {
      String key = (String)skeys.next();
      if(key.startsWith("store_")) {
        String storeKey = key.substring("store_".length());
        Object storeVal = props.get(key);
        if(!(storeVal instanceof StreamTable)) {
          logger.error("Invalid or missing value for key: "+storeKey+": "+storeVal+
                             " (value must be a dictionary)");
          continue;
        }
        StreamTable storeSettings = (StreamTable)storeVal;
        String classStr = storeSettings.getStr("classname", null);
        if(classStr==null) {
          logger.error("Invalid or missing classname for store "+storeKey+": "+storeSettings);
          continue;
        }
        
        File storeDir = new File(baseDirectory, storeSettings.getStr("basedir", "storage_"+storeKey));
        try {
          Storage store = (Storage)Class.forName(classStr).newInstance();
          store.initWithDirectory(server, storeDir);
          otherStorage.put(storeKey, store);
        } catch (Exception e) {
          if(e instanceof DOException) throw (DOException)e;
          throw new DOException(DOException.STORAGE_ERROR, 
                                "Storage instantiation error: "+e);
        }
      }
    }
    
    // put the default environment into the database
    otherStorage.put("default", fsStorage);
    
    // create the database environment
    try {
      File indexDir = new File(baseDirectory, "multi_index");
      if(!indexDir.exists()) indexDir.mkdirs();
      
      EnvironmentConfig envConfig = new EnvironmentConfig();
      envConfig.setTransactional(true);
      envConfig.setAllowCreate(true);
      envConfig.setSharedCache(true);
      environment = new Environment(indexDir, envConfig);
      
      com.sleepycat.je.Transaction openTxn = environment.beginTransaction(null, null);
      DatabaseConfig dbConfig = new DatabaseConfig();
      dbConfig.setTransactional(true);
      dbConfig.setAllowCreate(true);
      dbConfig.setSortedDuplicates(false);
      muxDB = environment.openDatabase(openTxn, "objindex", dbConfig);
      if(logger.isDebugEnabled()) {
        logger.debug("DB Stats: "+muxDB.getStats(null));
        logger.debug("DB Dir: "+indexDir.getAbsolutePath());
      }
      openTxn.commitSync();
    } catch (Exception e) {
      logger.error("Error loading storage index",e);
      throw new DOException(DOException.STORAGE_ERROR,
                            "Error loading storage index: "+e);
    }
  }
  
  public void close() {
      if (muxDB != null) try {
          muxDB.close();
      } catch (Exception e) {
          logger.error("Exception closing", e);
      }
      if (environment != null) try {
          environment.close();
      } catch (Exception e) {
          logger.error("Exception closing", e);
      }
      if (fsStorage != null) try {
          fsStorage.close();
      } catch (Exception e) {
          logger.error("Exception closing", e);
      }
      if (otherStorage != null) {
          for (Storage store : otherStorage.values()) {
              try {
                  store.close();
              } catch (Exception e) {
                  logger.error("Exception closing", e);
              }
          }
      }
  }
  
  
  public void initTransactionQueue(File txnDir) throws Exception {
      this.fsStorage.initTransactionQueue(txnDir);
      for (Storage store : (Collection<Storage>) otherStorage.values()) {
          store.setTransactionQueue(this.fsStorage.getTransactionQueue());
      }
  }
  public void setTransactionQueue(AbstractTransactionQueue txnQueue) {
      this.fsStorage.setTransactionQueue(txnQueue);
      for (Storage store : (Collection<Storage>) otherStorage.values()) {
          store.setTransactionQueue(this.fsStorage.getTransactionQueue());
      }
  }
  public AbstractTransactionQueue getTransactionQueue() {
      return this.fsStorage.getTransactionQueue();
  }
  
  
  /** Returns the storage ID for the storage module that is responsible for the given
    * object */
  private String storageKeyForObject(String objectID) 
  throws DOException
  {
    if(objectID==null) return "default";
    DatabaseEntry dbVal = new DatabaseEntry();
    DatabaseEntry dbKey = new DatabaseEntry(Util.encodeString(objectID.toLowerCase()));
    OperationStatus status = null;
    try {
      status = muxDB.get(null, dbKey, dbVal, null);
    } catch (Exception e) {
      throw new DOException(DOException.STORAGE_ERROR,
                            "Error loading storage index: "+e);
    }
    if(status==OperationStatus.NOTFOUND) {
      return "default";
    } else if(status==OperationStatus.SUCCESS) {
      String storageID = Util.decodeString(dbVal.getData());
      if(storageID==null || !otherStorage.containsKey(storageID)) {
        logger.warn("Unknown storage key: "+storageID+"; using default");
        return "default";
      } else {
        return storageID;
      }
    } else {
      throw new DOException(DOException.STORAGE_ERROR,
                            "Error accessing storage index for object "+objectID+": "+status);
    }
  }
  
  /** Returns the key defining which storage module is responsible for the given object */
  private Storage storageForObject(String objectID) 
    throws DOException
  {
    return (Storage)otherStorage.get(storageKeyForObject(objectID));
  }
  
  
  /** Move the object from the old storage module into the new one */
  private void moveObject(String objectID, String oldStorageID, String newStorageID) 
    throws DOException
  {
    Storage oldStorage = (Storage)otherStorage.get(oldStorageID);
    Storage newStorage = (Storage)otherStorage.get(newStorageID);
    
    if(oldStorage==null || newStorage==null || oldStorage==newStorage)
      return; // cancel the operation if the storage modules are the same or one is non-existent
    
    if(!oldStorage.doesObjectExist(objectID)) {
      throw new DOException(DOException.NO_SUCH_OBJECT_ERROR, 
                            "No such object exists in storage "+oldStorageID);
    }
    
    try {
      PipedOutputStream pout = new PipedOutputStream();
      PipedInputStream pin = new PipedInputStream(pout);
      SerializeObjectThread sot = new SerializeObjectThread(objectID, oldStorage, pout);
      sot.start();
      newStorage.deserializeObject(objectID, TRANSFER_FORMAT, pin);
      if(sot.err!=null) throw sot.err;
    } catch (Exception e) {
      logger.error("Exception in moveObject",e);  
      if(e instanceof DOException) throw (DOException)e;
      else throw new DOException(DOException.STORAGE_ERROR, 
                                 "Unable to serialize object from "+oldStorageID+" to "+newStorageID);
    }
    
    // if the transfer was complete, update the index and delete the old object
    try {
      muxDB.put(null, new DatabaseEntry(Util.encodeString(objectID.toLowerCase())),
                new DatabaseEntry(Util.encodeString(newStorageID)));
    } catch (Exception e) {
      throw new DOException(DOException.STORAGE_ERROR, 
                            "Error moving object in storage multiplexer DB: "+e);
    }
    
    try {
      oldStorage.deleteObject(objectID, false, System.currentTimeMillis()+1);
    } catch (Exception e) {
      // the entire operation doesn't fail if we can't delete the old object
      logger.error("Warning:  unable to delete object "+objectID+
                         " from old storage after a move",e);
    }
  }
  
  
  private class SerializeObjectThread 
  extends Thread
  {
    private Storage storage;
    private String objectID;
    private OutputStream out;
    DOException err = null;
    
    SerializeObjectThread(String objectID, Storage storage, OutputStream out) {
      this.storage = storage;
      this.objectID = objectID;
      this.out = out;
    }
    
    public void run() {
      try {
        storage.serializeObject(objectID, "basiczip", out);
      } catch (DOException e) {
        logger.error("Error serializing object",e);
        this.err = e;
      }
    }
  }

  
  /**
   * Creates a new digital object with the given ID, if one does not already exist.
   * Returns the object identifier.
   */
  public String createObject(String id, String objectName, boolean logTxn)
    throws DOException
  {
    return storageForObject(id).createObject(id, objectName, logTxn);
  }
  
  
  /**
   * Creates a new digital object with the given ID, if one does not already exist.
   * Returns the object identifier.
   */
  public String createObject(String id, String objectName, boolean logTxn, long timestamp)
    throws DOException
  {
    return storageForObject(id).createObject(id, objectName, logTxn, timestamp);
  }
  
  
  /** 
   * Deletes the given object.
   */
  public void deleteObject(String objectID, boolean logTxn)
    throws DOException
  {
    storageForObject(objectID).deleteObject(objectID, logTxn);
  }
  
  /** 
   * Deletes the given object.
   */
  public void deleteObject(String objectID, boolean logTxn, long timestamp)
    throws DOException
  {
    storageForObject(objectID).deleteObject(objectID, logTxn, timestamp);
    
    try {
      OperationStatus status = 
        muxDB.delete(null, new DatabaseEntry(Util.encodeString(objectID.toLowerCase())));
      logger.debug("Delete from mux status: "+status);
    } catch (Exception e) {
      logger.error("Error deleting object from mux table",e);
    }
  }
  
  
  /** Returns any known metadata for the digital object with the given identifier.
    * If the given object is non-null then the metadata is stored in
    * that object which is also returned.  Otherwise, a new instance
    * is constructed and returned.  Note that this method returns a non-null value
    * whether or not the object exists.
    */
  public DOMetadata getObjectInfo(String objectID, DOMetadata metadata)
    throws DOException
  {
    return storageForObject(objectID).getObjectInfo(objectID, metadata);
  }
  
  /** Sets the metadata for the given digital object. */
  public void setObjectInfo(DOMetadata metadata) 
    throws DOException
  {
    storageForObject(metadata.getObjectID()).setObjectInfo(metadata);
  }

  /** Remove the attributes with the given keys from the object or data element.
    * If the elementID is null then the attributes are removed from the object,
    * otherwise they are removed from the element. */
  public void deleteAttributes(String objectID, String elementID,
                               String attributeKeys[], boolean logTxn,
                               long timestamp)
    throws DOException
  {
    storageForObject(objectID).deleteAttributes(objectID, elementID, attributeKeys,
                                                logTxn, timestamp);
  }
  
  
  /** Add the given key-value attribute to the object, replacing any existing
    * attribute that has the same key.  If the elementID is non-null then the
    * attribute is associated with the identified element within the object.
    */
  public void setAttributes(String objectID, String elementID, 
                            HeaderSet attributes,
                            boolean logTxn, long timestamp)
    throws DOException
  {
    // check to see if the STORAGE_ATTRIBUTE_KEY is being set which may move the object
    // to another storage module
    logger.debug("Setting attributes: "+attributes+" for object: "+objectID+" and element: "+elementID);
    if(elementID==null && attributes.hasHeader(STORAGE_ATTRIBUTE_KEY) &&
       !objectID.equalsIgnoreCase(mainServer.getServerID())) {
      //synchronized(storageMUXLock) {
        String newStorageID = attributes.getStringHeader(STORAGE_ATTRIBUTE_KEY, "default");
        String oldStorageID = storageKeyForObject(objectID);
        if(!newStorageID.equals(oldStorageID)) {
          // we need to move the object and then apply the new attributes
          moveObject(objectID, oldStorageID, newStorageID);
        }
      //}
    }
    
    storageForObject(objectID).setAttributes(objectID, elementID, attributes,
                                             logTxn, timestamp);
  }
  
  
  /** Get the value that has been associated with the given key.  If no value
    * has been associated with the key then this will return null.  If the given
    * elementID is null then this will return object-level attributes.  Otherwise
    * it will return attributes for the given element. */
  public HeaderSet getAttributes(String objectID, String elementID, HeaderSet container) 
    throws DOException
  {
    return storageForObject(objectID).getAttributes(objectID, elementID, container);
  }
  
  
  /**
   * Returns true if the given digital object exists.
   */
  public boolean doesObjectExist(String objectID)
    throws DOException
  {
    return storageForObject(objectID).doesObjectExist(objectID);
  }

  
  /**
   * Returns true if the given data element exists
   */
  public boolean doesDataElementExist(String objectID, String elementID)
    throws DOException
  {
    return storageForObject(objectID).doesDataElementExist(objectID, elementID);
  }


  /**
   * Returns the File in which the given data element is stored, if any.
   * This can return null on servers where data elements are not stored
   * in files.  This is used where operators need to do more with a data
   * element than simple read and write operations.  Examples inlude
   * indexes, databases, etc.
   */
  public File getFileForDataElement(String objectID, String elementID)
    throws DOException
  {
    return storageForObject(objectID).getFileForDataElement(objectID, elementID);
  }
  
  
  /**
   * Iterator over the object IDs in the each of the storage modules
   * 
   */
  private class MultiEnumerator
    implements Enumeration
  {
    private Storage currentStorage = null;
    private Iterator repoIterator = null;
    private Enumeration objEnum = null;
    
    private Object nextResult = null;
    
    public MultiEnumerator() 
      throws DOException
    {
      repoIterator = otherStorage.values().iterator();
      objEnum = null; //fsStorage.listObjects();

      prefetchNextResult();
    }
    
    private synchronized void prefetchNextResult() {
      // if there are more results in the current storage, return them
      if(objEnum!=null && objEnum.hasMoreElements()) {
        nextResult = objEnum.nextElement();
        return;
      }
      
      // apparently no results from the current storage, so we'll get the next one
      while(repoIterator!=null && repoIterator.hasNext()) {
        currentStorage = (Storage)repoIterator.next();
        try {
          objEnum = currentStorage.listObjects();
          prefetchNextResult();
          if(nextResult!=null) return;
        } catch (Exception e) {
          logger.error("Exception in prefetchNextResult",e);
        }
      }
      
      // if we fall out of the loop then we've seen all the storage modules
      nextResult = null;
    }
    
    public synchronized boolean hasMoreElements() {
      return nextResult!=null;
    }
    
    
    public synchronized Object nextElement()
      throws java.util.NoSuchElementException
    {
      Object result = nextResult;
      if(result==null) throw new NoSuchElementException();
      prefetchNextResult();
      return result;
    }
    
  }
  
  
  /**
   * Returns an Enumeration of all of the objects in the repository.
   */
  public Enumeration listObjects()
    throws DOException
  {
    return new MultiEnumerator();
  }
  
  
  /**
   * Returns an Enumeration (so that not all elements have to be loaded at once)
   */
  public Enumeration listDataElements(String objectID)
    throws DOException
  {
    return storageForObject(objectID).listDataElements(objectID);
  }
  
  
  /**
   * Returns the identified data element for the given object.
   */
  public InputStream getDataElement(String objectID, String elementID)
    throws DOException
  {
    return storageForObject(objectID).getDataElement(objectID, elementID);
  }
  
  
  /** 
   * Stores the data read from the given InputStream into the given data element for
   * the object identified by objectID.  This reads from the InputStream until the
   * end of the stream has been reached.
   */
  public void storeDataElement(String objectID, String elementID, 
                               InputStream input, boolean logTxn, boolean append)
    throws DOException
  {
    storageForObject(objectID).storeDataElement(objectID, elementID, input, logTxn, append);
  }
  
  
  /** 
   * Stores the data read from the given InputStream into the given data element for
   * the object identified by objectID.  This reads from the InputStream until the
   * end of the stream has been reached.
   */
  public void storeDataElement(String objectID, String elementID, 
                               InputStream input, boolean logTxn, 
                               boolean append, long timestamp)
    throws DOException
  {
    storageForObject(objectID).storeDataElement(objectID, elementID, input, 
                                                logTxn, append, timestamp);
  }
  
  
  /** 
   * Deletes the specified data element from the given object.  Returns true if the
   * specified data element ever existed in the first place.
   */
  public boolean deleteDataElement(String objectID, String elementID, boolean logTxn)
    throws DOException
  {
    return storageForObject(objectID).deleteDataElement(objectID, elementID, logTxn);
  }
  
  
  /** 
   * Deletes the specified data element from the given object.  Returns true if the
   * specified data element ever existed in the first place.
   */
  public boolean deleteDataElement(String objectID, String elementID, boolean logTxn,
                                   long timestamp)
    throws DOException
  {
    return storageForObject(objectID).deleteDataElement(objectID, elementID, logTxn, timestamp);
  }
  
  /** Write the entire object into the given OutputStream in the given format */
  public void serializeObject(String objectID, String format, OutputStream out)
    throws DOException
  {
    storageForObject(objectID).serializeObject(objectID, format, out);
  }
  
  /** Reset the object's state and re-initialize the object with the data in the 
   *  given InputStream in the given format. */
  public void deserializeObject(String objectID, String format, InputStream in) 
    throws DOException
  {
    storageForObject(objectID).deserializeObject(objectID, format, in);
  }
  
}

