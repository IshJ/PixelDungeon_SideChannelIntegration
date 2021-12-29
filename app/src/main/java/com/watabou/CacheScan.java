/*
This class contains functions:
1. initializing the scanning thread.(private void init(Context mContext))
2. sending notifications.(void Notify(Context mContext))
*/
package com.watabou;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.watabou.pixeldungeon.PixelDungeon;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static android.app.PendingIntent.FLAG_UPDATE_CURRENT;
//import static com.SMU.DevSec.JobInsertRunnable.insert_locker;
//import static com.SMU.DevSec.MainActivity.audio;
//import static com.SMU.DevSec.MainActivity.camera;
//import static com.SMU.DevSec.MainActivity.check;
//import static com.SMU.DevSec.MainActivity.compilerValues;
//import static com.SMU.DevSec.MainActivity.day;
//import static com.SMU.DevSec.MainActivity.frontAppValues;
//import static com.SMU.DevSec.MainActivity.groundTruthValues;
//import static com.SMU.DevSec.MainActivity.isCollected;
//import static com.SMU.DevSec.MainActivity.lastday;
//import static com.SMU.DevSec.MainActivity.location;
//import static com.SMU.DevSec.MainActivity.pkg_name;
//import static com.SMU.DevSec.MainActivity.pkg_permission;
//import static com.SMU.DevSec.MainActivity.quering;
//import static com.SMU.DevSec.MainActivity.status;
//import static com.SMU.DevSec.SideChannelJob.locker;
import static com.watabou.pixeldungeon.PixelDungeon.sideChannelValues;
import static java.util.Objects.requireNonNull;
import static com.watabou.JobInsertRunnable.insert_locker;


public class CacheScan {
    private static final String TAG = "CacheScan";
    String app;
    private int sameapp = 0;
    static boolean ischeckedaddr = false;
    static boolean[] handled = {true, true, true, true};
    volatile static long lastactivetime = 0;
    private String preapp = "DevSec";
    private boolean reset_thresh = false;
    private long lastcamera = 0;
    private long lastaudio = 0;
    static long notification = 0;
    static long answered = 0;
    static boolean notifying = false;//
    boolean filtered = false;
    private static ArrayList<String> target_functions = new ArrayList<String>();
    private final HashMap<String, String> behaviour_map = new HashMap<String, String>();
    private ArrayList<int[]> ALpattern = new ArrayList<int[]>();
    private int[] thresholdforpattern = {10, 10};//number of different functions we monitored on camera set and audio set
    private String[] BehaviorList = {"Information", "Camera", "AudioRecorder", "Location"};
    private double threshold_level = 0.2; //if 20% audio functions are activated, we think it is a true event.
    private int[] cleanpattern = {0, 0};
    private String AppStringforcheck = "";
    private long firsthit = 0;
    private long setfalse = 0;
    private boolean dismiss = false;
    private boolean exceedtime = false;
    private static File CacheDir;
    public static boolean initializing = false;

    CacheScan(Context mContext) throws IOException {
        init(mContext);
    }

    /**
     * Function to initialize the class
     */
    private void init(Context mContext) throws IOException {
        initializing = true;
        int pid = android.os.Process.myPid();
//        CacheDir = mContext.getCacheDir();
//        String audioapi = "Audio";
//        behaviour_map.put(audioapi, "AudioRecord");
//        String cameraapi = "Camera";
//        behaviour_map.put(cameraapi, "Camera");
//        AssetManager am = mContext.getAssets();
//        //Read target functions needed to monitor
//        String[] targets = am.list("targets");
        String[] dexlist = new String[1];
        String[] filenames = new String[1];
        String[] func_lists = new String[1];
//        //Log.d(TAG, "Target:" + target_func + " " + target_lib);
//        SharedPreferences edit = mContext.getSharedPreferences("user", 0);
//        notification = edit.getLong("Notification", 0);
//        answered = edit.getLong("Answered", 0);
////        lastday = edit.getLong("lastday", 0);
////        day = edit.getLong("day", 0); //how many days it has run

        init(dexlist, filenames, func_lists);//initiate the JNI function
        Log.d(TAG, "Threshold Level is at " + threshold_level);//only output
        initializing = false;
//        check = true;
    }

    /**
     * Function to execute search the address of target file in memory
     */
    private String[] exec(int pid, String target) {
        String data = "";
        try {
            Process p = null;
            String command = "grep " + target + " /proc/`pgrep " + PixelDungeon.TAG + "`/maps"; //还没进内存
            //Log.d(TAG,"TTTTTTTTTTTT"+command);
            p = Runtime.getRuntime().exec(command);
            BufferedReader ie = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String error = null;
            while ((error = ie.readLine()) != null
                    && !error.equals("null")) {
                data += error + "\n";
            }
            String line = null;
            while ((line = in.readLine()) != null
                    && !line.equals("null")) {
                data += line + "\n";
            }
            String[] arr = data.split("\n");
            for (String st : arr) {
                if (st.contains("-xp")) {// && st.split("/").length == 4) {
                    Log.d(TAG, st);
                    data = st;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return data.split(" ");
    }

    /**
     * Function to extract dex files from jar package
     */
    public static String[] decompress(String fileName) {
        JarFile jf = null;
        String filename = "";
        String[] jarlist;
        ArrayList<String> dexfiles = new ArrayList<String>();
        try {
            jf = new JarFile(fileName);
            for (Enumeration<JarEntry> e = jf.entries(); e.hasMoreElements(); ) {
                JarEntry je = (JarEntry) e.nextElement();
                Log.d(TAG, "jar file has file:" + je.getName());
                if (je.getName().endsWith(".dex") || je.getName().endsWith(".jar")) {
                    filename = CacheDir + "/" + je.getName();
                    File file = new File(filename);
                    unpack(jf, je, file);
                    Log.d(TAG, "Extract Jar:" + filename);
                    dexfiles.add(filename);
                }
            }
            if (dexfiles.size() == 0) {
                return null;
            }
            return dexfiles.toArray(new String[0]);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;//new String[0];
    }

    /*
    unpack jar
     */
    private static void unpack(JarFile jarFile, JarEntry entry, File file) throws IOException {
        try (InputStream inputStream = jarFile.getInputStream(entry)) {
            try (OutputStream outputStream = new FileOutputStream(file)) {
                int BUFFER_SIZE = 1024;
                byte[] buffer = new byte[BUFFER_SIZE];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
                outputStream.flush();
            }
        }
    }


    /**
     * Function to check and send notification
     */
    void Notify(Context mContext) {
        //insert the logs into dataset
        long[] times = GetTimes();//retrieve the times of all activated functions. In native-lib/CheckFlags.cpp
        int[] logs;
        long[] timingCounts;
        long[] addresses;
        if (times != null && times.length > 0) {
//            int[] thresholds = GetThresholds();//Get the thresholds during the period.(when a function was activated, we record the threshold) In native-lib/CheckFlags.cpp
            logs = GetLogs();//Get all activated functions during the period. In native-lib/CheckFlags.cpp
            Log.d("sidescandb", "returned from getLogs");
            timingCounts = GetTimingCounts();
            addresses = GetAddresses();


            Log.d("sidescandb", " addresses: " + addresses.length);
            Log.d("sidescandb", " logs: " + logs.length);
            Log.d("sidescandb", " times: " + times.length);
//            if (addresses.length > 0) {
//                Log.d("sidescandb", " addresses[0]: " + addresses[0]);
//            }
            ArrayList<SideChannelValue> scvs = new ArrayList<SideChannelValue>();
            // Store these data into an array, they will be save in a database eventually.
            for (int i = 0; i < times.length; i++) {
//                if(logs[i]>120){
//                    continue;
//                }
                SideChannelValue sideChannelValue = new SideChannelValue();
                sideChannelValue.setSystemTime(times[i]);
                sideChannelValue.setScanTime(logs[i]);
                sideChannelValue.setAddress(String.valueOf(addresses[i]));
                sideChannelValue.setCount(timingCounts[i]);
                scvs.add(sideChannelValue);
            }
            if (scvs.size() > 0) {
                insert_locker.lock();

                sideChannelValues.addAll(scvs);
                insert_locker.unlock();
//                Log.d("sidescandb", "cachescan");
//                new Thread(new JobInsertRunnable(mContext)).start();
            }
            Log.d("sidescandb", "sideChannelValues.size() " + sideChannelValues.size());
        }

    }

    public static native long[] GetTimes(); //for compiler

    public static native long[] GetAddresses();

    //    /**
//     * Function to get all activations.
//     */
    public static native int[] GetLogs(); //for compiler

    public static native long[] GetTimingCounts(); //for compiler

    private void intentBuild(Intent intent, long time, String app, int flag, int ignored, int cmp) {
        intent.putExtra("arise", time);
        intent.putExtra("app", app);
        intent.putExtra("flag", flag);
        intent.putExtra("ignored", ignored);
        intent.putExtra("pattern", cmp);
    }

    /**
     * Function to reset threshold, not be used now.
     */
    private void ResetThreshold(Context mContext) {
    }

    /**
     * Function to execute shell.
     */
    private String[] exec(String target) {
        StringBuilder data = new StringBuilder();
        try {
            Process p = null;
            //Log.d(TAG,"TTTTTTTTTTTT"+command);
            p = Runtime.getRuntime().exec(target);
            BufferedReader ie = new BufferedReader(new InputStreamReader(p.getErrorStream()));
            BufferedReader in = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String error = null;
            while ((error = ie.readLine()) != null
                    && !error.equals("null")) {
                data.append(error).append("\n");
            }
            String line = null;
            while ((line = in.readLine()) != null
                    && !line.equals("null")) {
                data.append(line).append("\n");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        Log.d(TAG, data.toString());
        return data.toString().split(" ");
    }


    public static native int getthreshold();

     /**
     * Function to initialize the scanner.
     */
    public static native void init(String[] dexlist, String[] filename, String[] func_list);


}
