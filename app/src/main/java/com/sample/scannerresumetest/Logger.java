package com.sample.scannerresumetest;

import android.util.Log;

public class Logger {
    public static void i(String sTAG, String sLog){
        Log.i(sTAG,sLog);
    }
    public static void w(String sTAG, String sLog){
        Log.w(sTAG,sLog);
    }
    public static void v(String sTAG, String sLog){
        Log.v(sTAG,sLog);
    }    public static void e(String sTAG, String sLog, Exception ex){
        Log.e(sTAG, sLog + ": "+ ex.getMessage());
    }
    public static void e(String sTAG, String sLog){
        Log.e(sTAG, sLog );
    }
    public static void wtf(String sTAG, String sLog, Throwable ex){
        Log.e(sTAG, sLog +": "+ ex.getMessage());
    }
}
