/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import net.cnri.repository.RepositoryException;

/**
 * A query reflecting a boolean expression.  Modeled on the Lucene style of Boolean query.  Comprises a list of subqueries with occurence marker, which are either {@code MUST}, {@code SHOULD}, or {@code MUST_NOT}.
 * <p>
 * A BooleanQuery will match an object if and only if:
 * <ul>
 * <li> it matches every {@code MUST} subquery; 
 * <li> it matches at least one {@code SHOULD} subquery (or there is no such subquery); and
 * <li> it matches no {@code MUST_NOT} subquery.
 * </ul>
 * "AND" expressions map to {@code MUST}; "OR" to {@code SHOULD}; "NOT" to {@code MUST_NOT}.
 * 
 * @see BooleanClause
 * @see BooleanClause.Occur
 */
public class BooleanQuery implements Query {
	
	private final List<BooleanClause> clauses;
	
	/**
     * Constructs an instance.
	 * @param clauses the clauses of the instance
	 */
	public BooleanQuery(Collection<BooleanClause> clauses) {
		this.clauses = Collections.unmodifiableList(new ArrayList<BooleanClause>(clauses));
	}
	
	/** 
	 * Constructs an instance from parameters which are each either BooleanClause, Query, or BooleanClause.Occur.
     * <p>
     * Example: {@code new BooleanQuery(q1,MUST,q2,SHOULD,q3,SHOULD,q4,MUST_NOT)} models {@code q1 /\ (q2 \/ q3) /\ ~q4}.
	 * 
	 * @param objects the objects used to construct the instance.  Each object must be a BooleanClause, a Query, or a BooleanClause.Occur.  Each Query will be converted into a BooleanClause 
	 * with its Occur set to the next BooleanClause.Occur in the arguments.  All the BooleanClauses become the clauses of the constructed instance.
	 */
	public BooleanQuery(Object... objects) {
	    List<BooleanClause> argClauses = new ArrayList<BooleanClause>();
	    for(int i = 0; i < objects.length; i++) {
	        if(objects[i] instanceof BooleanClause) {
	            argClauses.add((BooleanClause)objects[i]);
	        }
	        else if(objects[i] instanceof Query) {
	            BooleanClause.Occur occur = BooleanClause.Occur.MUST;
	            for(int j = i+1; j < objects.length; j++) {
	                if(objects[j] instanceof BooleanClause.Occur) {
	                    occur = (BooleanClause.Occur)objects[j];
	                    break;
	                }
	            }
	            argClauses.add(new BooleanClause((Query)objects[i],occur));
	        }
	        else if(objects[i] instanceof BooleanClause.Occur) {
	            continue;
	        }
	        else throw new ClassCastException();
	    }
	    this.clauses = Collections.unmodifiableList(argClauses);
	}
	
	/**
	 * @return the clauses of this instance
	 */
	public List<BooleanClause> clauses() {
		return clauses;
	}
	
	public boolean onlyContainsMustNotClauses() {
	    for(BooleanClause clause : clauses) {
	        if(clause.getOccur()!=BooleanClause.Occur.MUST_NOT) return false;
	    }
	    return true;
	}

	public boolean containsNoMustClauses() {
        for(BooleanClause clause : clauses) {
            if(clause.getOccur()==BooleanClause.Occur.MUST) return false;
        }
        return true;
	}
	
	public String toString() {
		String result = "";
		for (BooleanClause clause : clauses) {
			result += " " + clause.toString();
		}
		return result;
	}
	
    public <T> T accept(QueryVisitor<T> visitor) throws RepositoryException {
        return visitor.visitBooleanQuery(this);
    }
}
