package net.kwatts.powtools;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.databinding.DataBindingUtil;
import android.databinding.Observable;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.location.Address;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.app.AppCompatDelegate;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.SwitchCompat;
import android.support.v7.widget.Toolbar;
import android.content.pm.PackageManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.Chronometer;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;


import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.Legend;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;
import com.github.mikephil.charting.data.PieEntry;
import com.github.mikephil.charting.utils.ColorTemplate;
import com.google.android.gms.location.LocationRequest;
import com.patloew.rxlocation.RxLocation;
import com.tbruyelle.rxpermissions2.RxPermissions;

import net.kwatts.powtools.database.entities.Attribute;
import net.kwatts.powtools.database.entities.Moment;
import net.kwatts.powtools.database.entities.Ride;
import net.kwatts.powtools.events.NotificationEvent;
import net.kwatts.powtools.model.OWDevice;
import net.kwatts.powtools.util.BluetoothUtil;
import net.kwatts.powtools.util.BluetoothUtilImpl;
import net.kwatts.powtools.util.DiagramCache;
import net.kwatts.powtools.util.Notify;
import net.kwatts.powtools.util.PermissionUtil;
import net.kwatts.powtools.util.SharedPreferencesUtil;
import net.kwatts.powtools.util.SpeedAlertResolver;
import net.kwatts.powtools.util.debugdrawer.DebugDrawerMockBle;
import net.kwatts.powtools.view.AlertsMvpController;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.honorato.multistatetogglebutton.MultiStateToggleButton;
import org.honorato.multistatetogglebutton.ToggleButton;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import io.palaima.debugdrawer.DebugDrawer;
import io.palaima.debugdrawer.commons.SettingsModule;
import io.palaima.debugdrawer.timber.TimberModule;
import io.reactivex.Single;
import io.reactivex.observers.DisposableObserver;
import io.reactivex.observers.DisposableSingleObserver;
import io.reactivex.schedulers.Schedulers;
import timber.log.Timber;

import static net.kwatts.powtools.model.OWDevice.KEY_RIDE_MODE;
import static net.kwatts.powtools.model.OWDevice.MockOnewheelCharacteristicSpeed;


// http://blog.davidvassallo.me/2015/09/02/ble-health-devices-first-steps-with-android/
// https://github.com/alt236/Bluetooth-LE-Library---Android
// https://github.com/Fakher-Hakim/android-BluetoothLeGatt
// https://developer.android.com/tools/data-binding/guide.html
// https://github.com/iDevicesInc/SweetBlue
// https://www.evilsocket.net/2015/01/29/nike-fuelband-se-ble-protocol-reversed/
// mapping to google maps with geojson? https://developers.google.com/maps/documentation/android-api/utility/geojson#style-feature
// OW stats: 58VDC charger, 3.5Amp with 130Wh (LiFEPO4 Nano-phosphate Litium) and 500W motor
// Other stats: Likely 7500 mah = 7500/58 is 130Wh
// Calculations: 130wh/48v = 2.7AH  - a 2.7AH battery would take 54 minutes to charge.... (2.7/3.5 = 0.9 x 60 minutes)
// AMP hours = a battery with 1 amp-hour supplies 1 amp to load for 1 hour. 2 amps for 1/2 hour, etc
// I (current measured in Amps) = V (Volts) / R (resistance,ohms)
// The consumed power of motor is P (input power, measured in Watts) = I (Amps) * V (applied Voltage)
// Should show stats with,
// - Speed
// - Consumed power of motor (W)
// - Consumed power total
// 12.8V 6.9Ah 88.32Wh

public class MainActivity extends AppCompatActivity implements
        SharedPreferences.OnSharedPreferenceChangeListener, ActivityCompat.OnRequestPermissionsResultCallback  {

    private static final boolean ONEWHEEL_LOGGING = true;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private static final int WRITE_EXTERNAL_STORAGE_PERMS = 2;


    MultiStateToggleButton mRideModeToggleButton;
    int mRideModePosition;
    boolean mRideModePositionSetOnceFlag;

    private android.os.Handler mLoggingHandler = new Handler();
    private SpeedAlertResolver speedAlertResolver = new SpeedAlertResolver(App.INSTANCE.getSharedPreferences());

    private Handler handler;
    private static int periodicChallengeCount = 0;

    private Context mContext;
    ScrollView mScrollView;
    Chronometer mChronometer;
    OWDevice mOWDevice;
    net.kwatts.powtools.databinding.ActivityMainBinding mBinding;
    BluetoothUtil bluetoothUtil;
    Notify notify;

    PieChart mBatteryChart;
    Ride ride;
    private DisposableObserver<Address> rxLocationObserver;
    private AlertsMvpController alertsController;


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEvent(NotificationEvent event){
        Timber.d( event.message + ":" + event.title);
        final String title = event.title;
        final String message = event.message;
        runOnUiThread(() -> {
            notify.status(title, message);
        });
    }

    public void updateLog(final String msg) {
        Timber.i(msg);
        runOnUiThread(() -> {
            //mBinding.owLog.setMovementMethod(new ScrollingMovementMethod());
            mBinding.owLog.append("\n" + msg);
            mScrollView.fullScroll(View.FOCUS_DOWN);
        });

    }

    private void initBatteryChart() {
        mBatteryChart = findViewById(R.id.batteryPieChart);
        // configure pie chart
        mBatteryChart.setUsePercentValues(true);
        mBatteryChart.setDescription(new Description());
        // enable hole and configure
        mBatteryChart.setDrawHoleEnabled(true);
        Legend legend = mBatteryChart.getLegend();
        legend.setEnabled(false);
    }

    public void updateBatteryRemaining(final int percent) {
        // Update ongoing notification
        notify.remain(percent);
        notify.alert(percent);

        runOnUiThread(() -> {
            try {
                ArrayList<PieEntry> entries = new ArrayList<>();
                entries.add(new PieEntry(percent, 0));
                entries.add(new PieEntry(100 - percent, 1));
                PieDataSet dataSet = new PieDataSet(entries, "battery percentage");
                ArrayList<Integer> mColors = new ArrayList<>();
                mColors.add(ColorTemplate.rgb("#2E7D32")); //green
                mColors.add(ColorTemplate.rgb("#C62828")); //red
                dataSet.setColors(mColors);
                dataSet.setDrawValues(false);

                PieData newPieData = new PieData( dataSet);
                mBatteryChart.setCenterText(percent + "%");
                mBatteryChart.setCenterTextTypeface(Typeface.DEFAULT_BOLD);
                mBatteryChart.setCenterTextColor(ColorTemplate.rgb("#616161"));
                mBatteryChart.setCenterTextSize(20f);
                mBatteryChart.setDescription(null);

                mBatteryChart.setData(newPieData);
                mBatteryChart.notifyDataSetChanged();
                mBatteryChart.invalidate();
            } catch (Exception e) {
                Timber.e( "Got an exception updating battery:" + e.getMessage());
            }
        });

        alertsController.handleChargePercentage(percent);
    }
    public void deviceConnectedTimer(final boolean start) {
        runOnUiThread(() -> {
            if (start) {
/*
                mChronometer.setOnChronometerTickListener(new Chronometer.OnChronometerTickListener() {
                    @Override
                    public void onChronometerTick(Chronometer chronometer) {
                        long systemCurrTime = SystemClock.elapsedRealtime();
                        long chronometerBaseTime = mChronometer.getBase();
                        long deltaTimeSeconds = TimeUnit.MILLISECONDS.toSeconds(systemCurrTime - chronometerBaseTime);

                        //Gemini unlocker, write firmware periodically (< 24 seconds) or disconnects
                        //if (deltaTimeSeconds % 15L == 0) {
                            //mOWDevice.sendKeyChallengeForGemini(getBluetoothUtil());
                        //}
                    }
                });
*/
                mChronometer.setBase(SystemClock.elapsedRealtime());
                mChronometer.start();
            } else {
                mChronometer.stop();
            }
        });


    }

    private void periodicChallenge() {
        final int repeatTime = 15000;

        periodicChallengeCount++;

        handler.postDelayed(new Runnable() {
            public void run() {
                if (getBluetoothUtil().isPeriodicChallengeRequired() && mOWDevice != null && getBluetoothUtil().getIsUnlocked() == 2) {
                    mOWDevice.sendKeyChallengeForGemini(getBluetoothUtil());
                }
                if (periodicChallengeCount == 1) {
                    handler.postDelayed(this, repeatTime);
                } else {
                    periodicChallengeCount--;
                }
            }
        }, repeatTime);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Timber.d( "Starting...");
        super.onCreate(savedInstanceState);

        //Permission to write to storage
        enableWritingToStorage();

        mContext = this;

        notify = new Notify();
        notify.init(MainActivity.this);

        EventBus.getDefault().register(this);

        initWakelock();

        // this line won't compile? File -> Invalidate caches https://stackoverflow.com/a/42824662/247325
        mBinding = DataBindingUtil.setContentView(this, net.kwatts.powtools.R.layout.activity_main);

        alertsController = new AlertsMvpController(this);

        setupDarkModes(savedInstanceState);

        App.INSTANCE.getSharedPreferences().registerListener(this);

        if (!App.INSTANCE.getSharedPreferences().isEulaAgreed()) {
            showEula();
        }


        setupOWDevice();

        setupToolbar();

        mScrollView = findViewById(R.id.logScroller);

        if (App.INSTANCE.getSharedPreferences().isLoggingEnabled()) {
            initLogging();
        }

        mChronometer = findViewById(R.id.chronometer);

        initBatteryChart();
        initLightSettings();
        initRideModeButtons();

        new DebugDrawer.Builder(this)
                .modules(
                        new DebugDrawerMockBle(this),
                        new SettingsModule(this),
                        new TimberModule()
                ).build();

        handler = new Handler(Looper.getMainLooper());
        periodicChallenge();
    }

    public BluetoothUtil getBluetoothUtil() {
        if (bluetoothUtil == null) {
            bluetoothUtil = new BluetoothUtilImpl();
        }

        return bluetoothUtil;
    }

    public void provideBluetoothUtil(BluetoothUtil bluetoothUtil){
        this.bluetoothUtil = bluetoothUtil;
        bluetoothUtil.init(this, mOWDevice);
    }

    private void initWakelock() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void setupToolbar() {
        Toolbar mToolbar = findViewById(R.id.tool_bar);
        mToolbar.setTitle("POWheel");
        mToolbar.setLogo(R.mipmap.ic_launcher);
        setSupportActionBar(mToolbar);
    }

    private void setupOWDevice() {
        mOWDevice = new OWDevice();
        mBinding.setOwdevice(mOWDevice);

        mOWDevice.showDebugWindow.set(App.INSTANCE.getSharedPreferences().isDebugging());
        mOWDevice.isOneWheelPlus.set(App.INSTANCE.getSharedPreferences().isOneWheelPlus());
        mOWDevice.setupCharacteristics();
        mOWDevice.isConnected.set(false);

        //mOWDevice.bluetoothLe.set("Off");
        //mOWDevice.bluetoothStatus.set("Disconnected");

        mOWDevice.isConnected.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable observable, int i) {
                if (mOWDevice.isConnected.get() && isNewOrNotContinuousRide()) {
                    ride = new Ride();
                    App.dbExecute(database -> ride.id = database.rideDao().insert(ride));
                }
            }
        });

        mOWDevice.characteristics.get(MockOnewheelCharacteristicSpeed).value.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable observable, int i) {
                String speedString = mOWDevice.characteristics.get(MockOnewheelCharacteristicSpeed).value.get();
                alertsController.handleSpeed(speedString);
                //updateGaugeOnSpeedChange(mProgressiveGauge, speedString);
            }
        });
        getBluetoothUtil().init(MainActivity.this, mOWDevice);
    }


    private void logOnChange(OWDevice.DeviceCharacteristic deviceCharacteristic) {
        deviceCharacteristic.value.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable observable, int i) {
                Timber.d(deviceCharacteristic.key.get() + " = " + deviceCharacteristic.value.get());
            }
        });
    }

    boolean isNewOrNotContinuousRide() {

        if (ride == null) {
            return true;
        }
        if (ride.end == null) {
            Timber.e("isNewOrNotContinuousRide: unexpected state, ride.end not set");
            return true;
        }

        long millisSinceLastMoment = new Date().getTime() - ride.end.getTime();
        // Not continuous is defined as 1 min break. Maybe configurable in the future.
        return TimeUnit.MINUTES.toMillis(1) > millisSinceLastMoment;
    }

    private void showEula() {
        new MaterialDialog.Builder(this)
                .theme(Theme.LIGHT)
                .title("WARNING")
                .content(getResources().getString(R.string.eula, BuildConfig.VERSION_NAME))
                .positiveText("AGREE")
                .onPositive((dialog, which) ->
                        App.INSTANCE.getSharedPreferences().setEulaAgreed(true))
                .show();
    }

    private void setupDarkModes(Bundle savedInstanceState) {
        if (App.INSTANCE.getSharedPreferences().isDayNightMode()) {
            if (savedInstanceState == null) {
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_AUTO);
                recreate();
            }
        }
        if (App.INSTANCE.getSharedPreferences().isDarkNightMode()) {
            if (savedInstanceState == null) {
                getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                recreate();
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }
    @Override
    public void onDestroy() {
        App.INSTANCE.getSharedPreferences().removeListener(this);
        notify.stopStatusNotification();
        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);

        enableOptionsMenu(menu);
        return true;
    }

    private void enableOptionsMenu(Menu menu) {
        if(getBluetoothUtil().isConectionEnabled())
        {
            showConnectedMenus(menu);
        } else {
            showConnectionDisabledMenu(menu);
        }


        /* AJREDO
        if (mOWDevice.isConnected.get()) {
            showConnectedMenus(menu);
        } else if (!getBluetoothUtil().isScanning()) {
            showConnectionDisabledMenu(menu);
        } else {
            showScanningMenus(menu);
        }*/
    }

    private void showConnectionDisabledMenu(Menu menu) {
        menu.findItem(R.id.menu_stop).setVisible(false);
        menu.findItem(R.id.menu_scan).setVisible(true);
        menu.findItem(R.id.menu_disconnect).setVisible(false);
        menu.findItem(R.id.menu_refresh).setActionView(null);
    }

    private void showScanningMenus(Menu menu) {
        menu.findItem(R.id.menu_stop).setVisible(true);
        menu.findItem(R.id.menu_scan).setVisible(false);
        menu.findItem(R.id.menu_disconnect).setVisible(false);
        menu.findItem(R.id.menu_refresh).setActionView(R.layout.actionbar_progress_indeterminate);
    }

    private void showConnectedMenus(Menu menu) {
        menu.findItem(R.id.menu_disconnect).setVisible(true);
        menu.findItem(R.id.menu_stop).setVisible(false);
        menu.findItem(R.id.menu_scan).setVisible(false);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_logs:
                startActivity(new Intent(MainActivity.this, RidesListActivity.class));
                break;
            case R.id.menu_scan:


                getPermissions().subscribe(new DisposableSingleObserver<Boolean>() {
                           @Override
                           public void onSuccess(Boolean aBoolean) {
                               getBluetoothUtil().startScanning();
                               // TODO move this to where we're actually connected to device? (or maybe its better here so we can achieve a location lock before logging)
                               if (App.INSTANCE.getSharedPreferences().isLoggingEnabled()) {
                                   startLocationScan();
                               }
                           }

                           @Override
                           public void onError(Throwable e) {
                                e.printStackTrace();
                           }
                });

                break;
            case R.id.menu_stop:
                getBluetoothUtil().stopScanning();
                notify.waiting();
                this.invalidateOptionsMenu();
                break;
            case R.id.menu_disconnect:
                mOWDevice.isConnected.set(false);
                getBluetoothUtil().disconnect();
                notify.waiting();
                Timber.i("Disconnected from device by user.");
                deviceConnectedTimer(false);
                mLoggingHandler.removeCallbacksAndMessages(null);
                this.invalidateOptionsMenu();
                break;
            case R.id.menu_about:
                showEula();
                break;
            case R.id.menu_settings:
                Intent i = new Intent(this, MainPreferencesActivity.class);
                startActivity(i);
                break;
            case R.id.menu_refresh:
                createNewRide();
                break;
        }

        return true;
    }

    private void createNewRide() {
        App.dbExecute((database) -> database.rideDao().insert(new Ride()));
    }

    private void startLocationScan() {

        RxLocation rxLocation = new RxLocation(this);

        LocationRequest locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(TimeUnit.SECONDS.toMillis(5));

        rxLocationObserver = rxLocation.location()
                .updates(locationRequest)
                .subscribeOn(Schedulers.io())
                .flatMap(location -> rxLocation.geocoding().fromLocation(location).toObservable())
                .observeOn(Schedulers.io())
                .subscribeWith(new DisposableObserver<Address>() {
                    @Override public void onNext(Address address) {

                        boolean isLocationsEnabled = App.INSTANCE.getSharedPreferences().isLocationsEnabled();
                        if (isLocationsEnabled) {
                            mOWDevice.setGpsLocation(address);
                        } else if (rxLocationObserver != null) {
                            rxLocationObserver.dispose();
                        }
                    }

                    @Override public void onError(Throwable e) {
                        Timber.e( "onError: error retreiving location", e);
                    }

                    @Override public void onComplete() {
                        Timber.d( "onComplete: ");
                    }
                });
    }

    private Single<Boolean> getPermissions() {
        // TODO I think this is necessary since changing the target api
        RxPermissions rxPermissions = new RxPermissions(this);
        return rxPermissions
                .request(Manifest.permission.ACCESS_FINE_LOCATION)
                .firstOrError();
    }


    @Override
    protected void onResume() {
        super.onResume();

        if (getBluetoothUtil().isConnected()) {
            mOWDevice.bluetoothStatus.set("Connected");
        } else {
            getBluetoothUtil().reconnect(this);
        }

        alertsController.recaptureMedia(this);



        this.invalidateOptionsMenu();
    }

    @Override
    protected void onPause() {
        super.onPause();

        // TODO if we want background support, we don't want to do this here
        if (rxLocationObserver != null) {
            rxLocationObserver.dispose();
        }

        // TODO this is not ideal but is recommended because it saves battery
        alertsController.releaseMedia();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Timber.i("onSharedPreferenceChanged callback");
        switch (key) {
            case SharedPreferencesUtil.METRIC_UNITS:
                mOWDevice.refreshCharacteristics();
                refreshMetricViews();
                break;

            case SharedPreferencesUtil.DARK_NIGHT_MODE:
                boolean checkDarkNightMode = sharedPreferences.getBoolean(key, false);
                getDelegate().setLocalNightMode(
                        checkDarkNightMode ?
                                AppCompatDelegate.MODE_NIGHT_YES
                                : AppCompatDelegate.MODE_NIGHT_NO);

                recreate();
//                mTracker.send(new HitBuilders.EventBuilder().setCategory("SharedPreferences").setAction("darkNightMode")
//                        .setLabel((checkDarkNightMode) ? "on" : "off").build());
                break;

            case SharedPreferencesUtil.LOG_LOCATIONS:
                boolean checkLogLocations = sharedPreferences.getBoolean(key, false);
                if (!checkLogLocations && mOWDevice != null) {
                    mOWDevice.setGpsLocation(null);
                }
                break;

            case SharedPreferencesUtil.REMAIN_METHOD:
                if (mOWDevice != null) {
                    mOWDevice.forceBatteryRemaining();
                }
                break;

            default:
                Timber.d( "onSharedPreferenceChanged: " + key);
        }

    }

    private void refreshMetricViews() {
        // TODO Auto convert the speed alert for the user or meh?
    }

    public void initLogging() {
        if (ONEWHEEL_LOGGING) {
            Runnable deviceFileLogger = new Runnable() {
                @Override
                public void run() {
                    int mLoggingFrequency = App.INSTANCE.getSharedPreferences().getLoggingFrequency();
                    mLoggingHandler.postDelayed(this, mLoggingFrequency);
                    if (mOWDevice.isConnected.get()) {
                        try {
                            persistMoment();

                        } catch (Exception e) {
                            Timber.e( "unable to write logs", e);
                        }
                    }
                }
            };
            mLoggingHandler.postDelayed(deviceFileLogger, App.INSTANCE.getSharedPreferences().getLoggingFrequency());

        }
    }

    private void persistMoment() throws Exception {
        App.dbExecute(database -> {
            Date latestMoment = new Date();

            if (ride.start == null) {
                ride.start = latestMoment;
            }
            ride.end = latestMoment;
            database.rideDao().updateRide(ride);

            Moment moment = new Moment(ride.id, latestMoment);
            moment.rideId = ride.id;
            long momentId = database.momentDao().insert(moment);
            List<Attribute> attributes = new ArrayList<>();
            for (OWDevice.DeviceCharacteristic deviceReadCharacteristic : mOWDevice.getNotifyCharacteristics()) {
                Attribute attribute = new Attribute();
                attribute.setMomentId(momentId);

                String k = deviceReadCharacteristic.key.get();
                String v = deviceReadCharacteristic.value.get();


                attribute.setValue(v);
                attribute.setKey(k);

                attributes.add(attribute);
            }
            database.attributeDao().insertAll(attributes);
            if (mOWDevice.getGpsLocation() != null) {
                moment.setGpsLat(mOWDevice.getGpsLocation().getLatitude());
                moment.setGpsLong(mOWDevice.getGpsLocation().getLongitude());
            }
            LogMomentToSd(attributes, latestMoment);
        });
    }

    private void LogMomentToSd(List<Attribute> attributes, Date moment) {
        if(App.momentLogger == null) {
            App.momentLogger = new MomentLogger();
        }
        App.momentLogger.log(moment, attributes);
    }

    SwitchCompat mMasterLight;
    SwitchCompat mCustomLight;
    SwitchCompat mFrontBright;
    SwitchCompat mBackBright;
    SwitchCompat mFrontBlink;
    SwitchCompat mBackBlink;

    int frontBlinkCount = 0;
    int backBlinkCount = 0;

    public void updateStateMachine(DiagramCache diagramCache, String message) {
        ((TextView)findViewById(R.id.event)).setText(message);
        new DownloadImageTask((ImageView) findViewById(R.id.state_diagram)).execute(diagramCache);
    }

    private class DownloadImageTask extends AsyncTask<DiagramCache, Void, Bitmap> {
        ImageView bmImage;
        public DownloadImageTask(ImageView bmImage) {
            this.bmImage = bmImage;
        }

        protected Bitmap doInBackground(DiagramCache... diagramCaches) {
            DiagramCache diagramCache = diagramCaches[0];
            Bitmap diagramBitmap = null;
            try {
                InputStream diagramInputStream = diagramCache.getActiveStateDiagram();
                if(diagramInputStream != null) {
                    diagramBitmap = BitmapFactory.decodeStream(diagramInputStream);
                }
            } catch (Exception e) {
                Log.e("Error", e.getMessage());
                e.printStackTrace();
            }
            return diagramBitmap;
        }

        protected void onPostExecute(Bitmap result) {
            bmImage.setImageBitmap(result);
        }
    }

    public class mFrontBlinkTaskTimerTask extends TimerTask
    {
        @Override
        public void run() {
            if ((frontBlinkCount % 2) == 0) {
                mOWDevice.setCustomLights(getBluetoothUtil(), 0, 0, 60);
            } else {
                mOWDevice.setCustomLights(getBluetoothUtil(), 0, 0, 0);
            }
            frontBlinkCount++;
        }
    }
    mFrontBlinkTaskTimerTask mFrontBlinkTimerTask;
    Timer mFrontBlinkTimer;

    public class mBackBlinkTaskTimerTask extends TimerTask
    {
        @Override
        public void run() {
            if ((backBlinkCount % 2) == 0) {
                mOWDevice.setCustomLights(getBluetoothUtil(), 1, 1, 60);
            } else {
                mOWDevice.setCustomLights(getBluetoothUtil(), 1, 1, 0);
            }
            backBlinkCount++;
        }
    }
    mBackBlinkTaskTimerTask mBackBlinkTimerTask;
    Timer mBackBlinkTimer;

    public void initLightSettings() {
        mMasterLight = this.findViewById(R.id.master_light_switch);
        mCustomLight = this.findViewById(R.id.custom_light_switch);

        mFrontBright = this.findViewById(R.id.front_bright_switch);
        mBackBright = this.findViewById(R.id.back_bright_switch);
        mFrontBlink = this.findViewById(R.id.front_blink_switch);
        mBackBlink = this.findViewById(R.id.back_blink_switch);

        mMasterLight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mOWDevice.isConnected.get()) {
                if (isChecked) {
                    Timber.i("Lights turned on");
                    mOWDevice.setLights(getBluetoothUtil(), 1);
                } else {
                    Timber.i("Lights turned off");
                    mOWDevice.setLights(getBluetoothUtil(), 0);
                }
            }
        });

        mCustomLight.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mOWDevice.isConnected.get()) {
                if (isChecked) {
                    Timber.i("Custom lights turned on");
                    mOWDevice.setLights(getBluetoothUtil(), 2);
                } else {
                    Timber.i("Custom lights turned off");
                    mOWDevice.setLights(getBluetoothUtil(), 0);

                }
            }
        });


        mFrontBright.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mOWDevice.isConnected.get()) {
                if (isChecked) {
                    mOWDevice.setCustomLights(getBluetoothUtil(), 0,0,60);
                 } else {
                    mOWDevice.setCustomLights(getBluetoothUtil(), 0,0,30);
                 }
            }

        });

        mBackBright.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mOWDevice.isConnected.get()) {
                if (isChecked) {
                    mOWDevice.setCustomLights(getBluetoothUtil(), 1,1,60);
                } else {
                    mOWDevice.setCustomLights(getBluetoothUtil(), 1,1,30);

                }
            }

        });


        mFrontBlink.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mOWDevice.isConnected.get()) {
                if (isChecked) {
                    mFrontBlinkTimerTask = new mFrontBlinkTaskTimerTask();
                    mFrontBlinkTimer = new Timer();
                    mFrontBlinkTimer.scheduleAtFixedRate(mFrontBlinkTimerTask, 0, 500);

                } else {
                    if (mFrontBlinkTimer != null) {
                        mFrontBlinkTimer.cancel();
                        mFrontBlinkTimer.purge();
                        mFrontBlinkTimer = null;
                    }
                    if (mFrontBlinkTimerTask != null) {
                        mFrontBlinkTimerTask.cancel();
                        mFrontBlinkTimerTask = null;
                    }
                }

            }

        });

        mBackBlink.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (mOWDevice.isConnected.get()) {
                if (isChecked) {
                    mBackBlinkTimerTask = new mBackBlinkTaskTimerTask();
                    mBackBlinkTimer = new Timer();
                    mBackBlinkTimer.scheduleAtFixedRate(mBackBlinkTimerTask, 0, 500);

                } else {
                    if (mBackBlinkTimer != null) {
                        mBackBlinkTimer.cancel();
                        mBackBlinkTimer.purge();
                        mBackBlinkTimer = null;
                    }
                    if (mBackBlinkTimerTask != null) {
                        mBackBlinkTimerTask.cancel();
                        mBackBlinkTimerTask = null;
                    }
                }

            }
        });

    }



    public void initRideModeButtons() {
        mRideModeToggleButton = this.findViewById(R.id.mstb_multi_ridemodes);
        if (mOWDevice.isOneWheelPlus.get()) {
            mRideModeToggleButton.setElements(getResources().getStringArray(R.array.owplus_ridemode_array));
        } else {
            mRideModeToggleButton.setElements(getResources().getStringArray(R.array.ow_ridemode_array));
        }

        final ToggleButton.OnValueChangedListener onToggleValueChangedListener = position -> {
            if (mOWDevice.isConnected.get()) {
                Timber.d( "mOWDevice.setRideMode button pressed:" + position);
                double mph = net.kwatts.powtools.util.Util.rpmToMilesPerHour(mOWDevice.speedRpm.get());
                if (mph > 12) {
                    Toast.makeText(mContext, "Unable to change riding mode, your going too fast! (" + mph + " mph)", Toast.LENGTH_SHORT).show();
                    //TODO: fix, kicks back to change listener & creates an infinite amount of evil whirling dervishes
                    //mRideModeToggleButton.setValue(mRideModePosition);
                } else {
                    mRideModePosition = position;
                    if (mOWDevice.isOneWheelPlus.get()) {
                        Timber.i("Ridemode changed to:" + position + 4);
                        mOWDevice.setRideMode(getBluetoothUtil(), position + 4); // ow+ ble value for ridemode 4,5,6,7,8 (delirium)
                    } else {
                        Timber.i("Ridemode changed to:" + position + 1);
                        mOWDevice.setRideMode(getBluetoothUtil(), position + 1); // ow uses 1,2,3 (expert)
                    }
                }
            } else {
                Toast.makeText(mContext, "Not connected to Device!", Toast.LENGTH_SHORT).show();
            }
        };
        mRideModeToggleButton.setOnValueChangedListener(onToggleValueChangedListener);
        mOWDevice.getDeviceCharacteristicByKey(KEY_RIDE_MODE).value.addOnPropertyChangedCallback(new Observable.OnPropertyChangedCallback() {
            @Override
            public void onPropertyChanged(Observable observable, int i) {
                runOnUiThread(() -> {

                    String rideMode = mOWDevice.getDeviceCharacteristicByKey(KEY_RIDE_MODE).value.get();
                    int rideModeInt = Integer.parseInt(rideMode);
                    if (mOWDevice.isOneWheelPlus.get()) {
                        rideModeInt -= 4;
                    } else {
                        rideModeInt -= 1;
                    }

                    mRideModeToggleButton.setOnValueChangedListener(null);
                    mRideModeToggleButton.setValue(rideModeInt);
                    mRideModeToggleButton.setOnValueChangedListener(onToggleValueChangedListener);

                });
            }
        });



    }

    private void enableWritingToStorage() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            PermissionUtil.requestPermission(this, WRITE_EXTERNAL_STORAGE_PERMS,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE, false);
        } else {
            Timber.d("We already have permission for writing to storage.");
        }
    }
}
