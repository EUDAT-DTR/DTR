/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import net.cnri.repository.RepositoryException;

/**
 * A query that matches an object where an attribute has an exact value.
 */
public class AttributeQuery implements Query {
	private String attributeName;
	protected String value;
	
    public AttributeQuery(String attributeName, String value) {
        this.attributeName = attributeName;
        this.value = value;
    }
	
    public String getAttributeName() { return attributeName; }
    public String getValue() { return value; }
    
    public <T> T accept(QueryVisitor<T> visitor) throws RepositoryException {
        return visitor.visitAttributeQuery(this);
    }
}
