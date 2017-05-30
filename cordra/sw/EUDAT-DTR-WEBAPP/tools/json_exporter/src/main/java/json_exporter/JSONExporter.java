package eudatdtr.tools;

import com.sleepycat.je.*;
import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import net.cnri.apps.doserver.HashDirectoryStorage;
import net.cnri.dobj.DOException;
import net.cnri.dobj.DOMetadata;
import net.handle.hdllib.Util;

public class JSONExporter {

    private File baseDirectory;
    private HashDirectoryStorage storage = null;

    public JSONExporter(File baseDirectory) {

        this.baseDirectory = baseDirectory;

        try {
            if(!baseDirectory.exists()) {
                System.err.println("ERROR: The directory specified does not exist");
                System.exit(1);
            }
        } catch (Exception e) {
            System.err.println("ERROR: Unable to access storage directory: " + e);
            System.exit(1);
        }

        try {
            this.storage = new HashDirectoryStorage();
            this.storage.initWithDirectory(null, this.baseDirectory, true);

        } catch (Exception e) {
            System.err.println("ERROR: Unable to access storage: " + e);
            System.exit(1);
        }
    }

    public void dumpRecords(File outputFile) {

        PrintStream out = null;

        if(outputFile == null) {
            out = System.out;
        }
        else {
            try {
                out = new PrintStream(new FileOutputStream(outputFile));
            }
            catch(Exception e) {
                System.err.println("ERROR: Unable to create output file: " + e);
                System.exit(1);
            }
        }

        try {
            for(Enumeration en = storage.listObjects(); en.hasMoreElements();) {
                String objectID = String.valueOf(en.nextElement());

                // skip internal objects stored in the repository
                if(objectID.equals("design") || 
                   objectID.endsWith("repo")) {
                    continue;
                }

                // the getObjectInfo() method is private, we get access to it
                // using reflection
                Method method = this.storage.getClass().
                    getDeclaredMethod("getObjectInfo", String.class, DOMetadata.class);
                
                DOMetadata metadata = (DOMetadata) method.
                    invoke(storage, objectID, (DOMetadata) null);

                // since the repository contains several internal records with
                // potentially sensitive information, we need to dump only
                // records created by registered users. This means that we 
                // needs to skip any records created by the special (as in 
                // internal to the repository) users "admin" or "anonymous"
                String creator = metadata.getTag("objatt.createdBy", null);

                if(creator == null || creator.equals("admin") || creator.equals("anonymous")) {
                    continue;
                }

                String json = metadata.getTag("objatt.json", null);
                
                if(json == null) {
                    continue;
                }

                out.println(json);
            }

            if(outputFile != null) {
                out.close();
            }
        } catch (Exception e) {
            System.err.println("ERROR: Unable to dump records: " + e);
            System.exit(1);
        }
    }

    public static void main(String args[]) {

        File baseDirectory = null;
        File outputFile = null;

        if(args.length != 1 && args.length != 2) {
            System.err.println("Usage: JSONExporter DIR_TO_REPO [OUTPUT_FILE]");
            System.err.println("  (Example: JSONExporter ~/EUDAT-DTR/cordra/data/storage/ dump.json)");
            System.exit(1);
        }

        System.out.println(
"*************************************************************************\n" +
" WARNING: This tool accesses the raw EUDAT-DTR repository database in a\n" + 
" read-only manner, but without enforcing any consistency or transaction\n" +
" mechanisms. To prevent any problems derived from concurrent accesses,\n" + 
" make sure that the service is stopped before extracting data from it.\n" +
"*************************************************************************\n");

        System.out.println("Press \"ENTER\" to continue or \"Ctrl+C\" to cancel...");

        try(Scanner scanner = new Scanner(System.in, "UTF-8")) {
            scanner.nextLine();
        }
        catch(Exception e) {
            System.exit(0);
        }

        baseDirectory = new File(args[0]);

        if(args.length == 2) {
            outputFile = new File(args[1]);
        }

        JSONExporter exporter = new JSONExporter(baseDirectory); 
        exporter.dumpRecords(outputFile);
    }
}
