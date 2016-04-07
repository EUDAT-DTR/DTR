/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.txnlog;

import net.cnri.dobj.*;
import net.cnri.util.*;

import java.io.*;
import java.util.*;

/**
 * Class that holds the information known about a single transaction
 * on a handle server.  This is generally never used on the client side.
 */

public class Transaction {
  public static final byte ACTION_OBJ_ADD = 0;
  public static final byte ACTION_OBJ_DEL = 1;
  public static final byte ACTION_OBJ_DATA_UPDATE = 2;
  public static final byte ACTION_OBJ_DATA_DELETE = 3;
  public static final byte ACTION_COMMENT = 4;
  public static final byte ACTION_OBJ_ATTRIBUTE_UPDATE = 5;
  public static final byte ACTION_OBJ_ATTRIBUTE_DELETE = 6;
  public static final byte ACTION_LOGGED_ACTION = 7;
  
  public static final String TK_ACTION = "a";
  public static final String TK_OBJECTID = "oid";
  public static final String TK_TIMESTAMP = "ts";
  public static final String TK_DATAELEMENT = "de";
  public static final String TK_ACTUALTIME = "at";
  public static final String TK_ATTRIBUTES = "kv"; // DO attributes affected by the transaction
  public static final String TK_METADATA = "md"; // informational attributes about the transaction
  
  public byte action;
  public long timestamp;
  public String objectID;
  public String dataElement = null;
  public HeaderSet attributes = null;
  public long actualTime = 0;
  public HeaderSet metadata = null;
  
  public Transaction(HeaderSet txnInfo) {
    this.action = (byte)txnInfo.getIntHeader(TK_ACTION, -1);
    this.objectID = txnInfo.getStringHeader(TK_OBJECTID, "");
    this.timestamp = txnInfo.getLongHeader(TK_TIMESTAMP, 0);
    this.dataElement = txnInfo.getStringHeader(TK_DATAELEMENT, null);
    this.attributes = txnInfo.getHeaderSubset(TK_ATTRIBUTES);
    this.actualTime = txnInfo.getLongHeader(TK_ACTUALTIME, 0);
    this.metadata = txnInfo.getHeaderSubset(TK_METADATA);
  }
  
  public Transaction(byte action, String objectID, long timestamp) {
    this(action, objectID, timestamp, null);
  }
  
  public Transaction(byte action, String objectID, long timestamp,
                     String dataElement) {
    this.action = action;
    this.objectID = objectID;
    this.timestamp = timestamp;
    this.dataElement = dataElement;
  }
  
  /** Read a transaction from the given input stream */
  public static final Transaction readTxn(InputStream in) 
    throws Exception
  {
    int firstByte;
    do { // skip any leading newline characters
      firstByte = in.read();
      if(firstByte==-1) return null; // reached end of input
    } while(firstByte=='\n'||firstByte=='\r');
    
    if(firstByte=='t') {
      // this transaction has the new format
      HeaderSet hdr = new HeaderSet();
      hdr.readHeaders(in);
      hdr.setMessageType("t"+hdr.getMessageType()); // put the 't' back on
      return new Transaction(hdr);
    } else {
      // read a line of input containing the old transaction format
      StringBuffer sb = new StringBuffer();
      sb.append((char)firstByte);
      int b;
      while(true) {
        b = in.read();
        if(b==-1 || b=='\n' || b=='\r') break;
        sb.append((char)b);
      }
      String txnStr = sb.toString();
      
      try {
        int sepIdx = -1;
        long timestamp =
          Long.parseLong(txnStr.substring(sepIdx+1,
                                          sepIdx=nextField(sepIdx,txnStr)));
        byte action = 
          (byte)Integer.parseInt(txnStr.substring(sepIdx+1
                                                  , sepIdx=nextField(sepIdx,txnStr)));
        String objectID = 
          StringUtils.unbackslash(txnStr.substring(sepIdx+1,
                                                   sepIdx=nextField(sepIdx,txnStr)));
        
        Transaction txn = new Transaction(action, objectID, timestamp);
        txn.dataElement = 
          StringUtils.unbackslash(txnStr.substring(sepIdx+1, 
                                                   sepIdx=nextField(sepIdx,txnStr)));
        return txn;
      } catch (Exception e) {
        Exception e2 = new Exception("Transaction decoding error: "+e+"\n txn:"+txnStr);
        e2.initCause(e);
        throw e2;
      }
    }
  }
  
  /** Encode the given transaction and write it to the given OutputStream. 
    * This does not perform any synchronization, so synchronization should be
    * handled outside of any calls to this method.
    */
  public static final void encodeTxn(Transaction txn, OutputStream out) 
    throws IOException
  {
    // this transaction has the new format
    HeaderSet hdr = new HeaderSet("txn");
    hdr.addHeader(TK_ACTION, (int)txn.action);
    hdr.addHeader(TK_ACTUALTIME, txn.actualTime);
    hdr.addHeader(TK_OBJECTID, txn.objectID);
    hdr.addHeader(TK_TIMESTAMP, txn.timestamp);
    if(txn.dataElement!=null) hdr.addHeader(TK_DATAELEMENT, txn.dataElement);
    if(txn.attributes!=null) hdr.addHeader(TK_ATTRIBUTES, txn.attributes);
    if(txn.metadata!=null) hdr.addHeader(TK_METADATA, txn.metadata);    
    hdr.writeHeaders(out);
  }
  
  public String toString() {
    StringBuffer sb = new StringBuffer("DOTxn[");
    sb.append("txn:");
    sb.append(timestamp);
    sb.append("; action=");
    sb.append(actionToString(action));
    sb.append("; obj=");
    sb.append(objectID);
    if(dataElement!=null) {
      sb.append("; data=");
      sb.append(dataElement);
    }
    if(attributes!=null) {
      for(Iterator it=attributes.iterator(); it.hasNext(); ) {
        sb.append("; attribute:");
        sb.append(it.next());
      }
    }
    if(metadata!=null) {
        for(Iterator it=metadata.iterator(); it.hasNext(); ) {
          sb.append("; metadata:");
          sb.append(it.next());
        }
      }
    sb.append(";]");
    return sb.toString();
  }
  
  public static final String actionToString(byte action) {
    switch(action) {
      case ACTION_OBJ_ADD: return "object-add";
      case ACTION_OBJ_DEL: return "object-del";
      case ACTION_OBJ_DATA_UPDATE: return "data-update";
      case ACTION_OBJ_DATA_DELETE: return "data-del";
      case ACTION_OBJ_ATTRIBUTE_UPDATE: return "attribute-update";
      case ACTION_OBJ_ATTRIBUTE_DELETE: return "attribute-delete";
      case ACTION_LOGGED_ACTION: return "logged-action";
      case ACTION_COMMENT: return "comment";
      default: return "unknown:"+action;
    }
  }
  
  private static int nextField(int currPos, String txnStr)
    throws Exception
  {
    if(currPos>=txnStr.length())
      throw new Exception("No more fields in transaction");
    int i=currPos+1;
    for( ; i<txnStr.length(); i++) {
      if(txnStr.charAt(i)=='|') {
        break;
      }
    }
    return i;
  }
  

}



