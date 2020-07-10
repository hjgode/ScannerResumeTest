package com.sample.scannerresumetest;

import android.os.Handler;
import android.provider.Settings;

import com.honeywell.aidc.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Semaphore;


public class Imager implements BarcodeReader.BarcodeListener, BarcodeReader.TriggerListener {

    // Properties
    private boolean mScanning;
    private boolean mTriggerPressed;
    private boolean mClaimed;

    boolean isScanning() { return mScanning;}
    boolean isTriggerPressed() { return mTriggerPressed;}
    boolean isClaimed() { return mClaimed;}

    public interface ImagerListener {
        void onImagerFailure();
        void onImagerData(String data);
        void onImagerTrigger(boolean triggerPressed);
        void onInternalError();
        void afterReinit();
    }
    private ImagerListener mImagerListener;

    private BarcodeReader mBarcodeReader;
    private MainActivity mMainActivity;
    private AidcManager mAidcManager;
    private boolean enabled = false;

    // Eventmanager
    private Runnable mImagerEventsManager;
    private Handler mImagerEventsHandler;
    private List<SystemAction> mImagerEvents;
    private Semaphore mImagerEventsSem;

    // Constructor
    Imager( MainActivity mainActivity ) {

        mMainActivity = mainActivity;
        mScanning = false;
        mTriggerPressed = false;
        mClaimed = false;

        mImagerListener = null;
        mBarcodeReader = null;
        mAidcManager = null;

        mImagerEventsHandler = new Handler();
        mImagerEvents = new  ArrayList<>();
        mImagerEventsSem = new Semaphore(1);
        defineImagerEvents();

        initAidcManager( false );
    }

    void setImagerListener(ImagerListener imagerListener ) {
        mImagerListener = imagerListener;
    }

    void resetImager() {
        mImagerListener = null;
        disableScanner();
    }

    void disableScanner(){
        //TODO Implement disableScanner
    }
    private void basicConfiguration() {
        try {
            if (mBarcodeReader != null) {
                Map<String, Object> properties = new HashMap<>();

                // Set Symbologies Off
                properties.put(BarcodeReader.PROPERTY_CODE_128_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_GS1_128_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_QR_CODE_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_CODE_39_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_DATAMATRIX_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_UPC_A_ENABLE, false);
                properties.put(BarcodeReader.PROPERTY_EAN_13_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_AZTEC_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_CODABAR_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_INTERLEAVED_25_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_PDF_417_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_NOTIFICATION_GOOD_READ_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_NOTIFICATION_BAD_READ_ENABLED, false);
                properties.put(BarcodeReader.PROPERTY_CENTER_DECODE, true);

                // Apply the settings
                mBarcodeReader.setProperties(properties);
            }
        } catch (Exception e) {
            Logger.e(Modules.modImager, LogMessages.errConfigImager, e);
            if (mImagerListener != null)
                mImagerListener.onInternalError();
        }
    }

    private void initAidcManager( boolean reinit ) {
        Logger.i(Modules.modImager, LogMessages.logStartAidcManager);
        /*
        Bind to the scanner service and create the singleton instance of AidcManager. Once created, the AidcManager is passed to
        the provided AidcManager.CreatedCallback in the AidcManager.CreatedCallback.onCreated(AidcManager) method.
        The application can then use the AidcManager to create a BarcodeReader.
         */
        AidcManager.create(mMainActivity, aidcManager -> {
            // Different Context!!! - Callback-Entry
            try {
                boolean thisreinit = reinit;
                if (mAidcManager != null) {
                    mBarcodeReader = null;
                    Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logImagerManufactorDriver, "Invalid AidcManager create callback recall  ..."));
                    thisreinit = true;
                }

                if (aidcManager != null) {
                    mAidcManager = aidcManager;
                    Logger.i(Modules.modImager, LogMessages.logRegisterImager);
                    mBarcodeReader = mAidcManager.createBarcodeReader();
                    mBarcodeReader.addBarcodeListener(this);

                    // set the trigger mode to client control
                    mBarcodeReader.setProperty(BarcodeReader.PROPERTY_TRIGGER_CONTROL_MODE, BarcodeReader.TRIGGER_CONTROL_MODE_CLIENT_CONTROL);

                    mBarcodeReader.addTriggerListener(this);
                    if (!thisreinit)
                        thisreinit = mMainActivity.isAlreadLoaded();// Settings.getInstance().General.mainActivity.isAlreadyLoaded();
                    resume();
                    basicConfiguration();
                    if (!thisreinit) {
                        mMainActivity.runSystemAction(Constants.SysImagerLoaded);
                    } else if (mImagerListener != null)
                        mImagerListener.afterReinit();
                }
                else {
                    Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.errStartAIDCManager, "invalid AIDC manager reference"));
                    reinitImager();
                }
            } catch (Exception e) {
                Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.errStartAIDCManager, e.getMessage()));
                reinitImager();
            }
        });
    }

    public void release() {
        releaseBarcodeReader();
        releaseAidcManager();
    }

    private boolean closeBarcodeReader() {
        try {
            if (mBarcodeReader != null) {
                Logger.i(Modules.modImager, LogMessages.logUnregisterImager);
                mBarcodeReader.removeBarcodeListener(this);
                mBarcodeReader.removeTriggerListener(this);
                mBarcodeReader.release();
                mBarcodeReader.close();
                mClaimed = false;
                mBarcodeReader = null;
            }
            return true;
        } catch( Exception e ) {
            Logger.e(Modules.modImager, String.format(Locale.US,LogMessages.errReleaseImager, e));
        }
        return false;
    }

    private boolean closeAidcManager() {
        try {
            if (mAidcManager != null) {
                Logger.v(Modules.modImager, LogMessages.logReleaseAIDC);
                mAidcManager.close();
                mAidcManager = null;
            }
            return true;
        } catch (Exception e) {
            Logger.e(Modules.modImager, String.format(Locale.US,LogMessages.errReleaseAidcManager, e));
        }
        return false;
    }

    private void releaseBarcodeReader() {
        closeBarcodeReader();
        mBarcodeReader = null;
    }

    private void releaseAidcManager() {
        closeAidcManager();
        mAidcManager = null;
    }

    void repeatReinitImager() {
        Logger.w(Modules.modImager,LogMessages.logRepeatReinitImager);
        runImagerEvent(Constants.ImgReInitImager);
    }

    void reinitImager() {
        Logger.w(Modules.modImager,LogMessages.logReinitImager);
        runImagerEvent(Constants.ImgReInitImager);
    }

    /// Claim the reader, throw exception
    void resume() {
        try {
            if( ((mBarcodeReader != null)) && !mClaimed) {
                Logger.v( Modules.modImager,LogMessages.logResumeImager );
                mBarcodeReader.claim();
                mClaimed = true;
            }
        } catch (Exception e) {
            mClaimed = false;
            Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.resume()", e.getMessage()));
            if (mImagerListener != null)
                mImagerListener.onInternalError();
        }
    }

    void pause() {
        try {
            if ((mBarcodeReader != null) && mClaimed ) {
                mClaimed = false;
                Logger.v(Modules.modImager,LogMessages.logImagerPause );
                mBarcodeReader.release();
            }
        } catch (Exception e) {
            mClaimed = true;
            Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.pause()", e.getMessage()));
            if (mImagerListener != null)
                mImagerListener.onInternalError();
        }
    }

    private void defineImagerEvents() {
        mImagerEventsManager = () -> {
            try {
                boolean idle = true;
                SystemAction thisImagerEvent = null;
                if (mImagerEventsSem.tryAcquire()) {
                    if (mImagerEvents.size() > 0) {
                        thisImagerEvent = mImagerEvents.get(0);
                        mImagerEvents.remove(0);
                        idle = false;
                    }
                    mImagerEventsSem.release();
                } else {
                    // Erneut Senden,bis OK
                    idle = false;
                }

                if ((thisImagerEvent != null)) {
                    switch (thisImagerEvent.cmd) {
                        case Constants.ImgNewData: {
                            mScanning = false;
                            try {
                                if (mImagerListener != null) {
                                    mImagerListener.onImagerData(thisImagerEvent.sValue);
                                }
                            } catch (Exception e) {
                                Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.defineImagerEvents(newData)", e.getMessage()));
                                if (mImagerListener != null)
                                    mImagerListener.onInternalError();
                            }
                            break;
                        }
                        case Constants.ImgNoRead: {
                            mScanning = false;
                            try {
                                if (mImagerListener != null) {
                                    mImagerListener.onImagerFailure();
                                }
                            } catch (Exception e) {
                                Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.defineImagerEvents(noRead)", e.getMessage()));
                                if (mImagerListener != null)
                                    mImagerListener.onInternalError();
                            }
                            break;
                        }
                        case Constants.ImgTriggerChanged: {
                            try {
                                mTriggerPressed = thisImagerEvent.bValue;
                                Logger.v(Modules.modImager, String.format(LogMessages.logImagerTrigger, (mTriggerPressed ? "pressed" : "released")));
                                if (mImagerListener != null) {
                                    mImagerListener.onImagerTrigger(mTriggerPressed);
                                }
                            } catch (Exception e) {
                                Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.defineImagerEvents(triggerChanged) - Listener", e.getMessage()));
                            }
                            try {
                                boolean newScanning = (mTriggerPressed && enabled);
                                if (newScanning != mScanning) {
                                    Logger.v(Modules.modImager, String.format(LogMessages.logDoScanning, (newScanning ? "Activate" : "Deactivate")));
                                    mScanning = newScanning;
                                    mBarcodeReader.aim(mScanning);
                                    mBarcodeReader.light(mScanning);
                                    mBarcodeReader.decode(mScanning);

                                }
                            } catch (Exception e) {
                                Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.defineImagerEvents(triggerChanged)", e.getMessage()));
                                if (mImagerListener != null)
                                    mImagerListener.onInternalError();
                            }
                            break;
                        }
                        case Constants.ImgReInitImager: {
                            execReInitImager();
                            break;
                        }
                        default: {
                        }
                    }
                }
                if (!idle)
                    mImagerEventsHandler.postDelayed(mImagerEventsManager, 10);
            } catch (Exception e) {
                Logger.wtf(Modules.modImager, LogMessages.errImager, e.fillInStackTrace());
            }
        };
    }

    private void execReInitImager() {
        Handler h = new Handler();
        h.post(() -> {
            boolean ok = closeBarcodeReader();
            if (ok)
                ok = closeAidcManager();
            if (ok)
                initAidcManager(true);
            if (!ok) {
                try {
                    Thread.sleep(Constants.msRepeatReInitImager);
                } catch (Exception ignored) {}
                repeatReinitImager();
            }
        });
    }

    private void runImagerEvent( int event ) {
        SystemAction newEvent = new SystemAction(event);
        runImagerEvent( newEvent );
    }

    private void runImagerEvent( int event,boolean eventData ) {
        SystemAction newEvent = new SystemAction(event,eventData);
        runImagerEvent( newEvent );
    }

    private void runImagerEvent( int event,String eventData ) {
        SystemAction newEvent = new SystemAction(event,eventData);
        runImagerEvent( newEvent );
    }

    public void runImagerEvent(SystemAction newImagerEvent) {
        runImagerEvent(newImagerEvent, 0);
    }

    public void runImagerEvent(SystemAction newImagerEvent, int delay) {
        try {
            if( mMainActivity.isAppActive() ) {
                mImagerEventsSem.acquire();
                mImagerEvents.add(newImagerEvent);
                mImagerEventsSem.release();
                mImagerEventsHandler.postDelayed(mImagerEventsManager, delay);
            }
        } catch (InterruptedException e) {
            Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.runImagerEvent()", e.getMessage()));
        }
    }

    @Override
    public void onBarcodeEvent(BarcodeReadEvent barcodeReadEvent) {
        try {
            runImagerEvent( Constants.ImgNewData,barcodeReadEvent.getBarcodeData());
        } catch( Exception e ) {
            Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.onBarcodeEvent()", e.getMessage()));
            if (mImagerListener != null)
                mImagerListener.onInternalError();
        }
    }

    @Override
    public void onFailureEvent(BarcodeFailureEvent barcodeFailureEvent) {
        try {
            runImagerEvent( Constants.ImgNoRead );
        } catch (Exception e) {
            Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.onFailureEvent()", e.getMessage()));
            if (mImagerListener != null)
                mImagerListener.onInternalError();
        }
    }

    @Override
    public void onTriggerEvent(TriggerStateChangeEvent triggerStateChangeEvent) {
        try {
            runImagerEvent( Constants.ImgTriggerChanged,triggerStateChangeEvent.getState() );
        } catch (Exception e) {
            Logger.e(Modules.modImager, String.format(Locale.US, LogMessages.logInternalError, "Imager.onTriggerEvent()", e.getMessage()));
            if (mImagerListener != null)
                mImagerListener.onInternalError();
        }
    }
}
