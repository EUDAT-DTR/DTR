/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.cordra.api;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public interface SearchResults<T> extends Iterable<T>, AutoCloseable {
    int size();
    Iterator<T> iterator(); 
    void close();
    
    default Spliterator<T> spliterator() {
        int characteristics = Spliterator.IMMUTABLE | Spliterator.NONNULL | Spliterator.ORDERED;
        if (size() >= 0) {
            return Spliterators.spliterator(iterator(), size(), characteristics);
        } else {
            return Spliterators.spliteratorUnknownSize(iterator(), characteristics);
        }
    }
    default Stream<T> stream() {
        // TODO deal with stream.close
        return StreamSupport.stream(spliterator(), false);
    }
    default Stream<T> parallelStream() {
        return StreamSupport.stream(spliterator(), true);
    }
}
