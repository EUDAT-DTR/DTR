/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;

import org.apache.http.Consts;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.config.SocketConfig;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContexts;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class HttpClientManager {
    static class HttpClientLazyInit {
        static CloseableHttpClient httpClient = getNewHttpClient();
    }
    
    static CloseableHttpClient getNewHttpClient() {
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

    public CloseableHttpClient getClient() {
        return HttpClientLazyInit.httpClient;
    }
    
    public void destroy() {
        try {
            HttpClientLazyInit.httpClient.close();
        } catch (IOException e) {
            // ignored
        }
    }
}
