/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.layered;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import net.cnri.repository.AbstractDataElement;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.DataElement;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.util.AbstractCloseableIterator;

public class LayeredDataElement extends AbstractDataElement implements DataElement{

    public static final String ATTRIBUTE_DATA_ELEMENT_MISSING = "internal.layeredDataElementMissing";
    private final LayeredRepository repo;
    private final LayeredDigitalObject dobj;
    private final String name;
    private DataElement top;
    private DataElement bottom;
    private final ExecutorService execServ;

    public LayeredDataElement(LayeredDigitalObject dobj, String name, DataElement top, DataElement bottom, ExecutorService execServ) {
        this.repo = dobj.getRepository();
        this.dobj = dobj;
        this.name = name;
        this.top = top;
        this.bottom = bottom;
        this.execServ = execServ;
    }

    void setBottom(DataElement bottom) {
        this.bottom = bottom;
    }

    void liftToTop() throws RepositoryException {
        repo.liftToTop(dobj);
        this.top = dobj.getTop().getDataElement(name);
    }
    
    @Override
    public DigitalObject getDigitalObject() {
        return dobj;
    }

    @Override
    public String getName() {
        return name;
    }
    
    @Override
    public Map<String, String> getAttributes() throws RepositoryException {
        if(top!=null) {
            Map<String,String> res = new HashMap<String,String>(top.getAttributes());
            res.remove(ATTRIBUTE_DATA_ELEMENT_MISSING);
            return res;
        }
        else {
            return bottom.getAttributes();
        }
    }

    @Override
    public CloseableIterator<Entry<String, String>> listAttributes() throws RepositoryException {
        if(top!=null) {
            return new AbstractCloseableIterator<Map.Entry<String,String>>() {
                CloseableIterator<Map.Entry<String,String>> iter = top.listAttributes();
                @Override
                protected Entry<String,String> computeNext() {
                    if(!iter.hasNext()) return null;
                    Entry<String,String> next = iter.next();
                    while(next.getKey().equals(ATTRIBUTE_DATA_ELEMENT_MISSING)) {
                        if(!iter.hasNext()) return null;
                        next = iter.next();
                    }
                    return next;
                }
                @Override
                protected void closeOnlyOnce() {
                    // TODO Auto-generated method stub
                    super.closeOnlyOnce();
                }
            };
        }
        else {
            return bottom.listAttributes();
        }
    }

    @Override
    public String getAttribute(String aname) throws RepositoryException {
        if(top!=null) return top.getAttribute(aname);
        else return bottom.getAttribute(aname);
    }

    @Override
    public void setAttributes(final Map<String, String> attributes) throws RepositoryException {
        if(top==null) liftToTop();
        top.setAttributes(attributes);
        if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.setAttributes(attributes);
                return null;
            }
        });
    }

    @Override
    public void setAttribute(final String name,final String value) throws RepositoryException {
        if(top==null) liftToTop();
        top.setAttribute(name, value);
        if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.setAttribute(name, value);
                return null;
            }
        });
    }

    @Override
    public void deleteAttributes(final List<String> names) throws RepositoryException{
        if(top==null) liftToTop();
        top.deleteAttributes(names);
        if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.deleteAttributes(names);
                return null;
            }
        });
    }

    @Override
    public void deleteAttribute(final String aname) throws RepositoryException {
        if(top==null) liftToTop();
        top.deleteAttribute(aname);
        if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.deleteAttribute(aname);
                return null;
            }
        });
    }

//    @Override
//    public Uri getUri() {
//        Future<Uri> result = execServ.submit(new Callable<Uri>() {
//            @Override
//            public Uri call() {
//                return bottom.getUri();
//            }
//        });
//        try {
//            return result.get();
//        }
//        catch(ExecutionException e) {
//            throw new DOException(DOException.INTERNAL_ERROR,e);
//        }
//        catch(InterruptedException e) {
//            Thread.currentThread().interrupt();
//            return null;
//        }
//    }

    @Override
    public String getType() throws RepositoryException {
        if(top!=null) return top.getType();
        else return bottom.getType();
    }

    @Override
    public void setType(final String type) throws RepositoryException {
        if(top==null) liftToTop();
        top.setType(type);
        if(execServ!=null) execServ.submit(new Callable<Void>() {
            @Override
            public Void call() throws RepositoryException {
            	bottom.setType(type);
                return null;
            }
        });
    }

    @Override
    public InputStream read() throws RepositoryException {
        if(top==null) return bottom.read();
        else if(!"1".equals(top.getAttribute(ATTRIBUTE_DATA_ELEMENT_MISSING))) return top.read();
        else {
            final int outerPriority = Thread.currentThread().getPriority();
            Future<InputStream> result = execServ.submit(new Callable<InputStream>() {
                @Override
                public InputStream call() throws RepositoryException, IOException {
                    int priority = Thread.currentThread().getPriority();
                    Thread.currentThread().setPriority(outerPriority);
                    try {
                        if(bottom.getSize()<=8192) {
                            top.write(new BufferedInputStream(bottom.read(),8192),false);
                            top.deleteAttribute(ATTRIBUTE_DATA_ELEMENT_MISSING);
                            return top.read();
                        }
                        else return bottom.read();
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
    public long write(final InputStream data, final boolean append) throws IOException, RepositoryException {
        if(top==null) liftToTop();
        if(execServ==null) {
            return top.write(data,append);
        }
        
        final boolean attSet = "1".equals(top.getAttribute(ATTRIBUTE_DATA_ELEMENT_MISSING));
        boolean sendToBottom = (attSet && append) || (append && top.getSize() >= 8192);
        int off = 0;
        final byte[] buf = new byte[8192];
        int len = 8192 - (append?(int)top.getSize():0);
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
                        if(top.getSize() > 0 && append) bottom.write(top.read(), false);
                        if(!attSet) top.setAttribute(ATTRIBUTE_DATA_ELEMENT_MISSING,"1");
                        if(top.getSize() > 0) top.write(new ByteArrayInputStream(new byte[0]),false);
                        long res = bottom.write(new ByteArrayInputStream(buf,0,finalOff),append);
                        if(moreToRead) res += bottom.write(data, true);
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
            long res = top.write(new ByteArrayInputStream(buf,0,off),append);
            top.deleteAttribute(ATTRIBUTE_DATA_ELEMENT_MISSING);
            // TODO potential inefficiencies
            execServ.submit(new Callable<Void>() {
                @Override
                public Void call() throws RepositoryException {
                    try {
                        bottom.write(top.read(), false);
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
    public long getSize() throws RepositoryException {
        if(top==null) return bottom.getSize();
        if(execServ==null) {
            return top.getSize();
        }
        if(!"1".equals(top.getAttribute(ATTRIBUTE_DATA_ELEMENT_MISSING))) return top.getSize();
        else {
            final int outerPriority = Thread.currentThread().getPriority();
            Future<Long> result = execServ.submit(new Callable<Long>() {
                @Override
                public Long call() throws RepositoryException {
                    int priority = Thread.currentThread().getPriority();
                    Thread.currentThread().setPriority(outerPriority);
                    try {
                        return Long.valueOf(bottom.getSize());
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
}
