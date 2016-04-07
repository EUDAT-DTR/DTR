/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;
import net.cnri.util.ThreadSafeDateFormat;

import java.io.*;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines an object that provides access to the audit log mechanism
 * on this DO server
 */
public class AuditHookImpl
  implements DOOperation
{
  static final Logger logger = LoggerFactory.getLogger(AuditHookImpl.class);
    
  public static final String TXN_ID_PARAM = "txnid";
  public static final String TXN_PART_PARAM = "txnpart";
  public static final String TXN_PART_INPUT = "in";
  public static final String TXN_PART_OUTPUT = "out";
  public static final String TXN_PART_HEADERS = "info";
  public static final String TXN_PART_DATETIME = "datetime";
  
  public static final String OP_ID_PARAM = "opid";
  public static final String OBJECT_ID_PARAM = "objectid";
  public static final String START_DATE_PARAM = "starttime";
  public static final String END_DATE_PARAM = "endtime";
  public static final String CALLER_ID_PARAM = "callerid";

  private AuditLog auditLog;
  private ThreadSafeDateFormat dateFormat;
  
  AuditHookImpl(AuditLog auditLog) {
    this.auditLog = auditLog;
    this.dateFormat = new ThreadSafeDateFormat(DOConstants.DATE_FORMAT_MDYHMS);
  }

  /**
   * Returns true iff this object can perform the given operation on
   * behalf of the caller on the given object.  The operation, object,
   * caller, and request parameters are all provided by the given
   * DOOperationContext object.
   */
  public boolean canHandleOperation(DOOperationContext context) {
    if(!context.getTargetObjectID().equals(context.getServerID()))
      return false;
    
    String opID = context.getOperationID();
    if(opID.equals(DOConstants.AUDIT_QUERY_OP_ID))
      return true;
    else if(opID.equals(DOConstants.AUDIT_GET_OP_ID))
      return true;
    else
      return false;
  }

  /**
   * Returns a list of operations that this operator can perform
   * on the object identified by the DOOperationContext parameter.
   */
  public String[] listOperations(DOOperationContext context) {
    if(context.getTargetObjectID().equals(context.getServerID()))
      return new String[] { DOConstants.AUDIT_QUERY_OP_ID, DOConstants.AUDIT_GET_OP_ID };
    return null;
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
      if(!context.authenticateCaller()) {
        try {
          HeaderSet response = new HeaderSet("response");
          response.addHeader("status", "error");
          response.addHeader("message", "Invalid authentication as '"+context.getCallerID()+"'");
          response.writeHeaders(out);
          out.flush();
        } catch (Exception e) {}
        return;
      }
      
      String opID = context.getOperationID();
      if(opID.equals(DOConstants.AUDIT_QUERY_OP_ID)) {
        // perform a query on the audit catalog
        HeaderSet params = context.getOperationHeaders();
        String beginDateStr = params.getStringHeader(START_DATE_PARAM, null);
        String endDateStr = params.getStringHeader(END_DATE_PARAM, null);
        String txnObjID = params.getStringHeader(OBJECT_ID_PARAM, null);
        String txnCallerID = params.getStringHeader(CALLER_ID_PARAM, null);
        String txnOpID = params.getStringHeader(OP_ID_PARAM, null);
        long beginDate = 0;
        long endDate = 0;
        try {
          if(beginDateStr!=null)
            beginDate = Long.parseLong(beginDateStr.trim());
        } catch (Exception e) {}
        try {
          if(endDateStr!=null)
            endDate = Long.parseLong(endDateStr.trim());
        } catch (Exception e) {}
        
        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "success");
        response.writeHeaders(out);
        out.flush();
        
        OutputStreamWriter outWriter = new OutputStreamWriter(out, "UTF8");
        auditLog.listTransactionsIDs(txnObjID, txnOpID, txnCallerID, beginDate, endDate, outWriter);
        outWriter.flush();
        outWriter.close();
      } else if(opID.equals(DOConstants.AUDIT_GET_OP_ID)) {
        // retrieve an audit log for a specified transaction ID
        HeaderSet params = context.getOperationHeaders();
        
        String txnID = params.getStringHeader(TXN_ID_PARAM, null);
        String txnPart = params.getStringHeader(TXN_PART_PARAM, null);
        if(txnID==null) {
          HeaderSet response = new HeaderSet("response");
          response.addHeader("status", "error");
          response.addHeader("message", "Request was missing transaction ID parameter");
          response.writeHeaders(out);
        } else if(txnPart==null) {
          HeaderSet response = new HeaderSet("response");
          response.addHeader("status", "error");
          response.addHeader("message", "Request was missing transaction part parameter");
          response.writeHeaders(out);
        } else if(txnPart.equalsIgnoreCase(TXN_PART_OUTPUT)) {
          InputStream txnIn = auditLog.getTransactionOutput(txnID);
          if(txnIn==null) {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "error");
            response.addHeader("message", "No transaction with ID '"+txnID+"' was found");
            response.writeHeaders(out);
          } else {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "success");
            response.writeHeaders(out);
            byte buf[] = new byte[2048];
            int r;
            while((r=txnIn.read(buf))>=0) out.write(buf, 0, r);
          }
        } else if(txnPart.equalsIgnoreCase(TXN_PART_INPUT)) {
          InputStream txnIn = auditLog.getTransactionInput(txnID);
          if(txnIn==null) {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "error");
            response.addHeader("message", "No transaction with ID '"+txnID+"' was found");
            response.writeHeaders(out);
          } else {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "success");
            response.writeHeaders(out);
            byte buf[] = new byte[2048];
            int r;
            while((r=txnIn.read(buf))>=0) out.write(buf, 0, r);
          }
        } else if(txnPart.equalsIgnoreCase(TXN_PART_HEADERS)) {
          HeaderSet headers = auditLog.getTransactionHeaders(txnID);
          if(headers==null) {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "error");
            response.addHeader("message", "No transaction with ID '"+txnID+"' was found");
            response.writeHeaders(out);
          } else {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "success");
            response.writeHeaders(out);
            headers.writeHeaders(out);
          }
        } else if(txnPart.equalsIgnoreCase(TXN_PART_DATETIME)) {
          long txnDateTime = auditLog.getTransactionTime(txnID);
          if(txnDateTime==0) {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "error");
            response.addHeader("message", "No transaction with ID '"+txnID+"' was found");
            response.writeHeaders(out);
          } else {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "success");
            response.writeHeaders(out);
            String dateStr = dateFormat.format(new Date(txnDateTime));
            out.write(DOConnection.encodeUTF8(dateStr));
          }
        } else {
          HeaderSet response = new HeaderSet("response");
          response.addHeader("status", "error");
          response.addHeader("message", "Unknown transaction part: '"+txnPart+"'");
          response.writeHeaders(out);
        }
      } else {
        // unknown op ID - should never get here
      }
    } catch (Exception e) {
      // TODO:  Improve detail in error message
      logger.error("Error performing audit log query",e);
    } finally {
      try { in.close(); } catch (Throwable t) {}
      try { out.flush(); } catch (Throwable t) {}
      try { out.close(); } catch (Throwable t) {}
    }
  }
  

}
