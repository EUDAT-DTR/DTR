/**********************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
\**********************************************************************/

package net.cnri.apps.dogui.controller;

import net.cnri.apps.dogui.view.AdminToolUI;
import net.cnri.apps.doserver.ReplicationManager;
import net.cnri.apps.doserver.txnlog.Transaction;
import net.cnri.do_api.Repository;
import net.cnri.dobj.*;
import net.cnri.util.IOForwarder;
import net.cnri.util.StreamTable;

import java.io.IOException;
import java.io.InputStream;


public class DNAPusher
  extends DNASynchronizer
{
  private static final String STATUS_PREF_KEY = "push_status";
  private static final String LAST_TXN_STATKEY = "last_txn";
  public static final String PARAM_TXN_ID = "txn_id";
  public static final String PARAM_OBJ_ID = "object_id";
  public static final String PARAM_TXN_TYPE = "txn_type";
  public static final String PARAM_TIMESTAMP = "tstamp";
  public static final String PARAM_ACTUAL_TIMESTAMP = "atstamp";
  public static final String PARAM_ELEMENT_ID = "elementid";
  
  private AdminToolUI ui;
  private boolean deleteFromSource = false;
  private StreamTable syncStatus = new StreamTable();
  
  /** Copy all digital objects from the given source into the given destination
    * repository */
  public DNAPusher(AdminToolUI ui) {
    this.ui = ui;
  }
  
  /** Perform synchronization.  This is called from a separate thread.  If this
    * is a long-running method then it should occasionally call getState() to see
    * if it should keep running. */
  void performSynchronizationTask() {
    try {
      DOAuthentication auth = ui.getAuthentication(false);
      if(auth==null) {
        updateStatus(getProgress(),
                     "Waiting for user to authenticate");
        return;
      }
      
      Repository source = null;
      try {
        source = ui.getRepository(AdminToolUI.OUTBOX);
      } catch (Exception e) {
        updateStatus(getProgress(),
                     "Local repository not reachable.  Waiting");
        return;
      }
      if(source==null) {
        updateStatus(getProgress(),
                     "Local repository not specified.  Waiting");
        return;
      }
      
      String statusKey = STATUS_PREF_KEY+":"+source.getID();
      syncStatus = (StreamTable)ui.getMain().prefs().get(statusKey);
      if(syncStatus==null) {
        syncStatus = new StreamTable();
        ui.getMain().prefs().put(statusKey, syncStatus);
      }
      
      Repository destination = null;
      try {
        String netRepoID = ui.getUserObject().getAttribute(AdminToolUI.HOME_REPO_ATT, null);
        if(netRepoID==null || netRepoID.trim().length()<=0) {
          updateStatus(getProgress(),
                       "Home repository not set");
          return;
        }
        destination = ui.getRepositoryByID(netRepoID);
      } catch (Exception e) {
        updateStatus(getProgress(),
                     "Shared repository not reachable.  Waiting");
        return;
      }
      if(destination==null) {
        updateStatus(getProgress(),
                     "Destination repository not specified.  Waiting");
        return;
      }
      
      DOServiceInfo srcInfo = source.getConnection().getServiceInfo();
      String srcSvcID = srcInfo.getServiceID();
      
      StreamTable srcStatus;
      synchronized(syncStatus) {
        String srcKey = "source:"+srcSvcID;
        srcStatus = (StreamTable)syncStatus.get(srcKey);
        if(srcStatus==null) {
          syncStatus.put(srcKey, srcStatus = new StreamTable());
        }
      }
      
      boolean sawErrors = false;
      for(int i=0; i<srcInfo.getServerCount(); i++) {
        StreamPair io = null;
        DOClientConnection conn = null;
        try {
          // open a connection to the source server
          conn = new DOClientConnection(auth);
          conn.connect(srcInfo.getServer(i));
          
          // build and submit the request for transactions since the last update
          String svrStatKey = "server:"+i+":"+LAST_TXN_STATKEY;
          HeaderSet params = new HeaderSet();
          if(srcStatus.containsKey(svrStatKey)) {
            params.addHeader(PARAM_TXN_ID, srcStatus.getStr(svrStatKey, ""));
          }
          
          io = conn.performOperation(srcSvcID,
                                     DOConstants.GET_REPO_TXNS_OP_ID,
                                     params);
          
          HeaderSet txnInfo = new HeaderSet();
          InputStream in = io.getInputStream();
          
          while(txnInfo.readHeaders(in)) {
            String timestamp = txnInfo.getStringHeader(PARAM_TIMESTAMP, null);
            if(timestamp==null) {
              System.err.println("Warning:  ignoring received transaction "+
                                 "with no timestamp: "+txnInfo);
              continue;
            }
            
            processReplicationRecord(conn, srcSvcID, destination, txnInfo);
            srcStatus.put(svrStatKey, timestamp);
          }
        } catch (Exception e) {
          sawErrors = true;
          System.err.println("Error synchronizing with server "+i+" of service "+srcSvcID+": "+e);
          e.printStackTrace(System.err);
          updateStatus(-1, "Error synchronizing with "+srcSvcID+": "+e);
        } finally {
          try { conn.close(); } catch (Throwable t) {}
          ui.getMain().savePreferences();
        }
        
      } // end for server in srcInfo

      if(!sawErrors) {
        updateStatus(-1, "Objects pushed out as of "+(new java.util.Date()));
        //changeState(DNASynchronizer.STATE_STOPPED, -1, "Finished publishing objects");
      }
      
      // sleep for three minutes before restarting the process
      Thread.sleep(3 * 60 * 1000);
      
    } catch (Throwable t) {
      System.err.println("Error performing synchronization: "+t);
      t.printStackTrace(System.err);
    } finally {
    }
  }
  
  
  /** Process the given replication record by pushing the transaction to the
    * given server.  */
  private void processReplicationRecord(DOClientConnection srcConn,
                                        String srcRepoID,
                                        Repository dest, 
                                        HeaderSet txnInfo) 
    throws DOException, IOException
  {
    StreamPair srcIO = null;
    StreamPair io = null;
    
    try {
      //System.err.println(">>>pushing transaction: "+txnInfo+" from "+srcRepoID+" to "+dest.getID());
      String objectID = txnInfo.getStringHeader(PARAM_OBJ_ID, null);
      if(objectID.equals(srcRepoID)) return; // don't replicate the repo object itself
      
      String txnType = txnInfo.getStringHeader(PARAM_TXN_TYPE, "");
      if(txnType.equals("comment")) return; // information comment only
      
      //String txnID = txnInfo.getStringHeader(PARAM_TIMESTAMP, null);
      String txnElement = txnInfo.getStringHeader(PARAM_ELEMENT_ID, null);
      InputStream dataIn = null;
      
      if(txnType.equals(ReplicationManager.getTxnTypeStr(Transaction.ACTION_OBJ_DATA_UPDATE))) {
        HeaderSet getParams = new HeaderSet();
        getParams.addHeader("elementid", txnElement);
        srcIO = srcConn.performOperation(objectID,
                                         DOConstants.GET_DATA_OP_ID,
                                         getParams);
        try { srcIO.getOutputStream().close(); } catch (Exception e) {}
        dataIn = srcIO.getInputStream();
      }
      
      boolean writeData = dataIn!=null;
      try {
        System.err.println("Pushing txn: "+txnInfo);
        io = dest.performOperation(DOConstants.PUSH_REPO_TXN_OP_ID,
                                                   txnInfo);
      } catch (DOException e) {
        if(e.getErrorCode()==DOException.REPLICATION_ITEM_OUT_OF_DATE) {
          //System.err.println("transaction "+txnInfo+" being ignored - out of date");
          writeData = false;
          return;
        } else {
          System.err.println("Error pushing replication operation: "+e);
          throw e;
        }
      }
      if(writeData) {
        IOForwarder.forwardStream(dataIn, io.getOutputStream());
      }
      
      try { srcIO.close(); } catch (Exception e) {}
      
      // close any output
      if(io!=null) io.getOutputStream().close();
      
      // read any response...
      HeaderSet response = new HeaderSet();
      response.readHeaders(io.getInputStream());
    } finally {
      if(srcIO!=null) try { srcIO.close(); } catch (Throwable t) {}
      if(io!=null) try { io.close(); } catch (Throwable t) {}
    }
  }
}
