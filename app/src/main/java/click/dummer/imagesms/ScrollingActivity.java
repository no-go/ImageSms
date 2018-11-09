package click.dummer.imagesms;

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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.ArrayList;
import java.util.Date;

public class ScrollingActivity extends AppCompatActivity {
    private static ScrollingActivity inst;
    private TextView textView;
    private FloatingActionButton fab;
    private SmsManager smsManager = SmsManager.getDefault();
    public static SharedPreferences pref;
    private String message;
    private ArrayList<String> parts;

    private static final int CAMERA_REQUEST = 1888;

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
                startActivityForResult(cameraIntent, CAMERA_REQUEST);
            }
        });
        readSms("content://sms/inbox");
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
        ArrayList<PendingIntent> sentPIs = new ArrayList<>();
        ArrayList<PendingIntent> deliveredPIs = new ArrayList<>();
        sentPIs.add(sentPI);
        deliveredPIs.add(deliveredPI);
        smsManager.sendMultipartTextMessage(phoneNumber, null, parts, sentPIs, deliveredPIs);
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

            message = "data:image/jpeg;base64," + Base64.encodeToString(byteArrayOutputStream.toByteArray(), Base64.NO_WRAP);
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
                    String phone = input.getText().toString();
                    pref.edit().putString("send_to", phone).apply();
                    sendSMS(phone, message);
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

    public static ScrollingActivity instance() {
        return inst;
    }

    @Override
    public void onStart() {
        super.onStart();
        inst = this;
    }

    private void readSms(String box) {
        ContentResolver contentResolver = getContentResolver();
        Cursor smsInboxCursor = contentResolver.query(Uri.parse(box), null, null, null, null);
        int indexBody = smsInboxCursor.getColumnIndex("body");
        int indexAddress = smsInboxCursor.getColumnIndex("address");
        int indexDate = smsInboxCursor.getColumnIndex("date");
        if (!(indexBody < 0 || !smsInboxCursor.moveToFirst())) {
            int counter = 0;
            int maxim = pref.getInt("display_limit", 25);
            do {
                counter++;
                Date date = new Date(smsInboxCursor.getLong(indexDate));
                textView.append(
                        smsInboxCursor.getString(indexAddress) + " " +
                                DateFormat.format(getString(R.string.date_format), date).toString() +
                                "\n"
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
                        dr.setBounds(0, 0, 2*decodedByte.getWidth(), 2*decodedByte.getHeight());

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
            } while (smsInboxCursor.moveToNext() && counter <= maxim);
        }
    }

    public void updateInbox() {
        readSms("content://sms/inbox");
    }

    public void updateOutbox() {
        readSms("content://sms/sent");
    }
}
