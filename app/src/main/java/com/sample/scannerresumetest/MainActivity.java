package com.sample.scannerresumetest;

import androidx.appcompat.app.AppCompatActivity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.honeywell.aidc.AidcManager;
import com.honeywell.aidc.BarcodeFailureEvent;
import com.honeywell.aidc.BarcodeReadEvent;
import com.honeywell.aidc.BarcodeReader;
import com.honeywell.aidc.ScannerUnavailableException;
import com.honeywell.aidc.TriggerStateChangeEvent;

import static com.honeywell.aidc.BarcodeReader.PROPERTY_TRIGGER_CONTROL_MODE;

public class MainActivity extends AppCompatActivity implements BarcodeReader.BarcodeListener,
        BarcodeReader.TriggerListener{
    private static String TAG="ScannerResumeTest";
    private boolean scannerEnabled=true;
    private static BarcodeReader barcodeReader;
    private static boolean bPhoneHasBeenUnlocked=false;
    private AidcManager manager;
    private Context _context=this;
    private int iResumeCount=0, iScanCount=0;

    // START modded
    boolean mIsAlreadLoaded=false;
    public boolean isAlreadLoaded(){
        return mIsAlreadLoaded;
    };
    public void runSystemAction(int iAction){
        Log.i(TAG, "runSystemAction called: "+iAction);
    }
    public boolean isAppActive(){
        Log.i(TAG, "isAppActive called: ");
        return true;
    }
    //END modded

    TextView txtScan, txtAmount, txtAmount2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(savedInstanceState!=null){
            iResumeCount=savedInstanceState.getInt("MyResumeCount");
            iScanCount=savedInstanceState.getInt("MyScanCount");
        }
        txtScan=findViewById(R.id.txtScan);
        txtAmount=findViewById(R.id.txtResumeCount);
        txtAmount2=findViewById(R.id.txtScanCount);

        try {
            txtAmount.setText(""+iResumeCount);
            txtAmount2.setText(""+iScanCount);
        }catch (Exception ex)
        {
            Log.e(TAG, "setText failed: "+ex.getMessage());
        }
        createScanner();

        PhoneUnlockedReceiver receiver = new PhoneUnlockedReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(receiver, filter);
    }

    public class PhoneUnlockedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_USER_PRESENT)){
                Log.d(TAG, "Phone unlocked");
                iResumeCount++;
                txtAmount.setText(""+iResumeCount);
//                claimScanner();
//                doScan();
                bPhoneHasBeenUnlocked =true;
            }else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)){
                Log.d(TAG, "Phone locked");
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        // Save UI state changes to the savedInstanceState.
        // This bundle will be passed to onCreate if the process is
        // killed and restarted.
//        savedInstanceState.putBoolean("MyBoolean", true);
//        savedInstanceState.putDouble("myDouble", 1.9);
//        savedInstanceState.putInt("MyInt", 1);
        savedInstanceState.putInt("MyResumeCount", iResumeCount);
        savedInstanceState.putInt("MyScanCount", iScanCount);
        // etc.
    }

    private void doStopScan(){
        if(barcodeReader!=null) {
            try {
                barcodeReader.aim(false);
                barcodeReader.light(false);
                barcodeReader.decode(false);
            }catch (Exception ex){
                Log.d(TAG, "exception in doStopScan(): " +ex.getMessage());
            }
        }
    }

    private void doScan(){
        Log.d(TAG, "doScan");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, barcodeReader.getStringProperty(PROPERTY_TRIGGER_CONTROL_MODE));
                }catch(Exception ex){

                }
                if(!scannerEnabled){
                    Log.d(TAG, "Scanner disabled");
                    return;
                }
                if(barcodeReader!=null) {
                    try {
                        barcodeReader.aim(true);
                        barcodeReader.light(true);
                        barcodeReader.decode(true);
                        final Handler handler = new Handler();
                        handler.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                // Do something after 5s = 5000ms
                                doStopScan();
                            }
                        }, 5000);
                    } catch (Exception ex) {
                        Log.d(TAG, "exception in doScan(): " + ex.getMessage());
                        destroyScanner();
                        createScanner();
                        claimScanner();
                    }
                }

            }
        });
    }

    private void createScanner(){
        Log.d(TAG, "createScanner");
        // create the AidcManager providing a Context and a
        // CreatedCallback implementation.
        AidcManager.create(this, new AidcManager.CreatedCallback() {

            @Override
            public void onCreated(AidcManager aidcManager) {
                manager = aidcManager;
                try {
                    barcodeReader = manager.createBarcodeReader();
                    claimScanner();
                }catch(Exception ex){
                    Log.e(TAG, "exception in manager.createBarcodeReader: "+ex.getMessage());
                }
            }
        });

    }

    void destroyScanner(){
        if (barcodeReader != null) {
            releaseScanner();
            // unregister barcode event listener
            barcodeReader.removeBarcodeListener(this);
            // unregister trigger state change listener
            barcodeReader.removeTriggerListener(this);
            // close BarcodeReader to clean up resources.
            barcodeReader.close();
            barcodeReader = null;
        }

        if (manager != null) {
            // close AidcManager to disconnect from the scanner service.
            // once closed, the object can no longer be used.
            manager.close();
            manager=null;
        }
    }

    private void claimScanner(){
        Log.d(TAG, "claimScanner");
        if (barcodeReader != null) {
            try {
                barcodeReader.claim();
                barcodeReader.addBarcodeListener(this);
                barcodeReader.addTriggerListener(this);
            } catch (ScannerUnavailableException e) {
                e.printStackTrace();
                Toast.makeText(this, "Scanner unavailable", Toast.LENGTH_SHORT).show();
            }
            try {
                barcodeReader.setProperty(PROPERTY_TRIGGER_CONTROL_MODE, BarcodeReader.TRIGGER_CONTROL_MODE_CLIENT_CONTROL);
            }catch (Exception ex){
                Toast.makeText(this, "PROPERTY_TRIGGER_CONTROL_MODE failed: "+ex.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

    }

    private void releaseScanner(){
        if (barcodeReader != null) {
            // release the scanner claim so we don't get any scanner
            // notifications while paused.
            try {
                barcodeReader.setProperty(PROPERTY_TRIGGER_CONTROL_MODE, BarcodeReader.TRIGGER_CONTROL_MODE_AUTO_CONTROL);
                // unregister barcode event listener
                barcodeReader.removeBarcodeListener(this);
                // unregister trigger state change listener
                barcodeReader.removeTriggerListener(this);
            }catch (Exception ex){
                Toast.makeText(this, "PROPERTY_TRIGGER_CONTROL_MODE failed: "+ex.getMessage(), Toast.LENGTH_LONG).show();
            }
            barcodeReader.release();
        }

    }

    @Override
    public void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        claimScanner();
        if(bPhoneHasBeenUnlocked){  //Resume is also called after a Power Resume
            doScan();
            bPhoneHasBeenUnlocked =false;
        }
    }

    @Override
    public void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        releaseScanner();
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
        destroyScanner();
    }

    @Override
    public void onFailureEvent(BarcodeFailureEvent arg0) {
        Log.d(TAG, "onFailureEvent");
        runOnUiThread(new Runnable() {

            @Override
            public void run() {
                Toast.makeText(_context, "No data", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // When using Automatic Trigger control do not need to implement the
    // onTriggerEvent function
    @Override
    public void onTriggerEvent(TriggerStateChangeEvent event) {
        Log.d(TAG, "onTriggerevent");
        if(scannerEnabled && event.getState()==true) //pressed?
            doScan();
        else if (event.getState()==false)
            doStopScan();

    }

    @Override
    public void onBarcodeEvent(final BarcodeReadEvent event) {
        Log.d(TAG, "onBarcodeEvent: " + event.getBarcodeData());
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // update UI to reflect the data
                txtScan.setText(event.getBarcodeData());
                iScanCount++;
                txtAmount2.setText(""+iScanCount);
            }
        });
    }

}
