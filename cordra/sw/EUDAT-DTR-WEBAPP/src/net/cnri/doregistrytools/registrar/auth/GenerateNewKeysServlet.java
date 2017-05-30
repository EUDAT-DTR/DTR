package net.cnri.doregistrytools.registrar.auth;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import net.cnri.doregistrytools.registrar.jsonschema.HttpClientManager;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarService;
import net.cnri.doregistrytools.registrar.jsonschema.RegistrarServiceFactory;
import net.cnri.doregistrytools.registrar.jsonschema.ServletErrorUtil;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.handle.apps.batch.BatchUtil;
import net.handle.hdllib.Common;
import net.handle.hdllib.GsonUtility;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.Resolver;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;
import net.handle.hdllib.trust.HandleClaimsSet;
import net.handle.hdllib.trust.HandleSigner;
import net.handle.hdllib.trust.JsonWebSignature;
import net.handle.hdllib.trust.JsonWebSignatureFactory;
import net.handle.hdllib.trust.Permission;
import net.handle.hdllib.trust.TrustException;

@WebServlet({"/generateKeys/*"})
public class GenerateNewKeysServlet extends HttpServlet {
    private static Logger logger = LoggerFactory.getLogger(GenerateNewKeysServlet.class);

    private static RegistrarService registrar;
    
    @Override
    public void init() throws ServletException {
        super.init();
        try {
            registrar = RegistrarServiceFactory.getRegistrarService(getServletContext());
        } catch (Exception e) {
            throw new ServletException(e);
        } 
    }
    
    private Gson gson = new Gson();
    @Override
    public void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String genKeysRequestJson = streamToString(req.getInputStream(), req.getCharacterEncoding());
        try {
            GenerateKeysRequest genKeysRequest = gson.fromJson(genKeysRequestJson, GenerateKeysRequest.class);
            ServletContext context = getServletContext();
            net.cnri.apps.doserver.Main serverMain = (net.cnri.apps.doserver.Main) context.getAttribute("net.cnri.apps.doserver.Main");
            generateNewKeys(genKeysRequest, serverMain);
            PrintWriter w = resp.getWriter();
            w.write(gson.toJson(new SuccessResponse()));
            w.close();
        } catch (Exception e) {
            logger.error("Exception in PUT /uiConfig", e);
            ServletErrorUtil.internalServerError(resp);
        } 
    }
    
    public static class SuccessResponse {
        public boolean success = true;
        public String message = "New keys generated.";
    }
    
    
    /**
     * Creates a new key pair
     * Creates a JWS that contains the new publickey and signs it with the old private key
     * Sends the JWS cert to DOR such that DOR can update the public key. 
     */
    private synchronized void generateNewKeys(GenerateKeysRequest genKeysRequest, net.cnri.apps.doserver.Main serverMain) throws Exception {
        PublicKey currentPublicKey = serverMain.getPublicKey();
        PrivateKey currentPrivateKey = serverMain.getPrivateKey();
        
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        logger.info("Generating keys");
        kpg.initialize(2048);
        KeyPair keys = kpg.generateKeyPair();
        
        PrivateKey newPrivateKey = keys.getPrivate();
        byte[] privateKeyBytes = Util.getBytesFromPrivateKey(newPrivateKey);
        byte[] encprivateKeyBytes = Util.encrypt(privateKeyBytes, null, Common.ENCRYPT_NONE);
        
        PublicKey newPublicKey = keys.getPublic();
        byte[] publicKeyBytes = Util.getBytesFromPublicKey(newPublicKey);
        
        String prefix = registrar.getDesign().serverPrefix;
        String repoHandle = prefix +"/repo";
        
        String certString = generateCertificate(newPublicKey, currentPrivateKey, repoHandle);
        JsonWebSignature jws = JsonWebSignatureFactory.getInstance().deserialize(certString);
        
        ///////// Just testing client side
//        if (jws.validates(currentPublicKey)) {
//            System.out.println("the cert validates");
//        } else {
//            System.out.println("the cert does not validate");
//        }
//        
//        HandleClaimsSet claims = getHandleClaimsSet(jws);
//        PublicKey extractedNewPublicKey = claims.publicKey;
//        
//        if (newPublicKey.equals(extractedNewPublicKey)) {
//            System.out.println("keys match");
//        } else {
//            System.out.println("keys don't match");
//        }
        //////////////// End test
        
        sendPublicKeyToPrefixService(jws);
        File baseDir = serverMain.getBaseFolder();
        backupOldKeys(baseDir);
        saveKeysToDisk(publicKeyBytes, encprivateKeyBytes, baseDir);
    }
    
    private void backupOldKeys(File baseDir) throws IOException {
        long now = System.currentTimeMillis();
        File publicKeyFile = new File(baseDir, net.cnri.apps.doserver.Main.PUBLIC_KEY_FILE);
        File privateKeyFile = new File(baseDir, net.cnri.apps.doserver.Main.PRIVATE_KEY_FILE);
        File keyBackupDir = new File(baseDir, "/keysbackup-"+now+"/");
        keyBackupDir.mkdir();
        FileUtils.copyFileToDirectory(publicKeyFile, keyBackupDir);
        FileUtils.copyFileToDirectory(privateKeyFile, keyBackupDir);
        publicKeyFile.delete();
        privateKeyFile.delete();
    }
    
    private void saveKeysToDisk(byte[] publicKeyBytes, byte[] encprivateKeyBytes, File baseDir) throws IOException {
        File newPublicKeyFile = new File(baseDir, net.cnri.apps.doserver.Main.PUBLIC_KEY_FILE);
        File newPrivateKeyFile = new File(baseDir, net.cnri.apps.doserver.Main.PRIVATE_KEY_FILE);
        
        // Save the private key to the file
        FileOutputStream keyOut = new FileOutputStream(newPrivateKeyFile);
        keyOut.write(encprivateKeyBytes);
        keyOut.close();

        // Save the public key to the file
        keyOut = new FileOutputStream(newPublicKeyFile);
        keyOut.write(publicKeyBytes);
        keyOut.close();
    }
    
    public HandleClaimsSet getHandleClaimsSet(JsonWebSignature signature)  {
        HandleClaimsSet claims = null;
        try {
            String payload = signature.getPayloadAsString();
            claims = GsonUtility.getGson().fromJson(payload, HandleClaimsSet.class);
        } catch (Exception e) {
            return null;
        }
        return claims;
    }
    
    private void sendPublicKeyToPrefixService(JsonWebSignature jws) throws HandleException, InternalException, ClientProtocolException, IOException {
        HandleResolver resolver = new HandleResolver();
        HandleValue[] values = resolver.resolveHandle(RegistrarService.REPOSITORY_SERVICE_PREFIX_HANDLE);
        List<HandleValue> urls = BatchUtil.getValuesOfType(values, "URL");
        if (urls.size() == 0) {
            throw new InternalException("Repository service not defined in handle record.");
        }
        String baseServiceUri = urls.get(0).getDataAsString();
        String url = baseServiceUri + "updateKeys/";
        postToUrl(url, jws.serialize());
    }
    
    private String postToUrl(String url, String data) throws ClientProtocolException, IOException, InternalException {
        HttpClientManager httpClientManager = new HttpClientManager();
        CloseableHttpClient httpclient = httpClientManager.getClient();
        try {
            HttpPost httpPost = new HttpPost(url);
            addCredentials(httpPost, "admin", "password");
            if(data!=null) httpPost.setEntity(new StringEntity(data,"UTF-8"));
            HttpResponse response = httpclient.execute(httpPost);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != 200) {
                throw new InternalException("Could not update keys. Status code " + statusCode);
            }
            HttpEntity entity = response.getEntity();
            if(entity==null) return null;
            String responseString = EntityUtils.toString(entity);
            EntityUtils.consume(entity);
            return responseString;
        } finally {
            //httpclient.close();
            //httpclient.getConnectionManager().shutdown();
        }
    }
    
    private void addCredentials(HttpRequest request, String username, String password) {
        if (username == null) return;
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(username, password);
        try {
            request.addHeader(new BasicScheme().authenticate(creds, request, null));
        } catch (AuthenticationException e) {
            throw new AssertionError(e);
        }
    }
    
    private String generateCertificate(PublicKey publicKey, PrivateKey privateKey, String repoHandle) throws TrustException {
        long notBefore = System.currentTimeMillis()/1000L - 300;
        long expiration = System.currentTimeMillis()/1000L + 365L*86400; //1 year, this cert is only used one time to sign the public key the expiration does not matter
        List<Permission> permissions = Collections.singletonList(new Permission(null, Permission.EVERYTHING)); //The perms are not relevent here
        List<String> chain = null;
        ValueReference subject = ValueReference.fromString("300:"+repoHandle);
        ValueReference issuer = ValueReference.fromString("300:"+repoHandle);
        HandleSigner handleSigner = new HandleSigner();
        JsonWebSignature jws = handleSigner.signPermissions(subject, publicKey, permissions, issuer, privateKey, chain, notBefore, expiration);
        String certString = jws.serialize();
        return certString;
    }
    
    protected static String streamToString(InputStream input, String encoding) throws IOException{
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        byte buf[] = new byte[4096];
        int r;
        while((r = input.read(buf)) >= 0) {
            bout.write(buf, 0, r);
        }
        if(encoding == null) return Util.decodeString(bout.toByteArray());
        else {
            return new String(bout.toByteArray(), encoding);
        }
    }
    
    public static class GenerateKeysRequest {
        public String keyPassphrase;
    }
}
