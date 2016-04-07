/*************************************************************************\
    Copyright (c) 2010 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at 
                  http://hdl.handle.net/1895.26/1014
---------------------------------------------------------------------------
Developer: Sean Reilly <sreilly@cnri.reston.va.us>
\*************************************************************************/

package net.cnri.apps.cmdline;

import net.handle.hdllib.Common;
import net.handle.hdllib.Util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;

/** This class is run from the command-line to generate authentication keys */
public class KeyGenerator {
  
  public static final void printUsage() { 
    System.err.println("usage: do-keygen [-alg <algorithm>] [-keysize <numbits>] <privkeyfile> <pubkeyfile>");
  }
  
  public static void main(String argv[])
    throws Exception
  {
    String algorithm = "RSA";
    int keySize = -1;
    File privKeyFile = null;
    File pubKeyFile = null;
    
    for(int i=0; i<argv.length; i++) {
      if(argv[i].startsWith("-")) {
        if(argv[i].equalsIgnoreCase("-alg") && i+1<argv.length) {
          algorithm = argv[++i];
        } else if(argv[i].equalsIgnoreCase("-keysize") && i+1<argv.length) {
          try {
            keySize = Integer.parseInt(argv[++i]);
          } catch (Exception e) {
            printUsage();
            System.exit(1);
          }
        } else {
          printUsage();
          System.exit(1);
        }
      } else if(privKeyFile==null) {
        privKeyFile = new File(argv[i]);
      } else if(pubKeyFile==null) {
        pubKeyFile = new File(argv[i]);
      } else {
        printUsage();
        System.exit(1);
      }
    }
    
    if (keySize <= 0) {
        if ("RSA".equals(algorithm)) keySize = 2048;
        else keySize = 1024;
    }
    
    if(privKeyFile==null || pubKeyFile==null) {
      printUsage();
      System.exit(1);
    }

    PrintStream out = System.out;
    BufferedReader in = new BufferedReader(new InputStreamReader (System.in));
    
    boolean encrypt = true;
    out.println("\nThe private key that is about to be generated should be stored");
    out.println("in an encrypted form on your computer.  Encryption of the");
    out.println("private key requires that you choose a secret passphrase that");
    out.println("will need to be entered whenever the server is started.");
    out.println("Note: Please take all precautions to make sure that only authorized ");
    out.println("users can read your private key.");
    while(true) {
      out.print("\nWould you like to encrypt your private key? (y/n) [y] ");
      out.flush();
      String line = in.readLine().trim().toUpperCase();
      if(line.length()<=0) {
        encrypt = true;
        break;
      }
      if(line.equals("Y")) {
        encrypt = true;
        break;
      } else if(line.equals("N")) {
        encrypt = false;
        break;
      } else {
        System.out.println("Invalid response, try again.");
      }
        
    }

    byte secKey[] = null;
    if(encrypt) {
      while(true) {
        // read the passphrase and use it to encrypt the private key
        secKey = Util.getPassphrase("Please enter the private key passphrase:");
        
        byte secKey2[] = Util.getPassphrase("Please re-enter the private key passphrase: ");
        if(!Util.equals(secKey, secKey2)) {
          System.err.println("Passphrases do not match!  Try again.\n");
          continue;
        } else {
          break;
        }
      }
    }
    
    out.println("Generating keys; algorithm="+algorithm+"; keysize="+keySize);
    
    generateKeys(algorithm, keySize, secKey, privKeyFile, pubKeyFile);
  }
  
  public static final void generateKeys(String algorithm, int keySize, byte secKey[], File privKeyFile, File pubKeyFile) 
    throws Exception
  {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance(algorithm);
    kpg.initialize(keySize);
    KeyPair keys = kpg.generateKeyPair();

    // get the bytes that make up the private key
    byte keyBytes[] = Util.getBytesFromPrivateKey(keys.getPrivate());
    
    // encrypt the private key bytes
    byte encKeyBytes[] = null;
    if(secKey!=null) {
      encKeyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_PBKDF2_AES_CBC_PKCS5);
      for(int i=0; i<keyBytes.length; i++) keyBytes[i] = (byte)0;
      for(int i=0; i<secKey.length; i++) secKey[i] = (byte)0;
    } else {
      encKeyBytes = Util.encrypt(keyBytes, secKey, Common.ENCRYPT_NONE);
    }

    // save the private key to a file...
    FileOutputStream fout = new FileOutputStream(privKeyFile);
    fout.write(encKeyBytes);
    fout.close();

    // save the public key to a file
    fout = new FileOutputStream(pubKeyFile);
    fout.write(Util.getBytesFromPublicKey(keys.getPublic()));
    fout.close();
  }
  
}

