/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj.delegation;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import net.cnri.dobj.DOClient;
import net.cnri.dobj.DOConstants;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.StreamPair;
import net.handle.hdllib.AbstractMessage;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.ErrorResponse;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.ResolutionRequest;
import net.handle.hdllib.ResolutionResponse;
import net.handle.hdllib.Util;

public class DelegationUtil implements DOConstants {
    private DelegationUtil() {}
    
    private static final String[] DELEGATION_OBJECT_QUERY = { DOConstants.OBJECT_SERVER_HDL_TYPE, DOConstants.RIGHTS_DELEGATION_OBJECT_HDL_TYPE, "HS_ALIAS" };

    private static HandleValue[] resolveHandle(String sHandle,String[] sTypes) throws HandleException {
        HandleResolver resolver = DOClient.getResolver().getResolver();
        if(sTypes==null) sTypes = new String[0];

        // convert the types and handle to UTF8 byte-strings
        byte types[][] = new byte[sTypes.length][];
        byte handle[];

        handle = Util.encodeString(sHandle);
        for(int i=0; i<sTypes.length; i++)
            types[i] = Util.encodeString(sTypes[i]);

        AbstractResponse response = resolver.processRequest(new ResolutionRequest(handle,types,null,null));

        if(response.responseCode==AbstractMessage.RC_HANDLE_NOT_FOUND) {
            return null;
        } else if (response.responseCode==AbstractMessage.RC_VALUES_NOT_FOUND) {
            return new HandleValue[0];
        } else if (response instanceof ErrorResponse){
            String msg = Util.decodeString( ((ErrorResponse)response).message );

            throw new HandleException(HandleException.INTERNAL_ERROR,
                    AbstractMessage.getResponseCodeMessage(response.responseCode)+": "+msg);
        }

        return ((ResolutionResponse)response).getHandleValues();
    }
    
    /** Returns the id of the DO which manages delegation for the given id. */
    public static String delegationObject(String id) throws DOException {
        HandleValue values[] = null;
        String aliasedID = id;
        while(values==null) {
            try {
                values = resolveHandle(aliasedID, DELEGATION_OBJECT_QUERY);
                if(values==null) {
                    throw new DOException(DOException.NO_SUCH_OBJECT_ERROR, id);
                } else if(values.length<=0) {
                    return aliasedID;
                }
                boolean wasAliased = false;
                for(int i=0; i<values.length; i++) {
                    if(values[i].getTypeAsString().equalsIgnoreCase("HS_ALIAS")) {
                        aliasedID = values[i].getDataAsString();
                        wasAliased = true;
                        break;
                    }
                }
                if(wasAliased) { // continue within this loop
                    values = null;
                }
            } catch (HandleException e) {
                DOException e2 = new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR, String.valueOf(e));
                e2.initCause(e);
                throw e2;
            }
        }
        
        for(int i=0; i<values.length; i++) {
            if(values[i].getTypeAsString().equalsIgnoreCase(DOConstants.RIGHTS_DELEGATION_OBJECT_HDL_TYPE)) {
                return values[i].getDataAsString();
            }
        }
        for(int i=0; i<values.length; i++) {
            if(values[i].getTypeAsString().equalsIgnoreCase(DOConstants.OBJECT_SERVER_HDL_TYPE)) {
                return id;
            }
        }
        throw new DOException(DOException.UNABLE_TO_LOCATE_OBJECT_ERROR,
                "Unable to locate the delegation object for "+id);
    }
    
    /** Returns whether a delegation (e.g. group to member) is authorized.
     * 
     * @param client the client to perform operations
     * @param delegate the delegate (e.g. member or subgroup)
     * @param delegator the delegator (e.g. group or supergroup) to check
     * @param delegationObject the DO to ask (looked up in HS if null)
     * @param repository the repository it lives in (looked up if null)
     */
    public static ResultWithTTL<Boolean> checkDelegation(DOClient client, String delegate, String delegator, String delegationObject, String repository) throws DOException {
        // degenerate case: same entity
        if(delegate.equalsIgnoreCase(delegator)) return new ResultWithTTL<Boolean>(true,Integer.MAX_VALUE);
        
        if(delegationObject==null) delegationObject = delegationObject(delegator);
        
        HeaderSet parameters = new HeaderSet();
        parameters.addHeader("delegator",delegator);
        parameters.addHeader("delegate",delegate);
        
        StreamPair pair = null;
        try {
            pair = client.performOperation(repository,delegationObject,CHECK_DELEGATE_OP_ID,parameters);
            pair.getOutputStream().close();
            InputStream in = pair.getInputStream();
            HeaderSet result = new HeaderSet();
            if(result.readHeaders(in)) {
                return new ResultWithTTL<Boolean>(result.getBooleanHeader("result",false), result.getIntHeader("ttl",0));
            }
            else {
                return new ResultWithTTL<Boolean>(false,0);
            }
        }
        catch(DOException e) {
            if (e.getErrorCode() == DOException.NO_SUCH_OBJECT_ERROR) {
                return new ResultWithTTL<Boolean>(false,0);
            }
            throw e;
        }
        catch(IOException e) {
            throw new DOException(DOException.INTERNAL_ERROR, "Exception checking delegation", e);
        }
        finally {
            try { pair.close(); } catch (Throwable e) {}
        }
    }
    
    /** List the entities delegating to the given entity, for example the groups of a member, or the supergroups of a group.
     * 
     * @param client the client to perform operations
     * @param delegate the delegate (e.g. member or subgroup)
     * @param delegationObj the DO to ask (looked up in HS if null)
     * @param repository the repository it lives in (looked up if null)
     */
    public static ResultWithTTL<List<String>> listDelegators(DOClient client, String delegate, String delegationObj, String repository) throws DOException {
        if(delegationObj==null) delegationObj = delegationObject(delegate);
        
        HeaderSet parameters = new HeaderSet();
        parameters.addHeader("delegate", delegate);
        
        StreamPair pair = null;
        try {
            pair = client.performOperation(repository, delegationObj, LIST_DELEGATORS_OP_ID, parameters);
            pair.getOutputStream().close();
            InputStream in = pair.getInputStream();
            HeaderSet result = new HeaderSet();
            List<String> delegators = new ArrayList<String>();
            Integer ttl = null;
            while(result.readHeaders(in)) {
                String delegator = result.getStringHeader("delegator",null);
                if(delegator!=null) delegators.add(delegator);
                if(result.hasHeader("ttl")) {
                    ttl = result.getIntHeader("ttl",0);
                    break;
                }
            }
            return new ResultWithTTL<List<String>>(delegators,ttl==null ? 0 : ttl);
        }
        catch(DOException e) {
            if (e.getErrorCode() == DOException.NO_SUCH_OBJECT_ERROR) {
                return new ResultWithTTL<List<String>>(Collections.<String>emptyList(),0);
            }
            throw e;
        }
        catch(IOException e) {
            throw new DOException(DOException.INTERNAL_ERROR, "Exception listing delegators", e);
        }
        finally {
            try { pair.close(); } catch (Throwable e) {}
        }
    }
    
    /** Returns all delegation chains of the given entity starting with the given initial delegators.  
     * Delegation objects and repositories are looked up in the handle system.
     * 
     * @param client the DO client to perform operations
     * @param initialDelegators starting points
     */
    public static ResultWithTTL<List<DelegationChain>> allImplicitDelegators(DOClient client, List<String> initialDelegators) throws DOException {
        return allImplicitDelegators(client,initialDelegators,false,null);
    }
        
    /** Returns all delegation chains of the given entity starting with the given initial delegators.
     * 
     * @param client the DO client to perform operations
     * @param initialDelegators starting points
     * @param alwaysOwnDelegationObject if true, each delegator is assumed to manage its own delegation; if false, a delegation object is looked up in the handle system
     * @param repository the repository each delegator lives in (looked up if null)
     */
    public static ResultWithTTL<List<DelegationChain>> allImplicitDelegators(DOClient client, List<String> initialDelegators, boolean alwaysOwnDelegationObject, String repository) throws DOException {
        Integer ttl = null;
        
        if(initialDelegators==null) return new ResultWithTTL<List<DelegationChain>>(Collections.<DelegationChain>emptyList(),Integer.MAX_VALUE); 
     
        List<DelegationChain> delegationChains = new ArrayList<DelegationChain>();
        Queue<DelegationChain> delegationChainsToProcess = new LinkedList<DelegationChain>();
        for(String delegator : initialDelegators) {
            delegationChainsToProcess.add(new DelegationChain(delegator,null));
        }
        DelegationChain chain;
        while((chain = delegationChainsToProcess.poll()) != null) {
            delegationChains.add(chain);
            
            ResultWithTTL<List<String>> delegators = listDelegators(client,chain.delegator,alwaysOwnDelegationObject ? chain.delegator : null,repository);
            if(ttl==null || delegators.ttl<ttl) ttl = delegators.ttl;
            for(String delegator : delegators.result) {
                delegationChainsToProcess.add(new DelegationChain(delegator,chain));
            }
        }
        
        return new ResultWithTTL<List<DelegationChain>>(delegationChains, ttl==null ? 0 : ttl);
    }
    
    /** Returns whether an id is an implicit delegate of a delegator via a specified delegation chain. 
     * Delegation objects and repositories are looked up in the handle system.
     * */
    public static ResultWithTTL<Boolean> checkImplicitDelegation(DOClient client, String delegate, DelegationChain chain) throws DOException {
        return checkImplicitDelegation(client,delegate,chain,false,null);
    }
    
    /** Returns whether an id is an implicit delegate of a delegator via a specified delegation chain. 
     * 
     * @param client the DO client to perform operations
     * @param delegate the id to be checked
     * @param chain the delegation chain to be checked
     * @param alwaysOwnDelegationObject if true, each delegator is assumed to manage its own delegation; if false, a delegation object is looked up in the handle system
     * @param repository the repository each delegator lives in (looked up if null)
     * */
    public static ResultWithTTL<Boolean> checkImplicitDelegation(DOClient client, String delegate, DelegationChain chain, boolean alwaysOwnDelegationObject, String repository) throws DOException {
        Integer ttl = null;
        String delegator = chain.delegator;
        DelegationChain subchain = chain;
        while((subchain = subchain.subchain) != null) {
            String subdelegator = subchain.delegator;
            ResultWithTTL<Boolean> check = checkDelegation(client,subdelegator,delegator,alwaysOwnDelegationObject ? delegator : null,repository);
            if(!check.result) return check;
            if(ttl==null || check.ttl < ttl) ttl = check.ttl;
            
            delegator = subdelegator;
        }
        
        ResultWithTTL<Boolean> check = checkDelegation(client,delegate,delegator,alwaysOwnDelegationObject ? delegator : null,repository);
        if(!check.result) return check;
        if(ttl==null || check.ttl < ttl) ttl = check.ttl;
        return new ResultWithTTL<Boolean>(true,ttl==null ? 0 : ttl);
    }
}
