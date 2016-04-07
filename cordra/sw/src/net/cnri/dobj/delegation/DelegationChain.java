/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.dobj.delegation;

/** A chain of subgroups, whose head is the super-most group. */
public class DelegationChain {
    public final String delegator;
    public final DelegationChain subchain;
    
    public DelegationChain(String delegator, DelegationChain subchain) {
        this.delegator = delegator;
        this.subchain = subchain;
    }
}
