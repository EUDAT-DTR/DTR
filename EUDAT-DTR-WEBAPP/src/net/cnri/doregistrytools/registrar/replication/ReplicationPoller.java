/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.replication;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;

import net.cnri.doregistrytools.registrar.auth.AuthConfig;
import net.cnri.doregistrytools.registrar.jsonschema.InvalidException;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;

public class ReplicationPoller implements Runnable {
    private static Logger logger = LoggerFactory.getLogger(ReplicationPoller.class);
                    
    private final RegistrarService registrar;
    private final CloseableHttpClient httpClient;
    private final ScheduledExecutorService scheduler;
    private final Gson gson;
    private List<RemoteRepositoryInfo> remoteRepositories;
    
    public ReplicationPoller(RegistrarService registrar, List<RemoteRepositoryInfo> remoteRepositories, CloseableHttpClient httpClient) {
        this.registrar = registrar;
        this.remoteRepositories = remoteRepositories;
        this.httpClient = httpClient;
        gson = new Gson();
        scheduler = Executors.newScheduledThreadPool(1);
        scheduler.scheduleAtFixedRate(this, 0, 1, TimeUnit.MINUTES);
        
    }

    @Override
    public void run() {
        for (RemoteRepositoryInfo remoteRepository : remoteRepositories) {
            try {
                replicateFrom(remoteRepository);
            } catch (Exception e) {
                logger.error("Exception in replicateFrom", e);
            }
        }
        try {
            registrar.persistRemoteRepositoryInfos(remoteRepositories);
        } catch (RepositoryException e) {
            logger.error("Exception in persistRemoteRepositoryInfos", e);
        }
    }
    
    public void shutdown() {
        scheduler.shutdown();
    }
    
    public void updateRemoteRepositories(List<RemoteRepositoryInfo> remoteRepositoriesUpdate) {
        UpdateRemoteRepositoriesTask updateTask = new UpdateRemoteRepositoriesTask(remoteRepositoriesUpdate);
        scheduler.schedule(updateTask, 0, TimeUnit.SECONDS);
    }
    
    private void replicateFrom(RemoteRepositoryInfo remoteRepository) throws IOException, RepositoryException {
        getRemoteDesign(httpClient, remoteRepository);
        getRemoteSchemas(httpClient, remoteRepository);
        getRecentlyModifiedObjects(httpClient, remoteRepository);
    }

    public void getRemoteDesign(CloseableHttpClient httpClient, RemoteRepositoryInfo remoteRepository) throws IOException, RepositoryException {
        String baseUri = remoteRepository.baseUri;
        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() -1);
        }
        String url = baseUri + "/design";
        HttpGet request = new HttpGet(url);
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = httpClient.execute(request);
            entity = response.getEntity();
            InputStream in = entity.getContent();
            Reader reader = new InputStreamReader(in, "UTF-8");
            
            JsonObject designObject = new JsonParser().parse(reader).getAsJsonObject();
            AuthConfig authConfig = gson.fromJson(designObject.get("authConfig"), AuthConfig.class);
            registrar.persistRemoteRepositoryDesign(remoteRepository.baseUri, authConfig);
            try {
                registrar.loadPersistentMetadata();
            } catch (InvalidException e) {
                throw new InternalException(e);
            }
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }

    public void getRemoteSchemas(CloseableHttpClient httpClient, RemoteRepositoryInfo remoteRepository) throws IOException, RepositoryException {
        String baseUri = remoteRepository.baseUri;
        if (baseUri.endsWith("/")) {
            baseUri = baseUri.substring(0, baseUri.length() -1);
        }
        String url = baseUri + "/replicate/schemas";
        HttpGet request = new HttpGet(url);
        try {
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(remoteRepository.username, remoteRepository.password);
            request.addHeader(new BasicScheme(Charset.forName("UTF-8")).authenticate(creds, request, null));
        } catch (AuthenticationException e) {
            throw new AssertionError(e);
        }
        
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        try {
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                logger.error("Exception in getRemoteSchemas response code: " + statusCode);
                return;
            }
            entity = response.getEntity();
            InputStream in = entity.getContent();
            Reader reader = new InputStreamReader(in, "UTF-8");
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                registrar.addReplicatedObject(jsonReader, remoteRepository.baseUri);
            }
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }

    }

    private void getRecentlyModifiedObjects(CloseableHttpClient httpClient, RemoteRepositoryInfo remoteRepository) throws IOException, ClientProtocolException, UnsupportedEncodingException, RepositoryException {
        String url = remoteRepository.baseUri + "/replicate?since=" + remoteRepository.lastTimestamp;
        if (remoteRepository.includeTypes != null) {
            url += "&" + listToParamString(remoteRepository.includeTypes, "include");
        }
        if (remoteRepository.excludeTypes != null) {
            url += "&" + listToParamString(remoteRepository.excludeTypes, "exclude");
        }

        HttpGet request = new HttpGet(url);
        
//        Credentials basicCreds = new Credentials(remoteRepository.username, remoteRepository.password);
//        String basicAuthHeader = basicCreds.getAuthHeader(); 
//        request.addHeader("Authorization", basicAuthHeader);
        
        try {
            UsernamePasswordCredentials creds = new UsernamePasswordCredentials(remoteRepository.username, remoteRepository.password);
            request.addHeader(new BasicScheme(Charset.forName("UTF-8")).authenticate(creds, request, null));
        } catch (AuthenticationException e) {
            throw new AssertionError(e);
        }
        
        CloseableHttpResponse response = null;
        HttpEntity entity = null;
        long mostRecentModified = 0;
        try {
            response = httpClient.execute(request);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                logger.error("Exception in getRecentlyModifiedObjects response code: " + statusCode);
                return;
            }
            entity = response.getEntity();
            InputStream in = entity.getContent();
            Reader reader = new InputStreamReader(in, "UTF-8");
            JsonReader jsonReader = new JsonReader(reader);
            jsonReader.beginArray();
            while (jsonReader.hasNext()) {
                long currentModified = registrar.addReplicatedObject(jsonReader, remoteRepository.baseUri);
                if (currentModified > mostRecentModified) {
                    mostRecentModified = currentModified;
                }
            }
            if (mostRecentModified > 0) {
                if (System.currentTimeMillis() - mostRecentModified > 120000) mostRecentModified++; //TODO consider improving this
                remoteRepository.lastTimestamp = mostRecentModified;
            }
        } finally {
            if (entity!=null) EntityUtils.consumeQuietly(entity);
            if (response != null) try { response.close(); } catch (IOException e) { }
        }
    }
    
    public static String listToParamString(List<String> list, String paramName) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            sb.append(paramName).append("=");
            sb.append(list.get(i));
            if (i < list.size() -1) {
                sb.append("&");
            }
        }
        return sb.toString();
    }
    
    public class UpdateRemoteRepositoriesTask implements Runnable {

        private List<RemoteRepositoryInfo> remoteRepositoriesUpdate;
        
        public UpdateRemoteRepositoriesTask(List<RemoteRepositoryInfo> remoteRepositoriesUpdate) {
            this.remoteRepositoriesUpdate = remoteRepositoriesUpdate;
        }
        
        @Override
        public void run() {
            for (RemoteRepositoryInfo remoteRepositoryInfoUpdate : remoteRepositoriesUpdate) {
                String remoteRepositoryUri = remoteRepositoryInfoUpdate.baseUri;
                if (remoteRepositoryUri.endsWith("/")) {
                    remoteRepositoryUri = remoteRepositoryUri.substring(0, remoteRepositoryUri.length() -1);
                }
                RemoteRepositoryInfo existingRemoteRepositoryInfo = getExistingRemoteRepositoryInfo(remoteRepositoryUri);
                if (existingRemoteRepositoryInfo != null) {
                    remoteRepositoryInfoUpdate.lastTimestamp = existingRemoteRepositoryInfo.lastTimestamp;
                }
            }
            remoteRepositories = remoteRepositoriesUpdate;
            try {
                registrar.persistRemoteRepositoryInfos(remoteRepositories);
            } catch (RepositoryException e) {
                logger.error("Exception in persistRemoteRepositoryInfos", e);
            }
        }
        
        private RemoteRepositoryInfo getExistingRemoteRepositoryInfo(String remoteRepositoryUri) {
            for (RemoteRepositoryInfo remoteRepositoryInfo : remoteRepositories) {
                String uri = remoteRepositoryInfo.baseUri;
                if (remoteRepositoryUri.equals(uri)) { 
                    return remoteRepositoryInfo;
                }
            }
            return null;
        } 
    }
}
