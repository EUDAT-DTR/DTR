/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.doip;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HeaderIterator;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.cnri.dobj.DOException;
import net.cnri.dobj.DOOperation;
import net.cnri.dobj.DOOperationContext;
import net.cnri.dobj.HeaderItem;
import net.cnri.dobj.HeaderSet;
import net.cnri.util.StringUtils;

public class RegistrarDoipOperations implements DOOperation {
    private static Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());

    public static final String GET_OBJECT_OP = "1037.1/get";
    public static final String CREATE_OBJECT_OP = "1037.1/create";
    public static final String UPDATE_OBJECT_OP = "1037.1/update";
    public static final String DELETE_OBJECT_OP = "1037.1/delete";
    public static final String SEARCH_OBJECT_OP = "1037.1/search";
    public static final String[] operations = {
        GET_OBJECT_OP,
        CREATE_OBJECT_OP,
        UPDATE_OBJECT_OP,
        DELETE_OBJECT_OP,
        SEARCH_OBJECT_OP
    };
    public static final List<String> operationsList = java.util.Arrays.asList(operations);
    
    
    private static final ConcurrentMap<String, String> authenticationMap = new ConcurrentHashMap<String, String>();
    private static final Random random = new SecureRandom();
    private static final Timer evictionTimer = new Timer("DOIP Eviction Timer", true);
    
    private final String baseUri;
    private final CloseableHttpClient httpClient;
    private final String repoHandle;
    
    public RegistrarDoipOperations(String baseUri, String repoHandle, CloseableHttpClient httpClient) {
        this.baseUri = trimTrailingSlash(baseUri);
        this.repoHandle = repoHandle;
        this.httpClient = httpClient;
    }
    
    @Override
    public String[] listOperations(DOOperationContext context) {
        return operations.clone();
    }

    @Override
    public boolean canHandleOperation(DOOperationContext context) {
        return operationsList.contains(context.getOperationID().toLowerCase(Locale.ENGLISH));
    }

    @Override
    public void performOperation(DOOperationContext context, InputStream in, OutputStream out) {
        try {
            String operation = context.getOperationID();
            String objectId = context.getTargetObjectID();
            String caller = context.getCallerID();
            HeaderSet params = context.getOperationHeaders();
            if (GET_OBJECT_OP.equals(operation)) {
                sendOperation("GET", "objects/" + StringUtils.encodeURLPath(objectId), caller, params, in, out);
            } else if (UPDATE_OBJECT_OP.equals(operation)) {
                sendOperation("PUT", "objects/" + StringUtils.encodeURLPath(objectId), caller, params, in, out);
            } else if (DELETE_OBJECT_OP.equals(operation)) {
                sendOperation("DELETE", "objects/" + StringUtils.encodeURLPath(objectId), caller, params, in, out);
            } else if (objectId.equalsIgnoreCase(repoHandle)) {
                if (SEARCH_OBJECT_OP.equals(operation)) {
                    sendOperation("GET", "objects/", caller, params, in, out);
                } else if (CREATE_OBJECT_OP.equals(operation)) {
                    sendOperation("POST", "objects/", caller, params, in, out);
                } else {
                    sendErrorResponse(out, "Operation '"+operation+"' not implemented!", DOException.OPERATION_NOT_AVAILABLE);
                }
            } else {
                sendErrorResponse(out, "Operation '"+operation+"' not implemented!", DOException.OPERATION_NOT_AVAILABLE);
            }
        } catch (Exception e) {
            logger.error("Error in performOperation", e);
        } finally {
            try { in.close(); } catch (Exception e) {}
            try { out.close(); } catch (Exception e) {}
        }
    }
    
    private void sendOperation(String method, String path, String caller, HeaderSet params, InputStream in, OutputStream out) throws IOException {
        HttpRequestBase request;
        if ("GET".equals(method)) {
            request = new HttpGet(baseUri + "/" + path + encodeQueryString(params));
        } else if ("POST".equals(method)) {
            request = new HttpPost(baseUri + "/" + path + encodeQueryString(params));
        } else if ("PUT".equals(method)) {
            request = new HttpPut(baseUri + "/" + path + encodeQueryString(params));
        } else if ("DELETE".equals(method)) {
            request = new HttpDelete(baseUri + "/" + path + encodeQueryString(params));
        } else {
            throw new IllegalArgumentException("Unknown method " + method);
        }
        for (HeaderItem item : params) {
            if (isHeader(item.getName())) {
                request.setHeader(item.getName(), item.getValue());
            }
        }
        if (!request.containsHeader("Authorization")) {
            String key = setAuthenticatedUser(caller);
            request.setHeader("Authorization", "Doip " + key);
        }
        if (request instanceof HttpEntityEnclosingRequestBase) {
            HeaderSet response = new HeaderSet("response");
            response.addHeader("status", "pending");
            response.writeHeaders(out);
            out.flush();
            HttpEntity entity = new InputStreamEntity(in);
            ((HttpEntityEnclosingRequestBase)request).setEntity(entity);
        }
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = httpClient.execute(request);
            sendSuccessResponse(out);
            HeaderSet headersHeaderSet = new HeaderSet();
            headersHeaderSet.addHeader("Status-Code", response.getStatusLine().getStatusCode());
            HeaderIterator iter = response.headerIterator();
            while(iter.hasNext()) {
                Header header = iter.nextHeader();
                if ("Set-Cookie".equalsIgnoreCase(header.getName())) continue;
                if ("Expires".equalsIgnoreCase(header.getName()) && header.getValue().contains(" 1970 ")) continue;
                headersHeaderSet.addHeader(header.getName(), header.getValue());
            }
            headersHeaderSet.writeHeaders(out);
            entity = response.getEntity();
            InputStream responseEntityInputStream = entity.getContent();
            try {
                IOUtils.copy(responseEntityInputStream, out);
            } finally {
                responseEntityInputStream.close();
            }
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }

    }

    private String encodeQueryString(HeaderSet params) {
        StringBuilder sb = new StringBuilder();
        for (HeaderItem item : params) {
            if (!isHeader(item.getName())) {
                if (sb.length() == 0) sb.append("?");
                else sb.append("&");
                sb.append(StringUtils.encodeURLComponent(item.getName()));
                sb.append("=");
                sb.append(StringUtils.encodeURLComponent(item.getValue()));
            }
        }
        return sb.toString();
    }
    
    private boolean isHeader(String s) {
        if ("Authorization".equalsIgnoreCase(s)) {
            return true;
        } else if ("Content-Type".equalsIgnoreCase(s)) {
            return true;
        } else if ("Accept-Encoding".equalsIgnoreCase(s)) {
            return true;
        } else {
            return false;
        }
    }
    
    private void sendSuccessResponse(OutputStream out) throws IOException {
        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "success");
        response.writeHeaders(out);
        out.flush();
    }

    
    private void sendErrorResponse(OutputStream out, String msg, int code) throws IOException {
        HeaderSet response = new HeaderSet("response");
        response.addHeader("status", "error");
        if(msg!=null) {
            response.addHeader("message", msg);
        }
        if(code>=0) {
            response.addHeader("code", code);
        }
        response.writeHeaders(out);
        out.flush();
    }

    private static String trimTrailingSlash(String s) {
        if (s.endsWith("/")) {
            return s.substring(0, s.length() - 1);
        } else { 
            return s;
        }
    }

    public static String getAuthenticatedUser(String key) {
        String userId = authenticationMap.replace(key, "");
        if (userId == null) authenticationMap.remove(key);
        else if (userId.isEmpty()) return null;
        return userId;
    }
    
    public static String setAuthenticatedUser(String userId) {
        byte[] randomBytes = new byte[20];
        while (true) {
            random.nextBytes(randomBytes);
            final String key = Base64.encodeBase64URLSafeString(randomBytes);
            if (authenticationMap.putIfAbsent(key, userId) == null) {
                evictionTimer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        authenticationMap.remove(key);
                    }
                }, 120000);
                return key;
            }
        }
    }
}
