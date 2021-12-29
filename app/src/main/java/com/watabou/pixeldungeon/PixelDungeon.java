/*
 * Pixel Dungeon
 * Copyright (C) 2012-2015 Oleg Dolya
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package com.watabou.pixeldungeon;

import javax.microedition.khronos.opengles.GL10;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;

import com.watabou.CacheScan;
import com.watabou.GroundTruthValue;
import com.watabou.MethodStat;
import com.watabou.SideChannelContract;
import com.watabou.SideChannelJob;
import com.watabou.SideChannelValue;
import com.watabou.noosa.Game;
import com.watabou.noosa.audio.Music;
import com.watabou.noosa.audio.Sample;
import com.watabou.pixeldungeon.scenes.GameScene;
import com.watabou.pixeldungeon.scenes.PixelScene;
import com.watabou.pixeldungeon.scenes.TitleScene;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class PixelDungeon extends Game {
    public static final String TAG = "PixelDungeon#";
    private static Long timingCount;
    static Lock ground_truth_insert_locker = new ReentrantLock();
    static int waitVal = 1000;
    Map<String, String> configMap = new HashMap<>();
    static final String CONFIG_FILE_PATH = "/data/local/tmp/config.out";
    public static Map<String, Integer> methodIdMap = new HashMap<>();

    public static CacheScan cs = null;

    public static int fd = -2;
    private Messenger mService;

    private Messenger replyMessenger = new Messenger(new MessengerHandler());
    public static ArrayList<SideChannelValue> sideChannelValues = new ArrayList<>();
    public static ArrayList<GroundTruthValue> groundTruthValues = new ArrayList<>();
    public static final List<MethodStat> methodStats = new ArrayList<>();

    private static Context mContext;


    static {
        System.loadLibrary("native-lib");
    }


    public PixelDungeon() {
        super(TitleScene.class);
        Log.d(TAG, "starting");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.scrolls.ScrollOfUpgrade.class,
                "com.watabou.pixeldungeon.items.scrolls.ScrollOfEnhancement");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.actors.blobs.WaterOfHealth.class,
                "com.watabou.pixeldungeon.actors.blobs.Light");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.rings.RingOfMending.class,
                "com.watabou.pixeldungeon.items.rings.RingOfRejuvenation");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.wands.WandOfReach.class,
                "com.watabou.pixeldungeon.items.wands.WandOfTelekenesis");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.actors.blobs.Foliage.class,
                "com.watabou.pixeldungeon.actors.blobs.Blooming");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.actors.buffs.Shadows.class,
                "com.watabou.pixeldungeon.actors.buffs.Rejuvenation");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.scrolls.ScrollOfPsionicBlast.class,
                "com.watabou.pixeldungeon.items.scrolls.ScrollOfNuclearBlast");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.actors.hero.Hero.class,
                "com.watabou.pixeldungeon.actors.Hero");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.actors.mobs.npcs.Shopkeeper.class,
                "com.watabou.pixeldungeon.actors.mobs.Shopkeeper");
        // 1.6.1
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.quest.DriedRose.class,
                "com.watabou.pixeldungeon.items.DriedRose");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.actors.mobs.npcs.MirrorImage.class,
                "com.watabou.pixeldungeon.items.scrolls.ScrollOfMirrorImage$MirrorImage");
        // 1.6.4
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.rings.RingOfElements.class,
                "com.watabou.pixeldungeon.items.rings.RingOfCleansing");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.rings.RingOfElements.class,
                "com.watabou.pixeldungeon.items.rings.RingOfResistance");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.weapon.missiles.Boomerang.class,
                "com.watabou.pixeldungeon.items.weapon.missiles.RangersBoomerang");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.rings.RingOfPower.class,
                "com.watabou.pixeldungeon.items.rings.RingOfEnergy");
        // 1.7.2
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.plants.Dreamweed.class,
                "com.watabou.pixeldungeon.plants.Blindweed");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.plants.Dreamweed.Seed.class,
                "com.watabou.pixeldungeon.plants.Blindweed$Seed");
        // 1.7.4
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.weapon.enchantments.Shock.class,
                "com.watabou.pixeldungeon.items.weapon.enchantments.Piercing");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.weapon.enchantments.Shock.class,
                "com.watabou.pixeldungeon.items.weapon.enchantments.Swing");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.scrolls.ScrollOfEnchantment.class,
                "com.watabou.pixeldungeon.items.scrolls.ScrollOfWeaponUpgrade");
        // 1.7.5
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.scrolls.ScrollOfEnchantment.class,
                "com.watabou.pixeldungeon.items.Stylus");
        // 1.8.0
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.actors.mobs.FetidRat.class,
                "com.watabou.pixeldungeon.actors.mobs.npcs.Ghost$FetidRat");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.plants.Rotberry.class,
                "com.watabou.pixeldungeon.actors.mobs.npcs.Wandmaker$Rotberry");
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.plants.Rotberry.Seed.class,
                "com.watabou.pixeldungeon.actors.mobs.npcs.Wandmaker$Rotberry$Seed");
        // 1.9.0
        com.watabou.utils.Bundle.addAlias(
                com.watabou.pixeldungeon.items.wands.WandOfReach.class,
                "com.watabou.pixeldungeon.items.wands.WandOfTelekinesis");
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

        Log.d(TAG, "Inside oncreate");

        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {

            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS
                            , Manifest.permission.WRITE_EXTERNAL_STORAGE
                            , Manifest.permission.CAMERA},
                    10);
        } else {
            setUpandRun();

        }


    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 10: {
                if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    setUpandRun();
                } else {
                    finish();
                }
            }
        }
    }


    @Override
    public void onWindowFocusChanged(boolean hasFocus) {

        super.onWindowFocusChanged(hasFocus);

        if (hasFocus) {
            updateImmersiveMode();
        }
    }

    protected void setUpandRun() {

        fd = createAshMem();
        if (fd < 0) {
            Log.d("ashmem ", "not set onCreate " + fd);
        }

        copyOdex();

        configMap = readConfigFile();
//        configMap.entrySet().forEach(e -> Log.d("configMap: ", e.getKey() + " " + e.getValue()));


        initializeDB();
        initializeDBAop();
        Intent begin = new Intent(this, SideChannelJob.class);
        bindService(begin, conn, Context.BIND_AUTO_CREATE);
        startForegroundService(begin);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        updateImmersiveMode();

        DisplayMetrics metrics = new DisplayMetrics();
        instance.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        boolean landscape = metrics.widthPixels > metrics.heightPixels;

        if (Preferences.INSTANCE.getBoolean(Preferences.KEY_LANDSCAPE, false) != landscape) {
            landscape(!landscape);
        }

        Music.INSTANCE.enable(music());
        Sample.INSTANCE.enable(soundFx());

        Sample.INSTANCE.load(
                Assets.SND_CLICK,
                Assets.SND_BADGE,
                Assets.SND_GOLD,

                Assets.SND_DESCEND,
                Assets.SND_STEP,
                Assets.SND_WATER,
                Assets.SND_OPEN,
                Assets.SND_UNLOCK,
                Assets.SND_ITEM,
                Assets.SND_DEWDROP,
                Assets.SND_HIT,
                Assets.SND_MISS,
                Assets.SND_EAT,
                Assets.SND_READ,
                Assets.SND_LULLABY,
                Assets.SND_DRINK,
                Assets.SND_SHATTER,
                Assets.SND_ZAP,
                Assets.SND_LIGHTNING,
                Assets.SND_LEVELUP,
                Assets.SND_DEATH,
                Assets.SND_CHALLENGE,
                Assets.SND_CURSED,
                Assets.SND_EVOKE,
                Assets.SND_TRAP,
                Assets.SND_TOMB,
                Assets.SND_ALERT,
                Assets.SND_MELD,
                Assets.SND_BOSS,
                Assets.SND_BLAST,
                Assets.SND_PLANT,
                Assets.SND_RAY,
                Assets.SND_BEACON,
                Assets.SND_TELEPORT,
                Assets.SND_CHARMS,
                Assets.SND_MASTERY,
                Assets.SND_PUFF,
                Assets.SND_ROCKS,
                Assets.SND_BURNING,
                Assets.SND_FALLING,
                Assets.SND_GHOST,
                Assets.SND_SECRET,
                Assets.SND_BONES,
                Assets.SND_BEE,
                Assets.SND_DEGRADE,
                Assets.SND_MIMIC);

    }

    private static class MessengerHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case 1:
                    Log.d("ashmem", "Received information from the server: " + msg.getData().getString("reply"));
                    break;
                default:
                    super.handleMessage(msg);
            }
        }
    }

    private ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = new Messenger(service);
            Message msg = Message.obtain(null, 0);
            Bundle bundle = new Bundle();
            if (fd < 0) {
                Log.d("ashmem ", "not set onServiceConnected " + fd);
            }
            setAshMemVal(fd, 4l);
            try {
                ParcelFileDescriptor desc = ParcelFileDescriptor.fromFd(fd);
                bundle.putParcelable("msg", desc);
                msg.setData(bundle);
                msg.replyTo = replyMessenger;      // 2
                mService.send(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {

        }

    };

    private Map<String, String> readConfigFile() {
        Map<String, String> configMap = new HashMap<>();
        try {
            List<String> configs = Files.lines(Paths.get(CONFIG_FILE_PATH)).collect(Collectors.toList());
            configs.stream().filter(c -> !c.contains("//") && c.contains(":")).forEach(c -> configMap.put(c.split(":")[0].trim(), c.split(":")[1].trim()));

        } catch (IOException e) {
            Log.d(TAG + "#", e.toString());
        }
        return configMap;
    }

    private void copyOdex() {
        try {

            String oatHome = "/sdcard/Documents/oatFolder/oat/arm64/";
            Optional<String> baseOdexLine = Files.lines(Paths.get("/proc/self/maps")).collect(Collectors.toList())
                    .stream().sequential().filter(s -> s.contains("com.watabou.pixeldungeon") && s.contains("base.odex"))
                    .findAny();
            Log.d("odex", Files.lines(Paths.get("/proc/self/maps")).collect(Collectors.joining("\n")));
            if (baseOdexLine.isPresent()) {
                String odexpath = "/data/app/" + baseOdexLine.get().split("/data/app/")[1];
                String vdexpath = "/data/app/" + baseOdexLine.get().split("/data/app/")[1].replace("odex", "vdex");
//                String odexRootPath = "/data/app/"+baseOdexLine.get().split("/data/app/")[1].replace("/oat/arm64/base.odex","*");
                Log.d(TAG + "#", odexpath);
                Log.d(TAG + "#", "cp " + odexpath + " " + oatHome);
                Process p = Runtime.getRuntime().exec("cp " + odexpath + " " + oatHome);
                p.waitFor();
                p = Runtime.getRuntime().exec("cp " + vdexpath + " " + oatHome);
                Log.d(TAG + "#", "cp " + vdexpath + " " + oatHome);

                p.waitFor();
                Log.d(TAG + "#", "odex copied");

            } else {
                Log.d(TAG + "#", "base odex absent");
            }

        } catch (IOException | InterruptedException e) {
            Log.d(TAG + "#", e.toString());
        }
    }

    private static void copyMethodMap() {
        String methodMapString = methodIdMap.entrySet().parallelStream().map(Object::toString).collect(Collectors.joining("|"));
        Log.d("MethodMap", methodMapString);
        Log.d("MethodMapCount", String.valueOf(methodIdMap.size()));

    }

    public static void switchNoFade(Class<? extends PixelScene> c) {
        PixelScene.noFade = true;
        switchScene(c);
    }

    /*
     * ---> Prefernces
     */

    public static void landscape(boolean value) {
        Game.instance.setRequestedOrientation(value ?
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE :
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        Preferences.INSTANCE.put(Preferences.KEY_LANDSCAPE, value);
    }

    public static boolean landscape() {
        return width > height;
    }

    // *** IMMERSIVE MODE ****

    private static boolean immersiveModeChanged = false;

    @SuppressLint("NewApi")
    public static void immerse(boolean value) {
        Preferences.INSTANCE.put(Preferences.KEY_IMMERSIVE, value);

        instance.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateImmersiveMode();
                immersiveModeChanged = true;
            }
        });
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        super.onSurfaceChanged(gl, width, height);

        if (immersiveModeChanged) {
            requestedReset = true;
            immersiveModeChanged = false;
        }
    }

    @SuppressLint("NewApi")
    public static void updateImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 19) {
            try {
                // Sometime NullPointerException happens here
                instance.getWindow().getDecorView().setSystemUiVisibility(
                        immersed() ?
                                View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                                        View.SYSTEM_UI_FLAG_FULLSCREEN |
                                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                                :
                                0);
            } catch (Exception e) {
                reportException(e);
            }
        }
    }

    public static boolean immersed() {
        return Preferences.INSTANCE.getBoolean(Preferences.KEY_IMMERSIVE, false);
    }

    // *****************************

    public static void scaleUp(boolean value) {
        Preferences.INSTANCE.put(Preferences.KEY_SCALE_UP, value);
        switchScene(TitleScene.class);
    }

    public static boolean scaleUp() {
        return Preferences.INSTANCE.getBoolean(Preferences.KEY_SCALE_UP, true);
    }

    public static void zoom(int value) {
        Preferences.INSTANCE.put(Preferences.KEY_ZOOM, value);
    }

    public static int zoom() {
        return Preferences.INSTANCE.getInt(Preferences.KEY_ZOOM, 0);
    }

    public static void music(boolean value) {
        Music.INSTANCE.enable(value);
        Preferences.INSTANCE.put(Preferences.KEY_MUSIC, value);
    }

    public static boolean music() {
        return Preferences.INSTANCE.getBoolean(Preferences.KEY_MUSIC, true);
    }

    public static void soundFx(boolean value) {
        Sample.INSTANCE.enable(value);
        Preferences.INSTANCE.put(Preferences.KEY_SOUND_FX, value);
    }

    public static boolean soundFx() {
        return Preferences.INSTANCE.getBoolean(Preferences.KEY_SOUND_FX, true);
    }

    public static void brightness(boolean value) {
        Preferences.INSTANCE.put(Preferences.KEY_BRIGHTNESS, value);
        Intent myIntent = new Intent(mContext, SavingActivity.class);
        mContext.startActivity(myIntent);

        if (scene() instanceof GameScene) {
            ((GameScene) scene()).brightness(value);
        }
    }


    public static boolean brightness() {
        return Preferences.INSTANCE.getBoolean(Preferences.KEY_BRIGHTNESS, false);
    }

    public static void donated(String value) {
        Preferences.INSTANCE.put(Preferences.KEY_DONATED, value);
    }

    public static String donated() {
        return Preferences.INSTANCE.getString(Preferences.KEY_DONATED, "");
    }

    public static void lastClass(int value) {
        Preferences.INSTANCE.put(Preferences.KEY_LAST_CLASS, value);
    }

    public static int lastClass() {
        return Preferences.INSTANCE.getInt(Preferences.KEY_LAST_CLASS, 0);
    }

    public static void challenges(int value) {
        Preferences.INSTANCE.put(Preferences.KEY_CHALLENGES, value);
    }

    public static int challenges() {
        return Preferences.INSTANCE.getInt(Preferences.KEY_CHALLENGES, 0);
    }

    public static void intro(boolean value) {
        Preferences.INSTANCE.put(Preferences.KEY_INTRO, value);
    }

    public static boolean intro() {
        return Preferences.INSTANCE.getBoolean(Preferences.KEY_INTRO, true);
    }

    /*
     * <--- Preferences
     */

    public static void reportException(Throwable tr) {
        Log.e("PD", Log.getStackTraceString(tr));
    }

    /**
     * Method to initialize database
     */
    void initializeDB() {
        // Creating the database file in the app sandbox
        SQLiteDatabase db = getBaseContext().openOrCreateDatabase("MainApp.db",
                MODE_PRIVATE, null);
        Locale locale = new Locale("EN", "SG");
        db.setLocale(locale);
        // Creating the schema of the database
        String sSQL = "CREATE TABLE IF NOT EXISTS " + SideChannelContract.GROUND_TRUTH + " (" +
                SideChannelContract.Columns.SYSTEM_TIME + " INTEGER NOT NULL, " +
                SideChannelContract.Columns.LABEL + " TEXT, " +
                SideChannelContract.Columns.COUNT + " INTEGER);";
        db.execSQL(sSQL);
        sSQL = "DELETE FROM " + SideChannelContract.GROUND_TRUTH;
        db.execSQL(sSQL);
        db.close();
    }

    void initializeDBAop() {
        // Creating the database file in the app sandbox
        SQLiteDatabase db = getBaseContext().openOrCreateDatabase("MainApp.db",
                MODE_PRIVATE, null);
        Locale locale = new Locale("EN", "SG");
        db.setLocale(locale);
        // Creating the schema of the database
        String sSQL = "CREATE TABLE IF NOT EXISTS " + SideChannelContract.GROUND_TRUTH_AOP + " (" +
                SideChannelContract.Columns.METHOD_ID + " INTEGER NOT NULL, " +
                SideChannelContract.Columns.START_COUNT + " INTEGER, " +
                SideChannelContract.Columns.END_COUNT + " INTEGER);";
        db.execSQL(sSQL);
        sSQL = "DELETE FROM " + SideChannelContract.GROUND_TRUTH_AOP;
        db.execSQL(sSQL);
        Log.d("dbinfo", SideChannelContract.GROUND_TRUTH_AOP + " count: " + getRecordCount(SideChannelContract.GROUND_TRUTH_AOP));
        db.close();
    }

    public long getRecordCount(String tableName) {
        SQLiteDatabase db = getBaseContext().openOrCreateDatabase("MainApp.db",
                MODE_PRIVATE, null);
        long count = DatabaseUtils.queryNumEntries(db, tableName);
        db.close();
        return count;
    }

    public static native long GetTimingCount();

    public static native int setSharedMap();

    public native void setSharedMapChildTest(int shared_mem_ptr, char[] fileDes);

    public native int createAshMem();

    public static native long readAshMem(int fd);

    public static native void setAshMemVal(int fd, long val);

}