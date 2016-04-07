/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.layered;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;

import net.cnri.repository.AbstractRepository;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.DirectRepository;
import net.cnri.repository.InternalException;
import net.cnri.repository.NoSuchDigitalObjectException;
import net.cnri.repository.Repositories;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.search.Query;
import net.cnri.repository.util.AbstractCloseableIterator;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.repository.wrapper.DirectRepositoryWrapper;

/**
 * 
 * The LayeredRepository is composed of two other repositories, a top and a bottom. When data is written to the LayeredRepository
 * the writes first happen to the top and then are written to the bottom in a low priority background thread.  
 * 
 * This is useful if you have a slow persistent storage repository such as the EmbededRepository and want to have faster access to it. 
 * In such an example you can create a LayeredRepository with a MemoryRepository as the top and an EmbededRepository as the bottom.
 *
 */
public class LayeredRepository extends AbstractRepository implements DirectRepository {
    final Repository top;
    final DirectRepository directTop;
    Repository bottom;
    DirectRepository directBottom;
    final Map<String,Boolean> objects = new ConcurrentHashMap<String,Boolean>();
    final Map<String,Boolean> topHandles = new ConcurrentHashMap<String,Boolean>();

    private final ConcurrentHashMap<String,CountDownLatch> liftingHandles = new ConcurrentHashMap<String,CountDownLatch>();

    private static final ThreadFactory lowPriorityThreadFactory = new ThreadFactory() {
    	public Thread newThread(Runnable r) {
        	Thread result = new Thread(r);
        	result.setPriority(3);
        	return result;
        }
    };

    private final ExecutorService execServ;

    public LayeredRepository(Repository top,Repository bottom) throws RepositoryException {
        this(top,bottom,true);
    }
    public LayeredRepository(Repository top,Repository bottom,boolean writesReachBottom) throws RepositoryException {
        this.top = top;
        this.directTop = top instanceof DirectRepository ? (DirectRepository)top : new DirectRepositoryWrapper(top);
        this.bottom = bottom;
        if(bottom!=null) {
            this.directBottom = bottom instanceof DirectRepository ? (DirectRepository)bottom : new DirectRepositoryWrapper(bottom);
        }
        if(writesReachBottom) execServ = Executors.newSingleThreadExecutor(lowPriorityThreadFactory);
        else execServ = null;
        if(bottom!=null) getSetOfExistingObjects();
    }
    
    // for use by MigrationRepository
    void setBottom(Repository bottom) throws RepositoryException {
        if(bottom==null) throw new IllegalStateException();
        this.bottom = bottom;
        this.directBottom = bottom instanceof DirectRepository ? (DirectRepository)bottom : new DirectRepositoryWrapper(bottom);
        getSetOfExistingObjects();
    }
    
    private void getSetOfExistingObjects() throws RepositoryException {
    	CloseableIterator<String> iter = null;
    	try {
	    	iter = bottom.listHandles();
	    	while (iter.hasNext()) {
	    		objects.put(iter.next(),Boolean.TRUE);
	    	}
    	} catch(UncheckedRepositoryException e) {
    	    e.throwCause();
    	} finally {
    		if (iter != null) iter.close();
    	}
        try {
            iter = top.listHandles();
            while (iter.hasNext()) {
                String handle = iter.next();
                objects.put(handle,Boolean.TRUE);
                topHandles.put(handle,Boolean.TRUE);
            }
        } catch(UncheckedRepositoryException e) {
            e.throwCause();
        } finally {
            if (iter != null) iter.close();
        }
    }
    
    public Repository getTop() {
        return top;
    }
    
    public Repository getBottom() {
        return bottom;
    }
    
    void liftToTop(LayeredDigitalObject dobj) throws RepositoryException {
        String handle = dobj.getHandle();
        if(topHandles.containsKey(handle)) {
            dobj.setTop(top.getDigitalObject(handle));
            return;
        }
        CountDownLatch newLatch = new CountDownLatch(1);
        CountDownLatch oldLatch = liftingHandles.putIfAbsent(handle,newLatch);
        if(oldLatch!=null) {
            try {
                oldLatch.await();
            }
            catch(InterruptedException e) {
                throw new InternalException(e);
            }
            if(topHandles.containsKey(handle)) {
                dobj.setTop(top.getDigitalObject(handle));
                return;
            }
            else {
                throw new InternalException("Unexpected status lifting in layered repo");
            }
        }
        try {
            DigitalObject newTop;
            if(execServ!=null) {
                newTop = top.createDigitalObject(handle);
                try {
                    copyForLayeredRepo(dobj.getBottom(),newTop);
                }
                catch(IOException e) {
                    throw new InternalException(e);
                }
            }
            else {
                try {
                    newTop = Repositories.copy(dobj.getBottom(),top,null,true);
                }
                catch(IOException e) {
                    throw new InternalException(e);
                }
            }
            dobj.setTop(newTop);
            topHandles.put(handle,Boolean.TRUE);
        }
        finally {
            newLatch.countDown();
            liftingHandles.remove(handle);
        }
    }
    
    public void lift(String handle) throws RepositoryException {
        if(topHandles.containsKey(handle)) return;
        LayeredDigitalObject dobj = getDigitalObject(handle);
        if(dobj==null) throw new NoSuchDigitalObjectException(handle);
        liftToTop(dobj);
    }
    
    @Override
    public boolean verifyDigitalObject(String handle) throws RepositoryException {
    	return objects.containsKey(handle);
    }
    
    @Override
    public LayeredDigitalObject createDigitalObject(final String handle) throws CreationException, RepositoryException {
        if (handle!=null && verifyDigitalObject(handle)) {
    		throw new CreationException();
    	} else {
            DigitalObject topObj = top.createDigitalObject(handle);
            final String actualHandle = topObj.getHandle();
            objects.put(actualHandle,Boolean.TRUE);
            topHandles.put(actualHandle,Boolean.TRUE);
            final LayeredDigitalObject res = new LayeredDigitalObject(this, actualHandle, topObj, null, execServ);
            if(execServ!=null) execServ.submit(new Callable<Void>() {
                @Override
                public Void call() throws RepositoryException, CreationException {
                    DigitalObject bottomObj = bottom.createDigitalObject(actualHandle);
                    res.setBottom(bottomObj);
                    return null;
                }
            });
            return res;
        }
    }

    @Override
    public LayeredDigitalObject getDigitalObject(final String handle) throws RepositoryException {
        if (!verifyDigitalObject(handle)) {
            return null;
        }
        if(topHandles.containsKey(handle)) {
            DigitalObject topObj = top.getDigitalObject(handle);
            final LayeredDigitalObject res = new LayeredDigitalObject(this, handle, topObj, null, execServ);
            if(execServ!=null) execServ.submit(new Callable<Void>() {
                @Override
                public Void call() throws RepositoryException, CreationException {
                	res.setBottom(bottom.getDigitalObject(handle));
                    return null;
                }
            });
            return res;
        }
        else {
            DigitalObject bottomObj = bottom.getDigitalObject(handle);
            if(bottomObj!=null) {
                LayeredDigitalObject res = new LayeredDigitalObject(this, handle, null, bottomObj, execServ);
                return res;
            } else {
                return null;
            }
        }
    }

    @Override
    public void deleteDigitalObject(final String handle) throws RepositoryException{
        topHandles.remove(handle);
        objects.remove(handle);
        try {
            top.deleteDigitalObject(handle);
        }
        catch(NoSuchDigitalObjectException e) {} 
        if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException, CreationException {
                try {
                    bottom.deleteDigitalObject(handle);
                }
                catch(NoSuchDigitalObjectException e) {} 
                return null;
            }
        });
    }  

    @Override
    public CloseableIterator<String> listHandles() {
        return new CloseableIteratorFromIterator<String>(objects.keySet().iterator());

    }

    @Override
    public CloseableIterator<DigitalObject> search(final Query query) throws RepositoryException {
        return new CloseableIteratorFromIterator<DigitalObject>(new TopBottomIterator<DigitalObject>(new SearchSpec<DigitalObject>() {
            @Override
            CloseableIterator<DigitalObject> search(Repository repo) throws RepositoryException {
                return repo.search(query);
            }
            @Override
            CloseableIterator<DigitalObject> searchExcluding(Repository repo, Collection<String> handles) throws RepositoryException {
                if(repo instanceof SupportsSearchExcluding) return ((SupportsSearchExcluding)repo).searchExcluding(query,handles);
                else return super.searchExcluding(repo,handles);
            }
        }));
    }

    @Override
    public CloseableIterator<String> searchHandles(final Query query) throws RepositoryException {
        return new CloseableIteratorFromIterator<String>(new TopBottomIterator<String>(new SearchSpec<String>() {
            @Override
            CloseableIterator<String> search(Repository repo) throws RepositoryException {
                return repo.searchHandles(query);
            }
            @Override
            CloseableIterator<String> searchExcluding(Repository repo, Collection<String> handles) throws RepositoryException {
                if(repo instanceof SupportsSearchExcluding) return ((SupportsSearchExcluding)repo).searchHandlesExcluding(query,handles);
                else return super.searchExcluding(repo,handles);
            }
        }));
    }

    private static abstract class SearchSpec<T> {
        abstract CloseableIterator<T> search(Repository repo) throws RepositoryException;
        CloseableIterator<T> searchExcluding(Repository repo, final Collection<String> handles) throws RepositoryException {
            final CloseableIterator<T> iter = search(repo);
        	return new AbstractCloseableIterator<T>() {
        	    @Override
        	    protected T computeNext() {
        	        T next;
        	        do {
        	            if(!iter.hasNext()) return null;
        	            next = iter.next();
        	        } while(handles.contains(toHandle(next)));        	        
        	        return next;
        	    }
        	    @Override
        	    protected void closeOnlyOnce() {
        	        iter.close();
        	    }
        	};
        } 
    }
    
    private class TopBottomIterator<T> extends AbstractCloseableIterator<T> {
        private final SearchSpec<T> searchSpec;
        private final CloseableIterator<T> topIter;
        private /*lazy*/ CloseableIterator<T> bottomIter;

        public TopBottomIterator(SearchSpec<T> searchSpec) throws RepositoryException {
            this.searchSpec = searchSpec;
            this.topIter = searchSpec.search(top);
        }

        @Override
        protected T computeNext() {
            try {
                while(topIter.hasNext()) {
                    T topIterItem = topIter.next();
                    if(topHandles.containsKey(toHandle(topIterItem))) {
                        topIterItem = fixUpDigitalObjectFromTop(topIterItem);
                        return topIterItem;
                    }
                }
                if(bottom==null) return null;
                if(bottomIter==null) {
                    bottomIter = searchSpec.searchExcluding(bottom,topHandles.keySet());
                }
                while(bottomIter.hasNext()) {
                    T bottomIterItem = bottomIter.next();   
                    String handle = toHandle(bottomIterItem);
                    if(objects.containsKey(handle) && !topHandles.containsKey(handle)) {
                        bottomIterItem = fixUpDigitalObjectFromBottom(bottomIterItem);
                        return bottomIterItem;
                    }
                }
                return null;
            }
            catch(RepositoryException e) {
                throw new UncheckedRepositoryException(e);
            }
        }
        
        private T fixUpDigitalObjectFromTop(T topIterItem) {
            if(topIterItem instanceof DigitalObject) {
                DigitalObject topObj = (DigitalObject)topIterItem;
                final String handle = topObj.getHandle();
                final LayeredDigitalObject res = new LayeredDigitalObject(LayeredRepository.this, handle, topObj, null, execServ);
                if(execServ!=null) execServ.submit(new Callable<Void>() {
                    @Override
                    public Void call() throws RepositoryException, CreationException {
                    	res.setBottom(bottom.getDigitalObject(handle));
                        return null;
                    }
                });                
                @SuppressWarnings("unchecked")
                T typedRes = (T)res;
                topIterItem = typedRes;
            }
            return topIterItem;
        }

        private T fixUpDigitalObjectFromBottom(T bottomIterItem) throws RepositoryException {
            if(bottomIterItem instanceof DigitalObject) {
                DigitalObject bottomObj = (DigitalObject)bottomIterItem;
                String handle = bottomObj.getHandle();
                LayeredDigitalObject res = new LayeredDigitalObject(LayeredRepository.this, handle, null, bottomObj, execServ);
                @SuppressWarnings("unchecked")
                T typedRes = (T)res;
                bottomIterItem = typedRes;
            }
            return bottomIterItem;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        protected void closeOnlyOnce() {
            topIter.close();
            if(bottomIter!=null) bottomIter.close();
        }
    }

    @SuppressWarnings("unchecked")
    private static String toHandle(Object item) {
        if(item instanceof String) return (String)item;
        else if(item instanceof DigitalObject) return ((DigitalObject)item).getHandle();
        else return ((Map<String,String>)item).get("objectid");
    }

    @Override
    public void close() {
        top.close();
        if(execServ!=null) {
            execServ.execute(new Runnable(){
                @Override
                public void run() {
                    bottom.close();
                }    
            });
            execServ.shutdown();
        }
        else {
            if(bottom!=null) bottom.close();
        }
//        try {
//            execServ.awaitTermination(Long.MAX_VALUE,TimeUnit.NANOSECONDS);
//        }
//        catch(InterruptedException e) {
//            Thread.currentThread().interrupt();
//        }
    }
    
    public static void copyForLayeredRepo(DigitalObject source, DigitalObject target) throws RepositoryException, IOException {
        if(source instanceof SupportsFastCopyForLayeredRepo) {
            ((SupportsFastCopyForLayeredRepo)source).copyTo(target);
        }
        else {
            target.setAttributes(source.getAttributes());
            CloseableIterator<DataElement> els = source.listDataElements();
            try {
                while(els.hasNext()) {
                    DataElement el = els.next();
                    DataElement newEl = target.createDataElement(el.getName());
                    newEl.setAttributes(el.getAttributes());
                    copyDataOrSetAttributeForMissing(el,newEl);
                }
            }
            catch(UncheckedRepositoryException e) {
                e.throwCause();
            }
            finally {
                els.close();
            }
        }
    }
    
    public static void copyDataOrSetAttributeForMissing(@SuppressWarnings("unused") DataElement source, DataElement target) throws RepositoryException, IOException {
//        if(source.getSize()<=8192) target.write(source.read());
//        else target.setAttribute(LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING,"1");
        target.setAttribute(LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING,"1");
    }
    
	@Override
	public Map<String, String> getAttributes(String handle, String elementName) throws RepositoryException {
		if(topHandles.containsKey(handle)) {
			return directTop.getAttributes(handle, elementName);
		} else {
			return directBottom.getAttributes(handle, elementName);
		}
	}
	
	@Override
	public CloseableIterator<Entry<String, String>> listAttributes(String handle, String elementName) throws RepositoryException {
		if(topHandles.containsKey(handle)) {
			return directTop.listAttributes(handle, elementName);
		} else {
			return directBottom.listAttributes(handle, elementName);
		}
	}
	
	@Override
	public String getAttribute(String handle, String elementName, String name) throws RepositoryException {
		if(topHandles.containsKey(handle)) {
			return directTop.getAttribute(handle, elementName, name);
		} else {
			return directBottom.getAttribute(handle, elementName, name);
		}
	}
	
	@Override
	public void setAttributes(final String handle, final String elementName, final Map<String, String> attributes) throws RepositoryException {
		lift(handle);
		directTop.setAttributes(handle, elementName, attributes);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	directBottom.setAttributes(handle, elementName, attributes);
                return null;
            }
        });
	}
	
	@Override
	public void setAttribute(final String handle, final String elementName, final String name, final String value) throws RepositoryException {
		lift(handle);
		directTop.setAttribute(handle, elementName, name, value);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	directBottom.setAttribute(handle, elementName, name, value);
                return null;
            }
        });
	}
	
	@Override
	public void deleteAttributes(final String handle, final String elementName, final List<String> names) throws RepositoryException {
		lift(handle);
		directTop.deleteAttributes(handle, elementName, names);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	directBottom.deleteAttributes(handle, elementName, names);
                return null;
            }
        });
	}
	
	@Override
	public void deleteAttribute(final String handle, final String elementName, final String name) throws RepositoryException {
		lift(handle);
		directTop.deleteAttribute(handle, elementName, name);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	directBottom.deleteAttribute(handle, elementName, name);
                return null;
            }
        });
	}
	
	@Override
	public boolean verifyDataElement(String handle, String name) throws RepositoryException {
		if(topHandles.containsKey(handle)) {
			return directTop.verifyDataElement(handle, name);
		} else {
			return directBottom.verifyDataElement(handle, name);
		}
	}
	
	@Override
	public void createDataElement(final String handle, final String name) throws CreationException, RepositoryException {
		lift(handle);
		directTop.createDataElement(handle, name);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	directBottom.createDataElement(handle, name);
                return null;
            }
        });
	}
	
	@Override
	public void deleteDataElement(final String handle, final String name) throws RepositoryException {
		lift(handle);
		directTop.deleteDataElement(handle, name);
		if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	directBottom.deleteDataElement(handle, name);
                return null;
            }
        });
	}
	
	@Override
	public CloseableIterator<String> listDataElementNames(String handle) throws RepositoryException {
		if(topHandles.containsKey(handle)) {
			return directTop.listDataElementNames(handle);
		} else {
			return directBottom.listDataElementNames(handle);
		}
	}
	
	@Override
	public InputStream read(final String handle, final String elementName) throws RepositoryException {
        if(!topHandles.containsKey(handle)) return directBottom.read(handle, elementName);
        else if(!"1".equals(directTop.getAttribute(handle, elementName, LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING))) return directTop.read(handle, elementName);
        else {
            final int outerPriority = Thread.currentThread().getPriority();
            Future<InputStream> result = execServ.submit(new Callable<InputStream>() {
                @Override
                public InputStream call() throws RepositoryException, IOException {
                    int priority = Thread.currentThread().getPriority();
                    Thread.currentThread().setPriority(outerPriority);
                    try {
                        if(directBottom.getSize(handle, elementName)<=8192) {
                        	directTop.write(handle, elementName, new BufferedInputStream(directBottom.read(handle, elementName),8192),false);
                            directTop.deleteAttribute(handle, elementName, LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING);
                            return directTop.read(handle, elementName);
                        }
                        else return directBottom.read(handle, elementName);
                    }
                    finally {
                        Thread.currentThread().setPriority(priority);
                    }
                }
            });
            try {
                return result.get();
            }
            catch(ExecutionException e) {
                throw new InternalException(e);
            }
            catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
	}
	
	@Override
	public long write(final String handle, final String elementName, final InputStream data, final boolean append) throws IOException, RepositoryException {
        if(!topHandles.containsKey(handle)) lift(handle);
        if(execServ==null) {
            return directTop.write(handle, elementName, data,append);
        }
        
        final boolean attSet = "1".equals(directTop.getAttribute(handle, elementName, LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING));
        boolean sendToBottom = (attSet && append) || (append && directTop.getSize(handle, elementName) >= 8192);
        int off = 0;
        final byte[] buf = new byte[8192];
        int len = 8192 - (append?(int)directTop.getSize(handle, elementName):0);
        int r;
        while((r = data.read(buf,off,len)) > 0) {
            off += r;
            len -= r;
            if(len==0) break;
        }
        final boolean moreToRead = r>=0;
        if(sendToBottom || moreToRead) {
            final int finalOff = off;
            final int outerPriority = Thread.currentThread().getPriority();
            Future<Long> result = execServ.submit(new Callable<Long>() {
                @Override
                public Long call() throws RepositoryException {
                    int priority = Thread.currentThread().getPriority();
                    Thread.currentThread().setPriority(outerPriority);
                    try {
                        if(directTop.getSize(handle, elementName) > 0 && append) directBottom.write(handle, elementName, directTop.read(handle, elementName), false);
                        if(!attSet) directTop.setAttribute(handle, elementName, LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING,"1");
                        if(directTop.getSize(handle, elementName) > 0) directTop.write(handle, elementName, new ByteArrayInputStream(new byte[0]),false);
                        long res = directBottom.write(handle, elementName, new ByteArrayInputStream(buf,0,finalOff),append);
                        if(moreToRead) res += directBottom.write(handle, elementName, data, true);
                        return Long.valueOf(res);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return Long.valueOf(0);
                    }
                    finally {
                        Thread.currentThread().setPriority(priority);
                    }
                }
            });
            if(moreToRead) {
                try {
                    return result.get().longValue();
                }
                catch(ExecutionException e) {
                    throw new InternalException(e);
                }
                catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return 0;
                }
            }
            else {
                return off;
            }
        }
        else {
            long res = directTop.write(handle, elementName, new ByteArrayInputStream(buf,0,off),append);
            directTop.deleteAttribute(handle, elementName, LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING);
            // TODO potential inefficiencies
            execServ.submit(new Callable<Void>() {
                @Override
                public Void call() throws RepositoryException {
                    try {
                    	directBottom.write(handle, elementName, directTop.read(handle, elementName), false);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return null;
                }
            });
            return res;
        }
	}
	
	@Override
	public long getSize(final String handle, final String elementName) throws RepositoryException {
        if(!topHandles.containsKey(handle)) return directBottom.getSize(handle, elementName);
        if(execServ==null) {
            return directTop.getSize(handle, elementName);
        }
        if(!"1".equals(directTop.getAttribute(handle, elementName, LayeredDataElement.ATTRIBUTE_DATA_ELEMENT_MISSING))) return directTop.getSize(handle, elementName);
        else {
            final int outerPriority = Thread.currentThread().getPriority();
            Future<Long> result = execServ.submit(new Callable<Long>() {
                @Override
                public Long call() throws RepositoryException {
                    int priority = Thread.currentThread().getPriority();
                    Thread.currentThread().setPriority(outerPriority);
                    try {
                        return Long.valueOf(directBottom.getSize(handle, elementName));
                    }
                    finally {
                        Thread.currentThread().setPriority(priority);
                    }
                }
            });
            try {
                return result.get().longValue();
            }
            catch(ExecutionException e) {
            	throw new InternalException(e);
            }
            catch(InterruptedException e) {
                Thread.currentThread().interrupt();
                return 0;
            }
        }
	}
	
	@Override
	public File getFile(String handle, String elementName) throws RepositoryException {
		throw new UnsupportedOperationException();
	}
}
