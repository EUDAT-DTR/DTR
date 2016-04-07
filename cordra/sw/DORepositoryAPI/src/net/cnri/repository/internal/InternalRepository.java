/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.internal;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.cnri.apps.doserver.DOServerOperationContext;
import net.cnri.apps.doserver.Main;
import net.cnri.dobj.DOException;
import net.cnri.dobj.HeaderSet;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.networked.NetworkedRepository;

/**
 * A repository interface to the server we are running within.  For use within servlets running in a repository server.
 * Example use:
 * <pre>
 *    Repository repo = new InternalRepository(serverMain,new SimpleAuthenticatedCaller(serverMain.getServerID()));
 * </pre>   
 * will connect to the server authenticated as the server itself (and thus will be authorized to do everything).
 * 
 * @see net.cnri.repository.internal.SimpleAuthenticatedCaller
 */
public class InternalRepository extends NetworkedRepository {
    private final ExecutorService pool;
    private final boolean shutdownPoolOnClose;
    
    public InternalRepository(final Main serverMain, final Caller caller) throws RepositoryException {
        this(serverMain, caller, Executors.newFixedThreadPool(25), true);
    }
    
    public InternalRepository(final Main serverMain, final Caller caller, final ExecutorService pool) throws RepositoryException {
        this(serverMain, caller, pool, false);
    }
        
    public InternalRepository(final Main serverMain, final Caller caller, final ExecutorService pool, final boolean shutdownPoolOnClose) throws RepositoryException {
        this.pool = pool;
        this.shutdownPoolOnClose = shutdownPoolOnClose;
        try {
            repo = new net.cnri.apps.doserver.InternalRepository(serverMain, pool) {
                @Override
                protected DOServerOperationContext getOpContext(String objectId, String operationId, HeaderSet parameters) {
                    return new InternalOperationContext(serverMain,caller,objectId,operationId,parameters);
                }
            };
        }
        catch(DOException e) {
            throw new InternalException(e);
        }
    }
    
    @Override
    public void close() {
        if (shutdownPoolOnClose) {
            pool.shutdown();
            try {
                pool.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ie) {
                // Preserve interrupt status
                Thread.currentThread().interrupt();
            }
        }
    }
}
