/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.android;

import java.lang.reflect.Constructor;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;

public class ExceptionSerializer {
    public static IllegalStateException serialize(Throwable t) {
        try {
            JSONObject json = new JSONObject();
            json.put("class",t.getClass().getName());
            json.put("message",t.getMessage());
            throw new IllegalStateException(json.toString(),t);
        }
        catch(JSONException e) {
            throw new IllegalStateException(t.getMessage());
        }
    }
    
    public static RepositoryException deserialize(String message) {
        try {
            JSONObject json = (JSONObject) new JSONTokener(message).nextValue();
            String className = json.has("class") ? json.getString("class") : null;
            String encodedMessage = json.has("message") ? json.getString("message") : null;
            if(className==null) {
                if(encodedMessage==null) encodedMessage = message;
                return new InternalException(encodedMessage);
            }
            Class<?> klass = Class.forName(className);
            Constructor<?> constructor = klass.getConstructor(String.class);
            Object res = constructor.newInstance(encodedMessage);
            if(res instanceof RepositoryException) return (RepositoryException)res;
            if(res instanceof Throwable) return new InternalException((Throwable)res);
            return new InternalException(message);
        }
        catch(Exception e) {
            return new InternalException(message);
        }
    }
    
}
