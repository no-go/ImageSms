package click.dummer.imagesms;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.format.DateFormat;
import android.text.style.ImageSpan;
import android.util.Base64;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class ScrollingActivity extends AppCompatActivity {
    private TextView textView;
    private FloatingActionButton fab;
    private SmsManager smsManager = SmsManager.getDefault();
    public static SharedPreferences pref;

    private static final int CAMERA_REQUEST = 1888;
    private Camera mCamera;
    private CameraPreview mPreview;
    private int cameraId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scrolling);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        textView = (TextView) findViewById(R.id.content);
        setSupportActionBar(toolbar);

        pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                //File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
                //File output = new File(dir, "CameraContentDemo.jpeg");
                //cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, Uri.fromFile(output));
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
        });

        ContentResolver contentResolver = getContentResolver();
        Cursor smsInboxCursor = contentResolver.query(Uri.parse("content://sms/inbox"), null, null, null, null);
        int indexBody = smsInboxCursor.getColumnIndex("body");
        int indexAddress = smsInboxCursor.getColumnIndex("address");
        int indexDate = smsInboxCursor.getColumnIndex("date");
        if (!(indexBody < 0 || !smsInboxCursor.moveToFirst())) {
            do {
                Date date = new Date(smsInboxCursor.getLong(indexDate));
                textView.append(
                        smsInboxCursor.getString(indexAddress) + " " +
                        DateFormat.format("dd.MM.yyyy HH:mm", date).toString() +
                        "\n"
                );
                if (smsInboxCursor.getString(indexBody).startsWith("data:image")) {
                    textView.append("  ");
                    CharSequence sp = textView.getText();
                    SpannableStringBuilder ssb = new SpannableStringBuilder(sp);
                    String coded = smsInboxCursor.getString(indexBody);
                    byte[] decodedString = Base64.decode(coded.substring(coded.indexOf(",") + 1), Base64.DEFAULT);

                    Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                    Drawable dr = new BitmapDrawable(getResources(), decodedByte);
                    dr.setBounds(0, 0, 2*decodedByte.getWidth(), 2*decodedByte.getHeight());

                    ImageSpan isp = new ImageSpan(dr);
                    ssb.setSpan(
                            isp,
                            sp.length()-2,
                            sp.length()-1,
                            Spannable.SPAN_INCLUSIVE_INCLUSIVE
                    );
                    textView.setText(ssb);
                } else {
                    textView.append(smsInboxCursor.getString(indexBody));
                }
                textView.append("\n\n");
            } while (smsInboxCursor.moveToNext());

        }
    }


    @Override
    public void onResume() {
        super.onResume();
        //mCamera = getCameraInstance();
        //mPreview = new CameraPreview(this, mCamera);
        //FrameLayout preview = (FrameLayout) findViewById(R.id.preview);
        //preview.addView(mPreview);
    }

    private void sendSMS(String phoneNumber, String message) {
        String SENT = "SMS_SENT";
        String DELIVERED = "SMS_DELIVERED";

        PendingIntent sentPI = PendingIntent.getBroadcast(this, 0, new Intent(SENT), 0);
        PendingIntent deliveredPI = PendingIntent.getBroadcast(this, 0, new Intent(DELIVERED), 0);

        //---when the SMS has been sent---
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        Snackbar.make(fab, getString(R.string.sent), Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Snackbar.make(fab, "Generic failure", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Snackbar.make(fab, "No service", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Snackbar.make(fab, "Null PDU", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Snackbar.make(fab, "Radio off", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                }
            }
        }, new IntentFilter(SENT));

        //---when the SMS has been delivered---
        registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context arg0, Intent arg1) {
                switch (getResultCode())
                {
                    case Activity.RESULT_OK:
                        Snackbar.make(fab, "SMS delivered", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case Activity.RESULT_CANCELED:
                        Snackbar.make(fab, "SMS not delivered", Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));
        ArrayList<String> parts = smsManager.divideMessage(message);
        ArrayList<PendingIntent> sentPIs = new ArrayList<>();
        ArrayList<PendingIntent> deliveredPIs = new ArrayList<>();
        sentPIs.add(sentPI);
        deliveredPIs.add(deliveredPI);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentPIs, deliveredPIs);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_scrolling, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(ScrollingActivity.this, PreferencesActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public Camera getCameraInstance() {
        Camera c = null;
        try {
            int numberOfCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                    cameraId = i;
                    c = Camera.open(i);
                    c.setDisplayOrientation(ori());
                    break;
                }
            }
        } catch (Exception e) {}
        return c;
    }

    public int ori() {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            Bitmap mutableBitmap = Bitmap.createScaledBitmap(
                    photo,
                    photo.getWidth()/2,
                    photo.getHeight()/2,
                    true
            );

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 50, byteArrayOutputStream);

            String message = "data:image/jpeg;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP);
            sendSMS(pref.getString("send_to", "#100*"), message);
        }
    }
}
