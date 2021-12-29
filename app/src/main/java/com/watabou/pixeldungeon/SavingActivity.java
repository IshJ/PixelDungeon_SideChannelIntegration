package com.watabou.pixeldungeon;

import android.os.Bundle;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.View;

import java.io.IOException;
import java.util.stream.Collectors;

import static com.watabou.pixeldungeon.PixelDungeon.methodIdMap;

public class SavingActivity extends AppCompatActivity {

    public static final String TAG = "SavingActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_saving);

        try {
            copyMethodMap();
            Log.d(TAG + "#", getDatabasePath("SideScan").toString());

            Process p = null;

            p = Runtime.getRuntime().exec("cp " + getDatabasePath("SideScan") + ".db /sdcard/Documents");
            p = Runtime.getRuntime().exec("cp " + getDatabasePath("MainApp") + ".db /sdcard/Documents");
            Log.d(TAG, "Saving Completed");
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        finish();
    }

    private void copyMethodMap() {
        String methodMapString = methodIdMap.entrySet().parallelStream().map(Object::toString).collect(Collectors.joining("|"));
        Log.d("MethodMap", methodMapString);
        Log.d("MethodMapCount", String.valueOf(methodIdMap.size()));

    }

}