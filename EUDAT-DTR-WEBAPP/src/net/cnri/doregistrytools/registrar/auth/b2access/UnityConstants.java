package net.cnri.doregistrytools.registrar.auth.b2access;

import java.io.File;
import net.cnri.apps.doserver.Main;
import net.cnri.util.StreamTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import io.jsonwebtoken.*;
import java.util.Date;
import java.util.StringJoiner;

public class UnityConstants {

    private static Logger logger = LoggerFactory.getLogger(UnityConstants.class);

    public static final String AUTHORIZATION_ENDPOINT = "authorization_endpoint";
    public static final String TOKEN_ENDPOINT = "token_endpoint";
    public static final String USERINFO_ENDPOINT = "userinfo_endpoint";
    public static final String CLIENT_ID = "client_id";
    public static final String CALLBACK_URI = "callback_uri";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String ALLOW_SELF_SIGNED_CERTIFICATE = "allow_self_signed_certificate";
    public static final String CLIENT_KEYSTORE = "client_keystore";
    public static final String CLIENT_KEYSTORE_PASSWORD = "client_keystore_password";

    public static String authorizationEndpoint = null;
    public static String tokenEndpoint = null;
    public static String userinfoEndpoint = null;
    public static String clientId = null;
    public static String callbackUri = null;
    public static String clientSecret = null;
    public static boolean allowSelfSignedCert = false;
    public static String clientKeystore = null;
    public static String clientKeystorePassword = null;

    public static final String remoteApp = "B2ACCESS";
    public static final String tokenIssuer = "dtr-pilot";
    public static final String tokenSubject = "unity-oauth2-request";
    public static final long tokenTTLSeconds = 300;

    public static void readUnityConfig() throws Exception {

        File baseFolder = Main.getRunningMain().getBaseFolder();

        File configFile = new File(baseFolder, "b2access.config.dct");

        StreamTable b2accessConfig = new StreamTable();

        try {
            if (configFile.exists()) {
                b2accessConfig.readFromFile(configFile);

                authorizationEndpoint = b2accessConfig.getStr(AUTHORIZATION_ENDPOINT);
                                        
                tokenEndpoint = b2accessConfig.getStr(TOKEN_ENDPOINT, null);
                userinfoEndpoint = b2accessConfig.getStr(USERINFO_ENDPOINT, null);
                clientId = b2accessConfig.getStr(CLIENT_ID, null);
                callbackUri = b2accessConfig.getStr(CALLBACK_URI, null);
                clientSecret = b2accessConfig.getStr(CLIENT_SECRET, null);
                allowSelfSignedCert = b2accessConfig.getBoolean(ALLOW_SELF_SIGNED_CERTIFICATE, false);
                clientKeystore = b2accessConfig.getStr(CLIENT_KEYSTORE, null);
                clientKeystorePassword = b2accessConfig.getStr(CLIENT_KEYSTORE_PASSWORD);

                // System.out.println("*********************************");
                // System.out.println("B2ACCESS Config:");
                // System.out.println("tokenEndpoint: " + tokenEndpoint);
                // System.out.println("userinfoEndpoint: " + userinfoEndpoint);
                // System.out.println("clientId: " + clientId);
                // System.out.println("callbackUri: " + callbackUri);
                // System.out.println("clientSecret: " + clientSecret);
                // System.out.println("allowSelfSignedCert: " + allowSelfSignedCert);
                // System.out.println("clientKeystore: " + clientKeystore);
                // System.out.println("clientKeystorePassword: " + clientKeystorePassword);
                // System.out.println("*********************************");

            } else {
                logger.error("b2access config file does not exist");
            }

        }
        catch(Exception e) {
            logger.error("Error loading b2access config file", e);
            throw e;
        }
    } 

    public static String createSecureToken() throws Exception {

        long nowMillis = System.currentTimeMillis();
        long expMillis = nowMillis + tokenTTLSeconds*1000;

        String secret = new StringJoiner(":")
            .add(remoteApp)
            .add(clientSecret)
            .toString();

        String token = Jwts.builder()
            .setIssuedAt(new Date(nowMillis))
            .setExpiration(new Date(expMillis))
            .setSubject(tokenSubject)
            .setIssuer(tokenIssuer)
            .signWith(
                SignatureAlgorithm.HS256,
                secret.getBytes("UTF-8")
            )
            .compact();

        return token;
    }

    public static boolean validateSecureToken(String token) {

        String secret = new StringJoiner(":")
            .add(remoteApp)
            .add(clientSecret)
            .toString();

        try {

            Jws<Claims> claims = Jwts.parser()
                .setSigningKey(secret.getBytes("UTF-8"))
                .parseClaimsJws(token);

            if (!claims.getBody().getSubject().equals(tokenSubject)) {
                System.out.println(claims.getBody().getSubject() + " vs " + tokenSubject);
                return false;
            }

            if (!claims.getBody().getIssuer().equals(tokenIssuer)) {
                System.out.println(claims.getBody().getIssuer() + " vs " + tokenIssuer);
                return false;
            }
        } catch (UnsupportedEncodingException e) {
            System.out.println(e);
            return false;
        } catch (RuntimeException e) {
            System.out.println(e);
            return false;
        }

        return true;
    }
}
