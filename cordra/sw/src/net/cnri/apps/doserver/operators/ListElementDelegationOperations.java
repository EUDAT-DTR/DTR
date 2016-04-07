/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.operators;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import net.cnri.dobj.DOOperationContext;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StorageProxy;
import net.cnri.dobj.delegation.ResultWithTTL;

public class ListElementDelegationOperations extends AbstractDelegationOperations {

    public static final String DELEGATES_ELEMENT_ID = "DELEGATES";
    public static final String DELEGATORS_ELEMENT_ID = "DELEGATORS";

    public static final String DELEGATES_ELEMENT_ID_PREFIX = "DELEGATES.";
    public static final String DELEGATORS_ELEMENT_ID_PREFIX = "DELEGATORS.";
    
    @Override
    protected ResultWithTTL<Boolean> checkDelegate(DOOperationContext context, String delegate, String delegator) throws IOException {
        String elementID = DELEGATES_ELEMENT_ID_PREFIX + delegator;
        StorageProxy storage = context.getStorage();
        InputStream in = null;
        BufferedReader reader = null;
        try {
            if(storage.doesDataElementExist(elementID)) {
                in = storage.getDataElement(elementID);
            }
            else if(delegator.equals(context.getTargetObjectID())) {
                elementID = DELEGATES_ELEMENT_ID;
                if(storage.doesDataElementExist(elementID)) {
                    in = storage.getDataElement(elementID);
                }
            }
            if(in==null) {
                return new ResultWithTTL<Boolean>(false,0);
//            throw new DOException(DOException.APPLICATION_ERROR,"Not a handler for this delegator");
            }

            reader = new BufferedReader(new InputStreamReader(in));
            String line;
            boolean isDelegate = false;
            while((line = reader.readLine()) != null) {
                if(line.equalsIgnoreCase(delegate)) {
                    isDelegate = true;
                    break;
                }
            }
            int ttl = storage.getElementAttributes(elementID,new HeaderSet()).getIntHeader("ttl",0);
            return new ResultWithTTL<Boolean>(isDelegate,ttl);
        }
        finally {
            if (reader != null) try { reader.close(); } catch (Exception e) {}
            if (in != null) try { in.close(); } catch (Exception e) {}
        }
    }

    @Override
    protected boolean isDelegationHandler(DOOperationContext context) throws IOException {
        StorageProxy storage = context.getStorage();
        Enumeration<String> enume = storage.listDataElements();
        
        while(enume.hasMoreElements()) {
            String elementID = enume.nextElement();
            if(elementID.startsWith(DELEGATORS_ELEMENT_ID)) return true;
        }
        return false;
    }

    @Override
    protected ResultWithTTL<List<String>> listDelegators(DOOperationContext context, String delegate) throws IOException {
        String elementID = DELEGATORS_ELEMENT_ID_PREFIX + delegate;
        StorageProxy storage = context.getStorage();
        InputStream in = null;
        BufferedReader reader = null;
        try {
            if(storage.doesDataElementExist(elementID)) {
                in = storage.getDataElement(elementID);
            }
            else if(delegate.equals(context.getTargetObjectID())) {
                elementID = DELEGATORS_ELEMENT_ID;
                if(storage.doesDataElementExist(elementID)) {
                    in = storage.getDataElement(elementID);
                }
            }
            if(in==null) {
                return new ResultWithTTL<List<String>>(Collections.<String>emptyList(),0);
//            throw new DOException(DOException.APPLICATION_ERROR,"Not a handler for this delegate");
            }

            reader = new BufferedReader(new InputStreamReader(in));
            String line;
            List<String> res = new ArrayList<String>();
            while((line = reader.readLine()) != null) {
                res.add(line);
            }
            int ttl = storage.getElementAttributes(elementID,new HeaderSet()).getIntHeader("ttl",0);
            return new ResultWithTTL<List<String>>(res,ttl);
        }
        finally {
            if (reader != null) try { reader.close(); } catch (Exception e) {}
            if (in != null) try { in.close(); } catch (Exception e) {}
        }
    }

}
