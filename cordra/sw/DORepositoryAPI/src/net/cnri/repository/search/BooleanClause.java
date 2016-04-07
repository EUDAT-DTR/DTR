/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

/**
 * A Boolean clause in a {@link BooleanQuery}.  Modeled on the Lucene style of Boolean query.  Comprises a subquery and an occurence marker, which is either {@code MUST}, {@code SHOULD}, or {@code MUST_NOT}.
 * <p>
 * A BooleanQuery will match an object if and only if:
 * <ul>
 * <li> it matches every {@code MUST} subquery;
 * <li> it matches at least one {@code SHOULD} subquery (or there is no such subquery); and
 * <li> it matches no {@code MUST_NOT} subquery.
 * </ul>
 */
public class BooleanClause {
	private final Query query;
	private final BooleanClause.Occur occur;
	
	/** 
	 * Constructs an instance.
	 * @param query the subquery
	 * @param occur the occurence marker
	 */
	public BooleanClause(Query query, BooleanClause.Occur occur) {
		this.query = query;
		this.occur = occur;
	}
	
	/**
	 * @return the subquery
	 */
    public Query getQuery() {
        return query;
    }

    /**
     * @return the occurence marker
     */
    public BooleanClause.Occur getOccur() {
        return occur;
    }
	
    /**
     * Enumerated type of occurence markers.
     */
	public enum Occur {
	    /**
	     * Marker for subqueries which must occur for the containing query to match.
	     */
		MUST, 
		/**
		 * Marker for subqueries which must not occur for the containing query to match.
		 */
		MUST_NOT,
		/**
		 * Marker for subqueries at least one of which must occur (if there are any) for the containing query to match.
		 */
		SHOULD 
	}
	
	public String toString() {
		return occur + " " + query.toString();
	}
}
