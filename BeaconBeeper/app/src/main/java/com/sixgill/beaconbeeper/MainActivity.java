package com.sixgill.beaconbeeper;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.crystal.crystalrangeseekbar.interfaces.OnRangeSeekbarChangeListener;
import com.crystal.crystalrangeseekbar.widgets.CrystalRangeSeekbar;
import com.facebook.stetho.Stetho;
import com.sixgill.beaconbeeper.com.sixgill.beaconbeepr.db.AppDatabase;
import com.sixgill.beaconbeeper.com.sixgill.beaconbeepr.db.Event;
import com.sixgill.beaconbeeper.com.sixgill.beaconbeepr.db.EventDao;

import org.altbeacon.beacon.Beacon;
import org.altbeacon.beacon.BeaconConsumer;
import org.altbeacon.beacon.BeaconManager;
import org.altbeacon.beacon.BeaconParser;
import org.altbeacon.beacon.RangeNotifier;
import org.altbeacon.beacon.Region;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class MainActivity extends AppCompatActivity implements BeaconConsumer {
    private static final int REQUEST_FINE_LOCATION = 2;
    private static final String TAG = "MainActivity";
    private static final String TEST_UUID = "f7826da6-4fa2-4e98-8024-bc5b71e0893e";
    private static final String TEST_MAJOR = "45983";
    private static final String TEST_MINOR = "5387";
    private static final boolean TEST_MODE = false;

    private boolean mListening;
    private String uuid;
    private String major;
    private String minor;
    private BeaconManager beaconManager;

    private int minRssi = -100;
    private int maxRssi = -30;
    private Vibrator vibrator;

    private SoundPool soundPool;
    private int soundId;
    private AppDatabase db;
    private EventDao mEventDao;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Stetho.initializeWithDefaults(getApplicationContext());

        if ( ContextCompat.checkSelfPermission( this, Manifest.permission.ACCESS_FINE_LOCATION ) != PackageManager.PERMISSION_GRANTED ) {
            ActivityCompat.requestPermissions( this, new String[] {  android.Manifest.permission.ACCESS_FINE_LOCATION  },
                    REQUEST_FINE_LOCATION );
        }
        beaconManager = BeaconManager.getInstanceForApplication(this);
        beaconManager.setForegroundBetweenScanPeriod(0);
        beaconManager.getBeaconParsers().clear();
        beaconManager.getBeaconParsers().add(new BeaconParser().setBeaconLayout("m:2-3=0215,i:4-19,i:20-21,i:22-23,p:24-24"));
        beaconManager.bind(this);

        // rssi range configuration
        final CrystalRangeSeekbar rangeSeekbar = findViewById(R.id.rangeSeekbar1);
        final TextView tvMin = findViewById(R.id.minRssiTextView);
        final TextView tvMax = findViewById(R.id.maxRssiTextView);

        rangeSeekbar.setOnRangeSeekbarChangeListener(new OnRangeSeekbarChangeListener() {
            @Override
            public void valueChanged(Number minValue, Number maxValue) {
                tvMin.setText("Min RSSI: " + String.valueOf(minValue));
                tvMax.setText("Max RSSI: " + String.valueOf(maxValue));
                minRssi = minValue.intValue();
                maxRssi = maxValue.intValue();
            }
        });

        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        soundPool = new SoundPool(1, AudioManager.STREAM_MUSIC, 0);
        soundId = soundPool.load(this, R.raw.quite_impressed, 1);

        db = AppDatabase.getDatabase(getApplicationContext());
        mEventDao = db.eventDao();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        soundPool.release();
        soundPool = null;
        soundId = -1;
    }

    public void startListening(View v) {
        final LinearLayout setupView = findViewById(R.id.setupView);
        EditText uuidEditText = findViewById(R.id.uuidEditText);
        EditText majorEditText =  findViewById(R.id.majorEditText);
        EditText minorEditText =  findViewById(R.id.minorEditText);

        uuid = uuidEditText.getText().toString();
        major = majorEditText.getText().toString();
        minor = minorEditText.getText().toString();
        if (TEST_MODE) {
            uuid = TEST_UUID;
            major = TEST_MAJOR;
            minor = TEST_MINOR;
        }
        if (!hasPermissions() || mListening) {
            Toast.makeText(this, "Don't have valid permissions!", Toast.LENGTH_LONG);
            return;
        }

        final LinearLayout listeningView = findViewById(R.id.listeningView);
        final TextView uuidTextView = findViewById(R.id.uuidTextView);
        final TextView majorTextView = findViewById(R.id.majorTextView);
        final TextView minorTextView = findViewById(R.id.minorTextView);
        final TextView distanceTextView = findViewById(R.id.distanceTextView);
        final Switch soundSwitch = findViewById(R.id.soundSwitch);
        final Switch vibrationSwitch = findViewById(R.id.vibrationSwitch);

        uuidTextView.setText("UUID: " + uuid);
        majorTextView.setText("Major: " + major);
        minorTextView.setText("Minor: " + minor);
        distanceTextView.setText("No beacon found.");

        final TimeZone tz = TimeZone.getTimeZone("UTC");


        beaconManager.removeAllRangeNotifiers();
        beaconManager.addRangeNotifier(new RangeNotifier() {
            @Override
            public void didRangeBeaconsInRegion(Collection<Beacon> beacons, Region region) {
                Beacon beacon = getClosestBeacon(beacons, uuid, major, minor);
                if (beacon != null) {
                    String distanceString = String.format(Locale.US, "%.2f", beacon.getDistance());
                    distanceTextView.setText("rssi: " + beacon.getRssi() + " distance: " + distanceString + " meters");

                    boolean inRange = beacon.getRssi() > minRssi && beacon.getRssi() < maxRssi;
                    float ratio = ((float)beacon.getRssi() - (float)minRssi) / ((float)maxRssi - (float)minRssi);
                    if (inRange) {
                        if (soundSwitch.isChecked()){
                            soundPool.play(soundId, ratio, ratio, 1, 0, 1f);
                        }
                        if (vibrationSwitch.isChecked()) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                VibrationEffect vibrationEffect = VibrationEffect.createOneShot(800, (int)(ratio * 255.0) + 1);
                                vibrator.vibrate(vibrationEffect);
                            } else {
                                vibrator.vibrate(800);
                            }
                        }
                    }
                    Event event = new Event();
                    event.uuid = uuid;
                    event.major = major;
                    event.minor = minor;
                    event.rssi = beacon.getRssi();
                    event.distance = beacon.getDistance();
                    event.sound = soundSwitch.isChecked();
                    event.vibration = vibrationSwitch.isChecked();
                    DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
                    dateFormat.setTimeZone(tz);
                    event.date = dateFormat.format(new Date());
                    new insertAsyncTask(mEventDao).execute(event);
                } else {
                    distanceTextView.setText("No beacon found.");
                }
            }
        });
        try {
            beaconManager.startRangingBeaconsInRegion(new Region("myRegion", null, null, null));
        } catch (RemoteException e) {}
        mListening = true;
        setupView.setVisibility(View.GONE);
        listeningView.setVisibility(View.VISIBLE);
    }

    private Beacon getClosestBeacon(Collection<Beacon> beacons, String uuid, String major, String minor) {
        ArrayList<Beacon> results = new ArrayList<>();
        for (Beacon beacon : beacons) {
            Log.i(TAG, "Info given " + uuid + " " + major + " " + minor);
            Log.i(TAG, "Beacon detected " + beacon.getId1().toString() + " " + beacon.getId2().toString() + " " + beacon.getId3().toString());
            if (beacon.getId1().toString().equals(uuid) && beacon.getId2().toString().equals(major) && beacon.getId3().toString().equals(minor)){
                results.add((beacon));
            }
        }
        if (results.size() == 0) {
            return null;
        }
        // sort where largest rssi is first
        Collections.sort(results, new Comparator<Beacon>() {
            @Override
            public int compare(Beacon o1, Beacon o2) {
                return o2.getRssi() - o1.getRssi();
            }
        });
        // take largest
        return results.get(0);
    }

    private boolean hasPermissions() {
        if (!hasLocationPermissions()) {
            requestLocationPermission();
            return false;
        }
        return true;
    }
    private boolean hasLocationPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }
    private void requestLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_FINE_LOCATION);
        }
    }

    @Override
    public void onBeaconServiceConnect() {
        Toast.makeText(this, "Connected to BLE", Toast.LENGTH_SHORT);
    }

    public void stopListening(View view) {
        final LinearLayout setupView = findViewById(R.id.setupView);
        final LinearLayout listeningView = findViewById(R.id.listeningView);
        beaconManager.removeAllRangeNotifiers();
        mListening = false;
        setupView.setVisibility(View.VISIBLE);
        listeningView.setVisibility(View.GONE);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case R.id.help:
                String url = "https://altbeacon.github.io/android-beacon-library/distance-calculations.html";
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setData(Uri.parse(url));
                startActivity(i);
                return true;
            case R.id.export:
                new exportAsyncTask(getApplicationContext(), mEventDao).execute();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private static class insertAsyncTask extends AsyncTask<Event, Void, Void> {

        private EventDao mAsyncTaskDao;

        insertAsyncTask(EventDao dao) {
            mAsyncTaskDao = dao;
        }

        @Override
        protected Void doInBackground(final Event... params) {
            mAsyncTaskDao.insertAll(params[0]);
            return null;
        }
    }

    private static class exportAsyncTask extends AsyncTask<Void, Void, Void> {

        private Context context;
        private EventDao eventDao;

        exportAsyncTask(Context context, EventDao eventDao) {
            this.context = context;
            this.eventDao = eventDao;
        }

        @Override
        protected Void doInBackground(final Void... args) {
            File outputDir = context.getCacheDir();
            File outputFile = null;
            try {
                outputFile = File.createTempFile("events-", ".csv", outputDir);

            } catch (IOException e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                return null;
            }
            FileOutputStream stream = null;
            try {
                stream = new FileOutputStream(outputFile);
            } catch (FileNotFoundException e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                return null;
            }
            try {
                stream.write(getEventCSVHeader().getBytes());
                for (Event event : eventDao.getEvents()) {
                    stream.write(getEventCSVLine(event).getBytes());
                }
            } catch (IOException e) {
                Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                return null;
            } finally {
                try {
                    stream.close();
                } catch (IOException e) {
                    Toast.makeText(context, e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            // Fetch Bitmap Uri locally
//             Uri.fromFile(outputFile);
            Uri uri = FileProvider.getUriForFile(context, context.getPackageName()+".fileprovider", outputFile);

            Intent shareIntent = new Intent();
            shareIntent.setAction(Intent.ACTION_SEND);
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.setType("text/csv");
            context.startActivity(shareIntent);
            return null;
        }

        private static final String getEventCSVHeader() {
            return "id,uuid,major,minor,rssi,distance,sound,vibration,date\n";
        }

        private static final String getEventCSVLine(Event event) {
            return String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s\n", String.valueOf(event.id),
                    event.uuid,
                    event.major,
                    event.minor,
                    String.valueOf(event.rssi),
                    String.valueOf(event.distance),
                    String.valueOf(event.sound),
                    String.valueOf(event.vibration),
                    String.valueOf(event.date));
        }
    }
}
