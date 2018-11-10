package click.dummer.imagesms;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;

public final class PermissionUtils {

    private PermissionUtils() {
    }

    public static boolean checkPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (!checkPermission(context, permission)) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkPermission(Context context, String permission) {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean writeGranted(Context context) {
        return checkPermission(context, Manifest.permission.SEND_SMS);
    }

    public static boolean camGranted(Context context) {
        return checkPermission(context, Manifest.permission.CAMERA);
    }

    public static boolean readGranted(Context context) {
        return (checkPermission(context, Manifest.permission.READ_SMS) && checkPermission(context, Manifest.permission.RECEIVE_SMS));
    }

    public static void requestPermissions(Object o, int permissionId, String... permissions) {
        if (o instanceof Activity) {
            ActivityCompat.requestPermissions((AppCompatActivity) o, permissions, permissionId);
        }
    }
}
