/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.networked;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.cnri.repository.AbstractRepository;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.search.BooleanClause;
import net.cnri.repository.search.BooleanQuery;
import net.cnri.repository.search.ElementAttributeQuery;
import net.cnri.repository.search.MatchAllObjectsQuery;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;
import net.cnri.repository.search.QueryResults;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.search.QueryVisitor;
import net.cnri.repository.search.SortField;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.dobj.DOAuthentication;
import net.cnri.dobj.DOClientConnection;
import net.cnri.dobj.DOException;
import net.cnri.dobj.DOServiceInfo;
import net.cnri.dobj.HeaderSet;
import net.cnri.dobj.PKAuthentication;
import net.cnri.dobj.SecretKeyAuthentication;
import net.cnri.dobj.StreamPair;


/**
 * 
 * A wrapper on net.cnri.do_api.Repository. Provides access to remote repositories using net.cnri.do_api.Repository providing the same interface 
 * as the other repository class in net.cnri.repository
 *
 */
public class NetworkedRepository extends AbstractRepository implements Repository {
	protected net.cnri.do_api.Repository repo;

	protected NetworkedRepository() {}
	
    public NetworkedRepository(DOAuthentication auth, String repoId) throws RepositoryException {
        try {
            DOClientConnection.setEncryptionParameters(DOClientConnection.RFC_2539_WELL_KNOWN_GROUP_2);
            repo = new net.cnri.do_api.Repository(auth,repoId);
        } catch (net.cnri.dobj.DOException e) {
            throw new InternalException(e);
        }
    }

    public NetworkedRepository(DOAuthentication auth, DOServiceInfo service) throws RepositoryException {
        try {
            DOClientConnection.setEncryptionParameters(DOClientConnection.RFC_2539_WELL_KNOWN_GROUP_2);
            repo = new net.cnri.do_api.Repository(auth,service);
        } catch (net.cnri.dobj.DOException e) {
            throw new InternalException(e);
        }
    }

    /**
     * Constructs a NetworkedRepository using a specified host and port and secret key authentication.
     * 
     * @param repoHandle handle of the repository.
     * @param host
     * @param port
     * @param pubKeyBytes public key of the server (for the client to authenticate the server).  This is the "publickey" file from the server directory.
     * @param userHandle handle of the user for client authentication.  May coincide with the repository handle if authenticating as the server itself.  (The server will look up this handle first in the password.dct file, then in the Handle System.)
     * @param userPassword password (secret key) for client authentication.  (Note: this is the password either from the HS_SECKEY value in the user handle, or stored in the server's password.dct file.)
     * @throws RepositoryException
     */
    public NetworkedRepository(String repoHandle, String host, int port, byte[] pubKeyBytes, String userHandle, byte[] userPassword) throws RepositoryException {
        this(new SecretKeyAuthentication(userHandle,userPassword),getDOServiceInfo(repoHandle, host, port, pubKeyBytes));
    }

    /**
     * Constructs a NetworkedRepository looking up connection parameters in the handle system and secret key authentication.
     * 
     * @param repoHandle handle of the repository.
     * @param userHandle handle of the user for client authentication.  May coincide with the repository handle if authenticating as the server itself.  (The server will look up this handle first in the password.dct file, then in the Handle System.)
     * @param userPassword password (secret key) for client authentication.  (Note: this is the password either from the HS_SECKEY value in the user handle, or stored in the server's password.dct file.)
     * @throws RepositoryException
     */
    public NetworkedRepository(String repoHandle, String userHandle, byte[] userPassword) throws RepositoryException {
        this(new SecretKeyAuthentication(userHandle,userPassword),repoHandle);
    }

    /**
     * Constructs a NetworkedRepository using a specified host and port and public key authentication.
     * 
     * @param repoHandle handle of the repository.
     * @param host
     * @param port
     * @param pubKeyBytes public key of the server (for the client to authenticate the server).  This is the "publickey" file from the server directory.
     * @param userHandle handle of the user for client authentication.  May coincide with the repository handle if authenticating as the server itself.
     * @param privateKeyFile path to a file containing the user's (possibly encrypted) private key.  If authenticating as the server itself, this is the "privatekey" file from the server directory.
     * @param passphrase passphrase to be used the decrypt an encrypted private key file.  Can be {@code null} if the private key file is not encrypted. 
     * @throws RepositoryException
     */
    public NetworkedRepository(String repoHandle, String host, int port, byte[] pubKeyBytes, String userHandle, String privateKeyFile, String passphrase) throws RepositoryException {
        this(getPublicKeyAuth(userHandle,privateKeyFile,passphrase),getDOServiceInfo(repoHandle, host, port, pubKeyBytes));
    }

    /**
     * Constructs a NetworkedRepository looking up connection parameters in the handle system and public key authentication.
     * 
     * @param repoHandle handle of the repository.
     * @param userHandle handle of the user for client authentication.  May coincide with the repository handle if authenticating as the server itself.
     * @param privateKeyFile path to a file containing the user's (possibly encrypted) private key.  If authenticating as the server itself, this is the "privatekey" file from the server directory.
     * @param passphrase passphrase to be used the decrypt an encrypted private key file.  Can be {@code null} if the private key file is not encrypted. 
     * @throws RepositoryException
     */
    public NetworkedRepository(String repoHandle, String userHandle, String privateKeyFile, String passphrase) throws RepositoryException {
        this(getPublicKeyAuth(userHandle,privateKeyFile,passphrase),repoHandle);
    }

    private static PKAuthentication getPublicKeyAuth(String userHandle, String privateKeyFile, String passphrase) throws RepositoryException {
        try {
            return PKAuthentication.readPKAuthenticationFromFile(userHandle,privateKeyFile,passphrase);
        } catch (Exception e) {
            throw new InternalException(e);
        }
    }

    private static DOServiceInfo getDOServiceInfo(String repoHandle, String host, int port, byte[] pubKeyBytes) throws RepositoryException {
        return new DOServiceInfo(repoHandle, host, port, pubKeyBytes);
    }
	
	net.cnri.do_api.Repository getRepository() {
		return repo;
	}
	
	/**
	 * Sends the repository a 1037/indexUpToDate operation.
	 * This method does not return until the repositories index is up to date.
	 */
	public void ensureIndexUpToDate() throws RepositoryException {
	    try {
	        StreamPair pair = repo.performOperation(getHandle(), "1037/indexUpToDate", (HeaderSet)null);
	        pair.close();
	    }
	    catch(IOException e) {
	        throw new InternalException(e);
	    }
    }
	
	public void reindexAllObjects() throws DOException, IOException, RepositoryException {
		CloseableIterator<String> handles = this.listHandles();
		while(handles.hasNext()) {
			String handle = handles.next();
			reindexObject(handle);
		}
	}
	
	/**
	 * Reindex the object with the specified handle.
	 * This method will not return until the reindex is complete.
	 * If you want a change to be reflected in the search results immediately 
	 * after the change is made you need to call this method
	 */
	public void reindexObject(String objectId) throws IOException {
		HeaderSet headerSet = new HeaderSet();
		headerSet.addHeader("objectid", objectId);
		StreamPair streamPair = repo.performOperation("1037/reindex", headerSet);
		streamPair.getOutputStream().close();
		InputStream in = streamPair.getInputStream();
		byte[] buff = new byte[4096];
		while(in.read(buff)>=0) {
			//Do nothing
		}
		in.close();
	}
	
	@Override
	public String getHandle() {
	    return repo.getID();
	}
	
	@Override
	public boolean verifyDigitalObject(String handle) throws RepositoryException {
		try {
			return repo.verifyDigitalObject(handle);
		}
		catch(IOException e) {
			throw new InternalException(e);
		}
	}

	private static final String objectAlreadyExistsMessage = new DOException(DOException.OBJECT_ALREADY_EXISTS, "", null).toString();
	
	@Override
	public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
		try {
			return new NetworkedDigitalObject(this, repo.createDigitalObject(handle));
        } catch (DOException e) {
            if (e.getErrorCode() == DOException.OBJECT_ALREADY_EXISTS) {
                throw new CreationException();
            } else if (e.getErrorCode() == DOException.STORAGE_ERROR && e.getMessage().contains(objectAlreadyExistsMessage)) {
                throw new CreationException();
            } else {
                throw new InternalException(e);
            }
		}
	}

	@Override
	public DigitalObject getDigitalObject(String handle) throws RepositoryException {
		try {
			return new NetworkedDigitalObject(this, repo.getDigitalObject(handle));
		}
		catch(net.cnri.dobj.DOException e) {
			if(e.getErrorCode()==net.cnri.dobj.DOException.NO_SUCH_OBJECT_ERROR) {
				return null;
			}
			else {
				throw new InternalException(e);
			}
		}
		catch(IOException e) {
			throw new InternalException(e);
		}
	}

	@Override
	public void deleteDigitalObject(String handle) throws RepositoryException {
		try {
			repo.deleteDigitalObject(handle);
		} catch (IOException e) {
			throw new InternalException(e);
		}
	}

	@SuppressWarnings("unchecked")
	@Override
	public CloseableIterator<String> listHandles() throws RepositoryException {
		Iterator<String> iter;
		try {
			iter = repo.listObjects();
		} catch (IOException e) {
			throw new InternalException(e);
		}
		return new CloseableIteratorFromIterator<String>(iter);
	}

	@SuppressWarnings("unchecked")
	@Override
	public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
		final Iterator<String> iter;
		try{
			iter = repo.listObjects();
		} catch (IOException e) {
			throw new InternalException(e);
		}
		return new AbstractCloseableIterator<DigitalObject>() {
		    @Override
		    protected DigitalObject computeNext() {
		        if(!iter.hasNext()) return null;
                DigitalObject result = null;
                try {
                    result = getDigitalObject(iter.next());
                } catch (RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
                return result;
		    }
		    
		    @Override
		    protected void closeOnlyOnce() {
                if(iter instanceof Closeable) try { ((Closeable)iter).close(); } catch(IOException e) {}
		    }
		};
	}

	public QueryResults<DigitalObject> search(String query, QueryParams queryParams) throws RepositoryException {
	    if(queryParams==null) queryParams = QueryParams.DEFAULT;
		final net.cnri.do_api.Repository.CloseableIterator<HeaderSet> doHolder;
		net.cnri.do_api.Repository.QueryResults results;
		try {
			//search(String query, List<String> returnedFields, List<String> sortFields, String sortOrder, int pageSize, int pageOffset)
			List<SortField> sortFields = queryParams.getSortFields();
			List<String> sortFieldsForTransport;
			if(sortFields==null) sortFieldsForTransport = null;
			else {
			    sortFieldsForTransport = new ArrayList<String>(sortFields.size());
			    for(SortField sortField : sortFields) {
			        if(sortField.isReverse()) sortFieldsForTransport.add(sortField.getName() + " DESC");
			        else sortFieldsForTransport.add(sortField.getName());
			    }
			}
			results = repo.search(query, queryParams.getReturnedFields(), sortFieldsForTransport, queryParams.getPageSize(), queryParams.getPageOffset(), toHeaderSet(queryParams.getExtras()));
			doHolder = results.getIterator();
		} catch (IOException e) {
			throw new InternalException(e);
		}
		CloseableIterator<DigitalObject> search_do = new AbstractCloseableIterator<DigitalObject>() {
		    @Override
		    protected DigitalObject computeNext() {
		        if(!doHolder.hasNext()) return null;
				HeaderSet set = doHolder.next();
				return new NetworkedSearchResultDigitalObject(NetworkedRepository.this,set);
			}

			@Override
			protected void closeOnlyOnce() {
				try {
					doHolder.close();
				} catch(IOException e) {
					// ignore
				}
			}
		};
		QueryResults<DigitalObject> queryResults = new QueryResults<DigitalObject>(results.getTotalMatches(), results.isMore(), search_do);
		return queryResults;
	}
	
	private static final List<String> JUST_OBJECTID = java.util.Arrays.asList(new String[] { "objectid" });
	
    public QueryResults<String> searchHandles(String query, QueryParams queryParams) throws RepositoryException {
        if(queryParams==null) queryParams = QueryParams.DEFAULT;
        final net.cnri.do_api.Repository.CloseableIterator<HeaderSet> doHolder;
        net.cnri.do_api.Repository.QueryResults results;
        try {
            //search(String query, List<String> returnedFields, List<String> sortFields, String sortOrder, int pageSize, int pageOffset)
            List<SortField> sortFields = queryParams.getSortFields();
            List<String> sortFieldsForTransport;
            if(sortFields==null) sortFieldsForTransport = null;
            else {
                sortFieldsForTransport = new ArrayList<String>(sortFields.size());
                for(SortField sortField : sortFields) {
                    if(sortField.isReverse()) sortFieldsForTransport.add(sortField.getName() + " DESC");
                    else sortFieldsForTransport.add(sortField.getName());
                }
            }
            results = repo.search(query, JUST_OBJECTID, sortFieldsForTransport, queryParams.getPageSize(), queryParams.getPageOffset(), toHeaderSet(queryParams.getExtras()));
            doHolder = results.getIterator();
            
        } catch (IOException e) {
            throw new InternalException(e);
        }
        CloseableIterator<String> search_do = new AbstractCloseableIterator<String>() {
            @Override
            protected String computeNext() {
                if(!doHolder.hasNext()) return null;
                HeaderSet set = doHolder.next();
                return set.getStringHeader("objectid",null);
            }
            
            @Override
            protected void closeOnlyOnce() {
                try {
                    doHolder.close();
                } catch(IOException e) {
                    // ignore
                }
            }
        };
        QueryResults<String> queryResults = new QueryResults<String>(results.getTotalMatches(), results.isMore(), search_do);
        return queryResults;
    }
    
    private static HeaderSet toHeaderSet(Map<String, String> map) {
        if (map == null || map.isEmpty()) return null;
        HeaderSet res = new HeaderSet();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            res.addHeader(entry.getKey(), entry.getValue());
        }
        return res;
    }
    
	@Override
    @Deprecated
	public QueryResults<DigitalObject> search(String query) throws RepositoryException {
	    return search(query,null);
	}
	
    @Override
	public QueryResults<DigitalObject> search(Query query, QueryParams queryParams) throws RepositoryException {
        return search(queryToString(query),queryParams);
	}
	
	@Override
	@Deprecated
	public QueryResults<String> searchHandles(String query) throws RepositoryException {
	    return searchHandles(query,null);
	}

    @Override
    public QueryResults<String> searchHandles(Query query, QueryParams queryParams) throws RepositoryException {
        return searchHandles(queryToString(query),queryParams);
    }
    
    @Override
    public QueryResults<DigitalObject> search(Query query) throws RepositoryException {
        return search(query,null);
    }

    @Override
    public QueryResults<String> searchHandles(Query query) throws RepositoryException {
        return searchHandles(query,null);
    }

    private static String quote(String s) {
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"")) return s;
        boolean quoted = true;
        for (int i = 0; i < s.length(); i++) {
            if (quoted) {
                quoted = false;
            } else {
                if (' ' == s.charAt(i)) return "\"" + s.replace("\\","\\\\").replace("\"", "\\\"") + "\"";
                else if ('\\' == s.charAt(i)) quoted = true;
            }
        }
        return s;
    }
    
    public static String queryToString(Query q) throws RepositoryException {
        return q.accept(new QueryVisitor<String>() {
            @Override
            public String visitMatchAllObjectsQuery(MatchAllObjectsQuery query) throws RepositoryException {
                return "*:*";
            }
            @Override
            public String visitRawQuery(RawQuery query) throws RepositoryException {
                return query.getQueryString();
            }
            @Override
            public String visitAttributeQuery(AttributeQuery attQuery) throws RepositoryException {
                return "objatt_" + attQuery.getAttributeName() + ":" + quote(attQuery.getValue());
            }
            @Override
            public String visitElementAttributeQuery(ElementAttributeQuery elattQuery) throws RepositoryException {
                return "elatt_" + elattQuery.getElementName() + "_" + elattQuery.getAttributeName() + ":" + quote(elattQuery.getValue());
            }
            @Override
            public String visitBooleanQuery(BooleanQuery booleanQuery) throws RepositoryException {
                StringBuilder sb = new StringBuilder();
                sb.append("(");
                boolean positive = false;
                for(BooleanClause clause : booleanQuery.clauses()) {
                    if(sb.length()>1) sb.append(" ");
                    if(clause.getOccur()==BooleanClause.Occur.MUST) sb.append("+");
                    else if(clause.getOccur()==BooleanClause.Occur.MUST_NOT) sb.append("-");
                    if(clause.getOccur()!=BooleanClause.Occur.MUST_NOT) positive = true;
                    sb.append(queryToString(clause.getQuery()));
                }
                if(!positive) {
                    if(sb.length()>1) sb.append(" ");
                    sb.append("*:*");
                }
                sb.append(")");
                return sb.toString();
            }
        });
    }

	@Deprecated
	@Override
	public CloseableIterator<Map<String, String>> searchMapping(String query) throws RepositoryException {
		final net.cnri.do_api.Repository.CloseableIterator<HeaderSet> doHolder;
		try {
			doHolder = repo.search(query);
		} catch (IOException e) {
			throw new InternalException(e);
		}
		net.cnri.do_api.Repository.CloseableIterator<Map<String, String>> search_do = new net.cnri.do_api.Repository.CloseableIterator<Map<String,String>>() {
			Map<String, String> map;
			@Override
			public boolean hasNext() {
				if(map==null) setMapToNextValidMap();
				return map!=null;
			}

			@Override
			public Map<String, String> next() {
				if(map==null) setMapToNextValidMap();
				Map<String,String> res = map;
				map = null;
				return res;
			}

			private void setMapToNextValidMap() {
				if(!doHolder.hasNext()) return;
				HeaderSet set = doHolder.next();
				while(!"result".equals(set.getMessageType()) && doHolder.hasNext()) {
					set = doHolder.next();
				}
				if(!"result".equals(set.getMessageType())) map = null;
				map = new MapFromHeaderSet(set);
			}
			
			@Override
			public void remove() {
				throw new UnsupportedOperationException();
			}

			@Override
			public void close() throws IOException {
				doHolder.close();
			}	
		};
		return new CloseableIteratorFromIterator<Map<String, String>>(search_do);
	}
	
	@Override
	public void close() {
	    try {
	        repo.getConnection().close();
	    }
	    catch(IOException e) {
	        // ignore
	    }
	}
	
	/**
	 * Perform an arbitrary operation.
	 * 
	 * @param objectId the id of the target object.
	 * @param operationId the operation to perform.
	 * @param parameters parameters of the operation.
	 * @return a StreamPair for the input and output of the operation.
	 * @throws RepositoryException
	 */
	public StreamPair performOperation(String objectId, String operationId, Map<String,String> parameters) throws RepositoryException {
	    HeaderSet headers = new HeaderSet();
	    for(Map.Entry<String,String> entry : parameters.entrySet()) {
	        headers.addHeader(entry.getKey(),entry.getValue());
	    }
	    return performOperation(objectId,operationId,headers);
	}

    /**
     * Perform an arbitrary operation.
     * 
     * @param objectId the id of the target object.
     * @param operationId the operation to perform.
     * @param parameters parameters of the operation.
     * @return a StreamPair for the input and output of the operation.
     * @throws RepositoryException
     */
	public StreamPair performOperation(String objectId, String operationId, HeaderSet parameters) throws RepositoryException {
	    try {
	        return repo.performOperation(objectId,operationId,parameters);
	    }
	    catch(IOException e) {
	        throw new InternalException(e);
	    }
	}
}
