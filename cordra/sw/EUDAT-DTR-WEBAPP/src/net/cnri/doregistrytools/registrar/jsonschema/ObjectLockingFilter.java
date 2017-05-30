/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.doregistrytools.registrar.jsonschema;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class ObjectLockingFilter implements Filter {

    private ConcurrentHashMap<String, ReferenceCountedLock> locks;
    
    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) throws IOException, ServletException {
        if(req instanceof HttpServletRequest && resp instanceof HttpServletResponse) {
            doHttpFilter((HttpServletRequest)req, (HttpServletResponse)resp, chain);
        } else {
            chain.doFilter(req, resp);
        }
    }
    
    private void doHttpFilter(HttpServletRequest req, HttpServletResponse resp, FilterChain chain) throws IOException, ServletException {
        boolean isLocked = false;
        String objectId = getObjectId(req);
        if (objectId != null) {
            if (req.getMethod().equalsIgnoreCase("PUT") || req.getMethod().equalsIgnoreCase("DELETE") || req.getMethod().equalsIgnoreCase("POST")) {
                lock(objectId);
                isLocked = true;
            }
        }
        try {
            chain.doFilter(req, resp);
        } finally {
            if (isLocked) {
                unlock(objectId);             
            }
        }
    }
    
    private void lock(String objectId) {
        ReferenceCountedLock existingLock = locks.get(objectId);
        if (existingLock != null && getAndIncrementIfPositive(existingLock.count) > 0) {
            existingLock.lock.lock();
            return;
        }
        ReferenceCountedLock newLock = new ReferenceCountedLock();
        while (true) {
            existingLock = locks.putIfAbsent(objectId, newLock);
            if (existingLock == null) {
                newLock.lock.lock();
                return;
            }
            if (getAndIncrementIfPositive(existingLock.count) > 0) {
                existingLock.lock.lock();
                return;
            }
        }
    }
    
    private void unlock(String objectId) {
        ReferenceCountedLock existingLock = locks.get(objectId);
        if (existingLock.count.decrementAndGet() == 0) {
            locks.remove(objectId);
        }
        existingLock.lock.unlock();
    }
    
    private static int getAndIncrementIfPositive(AtomicInteger ai) {
        while(true) {
            int current = ai.get();
            if(current<=0) return 0;
            if(current==Integer.MAX_VALUE) throw new IllegalStateException("Integer overflow");
            if(ai.compareAndSet(current, current+1)) return current;
        }
    }
    
    private String getObjectId(HttpServletRequest req) {
        String objectId = req.getPathInfo();
        if (objectId != null && !objectId.isEmpty()) {
            objectId = objectId.substring(1);
            if (objectId.isEmpty()) return null;
            return objectId;
        }
        return null;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        locks = new ConcurrentHashMap<String, ReferenceCountedLock>();
    }
    
    @Override
    public void destroy() {
    }

    private static class ReferenceCountedLock {
        Lock lock;
        AtomicInteger count;
        
        public ReferenceCountedLock() {
            lock = new ReentrantLock();
            count = new AtomicInteger(1);
        }
    }
}
