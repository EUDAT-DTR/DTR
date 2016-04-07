/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.cordra.api;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;

import org.apache.http.Consts;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;

import com.google.gson.Gson;

import net.cnri.util.StringUtils;

public class HttpCordraClient implements CordraClient {
    private final CloseableHttpClient httpClient;
    private final Gson gson;
    private final String baseUri;
    private final String username;
    private final String password;
    
    public HttpCordraClient(String baseUri, String username, String password) throws CordraException {
        this.httpClient = getNewHttpClient();
        this.gson = new Gson();
        if (!baseUri.endsWith("/")) baseUri += "/";
        this.baseUri = baseUri;
        this.username = username;
        this.password = password;
    }
    
    public void close() throws IOException {
        httpClient.close();
    }
    
    @Override
    public boolean authenticate(String usernameParam, String passwordParam) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            String uri = baseUri + "sessions";
            HttpPost request = new HttpPost(uri);
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            entity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 403) {
                return false;
            }
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            String responseString = EntityUtils.toString(entity);
            AuthResponse authResponse = gson.fromJson(responseString, AuthResponse.class);
            return authResponse.success;
        } catch (IOException e) {
            throw new CordraException(e);
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }
    
    @Override
    public AuthResponse authenticateAndGetResponse(String usernameParam, String passwordParam) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            String uri = baseUri + "sessions";
            HttpPost request = new HttpPost(uri);
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            entity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 403) {
                AuthResponse authResponse = new AuthResponse(false, null, usernameParam);
                return authResponse;
            }
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            String responseString = EntityUtils.toString(entity);
            AuthResponse authResponse = gson.fromJson(responseString, AuthResponse.class);
            return authResponse;
        } catch (IOException e) {
            throw new CordraException(e);
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }    
    
    @Override
    public boolean authenticate() throws CordraException {
        return authenticate(username, password);
    }
    
    @Override
    public CordraObject get(String id, String usernameParam, String passwordParam) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            HttpGet request = new HttpGet(baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?full");
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            entity = response.getEntity();
            String responseString = EntityUtils.toString(entity);
            return gson.fromJson(responseString, CordraObject.class);
        } catch (IOException e) {
            throw new CordraException(e);
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }

//    private void addCredentials(HttpRequest request) {
//        if (username == null) return;
//        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password);
//        try {
//            request.addHeader(new BasicScheme().authenticate(creds, request, null));
//        } catch (AuthenticationException e) {
//            throw new AssertionError(e);
//        }
//    }
    
    private void addCredentials(HttpRequest request, String usernameParam, String passwordParam) {
        if (usernameParam == null) return;
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(usernameParam, passwordParam);
        try {
            request.addHeader(new BasicScheme().authenticate(creds, request, null));
        } catch (AuthenticationException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public InputStream getPayload(String id, String payloadName, String usernameParam, String passwordParam) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            HttpGet request = new HttpGet(baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?payload=" + payloadName);
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            entity = response.getEntity();
            return entity.getContent();
            // TODO close should work
            //             if (entity!=null) EntityUtils.consumeQuietly(entity);
            // if (response != null) try { response.close(); } catch (IOException e) { }
        } catch (IOException e) {
            throw new CordraException(e);
        }
    }
    
    private InputStream getPartialPayload(String id, String payloadName, Long start, Long end, String usernameParam, String passwordParam) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            HttpGet request = new HttpGet(baseUri + "objects/" + StringUtils.encodeURLPath(id) + "?payload=" + payloadName);
            if (start != null || end != null) {
                String rangeHeader = createRangeHeader(start, end);
                request.addHeader("Range", rangeHeader);
            }
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404) {
                return null;
            }
            if (statusCode == 416) {
                throw new CordraException("Range is not satisfiable");
            }
            if (statusCode != 200 && statusCode != 206) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            entity = response.getEntity();
            return entity.getContent();
            // TODO close should work
            //             if (entity!=null) EntityUtils.consumeQuietly(entity);
            // if (response != null) try { response.close(); } catch (IOException e) { }
        } catch (IOException e) {
            throw new CordraException(e);
        }
    }
    
    private String createRangeHeader(Long start, Long end) {
        if (start != null && end != null) {
            return "bytes=" + start + "-" + end;
        } else if (start != null && end == null) {
            return "bytes=" + start + "-";
        } else if (start == null && end != null) {
            return "bytes=" + "-" + end;
        } else {
            return null;
        }
    }

    @Override
    public CordraObject create(String type, String contentJson, String usernameParam, String passwordParam) throws CordraException {
//        DigitalEntity d = new DigitalEntity();
//        d.content = gson.toJsonTree(contentJson);
//        create(d, null);
                
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            String uri = baseUri + "objects/?full&type=" + StringUtils.encodeURLComponent(type);
            HttpPost request = new HttpPost(uri);
            addCredentials(request, usernameParam, passwordParam);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("json", contentJson, ContentType.APPLICATION_JSON);
            request.setEntity(builder.build());
            response = httpClient.execute(request);
            entity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            String responseString = EntityUtils.toString(entity);
            return gson.fromJson(responseString, CordraObject.class);
        } catch (IOException e) {
            throw new CordraException(e);
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }
    
    @Override
    public CordraObject create(CordraObject d, String usernameParam, String passwordParam) throws CordraException {
        return createOrUpdate(d, true, usernameParam, passwordParam);
    }

    @Override
    public CordraObject update(CordraObject d, String usernameParam, String passwordParam) throws CordraException {
        return createOrUpdate(d, false, usernameParam, passwordParam);
    }

    private CordraObject createOrUpdate(CordraObject d, boolean isCreate, String usernameParam, String passwordParam) throws CordraException {
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            String uri = baseUri + "objects/";
            HttpEntityEnclosingRequestBase request;
            if (isCreate) {
                uri += "?full&type=" + StringUtils.encodeURLComponent(d.type);
                if (d.id != null) uri += "&handle=" + StringUtils.encodeURLComponent(d.id);
                request = new HttpPost(uri);
            } else {
                uri += StringUtils.encodeURLPath(d.id);
                uri += "?full";
                request = new HttpPut(uri);
            }
            addCredentials(request, usernameParam, passwordParam);
            MultipartEntityBuilder builder = MultipartEntityBuilder.create();
            builder.addTextBody("json", gson.toJson(d.content), ContentType.APPLICATION_JSON);
            List<String> payloadsToDelete = d.getPayloadsToDelete();
            for (String payloadToDelete : payloadsToDelete) {
                builder.addTextBody("payloadToDelete", payloadToDelete);
            }
            if (d.payloads != null) {
                for (Payload payload : d.payloads) {
                    InputStream in = payload.getInputStream();
                    if (in != null) { 
                        builder.addBinaryBody(payload.name, in, ContentType.create(payload.mediaType), payload.filename);
                    }
                }
            }
            request.setEntity(builder.build());
            response = httpClient.execute(request);
            entity = response.getEntity();
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            String responseString = EntityUtils.toString(entity);
            return gson.fromJson(responseString, CordraObject.class);
        } catch (IOException e) {
            throw new CordraException(e);
        } finally {
            if (d.payloads != null) {
                for (Payload payload : d.payloads) {
                    try { 
                        InputStream in = payload.getInputStream();
                        if (in != null) {
                            in.close(); 
                        }
                    } catch (IOException e) { }
                }
            }
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }

    @Override
    public void delete(String id, String usernameParam, String passwordParam) throws CordraException {
        CloseableHttpResponse response = null;
        try {
            HttpDelete request = new HttpDelete(baseUri + "objects/" + StringUtils.encodeURLPath(id));
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200 && statusCode != 201) {
                throw new CordraException("Unexpected result " + statusCode);
            }
        } catch (IOException e) {
            throw new CordraException(e);
        } finally {
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }

    @Override
    public SearchResults<CordraObject> search(String query, String usernameParam, String passwordParam) throws CordraException {
        return search(query, QueryParams.DEFAULT, usernameParam, passwordParam);
    }

    @Override
    public SearchResults<String> searchHandles(String query, String usernameParam, String passwordParam) throws CordraException {
        return searchHandles(query, QueryParams.DEFAULT, usernameParam, passwordParam);
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params, String usernameParam, String passwordParam) throws CordraException {
        if (params == null) params = QueryParams.DEFAULT;
        CloseableHttpResponse response = null;
        try {
            HttpGet request = new HttpGet(baseUri + "objects/" + "?query=" + StringUtils.encodeURLComponent(query) + "&" + encodeParams(params));
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            return new HttpCordraObjectSearchResults(response);
        } catch (Exception e) {
// TODO
//            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException ex) { }
            throw new CordraException(e);
        }
    }
    
    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params, String usernameParam, String passwordParam) throws CordraException {
        if (params == null) params = QueryParams.DEFAULT;
        CloseableHttpResponse response = null;
        try {
            HttpGet request = new HttpGet(baseUri + "objects/" + "?query=" + StringUtils.encodeURLComponent(query) + "&" + encodeParams(params));
            addCredentials(request, usernameParam, passwordParam);
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new CordraException("Unexpected result " + statusCode);
            }
            return new HttpHandleSearchResults(response);
        } catch (Exception e) {
// TODO
//            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException ex) { }
            throw new CordraException(e);
        }  
    }
    
    private String encodeParams(QueryParams params) {
        return "pageNum=" + params.getPageNumber() + "&pageSize=" + params.getPageSize();
    }

    @Override
    public SearchResults<CordraObject> list() throws CordraException {
        return search("*:*");
    }

    static CloseableHttpClient getNewHttpClient() throws CordraException {
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
//            SocketConfig socketConfig = SocketConfig.custom()
//                    .setSoTimeout(90000)
//                    .build();
            PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            connManager.setDefaultMaxPerRoute(20);
            connManager.setMaxTotal(20);
            connManager.setDefaultConnectionConfig(connectionConfig);
//            connManager.setDefaultSocketConfig(socketConfig);
            
            RequestConfig requestConfig = RequestConfig.custom()
//                    .setConnectTimeout(30000)
//                    .setSocketTimeout(90000)
                    .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                    .build();
            
            return HttpClients.custom()
                    .setConnectionManager(connManager)
                    .setDefaultRequestConfig(requestConfig)
                    .build();
        } catch (Exception e) {
            throw new CordraException(e);
        }
    }

    @Override
    public CordraObject create(CordraObject d) throws CordraException {
        return create(d, username, password);
    }

    @Override
    public CordraObject update(CordraObject d) throws CordraException {
        return update(d, username, password);
    }

    @Override
    public void delete(String id) throws CordraException {
        delete(id, username, password);
    }

    @Override
    public SearchResults<CordraObject> search(String query) throws CordraException {
        return search(query, username, password);
    }

    @Override
    public SearchResults<String> searchHandles(String query) throws CordraException {
        return searchHandles(query, username, password);
    }

    @Override
    public SearchResults<CordraObject> search(String query, QueryParams params) throws CordraException {
        return search(query, params, username, password);
    }

    @Override
    public SearchResults<String> searchHandles(String query, QueryParams params) throws CordraException {
        return searchHandles(query, params, username, password);
    }

    @Override
    public CordraObject get(String id) throws CordraException {
        return get(id, username, password);
    }

    @Override
    public InputStream getPayload(String id, String payloadName) throws CordraException {
        return getPayload(id, payloadName, username, password);
    }

    public InputStream getPartialPayload(String id, String payloadName, Long start, Long end) throws CordraException {
        return getPartialPayload(id, payloadName, start, end, username, password);
    }


}
