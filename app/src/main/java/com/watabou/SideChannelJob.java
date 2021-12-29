/*
This is a foreground service for scanning, side channel information collection and notificaiton poping. 
*/
package com.watabou;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
//import android.os.Process;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.watabou.pixeldungeon.PixelDungeon;
import com.watabou.pixeldungeon.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Scanner;
import java.util.StringTokenizer;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

//import static com.watabou.CacheScan.answered;
//import static com.watabou.CacheScan.notification;
//import static com.watabou.JobInsertRunnable.insert_locker;
import static com.watabou.JobInsertRunnable.insert_locker;
import static com.watabou.pixeldungeon.PixelDungeon.cs;
import static com.watabou.pixeldungeon.PixelDungeon.methodStats;
import static com.watabou.pixeldungeon.PixelDungeon.sideChannelValues;


public class SideChannelJob extends Service {
    public static volatile boolean continueRun;
    private static final String TAG = "AddressScan";
    private static int label = 0;
    static int[] pattern_filter = null;
    int scValueCount = 1;
    int index = 1;
    long cumulativeTime;
    private String currentFront = "DevSec";
    private static final String CHANNEL_ID = "e.smu.devsec";
    private static final String description = "Collecting Data";
    static Lock locker = new ReentrantLock();
    static String shared_mem_des = "";
    long start_time = 0;
    Thread thread_collect = null;
    Thread thread_notify = null;

    static long localCount = 0;
    //    don't change the format
    static int yieldVal = 100;

    static int fd = -2;

    Map<String, String> configMap = new HashMap<>();
    static final String CONFIG_FILE_PATH = "/data/local/tmp/config.out";

    /**
     * Start service by notification
     */
    @TargetApi(Build.VERSION_CODES.N)
    public void setForegroundService() {
        int importance = NotificationManager.IMPORTANCE_LOW;
        //build channel
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, CHANNEL_ID, importance);
        Intent intent;
        channel.setDescription(description);
        intent = new Intent(this, PixelDungeon.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);
        builder.setSmallIcon(R.mipmap.ic_launcher_round)
                .setContentTitle("DevSec")
                .setContentText("Scanning")
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setDeleteIntent(pendingIntent);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(channel);
        startForeground(111, builder.build());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Job started");
        start_time = System.currentTimeMillis();

//        SharedPreferences sharedPreferences = getSharedPreferences("Settings", Context.MODE_MULTI_PROCESS);
//        String savedValueInWriterProcess = sharedPreferences.getString("fd", "");
//        Log.d("shared_data_shm side channel sharedpref", " sp.toString() " + savedValueInWriterProcess);
//        sharedPreferences.edit().putString("fd", "56").commit();
        initializeDB();
        configMap = readConfigFile();
//        configMap.entrySet().forEach(e -> Log.d("configMap: ", e.getKey() + " " + e.getValue()));
        yieldVal = Integer.parseInt(Objects.requireNonNull(configMap.get("yield")));

//        shared_mem_des = intent.getStringExtra("shared_map_ptr");
//        Bundle bundle = intent.getExtras();
//        ParcelFileDescriptor fd = bundle.getParcelable("fd");
//        intent.getExtras().getBinder("fd");
//       ParcelFileDescriptor fd = (ParcelFileDescriptor) ((IBinder)intent.getExtras().getBinder("fd")).getData();
//        ParcelFileDescriptor fd = (ParcelFileDescriptor) intent.getParcelableArrayListExtra("fd").get(0);
//        Log.d("shared_data_shm side channel", " fd.toString() "+fd.toString());

//        Log.d("shared_data_shm side channel", " shared_mem_ptr "+shared_mem_des);

        doBackgroundWork();
        Toast.makeText(this, "Job scheduled successfully", Toast.LENGTH_SHORT)
                .show();
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Method to run data collection in the background
     */
    public void doBackgroundWork() {
        Log.d(TAG, "New Thread Created");

        Thread ashMemUpdatorThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while (fd < 0) {
                        Thread.sleep(10);
                    }
                    int i = 1;
                    long j = 0l;
                    while (true) {
                        i = 1;
                        j = j + 1;
                        setAshMemVal(fd, j);
                        while (i % 5 != 0) {
                            i++;
                        }

                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });

//         Send the job to a different thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                BigInteger odexMemMapBegin;
                BigInteger odexMemMapBegin2;

                try {
                    while (fd < 0) {
                        Thread.sleep(10);
                    }
                    if (cs == null) {

                        cs = new com.watabou.CacheScan(getBaseContext());//Initialize the CacheScan class
//                        PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putString("test", "test123").commit();
//                        String testString = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getString("test", "0");
//                        Log.d("rainviewer sidechannel", "testString "+ testString);

                        Log.d(TAG, "Initialize CacheScan");
//                        setSharedMapChild(2, shared_mem_des.toCharArray());

////                        exec memory
                        String odexExLine = getOdexExLine();
                        BigInteger odexMemExEnd = new BigInteger(odexExLine.split("-")[1], 16);
                        BigInteger odexMemExBegin = new BigInteger(odexExLine.split("-")[0], 16);
                        Log.d("OdexScan::", "exec memory begin: " + odexMemExBegin.toString(16));
                        Log.d("OdexScan::", "exec memory end: " + odexMemExEnd.toString(16));
////                        exec offset
                        String execOffsetString = configMap.get("execOffset");
                        BigInteger execOffset = new BigInteger(execOffsetString, 16);
                        Log.d("OdexScan::", "exec offset: " + execOffset.toString(16));

                        odexMemExBegin = odexMemExBegin.subtract(execOffset);

//####
//######
//                         exec memory-exec offset+4
//                        odexMemExBegin = odexMemExBegin.subtract(execOffset).add(new BigInteger("4", 10));
                        // exec memory-exec offset

                        odexMemMapBegin = new BigInteger(getOdexBeginAddress(), 16);
//                        odexMemMapBegin = odexMemMapBegin.add(execOffset);

                        Log.d(TAG, "Odex starting Address: " + odexMemMapBegin.toString(16));
                        Log.d(TAG, "Odex x starting Address: " + odexMemExBegin.toString(16));

//##########

//                        String addressForAdjusting = configMap.get("addressForAdjusting");
//                        String[] adjustingAddressesString = addressForAdjusting.split(",");
//                        long[] adjustingAddressesLong = new long[adjustingAddressesString.length];
//                        for (int i = 0; i < adjustingAddressesString.length; i++) {
////                          (exec memory-exec offset)+code offset
//                            adjustingAddressesString[i] = odexMemExBegin
//                                    .add(new BigInteger(adjustingAddressesString[i], 16)).toString(10);
//                            adjustingAddressesLong[i] = Long.parseLong(adjustingAddressesString[i]);
//                        }
//                        if (("1").equals(configMap.get("isAdjust"))) {
//                            adjustThreshold(adjustingAddressesLong, adjustingAddressesLong.length);
//                        }


//##########
                        String[] offsets = configMap.get("sideChannelOffsets").split(",");
                        int pauseVal = Integer.parseInt(configMap.get("pauseVal").trim());
                        int hitVal = Integer.parseInt(configMap.get("hitVal").trim());
                        boolean resetHitCounter = "1".equals(configMap.get("resetHitCounter").trim());
                        int splitVal = Integer.parseInt(configMap.get("splitVal").trim());
//don't change
//                        String ode++xOffsets = "6c1934,6c1958,6c196c,6c1984,6c199c,6c19c4,6c19f0";
//                        Log.d(TAG, "odex offsets:" + odexOffsets);

                        long[] longOffsets = new long[offsets.length];
                        List<Integer> splitIndexes = new ArrayList<>();
                        splitIndexes.add(0);
                        IntStream.range(1, 1 + (longOffsets.length / splitVal))
                                .forEach(b -> splitIndexes.add(Math.min(b * splitVal, longOffsets.length)));

                        for (int i = 0; i < offsets.length; i++) {
//                          (exec memory-exec offset)+code offset
                            offsets[i] = odexMemExBegin.add(new BigInteger(offsets[i], 16))
                                    .toString(10);
                            if (odexMemExEnd.compareTo(new BigInteger(offsets[i], 10)) > 0) {
                                longOffsets[i] = Long.parseLong(offsets[i]);
                            } else {
                                Log.d("odex ", offsets[i] + new BigInteger(offsets[i], 10).toString(16) + " is out of range");
                            }
//                            intOffsets[i] = Integer.parseInt(offsets[i]);
                        }
                        Log.d("odex", " end of for");
                        Log.d(TAG, "odex scanned offsets:" + String.join(",", offsets));
                        ashMemUpdatorThread.start();
                        while (true) {
//                            Log.d("rainviewerashmem", "side " + ShmLib.getValue("sh1",5));

                            localCount++;
                            if (localCount % yieldVal == 0) {
                                localCount = 0;
                                for (int sp = 0; sp < splitIndexes.size() - 1; sp++) {
                                    scan7(Arrays.copyOfRange(longOffsets, splitIndexes.get(sp), splitIndexes.get(sp + 1))
                                            , splitIndexes.get(sp + 1)-splitIndexes.get(sp), pauseVal, hitVal, resetHitCounter);
                                }
                                if (fd < 0) {
                                    Log.d("ashmem ", "not set yet" + fd);
                                } else {
//                                    setAshMemVal(fd, timeCount);
                                }
                                Thread.sleep(1);

                            }

                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
                //Start Scaning
                if (pattern_filter == null)
                    pattern_filter = com.watabou.Utils.getArray(getBaseContext(), "ptfilter");//Retrieve an array of pattern filter; since we only need to monitor partial functions, others will get blocked
            }
        }).start();


        thread_collect = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("sidescandb", "thread_collect run");

                // Initializing primitives and objects
                // Loop to collect side channel values via API calls
                try {
                    while (true) {

                        Thread.sleep(100);
                        while (cs == null) {
                            Thread.sleep(100);//Waiting until CacheScan class is initialized
                        }
//                        Log.d("sidechannel", "groundTruthValues " + groundTruthValues.size());

                        if (sideChannelValues.size() > 10) {//Do not save collected info when at trial mode
//                            Log.d("sidescandb", "sidechannel");
                            new Thread(new com.watabou.JobInsertRunnable(getBaseContext())).start();//start a thread to save data
//                            Log.d(TAG, "DB Updated");
                            index = 1;
                            scValueCount = 0;
                            cumulativeTime = 0;
                        }

                        if (methodStats.size() > 50) {
                            new Thread(new com.watabou.JobMainAppInsertRunnable(getBaseContext())).start();
                        }
                    }//while
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread_collect.start();


        //Start a notification thread.
        thread_notify = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("sidescandb", "thread_notify run");
                try {
                    while (cs == null) {
                        Thread.sleep(100);//Waiting until CacheScan class is initialized
                    }
                    while (true) {
                        Thread.sleep(2000);
                        cs.Notify(getBaseContext());//every 1 second to check if it need to send notification
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        thread_notify.start();


    }

    public String getOdexBeginAddress() {

        // get Process ID of the running app
        int pid = Process.myPid();

        Log.d(TAG, "%%%% spLASH! getOdexBeginAddress" + pid);

        try {

            Optional<String> odc = Files.lines(Paths.get("/proc/self/maps")).collect(Collectors.toList())
                    .stream().sequential().filter(s -> s.contains("com.watabou.pixeldungeon") && s.contains("base.odex"))
                    .findFirst().map(s -> new StringTokenizer(s, "-")).filter(StringTokenizer::hasMoreElements)
                    .map(StringTokenizer::nextToken);
            Log.d(TAG, "%%%% spLASH! odc " + odc);
            if (odc.isPresent()) {
                return odc.get();
            }
        } catch (Exception e) {
            Log.d(TAG, "ERROR!!!!" + e.toString());
        }
        return "77a6425000";
    }

    public String getOdexExLine() {

        boolean isMemMapping = false;
        // get Process ID of the running app
        int pid = Process.myPid();
        Log.d(TAG, "pid " + pid);

        try {

            Optional<String> odc = Files.lines(Paths.get("/proc/self/maps")).collect(Collectors.toList())
                    .stream().sequential().filter(s -> s.contains("com.watabou.pixeldungeon") && s.contains("base.odex") && s.contains("r-xp"))
                    .findFirst();

            Log.d(TAG, "odc " + odc);
            if (odc.isPresent()) {
                return odc.get().split("r-xp")[0].trim();
            }
        } catch (Exception e) {
            Log.d(TAG, "ERROR!!!!" + e.toString());
        }
        return "";
    }

    public String[] getMethodOffsets() {

        //SideChannelOffsets
        return new String[]{"000000", "6c2404", "6c2428", "6c243c", "6c2454", "6c246c", "6c2494", "6c24c0"};
//        return new String[]{"000000", "123"};
    }

    public String[] getClassOffsets() {
//       return new ArrayList<String>(Collections.nCopies(11, "000000")).toArray(new String[10]);

        return new String[]{"000000", "00010d78", "00010d68", "00010d70", "00010c6c", "00010d7c", "00010c04", "00010c44", "00010c18", "00010c60", "00010d64"};
    }

    public native void scan(int[] pattern, int length);

    public native long scan7(long[] arr, int length, int pauseVal, int hitVal, boolean resetHitCounter);

    public native void adjustThreshold(long[] arr, int length);

    public static native long readAshMem(int fd);

    public static native void setAshMemVal(int fd, long val);

    public native void scanOdex(long[] arr, int length);

    public native void pause();

    public native void setSharedMapChild(int shared_mem_ptr, char[] fileDes);

    public native long getOdexBegin(String fileName);


    /**
     * Method to compute battery level based on API call to BatteryManager
     *
     * @param context: Android activity context for detecting change in battery charge
     * @return
     */
    private float computeBatteryLevel(Context context) {

        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);

        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        return level / (float) scale;
    }

    @SuppressLint("CommitPrefEdits")
    @Override
    public void onCreate() {
        super.onCreate();
//        try {
//            int pid = android.os.Process.myPid();
//            Log.d(TAG, "%%%% spLASH! " + pid);
//            Runtime.getRuntime().exec("taskset -p f " + pid);
//            String cpuBind = getCommandResult("taskset -p " + pid);
//            Log.d(TAG, "cpu core: " + cpuBind);
//
//        } catch (Exception e) {
//            Log.d(TAG, e.toString());
//        }

        Log.d("foreground", "onCreate");
        //如果API在26以上即版本为O则调用startForefround()方法启动服务
        setForegroundService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        final long minutes = (System.currentTimeMillis()-start_time)/(1000*60);
//        pause();//pause the scanning
//        //locker.lock();
//        if(stage==0) {//not in trial mode
//            if(minutes>0) {
//                new Thread(new Runnable() {
//                    @Override
//                    public void run() {
//                        String r = TimerManager.getInstance().uploadTimeCheck(getBaseContext(), minutes);//
//                        if (r != null && r.equals("1"))
//                            Log.d(TAG, "Running time are uploaded successfully");
//                        else
//                            Log.d(TAG, "Unable to upload the running time");
//                    }
//                }).start();
//            }
//        }
//        //locker.unlock();
//        SharedPreferences edit = getBaseContext().getSharedPreferences("user",0);
//        SharedPreferences.Editor editor = edit.edit();
//        editor.putLong("Answered",answered);//record the numbr of notification
//        editor.putLong("Notification",notification);
//        editor.putLong("lastday",lastday);
//        editor.putLong("day",day);
//        editor.commit();
//        continueRun = false;
//        //make sure threads are stopped
//        if(thread_collect!=null) {
//            try {
//                thread_collect.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            thread_collect = null;
//        }
//        if(thread_notify!=null) {
//            try {
//                thread_notify.interrupt();
//                thread_notify.join();
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//            thread_notify = null;
//        }
//        Toast.makeText(this, "Job cancelled", Toast.LENGTH_SHORT)
//                .show();
    }

    private static class MessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Log.d("ashmem", " got the message sent by the client: ");
            ParcelFileDescriptor pfd = msg.getData().getParcelable("msg");
            fd = pfd.getFd();
            if (fd < 0) {
                Log.d("ashmem ", "not set MessengerHandler " + fd);
            }
            long c = readAshMem(fd);
            setAshMemVal(fd, 5l);
            Log.d("ashmem", " got the message sent by the client: " + pfd.getFd() + " " + c);
            Log.d("ashmem", " got the message sent by the client: new val " + readAshMem(fd));

            Messenger client = msg.replyTo; // 1
            Message replyMessage = Message.obtain(null, 1);
            Bundle bundle = new Bundle();
            bundle.putString("reply", "The server has received the message!");
            replyMessage.setData(bundle);
            try {
                client.send(replyMessage);
            } catch (RemoteException e) {
                e.printStackTrace();
            }

        }
    }

    private final Messenger mMessenger = new Messenger(new MessengerHandler());

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    /**
     * Method to initialize database
     */
    void initializeDB() {
        // Creating the database file in the app sandbox
        SQLiteDatabase db = getBaseContext().openOrCreateDatabase("SideScan.db",
                MODE_PRIVATE, null);
        Locale locale = new Locale("EN", "SG");
        db.setLocale(locale);
        // Creating the schema of the database
        String sSQL = "CREATE TABLE IF NOT EXISTS " + com.watabou.SideChannelContract.TABLE_NAME + " (" +
                //SideChannelContract.Columns._ID + " INTEGER PRIMARY KEY NOT NULL, " +
                com.watabou.SideChannelContract.Columns.SYSTEM_TIME + " INTEGER NOT NULL, " +
                com.watabou.SideChannelContract.Columns.SCAN_TIME + " INTEGER NOT NULL, " +
                com.watabou.SideChannelContract.Columns.ADDRESS + " TEXT, " +
                com.watabou.SideChannelContract.Columns.COUNT + " INTEGER);";
        db.execSQL(sSQL);
        sSQL = "DELETE FROM " + com.watabou.SideChannelContract.TABLE_NAME;
        db.execSQL(sSQL);
        Log.d("dbinfo", com.watabou.SideChannelContract.TABLE_NAME + " count: " + getRecordCount(com.watabou.SideChannelContract.TABLE_NAME));

        db.close();
    }

    public long getRecordCount(String tableName) {
        SQLiteDatabase db = getBaseContext().openOrCreateDatabase("SideScan.db",
                MODE_PRIVATE, null);
        long count = DatabaseUtils.queryNumEntries(db, tableName);
        db.close();
        return count;
    }

    private Map<String, String> readConfigFile() {
        Map<String, String> configMap = new HashMap<>();
        try {
            List<String> configs = Files.lines(Paths.get(CONFIG_FILE_PATH)).collect(Collectors.toList());
            configs.stream().filter(c -> !c.contains("//") && c.contains(":")).forEach(c -> configMap.put(c.split(":")[0].trim(), c.split(":")[1].trim()));
//            configMap.entrySet().forEach(e-> Log.d("configMap: " , e.getKey()+" "+e.getValue()));

        } catch (IOException e) {
            Log.d(TAG + "#", e.toString());
        }
        return configMap;
    }

//    private static String getCommandResult(String command) {
//        StringBuilder log = new StringBuilder();
//
//        try {
//            // Run the command
//            Process process = Runtime.getRuntime().exec(command);
//            BufferedReader bufferedReader = new BufferedReader(
//                    new InputStreamReader(process.getInputStream()));
//
//            // Grab the results
//            String line;
//            while ((line = bufferedReader.readLine()) != null) {
//                log.append(line + "\n");
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return log.toString();
//    }
}


//
////                        ####
////                        base memory
//                        odexMemMapBegin2 = new BigInteger(getOdexBeginAddress(), 16);
//                                Log.d("OdexScan::", "base memory: "+ odexMemMapBegin2.toString(16) );
////                        exec memory
//                                BigInteger odexMemExBegin = new BigInteger(getOdexBeginExAddress(), 16);
//                                Log.d("OdexScan::", "exec memory: "+ odexMemExBegin.toString(16) );
//
////                        exec offset
//                                BigInteger execOffset = new BigInteger("11a000", 16);
//                                Log.d("OdexScan::", "exec offset: "+ execOffset.toString(16) );
//
////                        code offset
//                                BigInteger codeOffset = new BigInteger("11a010", 16);
//                                Log.d("OdexScan::", "code offset: "+ codeOffset.toString(16) );
//
////                        exec memory+exec offset
//                                BigInteger odexMemEx2Begin = odexMemExBegin.add(execOffset);
//                                Log.d("OdexScan::", "exec memory+exec offset: "+ odexMemEx2Begin.toString(16) );
//
////                        exec memory+code offset
//                                BigInteger odexMemEx3Begin = odexMemExBegin.add(codeOffset);
//                                Log.d("OdexScan::", "exec memory+code offset: "+ odexMemEx3Begin.toString(16) );
//
////                        exec memory+exec offset+code offset
//                                BigInteger odexMemEx4Begin = odexMemExBegin.add(execOffset).add(codeOffset);
//                                Log.d("OdexScan::", "exec memory+exec offset+code offset: "+ odexMemEx4Begin.toString(16) );
//
////                        base memory+exec offset+code offset
//                                BigInteger odexMemEx5Begin = odexMemMapBegin2.add(execOffset).add(codeOffset);
//                                Log.d("OdexScan::", "base memory+exec offset+code offsety: "+ odexMemEx5Begin.toString(16) );
//
////                        base memory+code offset
//                                BigInteger odexMemEx6Begin = odexMemMapBegin2.add(codeOffset);
//                                Log.d("OdexScan::", "base memory+code offset: "+ odexMemEx6Begin.toString(16) );
//
////                        exec memory+code offset - exec offset
//                                BigInteger odexMemEx7Begin = odexMemExBegin.add(codeOffset).subtract(execOffset);
//                                Log.d("OdexScan::", "exec memory+code offset - exec offset: "+ odexMemEx7Begin.toString(16) );
//
//
////                        BigInteger odexMemEx7Begin = odexMemExBegin.add(new BigInteger("11a000", 16));
//
//                                List<BigInteger> toBeScanned = Arrays.asList(
//        odexMemMapBegin2,
////                                odexMemExBegin,
////                                execOffset,
////                                codeOffset,
//        odexMemEx2Begin,
//        odexMemEx3Begin,
//        odexMemEx4Begin,
//        odexMemEx5Begin,
//        odexMemEx6Begin,
//        odexMemEx7Begin
//        );
//
//        List<String> toBeScannedLong = toBeScanned.stream().map(s -> s.toString(10)).collect(Collectors.toList());
//        long[] longOffsets = new long[toBeScanned.size()];
//        for (int i = 0; i < toBeScanned.size(); i++) {
//        longOffsets[i] = Long.valueOf(toBeScannedLong.get(i));
//        }
////                        while (true) {
////                            scanOdex(longOffsets, longOffsets.length);
////                            Thread.sleep(500);
////                        }

//    scanOdex(longOffsets, longOffsets.length);
//                        while (true) {
//
//                                Thread.sleep(500);
//                                }
