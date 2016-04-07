/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.apps.doserver.txnlog.*;
import net.cnri.apps.doserver.txnlog.AbstractTransactionQueue.CloseableEnumeration;
import net.cnri.dobj.*;
import net.cnri.util.*;
import java.io.*;
import java.util.Iterator;
import java.util.Random;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReplicationManager {
  static final Logger logger = LoggerFactory.getLogger(ReplicationManager.class);

  // The number of minutes to sleep between each replication cycle
  private static int SLEEP_MINUTES = 1;
  
  private static final String STATUS_FILE_NAME = "repstat.dct";
  public static final String PARAM_TXN_ID = "txn_id";
  public static final String PARAM_OBJ_ID = "object_id";
  public static final String PARAM_TXN_TYPE = "txn_type";
  public static final String PARAM_TIMESTAMP = "tstamp";
  public static final String PARAM_ACTUAL_TIMESTAMP = "atstamp";
  
  private static final String LAST_TXN_STATKEY = "last_txn";
  
  public static final String TXN_TYPE_ADD_OBJ = "add";
  public static final String TXN_TYPE_DEL_OBJ = "del";
  public static final String TXN_TYPE_UPDATE_ELEMENT = "update_element";
  public static final String TXN_TYPE_DEL_ELEMENT = "del_element";
  public static final String TXN_TYPE_COMMENT = "comment";
  public static final String TXN_TYPE_ATT_UPDATE = "update_attribute";
  public static final String TXN_TYPE_ATT_DELETE = "delete_attribute";
  public static final String TXN_TYPE_LOGGED_UPDATE = "logged_update";
  
  
  private Random random = new Random();
  private Main mainServer = null;
  private Storage mainStorage = null;
  private ReplicationThread runner = null;
  private PushReplicationThread pushRunner = null;
  private StreamTable status = null;
  private File statusFile = null;
  private Object goLock = new Object();
  private boolean keepRunning = true;
  
  
  public ReplicationManager(Main m, Storage storage) 
    throws Exception
  {
    mainServer = m;
    mainStorage = storage;
    statusFile = new File(mainServer.getBaseFolder(), STATUS_FILE_NAME);
    status = new StreamTable();
    if(statusFile.exists()) {
      try { 
        status.readFromFile(statusFile);
      } catch (Exception e) {
        throw new Exception("Error reading replication status file ("+
                            statusFile.getAbsolutePath()+"): "+e, e);
      }
      if(!statusFile.canWrite()) {
        throw new Exception("Replication status file ("+
                            statusFile.getAbsolutePath()+
                            ") is not writable!");
      }
    }
  }    
  
  final synchronized void doPullReplication() {
      if(this.runner!=null && runner.isAlive()) return; // already running
      this.runner = new ReplicationThread();
      this.runner.setPriority(Thread.MIN_PRIORITY);
      this.runner.start();
  }

  final synchronized void doPushReplication() {
    if(this.pushRunner!=null && pushRunner.isAlive()) return; // already running
    this.pushRunner = new PushReplicationThread();
    this.pushRunner.start();
  }
  
  /** Return the operator used to handle replication operations from other servers.
    * This will return null if we are using 'push' synchronization mode as we will
    * push changes to other servers instead of other servers pulling from us. */
  final DOOperation getOperator() {
    return new ReplicationOperator(this, mainServer, mainStorage);
  }
  
  
  public void shutdown() {
    synchronized(goLock) {
      keepRunning = false;
      goLock.notifyAll();
    }
  }
  
  private class PushReplicationThread
    extends Thread
  {
    private boolean keepPushing = true;
    
    void stopPushing() {
      keepPushing = false;
    }
    
    public void run() {
      while(keepRunning) {
        try {
          synchronized (goLock) {
            goLock.wait(1000 * 60 * SLEEP_MINUTES); // sleep for two minutes
          }
        } catch (Throwable t) {}
        if(!keepRunning) continue;
        
        try {
          // resolve the service information
          DOAuthentication thisServiceID = mainServer.getAuth();
          String serviceID = thisServiceID.getID();
          // clear the service information cache
          DOClient.getResolver().getResolver().clearCaches();
          
          DOServiceInfo thisService = new DOServiceInfo(serviceID);
          int serverCount = thisService.getServerCount();
          
          // if the only server in the list is ourself, then we are in
          // offline mode, so wait a while and then try again
          if(serverCount<=1 && mainServer.isThisServer(thisService.getServer(0).getServerID())) {
            continue;
          }
          
          // randomize the order of the other servers so that we don't always 
          // push to the same server
          int randomServers[] = new int[serverCount];
          for(int i=0; i<serverCount; i++) {
            randomServers[i] = i;
          }
          if(serverCount>1) {
            int iterations = serverCount + (random.nextInt()%serverCount);
            for(int i=0; i<iterations; i++) {
              int idx1 = random.nextInt()%serverCount;
              int idx2;
              do {
                idx2 = random.nextInt()%serverCount;
              } while (idx1==idx2);
              int tmp = randomServers[idx1];
              randomServers[idx1] = randomServers[idx2];
              randomServers[idx2] = tmp;
            }
          }
          
          // push our changes to one of the other servers
          for(int i = 0; i<serverCount; i++) {
            int serverIdx = randomServers[i];
            DOServerInfo server = null;
            StreamPair io = null;
            String serverID = null;
            boolean wasSuccessfull = false;
            CloseableEnumeration txnEnum = null;
            try {
              server = thisService.getServer(serverIdx);
              serverID = server.getServerID();
              
              if(mainServer.isThisServer(serverID)) {
                continue;  // don't push changes to ourselves...
              }
              
              // get a list of transactions that have yet to be pushed out...
              long lastTime = status.getLong("last_push_time", 0);
              logger.debug("pushing transactions since: {} to {}",lastTime,server);
              
              txnEnum = mainServer.getTxnQueue().getCloseableScanner(lastTime);
              if(txnEnum.hasMoreElements()) {
                logger.debug("there are transactions to push...");
                
                // connect to the other server only if there are transactions to push
                DOClientConnection conn = new DOClientConnection(mainServer.getAuth());
                conn.connect(server);
                
                HeaderSet txnInfo = new HeaderSet();
                HeaderSet response = new HeaderSet();
                while(txnEnum.hasMoreElements()) {
                  if(!keepPushing) break;
                  
                  Transaction txn = (Transaction)txnEnum.nextElement();
                  if(logger.isDebugEnabled()) {
                    logger.debug("  sending txn with timestamp: "+
                                       txn.timestamp+
                                       (txn.timestamp<=lastTime ? " !!!" : ""));
                  }
                  txnInfo.removeAllHeaders();
                  response.removeAllHeaders();
                  
                  txnInfo.addHeader(PARAM_OBJ_ID, txn.objectID);
                  txnInfo.addHeader(PARAM_TXN_TYPE, getTxnTypeStr(txn.action));
                  txnInfo.addHeader(PARAM_TIMESTAMP, txn.timestamp);
                  if(txn.dataElement!=null)
                    txnInfo.addHeader(DOConstants.PARAM_ELEMENT_ID, txn.dataElement);
                  if(txn.attributes!=null)
                    txnInfo.addHeader(DOConstants.PARAM_ATTRIBUTES, txn.attributes);
                  InputStream dataIn = null;
                  if(txn.action==Transaction.ACTION_OBJ_DATA_UPDATE) {
                    dataIn = mainStorage.getDataElement(txn.objectID, txn.dataElement);
                  }
                  boolean writeData = dataIn!=null;
                  try {
                    if(logger.isDebugEnabled()) conn.DEBUG = true;
                    io = conn.performOperation(serviceID,
                                               DOConstants.PUSH_REPO_TXN_OP_ID, 
                                               txnInfo);
                    if(writeData) {
                      IOForwarder.forwardStream(dataIn, io.getOutputStream());
                    }
                    
                    // close any output
                    io.getOutputStream().close();
                  } catch (DOException e) {
                    if(e.getErrorCode()==DOException.REPLICATION_ITEM_OUT_OF_DATE) {
                      logger.warn("transaction "+txn+" being ignored - out of date");
                      writeData = false;
                    } else {
                      throw e;
                    }
                  } finally {
                    if(dataIn!=null) {
                      try { dataIn.close(); } catch (Throwable t) {}
                    }
                    try { io.getOutputStream().close(); } catch (Throwable t) {}
                  }
                  
                  // read any response...
                  response.readHeaders(io.getInputStream());
                  lastTime = txn.timestamp;
                  try { io.getInputStream().close(); } catch (Exception e) {}
                  status.put("last_push_time", lastTime);
                }
                
                // if we successfully pushed all transactions to a server, break the loop
                break;
              }
            } catch (Exception e) {
              // exception pushing changes to a server... keep going though
              logger.error("Error pushing changes to "+server,e);
              logger.info("Will try the next server...");
              wasSuccessfull = false;
            } finally {
                if (txnEnum != null) txnEnum.close();
            }
            if(wasSuccessfull) {
              // we've pushed to one server - that's all that is necessary
              break;
            }
            
            saveStatus();
          }
        } catch (Throwable t) {
          logger.error("Error attempting push replication",t);
          logger.info("Will try again soon");
        }
      }
    }
  }
  
  private class ReplicationThread
    extends Thread
  {
    
    public void run() {
      // sleep for little while to let the startup process settle down
      try { 
        if(keepRunning) {
          synchronized(goLock) { goLock.wait(30000); }
        }
      } catch (Exception e) {}
      
      try {
        while(keepRunning) {
          // re-query the server information from the handle system in case
          // the service information has changed
          
          try {
            // resolve the service information
            DOAuthentication thisServiceID = mainServer.getAuth();
            String serviceID = thisServiceID.getID();
            DOServiceInfo thisService = null;
            try {
                thisService = new DOServiceInfo(serviceID);
            }
            catch(DOException e) {
                // this allows us to set up the repository handle after starting the repository
                if(e.getErrorCode()==DOException.UNABLE_TO_LOCATE_OBJECT_ERROR) {
                    DOConnection.getResolver().getResolver().clearCaches();
                }
                throw e;
            }
            DOClientConnection conn = null;
            int serverCount = thisService.getServerCount();
            
            // replicate from each server...
            for(int i = 0; i<serverCount; i++) {
              DOServerInfo server = null;
              StreamPair io = null;
              String serverID = null;
              try {
                server = thisService.getServer(i);
                serverID = server.getServerID();
                
                if(mainServer.isThisServer(serverID)) {
                  continue;  // don't do replication from ourselves...
                }
                
                StreamTable serverStatus = (StreamTable)status.get("server."+serverID);
                if(serverStatus==null) {
                  serverStatus = new StreamTable();
                  status.put("server."+serverID, serverStatus);
                }
                logger.debug("Replicating with server {}; status={}",serverID,serverStatus);
                
                // TODO: add connection caching
                conn = new DOClientConnection(mainServer.getAuth());
                conn.connect(server);
                
                // build and submit the request for transactions since the last update
                HeaderSet params = new HeaderSet();
                String lastTxnID = serverStatus.getStr(LAST_TXN_STATKEY, "");
                if(serverStatus.containsKey(LAST_TXN_STATKEY)) {
                  params.addHeader(PARAM_TXN_ID, lastTxnID);
                }
                
                io = conn.performOperation(serviceID,
                                           DOConstants.GET_REPO_TXNS_OP_ID,
                                           params);
                
                InputStream in = io.getInputStream();
                HeaderSet record = new HeaderSet();
                while(record.readHeaders(in)) {
                  String timestamp = record.getStringHeader(PARAM_TIMESTAMP, null);
                  if(timestamp==null) {
                    logger.warn("Warning:  ignoring received transaction "+
                                       "with no timestamp: "+record);
                    continue;
                  }
                  
                  processReplicationRecord(conn, record);
                  serverStatus.put(LAST_TXN_STATKEY, timestamp);
                }
                
              } catch (Exception e) {
                logger.error("Error replicating with server: "+server,e);
              } finally {
                try { io.getOutputStream().close(); } catch (Throwable t) {}
                try { io.getInputStream().close(); } catch (Throwable t) {}
                try { conn.close(); } catch (Throwable t) {}
                saveStatus();
              }
            }
            
            // refresh the primary information
          } catch (Throwable t) {
            logger.error("Replication Error",t);
          }
          
          try {
            if(keepRunning) {
              synchronized(goLock) { goLock.wait(60000 * SLEEP_MINUTES);  }
            }
          } catch (Throwable t) {}
        } // end while
      } finally {
        runner = null;
      }
    }
    
  }

  
  private final void saveStatus() {
    try {
      status.writeToFile(statusFile);
    } catch (Throwable t) {
      logger.error("Unable to save replication status!",t);
    }
  }
  
  private final void processReplicationRecord(DOClientConnection conn, HeaderSet record) 
    throws Exception
  {
    String txnType = record.getStringHeader(PARAM_TXN_TYPE, "none").toLowerCase();
    String objID = record.getStringHeader(PARAM_OBJ_ID, null);
    long timestamp = record.getLongHeader(PARAM_TIMESTAMP, 0);
    String elementID = record.getStringHeader(DOConstants.PARAM_ELEMENT_ID, null);
    long actualTime = record.getLongHeader(PARAM_ACTUAL_TIMESTAMP, 0);
    
    // the 'actual' time can override the timestamp of the transaction record
    // this is for transactions that happened before they were recorded into
    // a replication transaction log
    if(actualTime!=0) timestamp = actualTime;
    
    if(objID!=null) objID = objID.trim();
    
    logger.debug(">>>transaction: {}",record);
    
    // the other side may report transactions that it received from others (such as us)
    // we should not record those in our own transaction log, however we will record changes
    // that we receive from others
    boolean logTxnReceived = true;
    
    synchronized(objID.toLowerCase().intern()) {
      if(txnType.equals(TXN_TYPE_LOGGED_UPDATE)) {
        // ignore it
        logTxnReceived = false;
      } else if(txnType.equals(TXN_TYPE_ADD_OBJ)) {
        // create the object locally...
        DOMetadata metadata = mainStorage.getObjectInfo(objID, null);
        if(metadata.getDateCreated() < timestamp) {
          // the object has not yet been created on this machine...
          if(metadata.getDateDeleted() > timestamp) {
            // object was deleted after this creation... do nothing
          } else if(!metadata.objectExists()) { // create the object if necessary
            mainStorage.createObject(objID, null, false);
            metadata.setDateDeleted(0);
          }
          metadata.setDateCreated(timestamp);
          metadata.updateModification(timestamp);
          mainStorage.setObjectInfo(metadata);
        }
      } else if(txnType.equals(TXN_TYPE_ATT_UPDATE)) {
        HeaderSet atts = record.getHeaderSubset(DOConstants.PARAM_ATTRIBUTES);
        mainStorage.setAttributes(objID, elementID, atts,
                                  false, timestamp);
      } else if(txnType.equals(TXN_TYPE_ATT_DELETE)) {
        HeaderSet atts = record.getHeaderSubset(DOConstants.PARAM_ATTRIBUTES);
        ArrayList keys = new ArrayList();
        for(Iterator it=atts.iterator(); it.hasNext(); ) {
          HeaderItem item = (HeaderItem)it.next();
          keys.add(item.getName());
        }
        String keyArray[] = (String[])keys.toArray(new String[keys.size()]);
        mainStorage.deleteAttributes(objID, elementID, keyArray, false, timestamp);
      } else if(txnType.equals(TXN_TYPE_DEL_OBJ)) {
        // delete the local object...
        DOMetadata metadata = mainStorage.getObjectInfo(objID, null);
        if(metadata.getDateDeleted() < timestamp) {
          // the object has not yet been deleted on this machine
          if(metadata.getDateCreated() > timestamp) {
            // object was re-created after this deletion... delete all elements
            // that have a timestamp less than this event
            for(Iterator it=metadata.getTagNames(); it.hasNext(); ) {
              String tag = (String)it.next();
              if(!tag.startsWith("de.")) continue;
              String val = metadata.getTag(tag, "0");
              long deTimestamp;
              try {
                deTimestamp = Long.parseLong(val);
              } catch (Exception e) {
                logger.error("Invalid timestamp: obj="+objID+"; element="+tag+"; val="+val,e);
                continue;
              }
              if(deTimestamp < timestamp) {
                mainStorage.deleteDataElement(objID, tag.substring("de.".length()), false,
                                              timestamp);
              }
            }
          } else if(metadata.objectExists()) { // delete the object, if necessary
            mainStorage.deleteObject(objID, false, timestamp);
            metadata.clearTags();
          }
          metadata.setDateDeleted(timestamp);
          metadata.updateModification(timestamp);
          mainStorage.setObjectInfo(metadata);
        }
        
      } else if(txnType.equals(TXN_TYPE_UPDATE_ELEMENT)) {
        DOMetadata metadata = mainStorage.getObjectInfo(objID, null);
        if(elementID==null) 
          throw new DOException(DOException.REPLICATION_ERROR, 
                                "Got element-update replication transaction with no element ID: "+
                                record);
        // check for the special case when the object doesn't yet exist
        boolean objectExists = metadata.objectExists();
        if(!objectExists && metadata.getDateDeleted()<timestamp) {
          // auto-create the object with the timestamp of this transaction
          mainStorage.createObject(objID, null, false);
          metadata.setDateCreated(timestamp);
          metadata.setDateDeleted(0);
          metadata.updateModification(timestamp);
          mainStorage.setObjectInfo(metadata);
          objectExists = true;
        } else if(!objectExists) {
          // the object doesn't exist locally and the object was deleted after this element's timestamp
        }

        String mdTagName = "de."+elementID;
        
        String oldTimestampStr = metadata.getTag(mdTagName, "0");
        long oldTimestamp = 0;
        try {
          oldTimestamp = Long.parseLong(oldTimestampStr);
        } catch (Exception e) {
          throw new DOException(DOException.REPLICATION_ERROR, 
              "Invalid data element timestamp in metadata: "+
                  oldTimestampStr);
        }
        
        if(oldTimestamp>timestamp) {
          // there is already a newer version of the data element on this server
          logger.warn("Received outdated element-updated transaction during "+
                      "replication: object="+objID+"; element="+elementID+";");
          return;
        }
        
        if(objectExists) {
          metadata.setTag(mdTagName, String.valueOf(timestamp));
          
          String createdTagName = "de-created." + elementID;
          if(oldTimestamp==0 && metadata.getTag(createdTagName,null)==null) metadata.setTag(createdTagName,String.valueOf(timestamp));
          
          // retrieve the updated data element
          HeaderSet params = new HeaderSet();
          params.addHeader("elementid", elementID);
          try {
            StreamPair getDEPair = conn.performOperation(objID, 
                DOConstants.GET_DATA_OP_ID,
                params);
            getDEPair.getOutputStream().close();
            mainStorage.storeDataElement(objID, elementID, getDEPair.getInputStream(),
                false, false);
            metadata.updateModification(timestamp);
            mainStorage.setObjectInfo(metadata);
          } catch (DOException doError) {
            int errCode = doError.getErrorCode();
            if(errCode==DOException.NO_SUCH_OBJECT_ERROR || errCode==DOException.NO_SUCH_ELEMENT_ERROR) {
              // ignore the error as the object or element was deleted
            } else {
              logger.error("Unable to replicate element "+elementID+" from object "+objID+"  code: "+doError.getErrorCode(),doError);
            throw doError;
            }
          }
        }
        
      } else if(txnType.equals(TXN_TYPE_DEL_ELEMENT)) {
        DOMetadata metadata = mainStorage.getObjectInfo(objID, null);
        if(elementID==null) 
          throw new DOException(DOException.REPLICATION_ERROR, 
                                "Got element-delete replication transaction with no element ID: "+
                                record);
        boolean objectExists = metadata.objectExists();
        // if the object doesn't exist, check for the special case when the object doesn't yet exist
        if(!objectExists && metadata.getDateDeleted()<timestamp) {
          // auto-create the object with the timestamp of this transaction
          mainStorage.createObject(objID, null, false);
          metadata.setDateCreated(timestamp);
          metadata.setDateDeleted(0);
          metadata.updateModification(timestamp);
          mainStorage.setObjectInfo(metadata);
          objectExists = true;
        } else if(!objectExists) {
          // the object doesn't exist locally, so ignore the deletion of one of its elements
        }
        String mdTagName = "de."+elementID;
        
        String oldTimestampStr = metadata.getTag(mdTagName, "0");
        long oldTimestamp = 0;
        try {
          oldTimestamp = Long.parseLong(oldTimestampStr);
        } catch (Exception e) {
          throw new DOException(DOException.REPLICATION_ERROR, 
                                "Invalid data element timestamp in metadata: "+
                                oldTimestampStr);
        }
        
        if(oldTimestamp>timestamp) {
          // there is already a newer version of the data element on this server
          logger.warn("Received outdated element-removed transaction during "+
                             "replication: object="+objID+"; element="+elementID+";");
          return;
        }
        
        metadata.setTag(mdTagName, String.valueOf(timestamp));
        if(objectExists) mainStorage.deleteDataElement(objID, elementID, false);
        metadata.updateModification(timestamp);
        mainStorage.setObjectInfo(metadata);
        
      } else if(txnType.equals(TXN_TYPE_COMMENT)) {
        logger.info("Received comment replication record: "+record);
      } else {
        throw new DOException(DOException.REPLICATION_ERROR,
                              "Unrecognized transaction type in record: "+record);
      }
      
      if(logTxnReceived) {
        // log this transaction in the transaction log has having been received
        mainStorage.getTransactionQueue().addTransaction(getLoggedTxn(record));
      }
      
    } // end synchronized
  }
  
  
  public static final Transaction getLoggedTxn(HeaderSet logInfo) {
    Transaction txn = new Transaction(Transaction.ACTION_LOGGED_ACTION,
                                      logInfo.getStringHeader(PARAM_OBJ_ID, null),
                                      System.currentTimeMillis(), // ,
                                      logInfo.getStringHeader(DOConstants.PARAM_ELEMENT_ID, null));
    txn.attributes = logInfo.getHeaderSubset(DOConstants.PARAM_ATTRIBUTES);
    txn.actualTime = logInfo.getLongHeader(PARAM_TIMESTAMP, 0);
    return txn;
  }
  
//  public static final byte getTxnAction(String typeStr) {
//    if(typeStr==null) return Transaction.ACTION_COMMENT;
//    if(typeStr.equals(...
//  }
  
  /** Returns the parameter value corresponding to the given transaction type */
  public static final String getTxnTypeStr(byte txnAction) {
    switch(txnAction) {
      case Transaction.ACTION_OBJ_ADD: 
        return ReplicationManager.TXN_TYPE_ADD_OBJ;
      case Transaction.ACTION_OBJ_ATTRIBUTE_UPDATE: 
        return ReplicationManager.TXN_TYPE_ATT_UPDATE;
      case Transaction.ACTION_OBJ_ATTRIBUTE_DELETE: 
        return ReplicationManager.TXN_TYPE_ATT_DELETE;
      case Transaction.ACTION_OBJ_DEL:
        return ReplicationManager.TXN_TYPE_DEL_OBJ;
      case Transaction.ACTION_OBJ_DATA_UPDATE:
        return ReplicationManager.TXN_TYPE_UPDATE_ELEMENT;
      case Transaction.ACTION_OBJ_DATA_DELETE:
        return ReplicationManager.TXN_TYPE_DEL_ELEMENT;
      case Transaction.ACTION_COMMENT:
        return ReplicationManager.TXN_TYPE_COMMENT;
      default:
        logger.error("Error: Ignoring unknown transaction type in txn log: "+((int)txnAction));
        return ReplicationManager.TXN_TYPE_COMMENT;
    }
  }
  

}
