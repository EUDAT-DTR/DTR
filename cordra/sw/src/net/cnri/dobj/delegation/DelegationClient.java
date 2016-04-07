/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj.delegation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.cnri.dobj.DOClient;
import net.cnri.dobj.DOConstants;
import net.cnri.dobj.DOException;

public class DelegationClient implements DOConstants {
    private final DOClient client;
    private final boolean defaultAlwaysOwnDelegationObject;
    private final String defaultRepository;
    private final boolean autoDiscoverDelegation;

    public DelegationClient(DOClient client) {
        this(client, true, null, true);
    }
    
    public DelegationClient(DOClient client, boolean defaultAlwaysOwnDelegationObject, String defaultRepository, boolean autoDiscoverDelegation) {
        this.client = client;
        this.defaultAlwaysOwnDelegationObject = defaultAlwaysOwnDelegationObject;
        this.defaultRepository = defaultRepository;
        this.autoDiscoverDelegation = autoDiscoverDelegation;
    }
    
    public boolean getAutoDiscoverDelegation() {
        return autoDiscoverDelegation;
    }
    
    /** List all objects which delegate to the given id via one of the initial delegators.
     * Can bypass initial delegators by passing singleton list of the delegate itself. 
     * Delegation objects and repositories are looked up in the handle system.
     * */
    public List<String> allImplicitDelegators(String delegate, List<String> initialDelegators) throws DOException {
        if (autoDiscoverDelegation && initialDelegators == null) initialDelegators = Collections.singletonList(delegate);
        return allImplicitDelegators(delegate,initialDelegators,defaultAlwaysOwnDelegationObject,defaultRepository);
    }
    
    /** List all objects which delegate to the given id via one of the initial delegators.
     * Can bypass initial delegators by passing singleton list of the delegate itself. 
     * 
     * @param alwaysOwnDelegationObject if true, each delegator is assumed to manage its own delegation; if false, a delegation object is looked up in the handle system
     * @param repository the repository each delegator lives in (looked up if null)
     * */
    public List<String> allImplicitDelegators(String delegate, List<String> initialDelegators, boolean alwaysOwnDelegationObject, String repository) throws DOException {
        if (autoDiscoverDelegation && initialDelegators == null) initialDelegators = Collections.singletonList(delegate);
        ResultWithTTL<List<DelegationChain>> allChains = DelegationUtil.allImplicitDelegators(client,initialDelegators,alwaysOwnDelegationObject,repository);
        Set<String> res = new HashSet<String>();
        for(DelegationChain chain : allChains.result) {
            res.add(chain.delegator);
        }
        res.remove(delegate);
        List<String> resList = new ArrayList<String>(res.size());
        resList.addAll(res);
        return resList;
    }
    
    /** Check whether a delegate is implicitly delegated to by a delegator via one of the initial delegators. 
     * Can bypass initial delegators by passing singleton list of the delegate itself. 
     * Delegation objects and repositories are looked up in the handle system.
     * */
    public boolean checkImplicitDelegation(String delegate, List<String> initialDelegators, String delegator) throws DOException {
        if (autoDiscoverDelegation && initialDelegators == null) initialDelegators = Collections.singletonList(delegate);
        return checkImplicitDelegation(delegate,initialDelegators,delegator,defaultAlwaysOwnDelegationObject,defaultRepository);
    }
    
    /** Check whether a delegate is implicitly delegated to by a delegator via one of the initial delegators. 
     * Can bypass initial delegators by passing singleton list of the delegate itself. 
     * 
     * @param alwaysOwnDelegationObject if true, each delegator is assumed to manage its own delegation; if false, a delegation object is looked up in the handle system
     * @param repository the repository each delegator lives in (looked up if null)
     * */
    public boolean checkImplicitDelegation(String delegate, List<String> initialDelegators, String delegator, boolean alwaysOwnDelegationObject, String repository) throws DOException {
        if (autoDiscoverDelegation && initialDelegators == null) initialDelegators = Collections.singletonList(delegate);
        ResultWithTTL<List<DelegationChain>> allChains = DelegationUtil.allImplicitDelegators(client,initialDelegators,alwaysOwnDelegationObject,repository);
        for(DelegationChain chain : allChains.result) {
            if(delegator.equalsIgnoreCase(chain.delegator)) {
                if(DelegationUtil.checkImplicitDelegation(client,delegate,chain,alwaysOwnDelegationObject,repository).result) return true;
            }
        }
        return false;
    }
}
