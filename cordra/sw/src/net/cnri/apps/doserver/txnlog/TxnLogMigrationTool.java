/*************************************************************************\
    Copyright (c) 2015 Corporation for National Research Initiatives;
                        All rights reserved.
     The CNRI open source license for this software is available at
                  http://hdl.handle.net/20.1000/106
\*************************************************************************/

package net.cnri.apps.doserver.txnlog;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Date;

import net.cnri.apps.doserver.txnlog.AbstractTransactionQueue.CloseableEnumeration;

public class TxnLogMigrationTool {

    /**
     * 
     * Can be used to convert a transaction log as created by TransactionQueue into a 
     * transaction log as used TransactionQueueBerkeleyDB
     * 
     * Renames the current txns dir to txns.old
     * Creates a new dir called txns which is where the berkeleydb will be stored.
     * Copies the transactions from the old queue to the new queue
     * 
     * @param txnDir The directory that the current transaction files are stored in.
     * @throws Exception
     */


    /**
     * Tests to see if you have .q files in your txns folder
     */
    public static boolean isTxnLogMigrationNeeded(File txnDir) {
        FilenameFilter filter = new TxnQueueFilter();
        File[] queueFiles = txnDir.listFiles(filter);
        return queueFiles.length !=0;
    }

    private static class TxnQueueFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return (name.endsWith(".q"));
        }
    }

    public static void performMigrationFromFileSystemToBerkeleyDB(File txnDir) throws Exception {
        Date start = new Date();
        System.out.println("Migrating transaction log to berkeleydb.");
        System.out.println("");
        File berkeleyDBdir = new File(txnDir.getAbsolutePath());
        File copyOfTxnDir = new File(txnDir.getParentFile(),txnDir.getName() + ".old");
        boolean success = txnDir.renameTo(copyOfTxnDir);
        if (!success) {
            throw new Exception("Could not rename txns folder during migration");
        }
        berkeleyDBdir.mkdirs();
        AbstractTransactionQueue berkeleyDBqueue = new TransactionQueueBerkeleyDB(berkeleyDBdir);
        AbstractTransactionQueue fileSystemTxnQueue = new TransactionQueue(copyOfTxnDir);
        copyTransactions(fileSystemTxnQueue, berkeleyDBqueue);
        Date end = new Date();
        System.out.println("");
        System.out.println("Migrating complete. Elapsed time: " + (end.getTime() - start.getTime()) + "ms");
    }

    private static void copyTransactions(AbstractTransactionQueue source, AbstractTransactionQueue destination) throws Exception {
        long count = 0;
        CloseableEnumeration e = source.getCloseableScanner(0);
        try {
            while (e.hasMoreElements()) {
                Transaction txn = (Transaction) e.nextElement();
                destination.addTransaction(txn);
                count++;
                if (count % 1000 == 0) {
                    System.out.println(count+ ": Transactions migrated.");
                }
            }
        } finally {
            e.close();
        }
    }

    private static void printUsage() {
        System.err.println("java net.cnri.apps.doserver.txnlog.TxnLogMigrationTool <txns-dir>");
    }

    public static void main(String argv[]) throws Exception {
        if(argv.length<1) {
            printUsage();
            return;
        }
        String dir = argv[0];
        performMigrationFromFileSystemToBerkeleyDB(new File(dir));
    }
}
