/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj.delegation;

/** A result together with a TTL in seconds. */
public class ResultWithTTL<T> {
    final public T result;
    final public int ttl;
    
    public ResultWithTTL(T result, int ttl) {
        this.result = result;
        this.ttl = ttl;
    }
}
