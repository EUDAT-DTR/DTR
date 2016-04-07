/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ConcurrentCountingHashMap<T> {
    ConcurrentHashMap<T,AtomicInteger> map = new ConcurrentHashMap<T,AtomicInteger>();

    public int incrementAndGet(T key) {
        int res;
        AtomicInteger oldValue = map.get(key);
        if(oldValue!=null && (res=getAndIncrementIfPositive(oldValue))>0) return res+1;
        AtomicInteger newCount = new AtomicInteger(1);
        while(true) {
            oldValue = map.putIfAbsent(key, newCount);
            if(oldValue==null) return 1;
            if((res=getAndIncrementIfPositive(oldValue))>0) return res+1;
        }
    }
    
    public int decrementAndGet(T key) {
        AtomicInteger oldValue = map.get(key);
        if(oldValue==null) return 0;
        int endingCount = oldValue.decrementAndGet();
        if(endingCount<=0) {
            map.remove(key);
            return 0;
        }
        else return endingCount;
    }
    
    public int get(T key) {
        AtomicInteger value = map.get(key);
        if(value==null) return 0;
        else return value.get();
    }
    
    private static int getAndIncrementIfPositive(AtomicInteger ai) {
        while(true) {
            int current = ai.get();
            if(current<=0) return 0;
            if(current==Integer.MAX_VALUE) throw new IllegalStateException("Integer overflow");
            if(ai.compareAndSet(current, current+1)) return current;
        }
    }
    
    public int size() {
        return map.size();
    }
}
