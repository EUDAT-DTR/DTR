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

import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 
* This class implements a digital object operator that can return a list of
* transactions, and the details for those transactions so that other servers
* can mirror the digital objects from this server.
*/
public class ReplicationOperator 
  implements DOOperation
{
  static final Logger logger = LoggerFactory.getLogger(ReplicationOperator.class);

  private ReplicationManager replicationManager;
  private Main mainServer;
  private Storage storage;
  private boolean keepRunning = false;
  
  ReplicationOperator(ReplicationManager replicationManager,
                      Main mainServer,
                      Storage storage) {
    this.replicationManager = replicationManager;
    this.mainServer = mainServer;
    this.storage = storage;
  }
  
  /** 
    * Returns true iff this object can perform the given operation on
    * behalf of the caller on the given object.  The operation, object,
    * caller, and request parameters are all provided by the given
    * DOOperationContext object.
    */
  public boolean canHandleOperation(DOOperationContext context) {
    String opID = context.getOperationID();
    return opID.equals(DOConstants.GET_REPO_TXNS_OP_ID) ||
      opID.equals(DOConstants.PUSH_REPO_TXN_OP_ID);
  }
  
  
  /**
    * Returns a list of operations that this operator can perform
   * on the object identified by the DOOperationContext parameter.
   */
  public String[] listOperations(DOOperationContext context) {
    // replication can only be done on the server object itself
    if(!context.getTargetObjectID().equals(context.getServerID()))
      return null;
    
    return new String[] {
      DOConstants.GET_REPO_TXNS_OP_ID,
      DOConstants.PUSH_REPO_TXN_OP_ID
    };
  }
  
  /** 
    * Performs the given operation (which this object has advertised that it
    * can handle) which consists of reading input (if any is expected) from the
    * given InputStream and writing the output of the operation (if any) to the
    * OutputStream.  This method should always close the input and output streams
    * when finished with them.  If there are any errors in the input, the error
    * message must be communicated on the OutputStream since all errors must be
    * at the application level.  Any exceptions thrown by this method will *not*
    * be communicated to the caller and are therefore not acceptable.
    */
  public void performOperation(DOOperationContext context,
                               InputStream in,
                               OutputStream out) {
    try {
      String opID = context.getOperationID();
      HeaderSet params = context.getOperationHeaders();
      if(opID.equals(DOConstants.GET_REPO_TXNS_OP_ID)) {
        sendSuccessResponse(out);
        
        // return a list of transactions since the last connection
        long lastTime = params.getLongHeader(ReplicationManager.PARAM_TXN_ID, 0);
        
        // Return the transactions since the last timestamp
        logger.debug("returning transactions since: {}",lastTime);
        HeaderSet txnInfo = new HeaderSet();
        CloseableEnumeration txnEnum = mainServer.getTxnQueue().getCloseableScanner(lastTime);
        try {
            while(txnEnum.hasMoreElements()) {
                Transaction txn = (Transaction)txnEnum.nextElement();
                if(logger.isDebugEnabled()) logger.debug("  sending txn with timestamp: "+txn.timestamp+
                        (txn.timestamp<=lastTime ? " !!!" : ""));
                txnInfo.removeAllHeaders();
                txnInfo.addHeader(ReplicationManager.PARAM_TXN_TYPE,
                        ReplicationManager.getTxnTypeStr(txn.action));
                txnInfo.addHeader(ReplicationManager.PARAM_OBJ_ID, txn.objectID);
                txnInfo.addHeader(ReplicationManager.PARAM_TIMESTAMP, txn.timestamp);
                txnInfo.addHeader(ReplicationManager.PARAM_ACTUAL_TIMESTAMP, txn.actualTime);

                if(txn.attributes!=null) {
                    txnInfo.addHeader(DOConstants.PARAM_ATTRIBUTES, txn.attributes);
                }          

                if(txn.dataElement!=null) {
                    txnInfo.addHeader(DOConstants.PARAM_ELEMENT_ID, txn.dataElement);
                }
                txnInfo.writeHeaders(out);
                out.flush();
            }
        } finally {
            txnEnum.close();
        }
      } else if(opID.equals(DOConstants.PUSH_REPO_TXN_OP_ID)) {
        
        // handle a transaction that is being pushed to us from another server
        String objectID = 
          params.getStringHeader(ReplicationManager.PARAM_OBJ_ID, null);
        String elementID = 
          params.getStringHeader(DOConstants.PARAM_ELEMENT_ID, null);
        String txnType = 
          params.getStringHeader(ReplicationManager.PARAM_TXN_TYPE, "");
        long timestamp = 
          params.getLongHeader(ReplicationManager.PARAM_TIMESTAMP, 0);
        long actualTime = 
          params.getLongHeader(ReplicationManager.PARAM_ACTUAL_TIMESTAMP, 0);
        
        // the 'actual' time can override the timestamp of the transaction record
        // this is for transactions that happened before they were recorded into
        // a replication transaction log
        if(actualTime!=0) timestamp = actualTime;
        
        DOMetadata metadata = storage.getObjectInfo(objectID, new DOMetadata());
        
        if(txnType.equals(ReplicationManager.TXN_TYPE_ADD_OBJ)) {
          // create the object locally...
          if(metadata.getDateCreated() < timestamp) {
            // the object has not yet been created on this machine...
            if(metadata.getDateDeleted() > timestamp) {
              // object was deleted after this creation... do nothing
            } else if(!metadata.objectExists()) { // create the object if necessary
              storage.createObject(objectID, null, true, timestamp);
            }
          }
        } else if(txnType.equals(ReplicationManager.TXN_TYPE_ATT_UPDATE)) {
          HeaderSet atts = params.getHeaderSubset(DOConstants.PARAM_ATTRIBUTES);
          storage.setAttributes(objectID, elementID, atts,
                                true, timestamp);
        } else if(txnType.equals(ReplicationManager.TXN_TYPE_ATT_DELETE)) {
          HeaderSet atts = params.getHeaderSubset(DOConstants.PARAM_ATTRIBUTES);
          ArrayList keys = new ArrayList();
          for(Iterator it=atts.iterator(); it.hasNext(); ) {
            HeaderItem item = (HeaderItem)it.next();
            keys.add(item.getName());
          }
          String keyArray[] = (String[])keys.toArray(new String[keys.size()]);
          storage.deleteAttributes(objectID, elementID, keyArray, true, timestamp);
        } else if(txnType.equals(ReplicationManager.TXN_TYPE_DEL_OBJ)) {
          // delete the local object...
          if(metadata.getDateDeleted() < timestamp) {
            storage.deleteObject(objectID, true, timestamp);
          }
        } else if(txnType.equals(ReplicationManager.TXN_TYPE_UPDATE_ELEMENT)) {
          if(elementID==null) {
            sendErrorResponse(out, "No data element specified for update element "+
                              "transaction.  params: "+params,
                              DOException.REPLICATION_ERROR);
            return;
          }
          String mdTagName = "de."+elementID;
          long oldTimestamp = 0;
          try {
            oldTimestamp = Long.parseLong(metadata.getTag(mdTagName, "0"));
          } catch (Exception e) { }

          if(oldTimestamp < timestamp) {
            sendSuccessResponse(out);
            storage.storeDataElement(objectID, elementID, in,
                                     true, false, timestamp);
          } else {
            sendErrorResponse(out, "Pushed data element '"+elementID+"' in object '"+
                              objectID+"' is obsolete due to a newer version.",
                              DOException.REPLICATION_ITEM_OUT_OF_DATE);
            return;
          }
        } else if(txnType.equals(ReplicationManager.TXN_TYPE_DEL_ELEMENT)) {
          if(elementID==null) {
            sendErrorResponse(out, "No data element specified for delete element "+
                              "transaction.  params: "+params,
                              DOException.REPLICATION_ERROR);
            return;
          }
          String mdTagName = "de."+elementID;
          long oldTimestamp = 0;
          try {
            oldTimestamp = Long.parseLong(metadata.getTag(mdTagName, "0"));
          } catch (Exception e) { }
          if(oldTimestamp < timestamp) {
            storage.deleteDataElement(objectID, elementID, true, timestamp);
          }
        } else if(txnType.equals(ReplicationManager.TXN_TYPE_COMMENT)) {
          logger.info("Replication Comment: "+params);
        } else {
          sendErrorResponse(out, "Unrecognized transaction type: "+txnType,
                            DOException.REPLICATION_ERROR);
          return;
        }
        
        sendSuccessResponse(out);
      } else {
        // unknown op ID - should never get here
      }
      
    } catch (Throwable t) {
      // TODO:  Implement better error reporting, both to the client and
      // for server logs
      logger.error("Error performing replication op",t);
      
    } finally {
      try { in.close(); } catch (Throwable t) {}
      try { out.flush(); } catch (Throwable t) {}
      try { out.close(); } catch (Throwable t) {}
    }
  }
  
  private void sendSuccessResponse(OutputStream out)
    throws IOException
  {
    HeaderSet response = new HeaderSet("response");
    response.addHeader("status", "success");
    response.writeHeaders(out);
    out.flush();
  }


  private void sendErrorResponse(OutputStream out, String msg, int code)
    throws IOException
  {
    HeaderSet response = new HeaderSet("response");
    response.addHeader("status", "error");
    if(msg!=null)
      response.addHeader("message", msg);
    if(code>=0)
      response.addHeader("code", code);
    response.writeHeaders(out);
    out.flush();
  }


}

