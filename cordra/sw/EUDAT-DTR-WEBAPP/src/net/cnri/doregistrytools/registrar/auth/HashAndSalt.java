/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.util.Random;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public class HashAndSalt {

    private static final int HASH_ITERATION_COUNT = 2048;
    private static final int HASH_KEY_LENGTH = 256;
    
    private byte[] hash;
    private byte[] salt;
    
    public HashAndSalt(String password) {
        salt = new byte[16];
        Random random = new SecureRandom();
        random.nextBytes(salt);
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_ITERATION_COUNT, HASH_KEY_LENGTH);
        SecretKeyFactory f = null;
        try {
            f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        hash = null;
        try {
            hash = f.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new AssertionError(e);
        }
    }

    public HashAndSalt(String hashHex, String saltHex) {
        hash = hexStringToByteArray(hashHex);
        salt = hexStringToByteArray(saltHex);
    }
    
    public boolean verifyPassword(String password) {
        byte[] testHash = null;

        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, HASH_ITERATION_COUNT, HASH_KEY_LENGTH);
        SecretKeyFactory f = null;
        try {
            f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        try {
            testHash = f.generateSecret(spec).getEncoded();
        } catch (InvalidKeySpecException e) {
            throw new AssertionError(e);
        }

        if (Arrays.equals(hash, testHash)) {
            return true;
        } else {
            return false;
        }

    }

    private static String toHexString(byte[] bytes) {
        char[] hexArray = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
                'a', 'b', 'c', 'd', 'e', 'f' };
        char[] hexChars = new char[bytes.length * 2];
        int v;
        for (int j = 0; j < bytes.length; j++) {
            v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v / 16];
            hexChars[j * 2 + 1] = hexArray[v % 16];
        }
        return new String(hexChars);
    }
    
    private static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                                 + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }
    
    public String getHashString() {
        return toHexString(hash);
    }

    public String getSaltString() {
        return toHexString(salt);
    }
}
