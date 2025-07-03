package click.dummer.imagesms;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Telephony;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.telephony.SmsMessage;
import android.text.InputType;
import android.text.format.DateFormat;
import android.util.Base64;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    private BroadcastReceiver smsReceiver;

    private WebView contentView;
    private String htmlContent;
    private FloatingActionButton fab;
    private ScrollView scrollView;
    private SmsManager smsManager = SmsManager.getDefault();
    public static SharedPreferences pref;

    private ByteArrayOutputStream byteArrayOutputStream;
    private String message;
    private ArrayList<String> parts;
    private String phone;
    private ArrayList<PendingIntent> sentPIs;
    private ArrayList<PendingIntent> deliveredPIs;

    public static final String PROJECT_LINK = "http://wertfrei.art";

    //-------------------------------------------------
    public static final String BORDER_STR = "::WEBP,";
    public static final String BORDER_STR_LONG = "data:image/webp;base64,";
    public static final String BORDER_STR2 = "::GIFF,";
    public static final String BORDER_STR2_LONG = "data:image/gif;base64,";
    public static final int BORDER_LEN = 7;
    //-------------------------------------------------

    private static final int CAMERA_REQUEST = 1888;
    private static final int READ_PERMISSION_REQ = 12;
    private static final int WRITE_PERMISSION_REQ = 13;
    private static final int CAM_PERMISSION_REQ = 14;

    public static String encode(byte[] data) {
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        contentView = (WebView) findViewById(R.id.content);

        setSupportActionBar(toolbar);
        try {
            ActionBar ab = getSupportActionBar();
            if (ab != null) {
                ab.setDisplayShowHomeEnabled(true);
                ab.setHomeButtonEnabled(true);
                ab.setDisplayUseLogoEnabled(true);
                ab.setLogo(R.mipmap.logoohne);
                ab.setTitle("  " + getString(R.string.app_name));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        scrollView = (ScrollView) findViewById(R.id.scrollview);

        pref = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        fab = (FloatingActionButton) findViewById(R.id.fab);

        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (PermissionUtils.camGranted(MainActivity.this)) {
                    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                } else {
                    String[] permissions = new String[]{Manifest.permission.CAMERA};
                    PermissionUtils.requestPermissions(MainActivity.this, CAM_PERMISSION_REQ, permissions);
                }
            }
        });

        if (!pref.contains("imgscale")) {
            pref.edit().putInt("imgscale", 40).commit();
        }
        if (!pref.contains("as_gif")) {
            pref.edit().putBoolean("as_gif", false).commit();
        }

        Intent intent = getIntent();

        if (intent != null && intent.getAction() != null && intent.getType() != null) {
            String action = intent.getAction();
            String type = intent.getType();
            if (Intent.ACTION_SEND.equals(action))
            {
                handleSendIntent(intent, type);
            }
        } else {
            updateInbox();
        }

        smsReceiver = new BroadcastReceiver() {

            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
                    contentClear();
                    readSms("content://sms/inbox");

                    Bundle bundle = intent.getExtras();
                    if (bundle != null) {
                        Object[] pdus = (Object[]) bundle.get("pdus");
                        if (pdus != null) {
                            for (Object pdu : pdus)
                            {
                                int cPos;
                                SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu);

                                appendHeader(
                                        smsMessage.getDisplayOriginatingAddress(),
                                        new Date(smsMessage.getTimestampMillis())
                                );
                                String messageBody = smsMessage.getMessageBody();

                                if (messageBody.contains(BORDER_STR)) {
                                    try {
                                        cPos = messageBody.indexOf(BORDER_STR);
                                        if (cPos > 0) {
                                            appendThis(messageBody.substring(0, cPos) + "<br>\n");
                                        }
                                        appendImage(messageBody.substring(cPos + BORDER_LEN), BORDER_STR_LONG);

                                    } catch (Exception e) {
                                        appendThis(e.getMessage());
                                    }
                                } else if (messageBody.contains(BORDER_STR2)) {
                                    try {
                                        cPos = messageBody.indexOf(BORDER_STR2);
                                        if (cPos > 0) {
                                            appendThis(messageBody.substring(0, cPos) + "<br>\n");
                                        }
                                        appendImage(messageBody.substring(cPos + BORDER_LEN), BORDER_STR2_LONG);

                                    } catch (Exception e) {
                                        appendThis(e.getMessage());
                                    }
                                } else {
                                    appendThis(messageBody);
                                }
                                appendThis("<br>\n<br>\n");
                            }
                            contentReady();
                            scrollView.fullScroll(View.FOCUS_DOWN);
                        }
                    }
                }
            }
        };
    }

    private void handleSendIntent(Intent intent, String type) {
        if (type.startsWith("image/"))
        {
            updateOutbox();
            Uri imageUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (imageUri != null) {
                try {
                    InputStream inputStream = getContentResolver().openInputStream(imageUri);
                    Bitmap photo = BitmapFactory.decodeStream(inputStream);
                    sendImageDialog(photo);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void sendSMS() {
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
                        Snackbar.make(fab, R.string.sent_hint, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                        Snackbar.make(fab, R.string.failure_hint, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_NO_SERVICE:
                        Snackbar.make(fab, R.string.no_service_hint, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_NULL_PDU:
                        Snackbar.make(fab, R.string.null_pdu_hint, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                    case SmsManager.RESULT_ERROR_RADIO_OFF:
                        Snackbar.make(fab, R.string.radio_off_hint, Snackbar.LENGTH_LONG)
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
                        Snackbar.make(fab, R.string.sms_delivered_hint, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();

                        break;
                    case Activity.RESULT_CANCELED:
                        Snackbar.make(fab, R.string.not_delivered_hint, Snackbar.LENGTH_LONG)
                                .setAction("Action", null).show();
                        break;
                }
            }
        }, new IntentFilter(DELIVERED));
        sentPIs = new ArrayList<>();
        deliveredPIs = new ArrayList<>();
        sentPIs.add(sentPI);
        deliveredPIs.add(deliveredPI);

        if (PermissionUtils.writeGranted(this)) {
            smsManager.sendMultipartTextMessage(phone, null, parts, sentPIs, deliveredPIs);
        } else {
            String[] permissions = new String[]{Manifest.permission.SEND_SMS};
            PermissionUtils.requestPermissions(this, WRITE_PERMISSION_REQ, permissions);
        }
    }

    private void sendImageDialog(Bitmap photo)
    {
        double scale = 0.01 * Integer.parseInt(pref.getString("scale", "50"));
        if (scale < 0.01) scale = 0.5;

        Bitmap mutableBitmap = Bitmap.createScaledBitmap(
                photo,
                (int) Math.round(photo.getWidth() * scale),
                (int) Math.round(photo.getHeight() * scale),
                true
        );
        int quality = Integer.parseInt(pref.getString("quality", "50"));
        byteArrayOutputStream = new ByteArrayOutputStream();

        if (pref.getBoolean("as_gif", false)) {
            GifEncoder gifEncoder = new GifEncoder();

            gifEncoder.start(byteArrayOutputStream);
            gifEncoder.setRepeat(0);
            gifEncoder.setDelay(100);
            gifEncoder.setQuality(quality);

            gifEncoder.addFrame(mutableBitmap);
            gifEncoder.finish();
        } else {
            mutableBitmap.compress(Bitmap.CompressFormat.WEBP, quality, byteArrayOutputStream);
        }

        message = BORDER_STR + encode(byteArrayOutputStream.toByteArray());
        parts = smsManager.divideMessage(message);

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.send_in_x_msg, String.valueOf(parts.size())));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);

        final EditText inputNr = new EditText(this);
        final EditText inputTxt = new EditText(this);

        inputNr.setInputType(InputType.TYPE_CLASS_PHONE);
        inputNr.setText(pref.getString("send_to", "#100*"));
        inputTxt.setInputType(InputType.TYPE_CLASS_TEXT);
        inputTxt.setHint(R.string.add_some_text_here);
        layout.addView(inputTxt);
        layout.addView(inputNr);
        builder.setView(layout);

        builder.setPositiveButton(R.string.ok_btn, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                phone = inputNr.getText().toString();
                if (pref.getBoolean("as_gif", false))
                {
                    message = inputTxt.getText().toString() +  BORDER_STR2 + encode(byteArrayOutputStream.toByteArray());

                } else {
                    message = inputTxt.getText().toString() +  BORDER_STR + encode(byteArrayOutputStream.toByteArray());
                }
                parts = smsManager.divideMessage(message);
                pref.edit().putString("send_to", phone).apply();
                sendSMS();
            }
        });
        builder.setNegativeButton(R.string.cancel_btn, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });

        builder.show();

    }

    private void readSms(String box) {
        if (PermissionUtils.readGranted(this))
        {
            ContentResolver contentResolver = getContentResolver();
            String sortOrder = Telephony.Sms.DATE + " DESC";
            String limit = pref.getString("display_limit","10");
            if (limit.equals("")) limit = "10";

            Cursor smsInboxCursor = contentResolver.query(
                    Uri.parse(box), null, null, null, sortOrder + " LIMIT " + limit
            );
            int indexBody = smsInboxCursor.getColumnIndex("body");
            int indexAddress = smsInboxCursor.getColumnIndex("address");
            int indexDate = smsInboxCursor.getColumnIndex("date");

            if (!(indexBody < 0 || !smsInboxCursor.moveToLast()))
            {
                do {
                    appendHeader(
                            smsInboxCursor.getString(indexAddress),
                            new Date(smsInboxCursor.getLong(indexDate))
                    );

                    String coded = smsInboxCursor.getString(indexBody);
                    int cPos;

                    if (smsInboxCursor.getString(indexBody).contains(BORDER_STR))
                    {

                        try {
                            cPos = coded.indexOf(BORDER_STR);
                            if (cPos > 0)
                            {
                                appendThis(coded.substring(0, cPos) + "<br>\n");
                            }
                            appendImage(coded.substring(cPos + BORDER_LEN), BORDER_STR_LONG);

                        } catch (Exception e) {
                            appendThis(e.getMessage());
                        }
                    }
                    else if (smsInboxCursor.getString(indexBody).contains(BORDER_STR2))
                    {
                        try {
                            cPos = coded.indexOf(BORDER_STR2);
                            if (cPos > 0)
                            {
                                appendThis(coded.substring(0, cPos) + "<br>\n");
                            }
                            appendImage(coded.substring(cPos + BORDER_LEN), BORDER_STR2_LONG);

                        } catch (Exception e) {
                            appendThis(e.getMessage());
                        }

                    } else {
                        appendThis(smsInboxCursor.getString(indexBody));
                    }
                    appendThis("<br>\n<br>\n");
                } while (smsInboxCursor.moveToPrevious());
            }
            Toast.makeText(getApplicationContext(), getString(R.string.freshup), Toast.LENGTH_SHORT).show();
        } else {
            String[] permissions = new String[]{Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS};
            PermissionUtils.requestPermissions(this, READ_PERMISSION_REQ, permissions);
        }
        scrollView.fullScroll(View.FOCUS_DOWN);

    }

    // ---------------------------------------------------------------------------------------------

    private void contentClear()
    {
        htmlContent = "<html><body>";
    }

    private void appendHeader(String address, Date date)
    {
        appendThis(
                "<b>" + address + "</b><br>\n" +
                "<small>" + DateFormat.format(getString(R.string.date_format), date).toString() + "</small><br>\n"
        );
    }

    private void appendImage(String base64code, String imgHeader)
    {
        int imgscale = pref.getInt("imgscale", 40);
        String base64Image = "<img src=\"" + imgHeader + base64code + "\" width=\"" + String.valueOf(imgscale) + "%\" />";
        appendThis(base64Image);
    }

    private void appendThis(String html)
    {
        htmlContent += html;
    }

    private void contentReady()
    {
        String newContent = htmlContent + "</body></html>";
        contentView.loadData(newContent, "text/html", "UTF-8");
    }


    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            sendImageDialog(photo);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            Intent intent = new Intent(MainActivity.this, PreferencesActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
            startActivity(intent);
            return true;
        } else if (id == R.id.action_read_inbox) {
            updateInbox();
            return true;
        } else if (id == R.id.action_read_outbox) {
            updateOutbox();
            return true;
        } else if (id == R.id.action_website) {
            Intent intentProj = new Intent(Intent.ACTION_VIEW, Uri.parse(PROJECT_LINK));
            startActivity(intentProj);
            return true;
        } else if (id == R.id.action_bigger) {
            int imgscale = pref.getInt("imgscale", 40) + 10;
            if (imgscale > 100) imgscale = 100;
            pref.edit().putInt("imgscale", imgscale).apply();
            updateInbox();
            return true;
        } else if (id == R.id.action_smaller) {
            int imgscale = pref.getInt("imgscale", 40) - 10;
            if (imgscale < 10) imgscale = 10;
            pref.edit().putInt("imgscale", imgscale).apply();
            updateInbox();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public void updateInbox() {
        contentClear();
        readSms("content://sms/inbox");
        contentReady();
    }

    public void updateOutbox() {
        contentClear();
        readSms("content://sms/sent");
        contentReady();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        boolean granted = false;
        switch (requestCode) {
            case CAM_PERMISSION_REQ:
                granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                } else {
                    //nobody knows what to do
                }
                break;
            case READ_PERMISSION_REQ:
                granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    updateInbox();
                } else {
                    //nobody knows what to do
                }
                break;
            case WRITE_PERMISSION_REQ:
                granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
                if (granted) {
                    smsManager.sendMultipartTextMessage(phone, null, parts, sentPIs, deliveredPIs);
                } else {
                    //nobody knows what to do
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter("android.provider.Telephony.SMS_RECEIVED");
        registerReceiver(smsReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(smsReceiver);
    }
}
