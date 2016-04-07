/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.internal;

import java.net.InetAddress;
import java.util.List;

/**
 * A specification of the credentials of a client connecting to a repository.  Used by {@link InternalRepository}.
 * <p>
 * Mirrors part of net.cnri.dobj.DOOperationContext.
 */
public interface Caller {
    /**
     * Returns the internet address of the client.  Used at the repository end for logging.
     */
    public InetAddress getClientAddress();
    /**
     * Returns the id (handle) of the caller.
     */
    public String getCallerId();
    /**
     * Returns {@code true} if the caller is authenticated.  This method can either perform the authentication, or can always return {@code true} if the authentication is guaranteed to be performed elsewhere.
     */
    public boolean authenticateCaller();
    /**
     * Returns a list of ids (such as groups) which the caller claims as credentials.
     */
    public List<String> getCredentialIds();
    /**
     * Returns {@code true} if the caller's credential is authenticated (e.g. a group membership is confirmed).
     */
    public boolean authenticateCredential(String credentialId);
    /**
     * Retrieve arbitrary information stored for the caller across multiple operations.  Can be used internally to cache authentication information.
     */
    public Object getConnectionMapping(Object mappingKey);
    /**
     * Store arbitrary information for the caller across multiple operations.  Can be used internally to cache authentication information.
     */
    public void setConnectionMapping(Object mappingKey, Object mappingData);
}
