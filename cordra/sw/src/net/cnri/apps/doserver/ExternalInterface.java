/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver;

import net.cnri.dobj.*;

import java.io.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class defines an object that provides access to internal
 * server operations.
 */
public class ExternalInterface
  implements DOOperation
{
  static final Logger logger = LoggerFactory.getLogger(ExternalInterface.class);
  private static final String KB_LABEL_PARAM = "knowbotid";
  
  private Main server;
  
  ExternalInterface(Main server) {
    this.server = server;
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
    if(opID.equals(DOConstants.INJECT_KNOWBOT_OP_ID)) {
      return true;
    }
    return false;
  }

  /**
   * Returns a list of operations that this operator can perform
   * on the object identified by the DOOperationContext parameter.
   */
  public String[] listOperations(DOOperationContext context) {
    if(context.getTargetObjectID().equals(context.getServerID()))
      return new String[] { DOConstants.INJECT_KNOWBOT_OP_ID };
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
        returnError(out, "Invalid authentication as '"+context.getCallerID()+"'");
        return;
      }

      String opID = context.getOperationID();
      if(opID.equals(DOConstants.INJECT_KNOWBOT_OP_ID)) {
        // perform a query on the audit catalog
        HeaderSet params = context.getOperationHeaders();
        String knowbotIDStr = params.getStringHeader(KB_LABEL_PARAM, null);
        if(knowbotIDStr==null) {
          returnError(out, "Required parameter \""+KB_LABEL_PARAM+"\" was missing.");
          return;
        }

        // return a preliminary success message
        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "success");
        response.writeHeaders(out);
        out.flush();
        
        try {
          server.getConfiguration().addKnowbot(knowbotIDStr, in);
        } catch (Exception e) {
          returnError(out, "Error submitting agent: "+e);
          return;
        }
        
        // return headers indicating that the request was successfully processed
        try {
          response = new HeaderSet("results");
          response.addHeader("status", "success");
          response.writeHeaders(out);
          out.close();
        } catch (Exception e) {} // don't bother reporting a failure to send the status
      } else {
        // unknown op ID - should never get here
      }
    } catch (Exception e) {
      // TODO:  Implement better error reporting, both to the client and
      // for server logs
      logger.error("Error performing knowbot injection",e);
      
      try {
        out.write(("\n\nError: "+e).getBytes("UTF8"));
      } catch (Exception e2) {}
    } finally {
      try { in.close(); } catch (Throwable t) {}
      try { out.close(); } catch (Throwable t) {}
    }
  }

  
  private void returnError(OutputStream out, String message) {
    try {
      HeaderSet response = new HeaderSet("response");
      response.addHeader("status", "error");
      response.addHeader("message", message);
      response.writeHeaders(out);
      out.flush();
    } catch (Throwable t) {
      try {
        logger.error("Error sending error response",t);
      } catch (Throwable t2) {}
    }
  }
  

}
