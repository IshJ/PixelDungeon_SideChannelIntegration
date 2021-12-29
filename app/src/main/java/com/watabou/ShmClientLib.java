package com.watabou;

public class ShmClientLib {
    static {
        System.loadLibrary("native-lib");
    }

    public static native int setVal(int pos, int val);
    public static native int getVal(int pos);
    public static native void setMap(int fd , int size);

}

