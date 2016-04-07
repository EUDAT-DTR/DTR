/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.repository.filesystem;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import net.cnri.repository.AbstractRepository;
import net.cnri.repository.CloseableIterator;
import net.cnri.repository.CreationException;
import net.cnri.repository.DigitalObject;
import net.cnri.repository.InternalException;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.UncheckedRepositoryException;
import net.cnri.repository.search.AbstractQueryVisitorForSearch;
import net.cnri.repository.search.AttributeQuery;
import net.cnri.repository.search.Query;
import net.cnri.repository.util.CloseableIteratorFromIterator;
import net.cnri.repository.util.FilteringCloseableIteratorWrapper;
import net.cnri.repository.util.MappingCloseableIteratorWrapper;

/**
 * Digital Object repository built on top of a file system. All modifications are written directly to the underlying file system.
 * You can instantiate this class on an existing file system that has been written by this class. Provides for a simple way to move
 * objects from one repository to another using sneakernet. Low performance as every set writes to disk. 
 */
public class FilesystemRepository extends AbstractRepository implements Repository {
    private File root;
    
    public FilesystemRepository(File root) {
        this.root = root;
    }
    
	@Override
	public boolean verifyDigitalObject(String handle) throws RepositoryException {
        if(handle==null) return false;
        File dobj = new File(root,encodeFilename(handle));
        File attrs = new File(dobj,FilesystemDigitalObject.ATTRIBUTES_FILE_NAME);
	    return dobj.isDirectory() && attrs.exists() && attrs.length() > 0;
	}

	@Override
	public DigitalObject createDigitalObject(String handle) throws CreationException, RepositoryException {
        if(handle==null) throw new UnsupportedOperationException();
        File dir = new File(root,encodeFilename(handle));
        File attrs = new File(dir,FilesystemDigitalObject.ATTRIBUTES_FILE_NAME);
        if(dir.exists()) {
            if(dir.isDirectory()) {
                if(attrs.exists() && attrs.length() > 0) {
                    throw new CreationException();
                }
            }
            else {
                throw new InternalException("Can't create object; file in the way");
            }
        }
        dir.mkdirs();
        try {
            attrs.createNewFile();
            FilesystemDigitalObject.writeEmptyAttributesFile(attrs, handle);
        }
        catch(IOException e) {
            throw new InternalException(e);
        }
		return new FilesystemDigitalObject(this,dir,handle);
	}

	@Override
	public DigitalObject getDigitalObject(String handle) throws RepositoryException {
        if(handle==null) return null;
        File dir = new File(root,encodeFilename(handle));
        File attrs = new File(dir,FilesystemDigitalObject.ATTRIBUTES_FILE_NAME);
        if(dir.isDirectory() && attrs.exists() && attrs.length() > 0) return new FilesystemDigitalObject(this,dir,handle);
        return null;
	}

	@Override
	public void deleteDigitalObject(String handle) throws RepositoryException {
        File dir = new File(root,encodeFilename(handle));
        File[] files = dir.listFiles();
        if(files==null) throw new InternalException("listFiles is null");
        for (File file : files) {
            file.delete();
        }
        dir.delete();
	}

	@Override
	public CloseableIterator<String> listHandles() {
	    List<String> handles = new ArrayList<String>();
        File[] files = root.listFiles();
        if(files==null) return new CloseableIteratorFromIterator<String>(Collections.<String>emptyList().iterator());
        for (File file : files) {
            File attrs = new File(file,FilesystemDigitalObject.ATTRIBUTES_FILE_NAME);
	        if(file.isDirectory() && attrs.exists() && attrs.length()>0) {
	            handles.add(decodeFilename(file.getName()));
	        }
	    }
        return new CloseableIteratorFromIterator<String>(handles.iterator());
	}

	@Override
	public CloseableIterator<DigitalObject> listObjects() throws RepositoryException {
        List<DigitalObject> objects = new ArrayList<DigitalObject>();
        File[] files = root.listFiles();
        if(files==null) return new CloseableIteratorFromIterator<DigitalObject>(Collections.<DigitalObject>emptyList().iterator());
        for (File file : files) {
            File attrs = new File(file,FilesystemDigitalObject.ATTRIBUTES_FILE_NAME);
            if(file.isDirectory() && attrs.exists() && attrs.length()>0) {
                objects.add(new FilesystemDigitalObject(this,file,decodeFilename(file.getName())));
            }
        }
        return new CloseableIteratorFromIterator<DigitalObject>(objects.iterator());
	}

    private static boolean reservedChar(char ch) {
        return ch <= 31 || ch == 127 || ch==' ' || ch=='<' || ch=='>' || ch==':' || ch=='"' || ch=='/' || ch=='\\' || ch=='|' || ch=='?' || ch=='*'
            || ch=='%';
    }
    
    private static final char[] hexBytes = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};


    // assuming Unicode is fine...
    static String encodeFilename(String s) {
        int i;
        for(i = 0; i < s.length(); i++) {
            if(reservedChar(s.charAt(i))) break;
        }
        if(i >= s.length()) return s;

        StringBuilder sb = new StringBuilder(s.substring(0,i));
        for( ; i < s.length(); i++) {
            char ch = s.charAt(i);
            if(reservedChar(ch)) {
                sb.append("%");
                sb.append(hexBytes[ch/16]);
                sb.append(hexBytes[ch%16]);
                //sb.append(Character.toUpperCase(Character.forDigit(ch / 16,16)));
                //sb.append(Character.toUpperCase(Character.forDigit(ch % 16,16)));
            }
            else sb.append(ch);
        }
        return sb.toString();
    }
    
    public static final byte decodeHexByte(byte ch1, byte ch2) {
        char n1 = (char) ((ch1>='0' && ch1<='9') ? ch1-'0' : ((ch1>='a' && ch1<='z') ? ch1-'a'+10 : ch1-'A'+10));
        char n2 = (char) ((ch2>='0' && ch2<='9') ? ch2-'0' : ((ch2>='a' && ch2<='z') ? ch2-'a'+10 : ch2-'A'+10));
        return (byte)(n1<<4 | n2);
    }

    static String decodeFilename(String s) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] bytes = s.getBytes("UTF-8");
            int i = 0;
            while(i < bytes.length) {
                byte ch = bytes[i++];
                if(ch=='%' && i+2<=s.length()) {
                    out.write(decodeHexByte(bytes[i++],bytes[i++]));
                } else {
                    out.write(ch);
                }
            }

            return new String(out.toByteArray(), "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }
    }
    
    @Override
    public void close() {
        // no-op
    }

    private CloseableIterator<DigitalObject> slowSearch(final AttributeQuery attQuery) throws RepositoryException {
        return new FilteringCloseableIteratorWrapper<DigitalObject>(listObjects()) {
            @Override
            protected boolean retain(DigitalObject candidate) {
                try {
                    return attQuery.getValue().equals(candidate.getAttribute(attQuery.getAttributeName()));
                } catch(RepositoryException e) {
                    throw new UncheckedRepositoryException(e);
                }
            }
        };
    }
    
	@Override
	public CloseableIterator<DigitalObject> search(Query query) throws RepositoryException {
	    return query.accept(new AbstractQueryVisitorForSearch.SearchObjects(this) {
	        @Override
	        public CloseableIterator<DigitalObject> visitAttributeQuery(AttributeQuery attQuery) throws RepositoryException {
	            return slowSearch(attQuery);
	        }
	    });
	}

    @Override
    public CloseableIterator<String> searchHandles(Query query) throws RepositoryException {
        return query.accept(new AbstractQueryVisitorForSearch.SearchHandles(this) {
            @Override
            public CloseableIterator<String> visitAttributeQuery(AttributeQuery attQuery) throws RepositoryException {
                return new MappingCloseableIteratorWrapper<DigitalObject,String>(slowSearch(attQuery)) {
                    @Override
                    protected String map(DigitalObject from) {
                        return from.getHandle();
                    }
                };
            }
        });
    }
    
    private RandomAccessFile raf;
    private FileChannel channel;
    private FileLock fileLock;
    private int sharedLocks;
    private int exclusiveLocks;
    private final Object fileLockLock = new Object();
    private ReentrantReadWriteLock threadLock = new ReentrantReadWriteLock(); 

    private void lockFile(boolean shared) throws RepositoryException {
        try {
            File file = new File(root,"lock");
            raf = new RandomAccessFile(file, "rw");
            channel = raf.getChannel();
            fileLock = channel.lock(0L,Long.MAX_VALUE,shared);
        }
        catch(IOException e) {
            try { channel.close(); } catch(IOException ex) {}
            try { raf.close(); } catch(IOException ex) {}
            throw new InternalException(e);
        }
    }

    public void lock(boolean shared) throws RepositoryException {
        Lock lock = null;
        try {
            if(shared) {
                lock = threadLock.readLock();
                lock.lock();
                synchronized(fileLockLock) {
                    sharedLocks++;
                    if(sharedLocks==0 && exclusiveLocks==0) {
                        lockFile(true);
                    }
                }
            }
            else {
                lock = threadLock.writeLock();
                lock.lock();
                synchronized(fileLockLock) {
                    exclusiveLocks++;
                    if(exclusiveLocks==1) {
                        lockFile(false);
                    }
                }
            }
        }
        catch(RepositoryException e) {
            lock.unlock();
            throw e;
        }
    }
    
    public void unlock() {
        boolean shared;
        boolean unlock;
        synchronized(fileLockLock) {
            if(sharedLocks>0) {
                shared = true;
                sharedLocks--;
                unlock = sharedLocks==0 && exclusiveLocks==0; 
            }
            else {
                shared = false;
                exclusiveLocks--;
                unlock = exclusiveLocks==0;
            }
            if(unlock) {
                try { fileLock.release(); } catch(IOException e) {}
                try { channel.close(); } catch(IOException ex) {}
                try { raf.close(); } catch(IOException ex) {}
                fileLock = null;
            }
        }
        if(shared) threadLock.readLock().unlock();
        else threadLock.writeLock().unlock();
    }
}
