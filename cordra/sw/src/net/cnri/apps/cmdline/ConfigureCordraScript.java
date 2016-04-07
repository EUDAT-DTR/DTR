package net.cnri.apps.cmdline;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;

import org.apache.commons.io.FileUtils;

import net.cnri.util.StreamTable;
import net.handle.hdllib.AbstractResponse;
import net.handle.hdllib.AdminRecord;
import net.handle.hdllib.AuthenticationInfo;
import net.handle.hdllib.Common;
import net.handle.hdllib.CreateHandleRequest;
import net.handle.hdllib.Encoder;
import net.handle.hdllib.HandleException;
import net.handle.hdllib.HandleResolver;
import net.handle.hdllib.HandleValue;
import net.handle.hdllib.PublicKeyAuthenticationInfo;
import net.handle.hdllib.Util;
import net.handle.hdllib.ValueReference;

public class ConfigureCordraScript {

    private static BufferedReader in  = new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws Exception {
    
        if (args.length == 0) {
            System.out.println("You need to specify the data directory of the server as an argument to the command");
            return;
        }
        
        File serverDir = new File(args[0]);
        
        System.out.println("Your Cordra instance will need to be identified by a handle, its 'repository handle'.");
        System.out.println("This handle needs to be a handle you control, that is, under a prefix allotted to you.");
        System.out.println("If you have prefix 12345, the typical repository handle would be 12345/repo");
        System.out.println();
        
        String repoHandle = responseToPrompt("Enter the repository handle you want to create");
        
        System.out.println();
        
        String handleAdminIdentity = responseToPrompt("Enter identity of your handle server administrator (e.g. 300:0.NA/12345)");

        String privateKeyPath = responseToPrompt("Enter the fully qualified path to the private key of your handle server administrator");
        String privateKeyPassphrase = responseToPrompt("Enter the passphrase of that private key, if needed (press return if no passphrase)");
        
        HandleResolver resolver = new HandleResolver();

        ValueReference handleAdminValRef = ValueReference.fromString(handleAdminIdentity);
        PrivateKey handleAdminPrivateKey = Util.getPrivateKeyFromFileWithPassphrase(new File(privateKeyPath), privateKeyPassphrase);
        
        AuthenticationInfo authInfo = new PublicKeyAuthenticationInfo(handleAdminValRef.handle, handleAdminValRef.index, handleAdminPrivateKey);
        
        //create key pair
        KeyPairBytes keyPairBytes = generateKeyPair();
        
        FileUtils.writeByteArrayToFile(new File(serverDir, "publickey"), keyPairBytes.publicKey);
        FileUtils.writeByteArrayToFile(new File(serverDir, "privatekey"), keyPairBytes.privateKey);
        
        System.out.println("Server keys created");
        
        String prefix = repoHandle.substring(0, repoHandle.indexOf('/'));
        String prefixHandle = "0.NA/" + prefix;
        
        registerRepoHandle(prefixHandle, repoHandle, keyPairBytes.publicKey, resolver, authInfo);

        System.out.println("Repository handle created");
        
        File configFile = new File(serverDir, "config.dct");
        StreamTable config = new StreamTable();
        config.readFromFile(configFile);
        config.put("serviceid", repoHandle);
        config.writeToFile(configFile);

        System.out.println("Repository config.dct edited");

        System.out.println();

        System.out.println("You will need to configure your handle server so that 300:" + repoHandle + " is an authorized handle administrator.");
        System.out.println("You can edit the handle server's config.dct and add 300:" + repoHandle + " to the \"server_admins\" list.");
        System.out.println("Also add \"server_admin_full_access\" = \"yes\" to the \"server_config\" section.");
        
        System.out.println();

        System.out.println("Finally in order for handles to be registered by Cordra, you will need to visit the Cordra admin interface");
        System.out.println("and set the Base URI in the Handle Records section.");
    }
    
    /******************************************************************************
     * Output a newline, prompt the user, get and return a trimmed response.
     */
    private static final String responseToPrompt(String prompt) throws IOException {
      System.out.print("\n" + prompt + ": ");
      System.out.flush();
      return in.readLine().trim();
    }
    
    private static void registerRepoHandle(String prefixHandle, String repoHandle, byte[] publicKeyBytes, HandleResolver resolver, AuthenticationInfo authInfo) throws HandleException {
        HandleValue[] values = new HandleValue[4];
        values[0] = new HandleValue();
        values[0].setIndex(100);
        values[0].setType(Common.ADMIN_TYPE);
        values[0].setData(Encoder.encodeAdminRecord(new AdminRecord(Util.encodeString(prefixHandle), 200, 
                        true, // addHandle
                        true, // deleteHandle
                        true, // addNA
                        true, // deleteNA
                        true, // readValue
                        true, // modifyValue
                        true, // removeValue
                        true, // addValue
                        true, // modifyAdmin
                        true, // removeAdmin
                        true, // addAdmin
                        true  // listHandles
                        )));
        values[1] = new HandleValue(300, "HS_PUBKEY", publicKeyBytes);
        values[2] = new HandleValue(1, "CNRI.OBJECT_SERVER", repoHandle);
        values[3] = new HandleValue(2, "CNRI.OBJECT_SERVER_INFO", "<serverinfo><server><id>1</id><hostaddress>127.0.0.1</hostaddress><port>9900</port></server></serverinfo>");
        CreateHandleRequest creReq = new CreateHandleRequest(Util.encodeString(repoHandle), values, authInfo);
        creReq.overwriteWhenExists = true;
        AbstractResponse resp = resolver.processRequest(creReq);
        if (resp.responseCode != AbstractResponse.RC_SUCCESS) throw HandleException.ofResponse(resp);
    }
    
    private static KeyPairBytes generateKeyPair() {
        KeyPairGenerator keyPairGen;
        try {
            keyPairGen = KeyPairGenerator.getInstance("RSA");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        keyPairGen.initialize(2048);
        KeyPair keyPair = keyPairGen.generateKeyPair();
        byte[] publicKey, privateKey;
        try {
            publicKey = Util.getBytesFromPublicKey(keyPair.getPublic());
            privateKey = Util.encrypt(Util.getBytesFromPrivateKey(keyPair.getPrivate()), null, Common.ENCRYPT_NONE);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
        return new KeyPairBytes(publicKey, privateKey);
    }
    
    private static class KeyPairBytes {
        public byte[] publicKey;
        public byte[] privateKey;

        public KeyPairBytes() {
        }

        public KeyPairBytes(byte[] publicKey, byte[] privateKey) {
            super();
            this.publicKey = publicKey;
            this.privateKey = privateKey;
        }
    }

}
