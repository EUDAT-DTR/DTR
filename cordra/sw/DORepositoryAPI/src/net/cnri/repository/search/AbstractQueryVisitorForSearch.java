/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.search;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.CollectionUtil;
import net.cnri.repository.util.IteratorIncludingExcludingSets;

/**
 * An effectively abstract implementation of {@link QueryVisitor} used by repository search implementations.  
 * Subclasses should implement {@link #visitAttributeQuery} and/or {@link #visitElementAttributeQuery}.
 * This class handles {@code BooleanQuery} (by performing the boolean operations), {@code RawQuery} (by interpreting it as an attribute query specification),
 * and {@code MatchAllObjectsQuery} (by passing to the {@code listHandles} or {@code listObjects} methods of the repository). 
 *
 * The repository being searched, and the class of the results (either {@code String.class} or {@code DigitalObject.class}), are available as protected fields.
 *
 * @param <T> either String (for searching handles) or DigitalObject (for searching object).
 */
public class AbstractQueryVisitorForSearch<T> implements QueryVisitor<CloseableIterator<T>> {
    /**
     * The repository being searched.
     */
    protected final Repository repo;
    /**
     * Either {@code String.class} for searching handles, or {@code DigitalObject.class} for searching objects.
     */
    protected final Class<T> klass;
    
    public AbstractQueryVisitorForSearch(Repository repo, Class<T> klass) {
        this.repo = repo;
        this.klass = klass;
    }
    
    @Override
    public CloseableIterator<T> visitAttributeQuery(AttributeQuery query) throws RepositoryException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public CloseableIterator<T> visitElementAttributeQuery(ElementAttributeQuery query) throws RepositoryException {
        throw new UnsupportedOperationException();
    }
    
    private static Pattern searchPattern = Pattern.compile("\\s*+att\\.name\\s*+=\\s*+'([^']*+)'\\s*+AND\\s*+att\\.value\\s*+=\\s*+'([^']*+)'\\s*+");

    @Override
    public CloseableIterator<T> visitRawQuery(RawQuery query) throws RepositoryException {
        Matcher m = searchPattern.matcher(query.getQueryString());
        if(!m.matches()) throw new UnsupportedOperationException();
        AttributeQuery queryObject = new AttributeQuery(m.group(1), m.group(2));
        return visitAttributeQuery(queryObject);
    }

    @Override
    @SuppressWarnings("unchecked")
    public CloseableIterator<T> visitMatchAllObjectsQuery(MatchAllObjectsQuery query) throws RepositoryException {
        if(klass==String.class) return (CloseableIterator<T>)repo.listHandles();
        else return (CloseableIterator<T>)repo.listObjects();
    }

    @Override
    public CloseableIterator<T> visitBooleanQuery(BooleanQuery booleanQuery) throws RepositoryException {
        if (booleanQuery.onlyContainsMustNotClauses()) {
            Set<T> mustNotResults = getMustNotResults(booleanQuery);
            CloseableIterator<T> all = visitMatchAllObjectsQuery(new MatchAllObjectsQuery());
            return new IteratorIncludingExcludingSets<T>(all,null,mustNotResults);            
        } else if(booleanQuery.containsNoMustClauses()) {
            Set<T> mustNotResults = getMustNotResults(booleanQuery);
            CloseableIterator<T> all = getShouldResultsIterator(booleanQuery);
            return new IteratorIncludingExcludingSets<T>(all,null,mustNotResults);            
        } else {
            CloseableIterator<T> firstMustResults = getFirstMustResultsIterator(booleanQuery);
            Set<T> restMustResults = getOtherMustAndShouldResults(booleanQuery);
            Set<T> mustNotResults = getMustNotResults(booleanQuery);
            return new IteratorIncludingExcludingSets<T>(firstMustResults,restMustResults,mustNotResults);            
        }
    }
    
    protected Set<T> getMustNotResults(BooleanQuery booleanQuery) throws RepositoryException {
        Set<T> res = new HashSet<T>();
        for(BooleanClause clause : booleanQuery.clauses()) {
            if(clause.getOccur()!=BooleanClause.Occur.MUST_NOT) continue;
            CollectionUtil.addAllFromIterator(res,clause.getQuery().accept(this));
        }
        return res;
    }
    
    protected CloseableIterator<T> getShouldResultsIterator(final BooleanQuery booleanQuery) throws RepositoryException {
        return new AbstractCloseableIterator<T>() {
            final Iterator<BooleanClause> clauseIter = booleanQuery.clauses().iterator();
            CloseableIterator<T> curr;
            
            @Override
            protected T computeNext() {
                while(true) {
                    if(curr!=null) {
                        if(curr.hasNext()) return curr.next();
                        curr.close();
                    }
                    BooleanClause nextClause = null;
                    while(clauseIter.hasNext()) {
                        BooleanClause nextCandidateClause = clauseIter.next();
                        if(nextCandidateClause.getOccur()!=BooleanClause.Occur.SHOULD) continue;
                        nextClause = nextCandidateClause;
                        break;
                    }
                    if(nextClause==null) return null;
                    try {
                        curr = nextClause.getQuery().accept(AbstractQueryVisitorForSearch.this);
                    }
                    catch(RepositoryException e) {
                        throw new UncheckedRepositoryException(e);
                    }
                }
            }
            @Override
            protected void closeOnlyOnce() {
                if(curr!=null) {
                    curr.close();
                    curr = null;
                }
            }
        };
    }
    
    protected CloseableIterator<T> getFirstMustResultsIterator(BooleanQuery booleanQuery) throws RepositoryException {
        for(BooleanClause clause : booleanQuery.clauses()) {
            if(clause.getOccur()==BooleanClause.Occur.MUST) {
                return clause.getQuery().accept(this);
            }
        }
        return null;
    }
    
    protected Set<T> getOtherMustAndShouldResults(BooleanQuery booleanQuery) throws RepositoryException {
        boolean seenFirstMust = false;
        Set<T> res = null;
        Set<T> shoulds = null;
        for(BooleanClause clause : booleanQuery.clauses()) {
            if(clause.getOccur()==BooleanClause.Occur.MUST) {
                if(!seenFirstMust) seenFirstMust = true;
                else if(res==null) res = CollectionUtil.asSet(clause.getQuery().accept(this));
                else res.retainAll(CollectionUtil.asSet(clause.getQuery().accept(this)));
            }
            else if(clause.getOccur()==BooleanClause.Occur.SHOULD) {
                if(shoulds==null) shoulds = CollectionUtil.asSet(clause.getQuery().accept(this));
                CollectionUtil.addAllFromIterator(shoulds,clause.getQuery().accept(this));
            }
        }
        if(res==null) return shoulds;
        if(shoulds==null) return res;
        res.retainAll(shoulds);
        return res;
    }
    
    /**
     * Use {@code new AbstractQueryVisitorForSearch.SearchHandles(repo)} as shorthand for {@code new AbstractQueryVisitorForSearch<String>(repo,String.class)}.
     */
    public static class SearchHandles extends AbstractQueryVisitorForSearch<String> {
        public SearchHandles(Repository repo) {
            super(repo,String.class);
        }
    }

    /**
     * Use {@code new AbstractQueryVisitorForSearch.SearchObjects(repo)} as shorthand for {@code new AbstractQueryVisitorForSearch<DigitalObject>(repo,DigitalObject.class)}.
     */
    public static class SearchObjects extends AbstractQueryVisitorForSearch<DigitalObject> {
        public SearchObjects(Repository repo) {
            super(repo,DigitalObject.class);
        }
    }
}
