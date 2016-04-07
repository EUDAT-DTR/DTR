/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.internal;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * A specification of a caller claiming no credentials (other than its own id) which is considered to be already authenticated.
 *
 * @see InternalRepository
 */
public class SimpleAuthenticatedCaller implements Caller {
    private static InetAddress clientAddress = getLoopbackAddress();
    
    private static InetAddress getLoopbackAddress() {
        try {
            return InetAddress.getByName(null);
        }
        catch(UnknownHostException e) {
            return null;
        }
    }
    
    private final String callerId;

    /**
     * Constructs the instance.
     * @param callerId the id of this caller
     */
    public SimpleAuthenticatedCaller(String callerId) {
        this.callerId = callerId;
    }

    /**
     * Returns the loopback address.
     */
    @Override
    public InetAddress getClientAddress() {
        return clientAddress;
    }
    
    /**
     * Returns the id used to construct this instance.
     */
    @Override
    public String getCallerId() {
        return callerId;
    }

    /**
     * Always returns {@code true}.
     */
    @Override
    public boolean authenticateCaller() {
        return true;
    }

    /**
     * Always returns {@code null}.
     */
    @Override
    public List<String> getCredentialIds() {
        return null;
    }

    /**
     * Always returns {@code false}.
     */
    @Override
    public boolean authenticateCredential(String credentialId) {
        return false;
    }

    /**
     * Always returns {@code null}.
     */
    @Override
    public Object getConnectionMapping(Object mappingKey) {
        return null;
    }

    /**
     * Does nothing.
     */
    @Override
    public void setConnectionMapping(Object mappingKey, Object mappingData) {
    }

}
