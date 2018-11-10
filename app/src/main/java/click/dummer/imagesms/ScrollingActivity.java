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
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.telephony.SmsManager;
import android.telephony.gsm.SmsMessage;
import android.text.InputType;
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
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class ScrollingActivity extends AppCompatActivity {
    private TextView textView;
    private FloatingActionButton fab;
    private SmsManager smsManager = SmsManager.getDefault();
    public static SharedPreferences pref;

    private ArrayList<String> parts;
    private String phone;
    private ArrayList<PendingIntent> sentPIs;
    private ArrayList<PendingIntent> deliveredPIs;

    private static final int CAMERA_REQUEST = 1888;
    private static final int READ_PERMISSION_REQ = 12;
    private static final int WRITE_PERMISSION_REQ = 13;
    private static final int CAM_PERMISSION_REQ = 14;

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
                if (PermissionUtils.camGranted(ScrollingActivity.this)) {
                    Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                    startActivityForResult(cameraIntent, CAMERA_REQUEST);
                } else {
                    String[] permissions = new String[]{Manifest.permission.CAMERA};
                    PermissionUtils.requestPermissions(ScrollingActivity.this, CAM_PERMISSION_REQ, permissions);
                }
            }
        });

        if (!pref.contains("imgscale")) {
            pref.edit().putFloat("imgscale", 3.0f).commit();
        }

        readSms("content://sms/inbox");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu, menu);
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
        } else if (id == R.id.action_take_pic) {
            fab.callOnClick();
            return true;
        } else if (id == R.id.action_read_inbox) {
            updateInbox();
            return true;
        } else if (id == R.id.action_read_outbox) {
            updateOutbox();
            return true;
        } else if (id == R.id.action_bigger) {
            float imgscale = pref.getFloat("imgscale", 3.0f) * 1.2f;
            pref.edit().putFloat("imgscale", imgscale).apply();
            updateInbox();
            return true;
        } else if (id == R.id.action_smaller) {
            float imgscale = pref.getFloat("imgscale", 3.0f) * 0.9f;
            pref.edit().putFloat("imgscale", imgscale).apply();
            updateInbox();
            return true;
        }
        return super.onOptionsItemSelected(item);
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

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CAMERA_REQUEST && resultCode == Activity.RESULT_OK) {
            Bitmap photo = (Bitmap) data.getExtras().get("data");
            Bitmap mutableBitmap = Bitmap.createScaledBitmap(
                    photo,
                    photo.getWidth()/2,
                    photo.getHeight()/2,
                    true
            );
            int quality = Integer.parseInt(pref.getString("quality", "50"));
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            mutableBitmap.compress(Bitmap.CompressFormat.JPEG, quality, byteArrayOutputStream);

            String message = "data:image/jpeg;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP);
            parts = smsManager.divideMessage(message);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);

            final EditText input = new EditText(this);
            builder.setTitle(getString(R.string.send_in_x_msg, String.valueOf(parts.size())));
            input.setInputType(InputType.TYPE_CLASS_PHONE);
            input.setText(pref.getString("send_to", "#100*"));
            builder.setView(input);

            builder.setPositiveButton(R.string.ok_btn, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    phone = input.getText().toString();
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
    }

    private void readSms(String box) {
        if (PermissionUtils.readGranted(this)) {
            ContentResolver contentResolver = getContentResolver();
            Cursor smsInboxCursor = contentResolver.query(Uri.parse(box), null, null, null, null);
            int indexBody = smsInboxCursor.getColumnIndex("body");
            int indexAddress = smsInboxCursor.getColumnIndex("address");
            int indexDate = smsInboxCursor.getColumnIndex("date");
            if (!(indexBody < 0 || !smsInboxCursor.moveToFirst())) {
                int counter = 0;
                String str = pref.getString("display_limit", "25");
                if (str.equals("")) str = "25";
                int maxim = Integer.parseInt(str);
                float imgscale = pref.getFloat("imgscale", 3.0f);
                do {
                    counter++;
                    Date date = new Date(smsInboxCursor.getLong(indexDate));
                    textView.append(
                            smsInboxCursor.getString(indexAddress) + "\n" +
                                    DateFormat.format(getString(R.string.date_format), date).toString() + "\n"
                    );
                    if (smsInboxCursor.getString(indexBody).startsWith("data:image/jpeg;base64,")) {
                        textView.append("  ");
                        CharSequence sp = textView.getText();
                        SpannableStringBuilder ssb = new SpannableStringBuilder(sp);
                        String coded = smsInboxCursor.getString(indexBody);
                        byte[] decodedString = Base64.decode(coded.substring(coded.indexOf(",") + 1), Base64.DEFAULT);

                        Bitmap decodedByte = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
                        if (decodedByte == null) {
                            textView.append(getString(R.string.problem_image));
                        } else {
                            Drawable dr = new BitmapDrawable(getResources(), decodedByte);
                            dr.setBounds(
                                    0,
                                    0,
                                    Math.round(imgscale*decodedByte.getWidth()),
                                    Math.round(imgscale*decodedByte.getHeight())
                            );

                            ImageSpan isp = new ImageSpan(dr);
                            ssb.setSpan(
                                    isp,
                                    sp.length()-2,
                                    sp.length()-1,
                                    Spannable.SPAN_INCLUSIVE_INCLUSIVE
                            );
                            textView.setText(ssb);
                        }
                    } else {
                        textView.append(smsInboxCursor.getString(indexBody));
                    }
                    textView.append("\n\n");
                } while (smsInboxCursor.moveToNext() && counter < maxim);
            }
            Toast.makeText(getApplicationContext(), getString(R.string.freshup), Toast.LENGTH_SHORT).show();
        } else {
            String[] permissions = new String[]{Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS};
            PermissionUtils.requestPermissions(this, READ_PERMISSION_REQ, permissions);
        }
    }

    public void updateInbox() {
        textView.setText("");
        readSms("content://sms/inbox");
    }

    public void updateOutbox() {
        textView.setText("");
        readSms("content://sms/sent");
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
                    readSms("content://sms/inbox");
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
}
