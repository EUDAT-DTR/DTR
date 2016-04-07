package net.cnri.cordra.api.examples;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.apache.log4j.BasicConfigurator;

import com.google.gson.Gson;
import com.google.gson.JsonParser;

import net.cnri.cordra.api.CordraClient;
import net.cnri.cordra.api.CordraException;
import net.cnri.cordra.api.CordraObject;
import net.cnri.cordra.api.HttpCordraClient;
import net.cnri.cordra.api.Payload;

public class Examples {

    public static void main(String[] args) throws CordraException, FileNotFoundException, UnsupportedEncodingException {
        String serverUrl = "http://localhost:8080/";
        String username = "ben";
        String password = "password";
        CordraClient client = new HttpCordraClient(serverUrl, username, password);
        
//        {
//            CordraObject d = client.get("20.5000.215/8aead1bcc8605f21061b");
//            d.addPayload("goo", "foo.txt", "text/plain", new ByteArrayInputStream("some txt".getBytes("UTF-8")));
//            d.deletePayload("foo");
//            d = client.update(d);
//            //if (true) return;
//        }
//        
//        {
//            String contentJson = "{\"name\":\"Jon Doe\", \"age\":59 }";
//            CordraObject d = new CordraObject();
//            d.type = "Person";
//            d.setContent(contentJson);
//            
//            d.addPayload("foo", "foo.txt", "text/plain", new ByteArrayInputStream("some txt".getBytes("UTF-8")));
//            d = client.create(d, username, password);
//            
//            System.out.println(d.id);
//            
//            d.deletePayload("foo");
//            d = client.update(d);
//            
//            
//        }
        
        
        ExecutorService exec = Executors.newFixedThreadPool(20);
        
        {
            Person p = new Person();
            p.name = "Francine";
            p.age = 42;
            
            for (int i = 0; i < 20000; i++) {
                int count = i;
                exec.submit(() -> {
//                    try {
                        CordraObject d = new CordraObject("Person", p);
                        d = client.create(d);
                    
                        System.out.println(count);
                        return null;
 //                   } catch (Exception e) {
 //                       e.printStackTrace();
 //                   }
                });
            }
            exec.shutdown();
        }
    }
    
    
    public static class Person {
        public String name;
        public int age;
    }
    

}
