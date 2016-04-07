/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.servlet;

import java.util.concurrent.Executors;

import javax.servlet.http.HttpServletRequest;

import net.cnri.apps.doserver.HTTPConnectionHandler.HTTPRepositoryInterface;
import net.cnri.apps.doserver.Main;
import net.cnri.dobj.DOAuthentication;
import net.cnri.dobj.DOException;
import net.cnri.dobj.SecretKeyAuthentication;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.networked.NetworkedRepository;


/**
 * You would instantiate this within a servlet in order to access a repository running on the same server.
 * Provides the the same interface as NetworkedRepsoitory but wraps a HTTPRepositoryInterface. 
 * <br/><br/>
 * Note: currently does not support actual request-level authentication.  We recommend to use 
 * {@link net.cnri.repository.internal.InternalRepository} instead, for instance
 * <pre>
 *    Repository repo = new InternalRepository(serverMain,new SimpleAuthenticatedCaller(serverMain.getServerID()));
 * </pre>   
 * will connect to the server authenticated as the server itself (and thus will be authorized to do everything).
 * 
 * @see net.cnri.repository.internal.InternalRepository
 * @see net.cnri.repository.internal.SimpleAuthenticatedCaller
 */
@Deprecated
public class ServletRepository extends NetworkedRepository {
	public ServletRepository(Main serverMain, HttpServletRequest request, DOAuthentication auth) throws RepositoryException {
		try {
			repo = new HTTPRepositoryInterface(serverMain, Executors.newCachedThreadPool(), request, auth.getID());
		} catch (DOException e) {
			throw new InternalException(e);
		}
	}
	
	public ServletRepository(Main serverMain, HttpServletRequest request, String myID, byte[] secretKey) throws RepositoryException {
		DOAuthentication auth = new SecretKeyAuthentication(myID, secretKey);
		try {
			repo = new HTTPRepositoryInterface(serverMain, Executors.newCachedThreadPool(), request, auth.getID());
		} catch (DOException e) {
			throw new InternalException(e);
		}		
	}
}
