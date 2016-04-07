/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.rest;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.net.ssl.SSLContext;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import net.cnri.repository.AbstractRepository;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.memory.MemoryDigitalObject;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.search.BooleanClause;
import net.cnri.repository.search.BooleanQuery;
import net.cnri.repository.search.ElementAttributeQuery;
import net.cnri.repository.search.MatchAllObjectsQuery;
import net.cnri.repository.search.Query;
import net.cnri.repository.search.QueryParams;
import net.cnri.repository.search.QueryResults;
import net.cnri.repository.search.QueryVisitor;
import net.cnri.repository.search.RawQuery;
import net.cnri.repository.search.SortField;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.repository.util.RepositoryJsonSerializerV2;
import net.cnri.util.StringUtils;

/**
 * This is an implementation of Repository API used for connecting to RestRepository servlet using JSON over HTTP.
 */
public class RestRepository extends AbstractRepository {
    private CloseableHttpClient client;
    final String baseUri;
    private final String username;
    private final String password;
    private final Header authHeader;
    
    public RestRepository(String baseUri, String username, String password) {
        this.baseUri = baseUri;
        this.username = username;
        this.password = password;
        this.authHeader = buildAuthHeader();
    }
    
    private Header buildAuthHeader() {
        try {
            return new BasicHeader("Authorization", "Basic " + Base64.encodeBase64String((username + ":" + password).getBytes("UTF-8")));
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
    
    protected String getJSONFromURLPrememptiveBasicAuth(String url) throws ClientProtocolException, IOException {
        HttpGet httpget = new HttpGet(url);
        httpget.addHeader(authHeader);
        HttpEntity entity = null;
        CloseableHttpResponse response = getClient().execute(httpget);
        try {
            entity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            String responseString = EntityUtils.toString(entity);
            return responseString;
        } finally {
            if (entity != null) consumeQuietly(entity);
            response.close();
        }
    }
    
    protected InputStream getInputStreamFromURLPrememptiveBasicAuth(String url) throws IOException {
        HttpGet httpget = new HttpGet(url);
        httpget.addHeader(authHeader);
        CloseableHttpResponse response = getClient().execute(httpget);
        boolean success = false;
        try {
            HttpEntity entity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            InputStream result = wrapInputStreamForResponse(entity.getContent(), response);
            success = true;
            return result;
        } finally {
            if (!success) response.close();
        }
    }
    
    private static InputStream wrapInputStreamForResponse(InputStream in, CloseableHttpResponse response) {
        return new ResponseClosingInputStreamWrapper(in, response);
    }
    
    static class ResponseClosingInputStreamWrapper extends FilterInputStream {
        private final CloseableHttpResponse response;
        
        public ResponseClosingInputStreamWrapper(InputStream in, CloseableHttpResponse response) {
            super(in);
            this.response = response;
        }
        
        @Override
        public int read() throws IOException {
            int res = super.read();
            if (res < 0) close();
            return res;
        }
        
        @Override
        public int read(byte[] b) throws IOException {
            int res = super.read(b);
            if (res < 0) close();
            return res;
        }
        
        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int res = super.read(b, off, len);
            if (res < 0) close();
            return res;
        }
        
        @Override
        public void close() throws IOException {
            IOException exception = null;
            try {
                super.close();
            } catch (IOException e) {
                exception = e;
            }
            try {
                response.close();
            } catch (IOException e) {
                if (exception == null) exception = e;
            }
            if (exception != null) throw exception;
        }
    }
    
    protected String postInputStreamToURLPrememptiveBasicAuth(String url, InputStream data) throws ClientProtocolException, IOException {
        HttpEntity entity = null;
        CloseableHttpResponse response = null;
        try {
            HttpPost httpPost = new HttpPost(url);
            if(data!=null) httpPost.setEntity(new InputStreamEntity(data, -1));
            httpPost.addHeader(authHeader);
            response = getClient().execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            entity = response.getEntity();
            if(entity==null) return null;
            String responseString = EntityUtils.toString(entity);
            return responseString;
        } finally {
            if (data!=null) try { data.close(); } catch (Exception e) { }
            if (entity != null) consumeQuietly(entity);
            if (response != null) response.close();
        }
    }
    
    protected String postToURLPrememptiveBasicAuth(String url, String data) throws ClientProtocolException, IOException {
        HttpPost httpPost = new HttpPost(url);
        if(data!=null) httpPost.setEntity(new StringEntity(data));
        httpPost.addHeader(authHeader);
        HttpEntity entity = null;
        CloseableHttpResponse response = getClient().execute(httpPost);
        try {
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            entity = response.getEntity();
            if(entity==null) return null;
            String responseString = EntityUtils.toString(entity);
            return responseString;
        } finally {
            if (entity != null) consumeQuietly(entity);
            response.close();
        }
    }
    
    protected int deleteFromURLPrememptiveBasicAuth(String url) throws ClientProtocolException, IOException {
        HttpDelete httpDelete = new HttpDelete(url);
        httpDelete.addHeader(authHeader);
        CloseableHttpResponse response = getClient().execute(httpDelete);
        try {
            HttpEntity entity = response.getEntity();
            consumeQuietly(entity);
            return response.getStatusLine().getStatusCode();
        } finally {
            response.close();
        }
    }
    
    @Override
    public String getHandle() {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public boolean verifyDigitalObject(String handle) throws RepositoryException {
        return getDigitalObject(handle) != null;
    }

    @Override
    public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        if(verifyDigitalObject(handle)) throw new CreationException();
        return getOrCreateDigitalObject(handle);
    }

    @Override
    public DigitalObject getDigitalObject(String handle) throws RepositoryException {
        String json;
        try {
            json = getJSONFromURLPrememptiveBasicAuth(baseUri + "/" + handle);
            if (json == null) {
                return null;
            } else {
                MemoryDigitalObject dobj = RepositoryJsonSerializerV2.createDigitalObjectFromJson(json);
                return new RestDigitalObject(this, dobj);
            }
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public DigitalObject getOrCreateDigitalObject(String handle) throws RepositoryException {
        try {
            if(handle==null) postToURLPrememptiveBasicAuth(baseUri + "/", null);
            else postToURLPrememptiveBasicAuth(baseUri + "/" + handle, null);
            return getDigitalObject(handle);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }

    @Override
    public CloseableIterator<String> listHandles() throws RepositoryException {
        final CloseableIterator<DigitalObject> objectsIter = listObjects();
        return new AbstractCloseableIterator<String>() {
            @Override
            protected String computeNext() {
                while(objectsIter.hasNext()) {
                    DigitalObject current = objectsIter.next();
                    return current.getHandle();
                }
                return null;
            }
            @Override
            protected void closeOnlyOnce() {
                objectsIter.close();
            }
        };
    }

    @Override
    public void deleteDigitalObject(String handle) throws RepositoryException {
        try {
            deleteFromURLPrememptiveBasicAuth(baseUri + "/" + handle);
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }
    
    private List<RestDigitalObject> getObjectsForJson(String json) throws RepositoryException {
        if (json == null) {
            return null;
        } else {
            List<MemoryDigitalObject> memoryDigitalObjects = RepositoryJsonSerializerV2.createDigitalObjectsFromJson(json);
            return wrapMemoryObjectsInRestObjects(memoryDigitalObjects);
        }
    }
    
    private List<RestDigitalObject> getObjectsForDOViews(List<net.cnri.repository.util.RepositoryJsonSerializerV2.DOView> list) throws RepositoryException {
        List<MemoryDigitalObject> memoryDigitalObjects = RepositoryJsonSerializerV2.createDigitalObjectsFromDOViews(list);
        return wrapMemoryObjectsInRestObjects(memoryDigitalObjects);
    }

    @Override
    public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        String json;
        try {
            json = getJSONFromURLPrememptiveBasicAuth(baseUri + "/");
            return new CloseableIteratorFromIterator<DigitalObject>(getObjectsForJson(json).listIterator());
        } catch (ClientProtocolException e) {
            throw new InternalException(e);
        } catch (IOException e) {
            throw new InternalException(e);
        }
    }
    
    private List<RestDigitalObject> wrapMemoryObjectsInRestObjects(List<MemoryDigitalObject> memoryDigitalObjects) {
        List<RestDigitalObject> result = new ArrayList<RestDigitalObject>();
        for(MemoryDigitalObject memoryDigitalObject : memoryDigitalObjects) {
            result.add(new RestDigitalObject(this, memoryDigitalObject));
        }
        return result;
    }

    private String buildRestQueryString(String query, List<String> returnedFields, List<String> sortFieldsForTransport, int pageSize, int pageOffset) {
        StringBuilder sb = new StringBuilder();
        query = StringUtils.encodeURLComponent(query);
        sb.append(baseUri).append("/?query=").append(query);
        if (returnedFields != null && returnedFields.size() != 0) {
            sb.append("&returnedFields=");
            sb.append(listOfStringsToString(returnedFields, ","));
        }
        if (sortFieldsForTransport != null && sortFieldsForTransport.size() != 0) {
            sb.append("&sortFields=");
            sb.append(StringUtils.encodeURLComponent(listOfStringsToString(sortFieldsForTransport, ",")));
        }
        sb.append("&pageSize=").append(pageSize);
        sb.append("&pageOffset=").append(pageOffset);
        return sb.toString();
    }
    
    private static String listOfStringsToString(List<String> strings, String delim) {
        Iterator<String> iterator = strings.iterator();
        if (iterator == null) {
            return null;
        }
        if (!iterator.hasNext()) {
            return "";
        }
        String first = iterator.next();
        if (!iterator.hasNext()) {
            return first;
        }
        StringBuilder sb = new StringBuilder();
        sb.append(first);
        while (iterator.hasNext()) {
            sb.append(delim);
            String next = iterator.next();
            sb.append(next);
        }
        return sb.toString();
    }
    
    public QueryResults<DigitalObject> search(String query, QueryParams queryParams) throws RepositoryException {
        QueryResults<DigitalObject> queryResults = null;
        if(queryParams==null) queryParams = QueryParams.DEFAULT;
        try {
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
            String restQueryString = buildRestQueryString(query, queryParams.getReturnedFields(), sortFieldsForTransport, queryParams.getPageSize(), queryParams.getPageOffset());
            String json = getJSONFromURLPrememptiveBasicAuth(restQueryString);
            
            QueryResultsForJson parsedResults = getQueryResultsFromJson(json);
            List<RestDigitalObject> resultObjects = getObjectsForDOViews(parsedResults.getResults());
            CloseableIterator<DigitalObject> resultsIter = new CloseableIteratorFromIterator<DigitalObject>(resultObjects.listIterator());
            queryResults = new QueryResults<DigitalObject>(parsedResults.getNumResults(), resultsIter);
        } catch (IOException e) {
            throw new InternalException(e);
        }
        return queryResults;
    }
    
    private static QueryResultsForJson getQueryResultsFromJson(String json) {
        Gson gsonInstance = new Gson();
        QueryResultsForJson result = gsonInstance.fromJson(json, QueryResultsForJson.class);
        return result;
    }
    
    private static final List<String> JUST_OBJECTID = java.util.Arrays.asList(new String[] { "objectid" });
    
    public QueryResults<String> searchHandles(String query, QueryParams queryParams) throws RepositoryException {
        QueryResults<String> queryResults = null;
        if(queryParams==null) queryParams = QueryParams.DEFAULT;
        try {
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
            String restQueryString = buildRestQueryString(query, JUST_OBJECTID, sortFieldsForTransport, queryParams.getPageSize(), queryParams.getPageOffset());
            String json = getJSONFromURLPrememptiveBasicAuth(restQueryString);
            QueryResultsForJson parsedResults = getQueryResultsFromJson(json);
            List<RestDigitalObject> resultObjects = getObjectsForDOViews(parsedResults.getResults());
            final CloseableIterator<DigitalObject> resultsIter = new CloseableIteratorFromIterator<DigitalObject>(resultObjects.listIterator());
            CloseableIterator<String> handlesIter = new AbstractCloseableIterator<String>() {
                @Override
                protected String computeNext() {
                    while(resultsIter.hasNext()) {
                        DigitalObject current = resultsIter.next();
                        return current.getHandle();
                    }
                    return null;
                }
                @Override
                protected void closeOnlyOnce() {
                    resultsIter.close();
                }
            };
            queryResults = new QueryResults<String>(parsedResults.getNumResults(), handlesIter);
        } catch (IOException e) {
            throw new InternalException(e);
        }
        return queryResults;
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

    @Override
    public synchronized void close() {
        if (client != null) try { client.close(); } catch (Exception e) { }
    }

    private synchronized CloseableHttpClient getClient() {
        if (client != null) return client;
        client = getNewHttpClient();
        return client;
    }
    
    private static CloseableHttpClient getNewHttpClient() {
        try {
            TrustStrategy trustStrategy = new TrustStrategy() {
                @Override
                public boolean isTrusted(X509Certificate[] chain, String authType) throws CertificateException {
                    return true;
                }
            };
            SSLContext sslContext = SSLContexts.custom().loadTrustMaterial(null, trustStrategy).build();
            SSLConnectionSocketFactory factory = new SSLConnectionSocketFactory(sslContext, SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER);

            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory>create()
                    .register("http", PlainConnectionSocketFactory.INSTANCE)
                    .register("https", factory)
                    .build();
            ConnectionConfig connectionConfig = ConnectionConfig.custom()
                    .setCharset(Consts.UTF_8)
                    .build();
            SocketConfig socketConfig = SocketConfig.custom()
                    .setSoTimeout(90000)
                    .build();
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connManager.setDefaultMaxPerRoute(20);
            connManager.setMaxTotal(20);
            connManager.setDefaultConnectionConfig(connectionConfig);
            connManager.setDefaultSocketConfig(socketConfig);
            
            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(30000)
                    .setSocketTimeout(90000)
                    .build();
            
            return HttpClients.custom()
                    .setConnectionManager(connManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    // EntityUtils.consume, present here to back-port to Android 8
    public static void consumeEntity(final HttpEntity entity) throws IOException {
        if (entity == null) {
            return;
        }
        if (entity.isStreaming()) {
            InputStream instream = entity.getContent();
            if (instream != null) {
                instream.close();
            }
        }
    }
    
    public static void consumeQuietly(final HttpEntity entity) {
        try {
            consumeEntity(entity);
        } catch (IOException e) {
            // ignore
        }
    }
}
