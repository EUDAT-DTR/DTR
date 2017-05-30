/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import net.handle.hdllib.Util;

public class HandleMinter {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    public HandleMinter(String prefix) {
        this.prefix = prefix;
    }
    
    public String mint(String data) {
        return prefix + "/" + digest(data);
    }

    public String mintByTimestamp() {
        return prefix + "/" + digest("" + System.currentTimeMillis() + pad(counter.getAndIncrement()));
    }
    
    public String mintWithSuffix(String suffix) {
        return prefix + "/" + suffix;
    }
    
    String pad(int n) {
        n = n % 1000;
        if (n < 0) n = (n + 1000) % 1000;
        if (n < 10) return "00" + n;
        if (n < 100) return "0" + n;
        return "" + n;
    }
    
    String digest(String data) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-1").digest(Util.encodeString(data));
            bytes = Util.substring(bytes, 0, 10);
            return Util.decodeHexString(bytes, false).toLowerCase(Locale.ENGLISH);
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }
    
}
