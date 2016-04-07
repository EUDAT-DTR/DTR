/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.internal;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.util.List;

import net.cnri.apps.doserver.ConcreteStorageProxy;
import net.cnri.apps.doserver.DOServerOperationContext;
import net.cnri.apps.doserver.Main;
import net.cnri.dobj.DOClient;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.PKAuthentication;
import net.cnri.dobj.StorageProxy;

/**
 * An context for executing an operation in an {@link InternalRepository}.  The methods in this class are dictated by server code; there are some methods that may be removed in the future by both the server and this API.
 */
public class InternalOperationContext extends DOServerOperationContext {
    private final Main serverMain;
    private final String objectId;
    private final String operationId;
    private final HeaderSet parameters;
    private final Caller caller;
    private volatile StorageProxy storage;
    private final Object storageLock = new Object();

    /**
     * Constructs this context.
     * @param serverMain the server we are running in
     * @param caller information about the caller
     * @param objectId the target of the operation
     * @param operationId the operation to perform
     * @param parameters parameters of the operation
     */
    public InternalOperationContext(Main serverMain,Caller caller,String objectId,String operationId,HeaderSet parameters) {
        this.caller = caller;
        this.serverMain = serverMain;
        this.objectId = objectId;
        this.operationId = operationId;
        this.parameters = parameters==null ? new HeaderSet() : parameters;
    }
    
    @Override
    public InetAddress getClientAddress() {
        return caller.getClientAddress();
    }

    @Override
    public String getCallerID() {
        return caller.getCallerId();
    }
    
    @Override
    public boolean authenticateCaller() {
        return caller.authenticateCaller();
    }
    
    @Override
    public String[] getCredentialIDs() {
        List<String> list = caller.getCredentialIds();
        if(list==null) return null;
        return list.toArray(new String[list.size()]);
    }
    
    @Override
    public boolean authenticateCredential(String credentialId) {
        return caller.authenticateCredential(credentialId);
    }
    
    @Override
    public Object getConnectionMapping(Object mappingKey) {
        return caller.getConnectionMapping(mappingKey);
    }
    
    @Override
    public void setConnectionMapping(Object mappingKey, Object mappingData) {
        caller.setConnectionMapping(mappingKey,mappingData);
    }
    
    @Override
    public String getTargetObjectID() {
        return objectId;
    }
    
    public String getServerID() {
        return serverMain.getServerID();
    }
    
    @Override
    public String getOperationID() {
        return operationId;
    }
    
    @Override
    public HeaderSet getOperationHeaders() {
        return parameters;
    }
    
    @Override
    public StorageProxy getStorage() {
        if(storage!=null) return storage;
        synchronized(storageLock) {
            if(storage!=null) return storage;
            HeaderSet txnMetadata = new HeaderSet();
            txnMetadata.addHeader("callerid", getCallerID());
            txnMetadata.addHeader("operationid", getOperationID());
            txnMetadata.addHeader("params", getOperationHeaders());
            storage = new ConcreteStorageProxy(serverMain.getStorage(),
                    getServerID(),
                    getTargetObjectID(), txnMetadata);
            return storage;
        }        
    }
    
    // used for proxied authentication
    @Override
    public DOClient getDOClientWithForwardedAuthentication(String callerId) {
        return new DOClient(PKAuthentication.getAnonymousAuth());
    }
    
    // used for federated search
    @Override
    public void performOperation(String serverId, String newObjectId, String newOperationId, HeaderSet params, InputStream input, OutputStream output) throws DOException {
        try {
            serverMain.performOperation(new InternalOperationContext(serverMain,caller,newObjectId,newOperationId,params),input,output);
        }
        catch(DOException e) {
            throw e;
        }
        catch(Exception e) {
            throw new DOException(DOException.SERVER_ERROR,"Exception performing derived operation",e);
        }
    }
}
