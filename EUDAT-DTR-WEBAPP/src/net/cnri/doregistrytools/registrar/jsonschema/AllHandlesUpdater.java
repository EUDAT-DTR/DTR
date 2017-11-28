package net.cnri.doregistrytools.registrar.jsonschema;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import net.cnri.repository.DigitalObject;
import net.cnri.repository.Repository;
import net.cnri.repository.RepositoryException;
import net.cnri.repository.search.QueryResults;
import net.cnri.repository.search.RawQuery;

public class AllHandlesUpdater {
    private Logger logger = LoggerFactory.getLogger(new Object() { }.getClass().getEnclosingClass());
    
    private volatile boolean inProgress = false; 
    private AtomicInteger progressCount = null;
    private int totalCount;
    private AtomicInteger exceptionCount = null;
    private ExecutorService execServ;
    private long startTime = 0L;
    private volatile boolean shutdown = false;
    private static final int NUM_THREADS = 20;
    
    public AllHandlesUpdater() {
        progressCount = new AtomicInteger();
        exceptionCount = new AtomicInteger();
    }
    
    public void shutdown() {
        shutdown = true;
        if (execServ != null) { 
            execServ.shutdown();
        }
    }
    
    public synchronized void updateAllHandles(final HandleClient handleClient, final Repository repo) {
        if (handleClient == null) {
            return;
        }
        if (inProgress) {
            return;
        }
        startTime = System.currentTimeMillis();
        exceptionCount.set(0);
        progressCount.set(0);
        totalCount = 0;
        inProgress = true;
        BlockingQueue<Runnable> queue = new LinkedBlockingDeque<Runnable>(100);
        execServ = new ThreadPoolExecutor(NUM_THREADS, NUM_THREADS, 0, TimeUnit.MILLISECONDS, queue, new ThreadPoolExecutor.CallerRunsPolicy());
        new Thread() {
            public void run() {
                finishUpdateAllHandles(handleClient, repo);
            }
        }.start();
    }
    
    private void finishUpdateAllHandles(HandleClient handleClient, Repository repo) {
        QueryResults<DigitalObject> iter = null;
        try {
            iter = (QueryResults<DigitalObject>) repo.search(new RawQuery("*:* NOT objatt_meta:true NOT objatt_remote:true"));
            totalCount = iter.size();
            while (iter.hasNext()) {
                if (shutdown) break;
                DigitalObject dobj = iter.next();
                String meta = dobj.getAttribute("meta");
                if (!"true".equals(meta)) {
                    UpdateHandleTask task = new UpdateHandleTask(handleClient, dobj);
                    execServ.submit(task);
                }
            }
        } catch (RepositoryException e) {
            logger.error("Error listing objects", e);
            exceptionCount.incrementAndGet();
        } finally {
            if (iter != null) {
                iter.close();
            }
        }
        execServ.shutdown();
        try {
            execServ.awaitTermination(Long.MAX_VALUE, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            execServ.shutdownNow();
            Thread.currentThread().interrupt();
        }
        inProgress = false;
    }
    
    public UpdateStatus getStatus() {
        UpdateStatus status = new UpdateStatus();
        status.inProgress = this.inProgress;
        status.total = this.totalCount;
        status.startTime = this.startTime;
        status.progress = this.progressCount.get();
        status.exceptionCount = this.exceptionCount.get();
        return status;
    }
    
    public class UpdateHandleTask implements Runnable {
        HandleClient handleClient;
        DigitalObject dobj;
        
        public UpdateHandleTask(HandleClient handleClient, DigitalObject dobj) {
            this.handleClient = handleClient;
            this.dobj = dobj;
        }
        
        @Override
        public void run() {
            if (shutdown) return;
            try {
                String type = dobj.getAttribute("type");
                String json = dobj.getAttribute("json");
                JsonNode jsonNode = JsonUtil.parseJson(json);
                handleClient.updateHandleFor(dobj.getHandle(), dobj, type, jsonNode);
                progressCount.incrementAndGet();
            } catch (Exception e) {
                logger.error("Exception updating handle " + dobj.getHandle(), e);
                exceptionCount.incrementAndGet();
            }
        }
        
    }
    
    public static class UpdateStatus {
        public boolean inProgress = false; 
        public int total;
        public int progress;
        public long startTime;
        public int exceptionCount;
    }
}
