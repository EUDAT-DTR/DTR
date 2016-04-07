/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.dobj.DOConstants;
import net.cnri.dobj.DOException;
import net.cnri.dobj.DOOperation;
import net.cnri.dobj.DOOperationContext;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.delegation.ResultWithTTL;

public abstract class AbstractDelegationOperations implements DOOperation, DOConstants {
    static final Logger logger = LoggerFactory.getLogger(AbstractDelegationOperations.class);
    
    public AbstractDelegationOperations() {
    }

    private static final String[] EMPTY_STRING_ARRAY = new String[0];
    private static final List<String> DELEGATION_OPS = Arrays.asList(CHECK_DELEGATE_OP_ID,
            LIST_DELEGATORS_OP_ID);
    private static final String[] DELEGATION_OPS_ARRAY = DELEGATION_OPS.toArray(EMPTY_STRING_ARRAY);

    public boolean canHandleOperation(DOOperationContext context) {
        String op = context.getOperationID().toLowerCase();
        return DELEGATION_OPS.contains(op);
    }

    public String[] listOperations(DOOperationContext context) {
        try {
            boolean canHandle = isDelegationHandler(context);
            if(canHandle) {
                return DELEGATION_OPS_ARRAY;
            }
            else return EMPTY_STRING_ARRAY;
        }
        catch(Exception e) {
            // err on the side of giving too many?
            return DELEGATION_OPS_ARRAY;
        }
    }

    public void performOperation(DOOperationContext context, InputStream in, OutputStream out) {
        try {
            String op = context.getOperationID().toLowerCase();
            HeaderSet params = context.getOperationHeaders();

            if(op.equals(CHECK_DELEGATE_OP_ID)) {
                String delegate = params.getStringHeader("delegate",null);
                String delegator = params.getStringHeader("delegator",null);
                if(delegate==null || delegator==null) {
                    sendErrorResponse(out,"Expecting delegate, delegator",DOException.APPLICATION_ERROR);
                    return;
                }

                ResultWithTTL<Boolean> check;
                try {
                    check = checkDelegate(context,delegate,delegator);
                }
                catch(Exception e) {
                    sendErrorResponse(out,"Error checking delegate: " + e,DOException.APPLICATION_ERROR);
                    return;
                }
                sendSuccessResponse(out);

                HeaderSet resp = new HeaderSet();
                resp.addHeader("result",(boolean)check.result);
                resp.addHeader("ttl",check.ttl);
                resp.writeHeaders(out);
            }
            else if(op.equals(LIST_DELEGATORS_OP_ID)) {
                String delegate = params.getStringHeader("delegate",null);
                if(delegate==null) {
                    sendErrorResponse(out,"Expecting delegate",DOException.APPLICATION_ERROR);
                    return;
                }

                HeaderSet resp = new HeaderSet();
                ResultWithTTL<List<String>> list;
                try {
                    list = listDelegators(context,delegate);
                }
                catch(Exception e) {
                    sendErrorResponse(out,"Error listing delegators: " + e,DOException.APPLICATION_ERROR);
                    return;
                }
                sendSuccessResponse(out);

                for(String delegator : list.result) {
                    resp.removeAllHeaders();
                    resp.addHeader("delegator",delegator);
                    resp.writeHeaders(out);
                }
                resp.removeAllHeaders();
                resp.addHeader("ttl",list.ttl);
                resp.writeHeaders(out);
            }
            else {
                sendErrorResponse(out, "Operation '"+op+"' not implemented!",
                        DOException.OPERATION_NOT_AVAILABLE);
                return;
            }
        } 
        catch (Exception e) {
            logger.error("Exception in performOperation",e);
        }
        finally {
            try { in.close(); } catch (Throwable e) {}
            try { out.close(); } catch (Throwable e) {}
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

    abstract protected boolean isDelegationHandler(DOOperationContext context) throws IOException;
    abstract protected ResultWithTTL<Boolean> checkDelegate(DOOperationContext context, String delegate, String delegator) throws IOException;
    abstract protected ResultWithTTL<List<String>> listDelegators(DOOperationContext context, String delegate) throws IOException;
}
