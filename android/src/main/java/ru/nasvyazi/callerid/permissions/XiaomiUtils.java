package ru.nasvyazi.callerid.permissions;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

//In develop
public class XiaomiUtils {
    public static boolean isXiaomiDevice(){
        return Build.MANUFACTURER.toLowerCase().contains("xiaomi");
    }

    public static boolean isMiui(){
        if (!isXiaomiDevice()) {
            return false;
        }
        try {
            return !getSystemProperty("ro.miui.ui.version.code").isEmpty() ||
                    !getSystemProperty("ro.miui.ui.version.name").isEmpty() ||
                    !getSystemProperty("ro.miui.internal.storage").isEmpty();
        } catch (Exception ex) {
            // что-то пошло не так, значит будем считать что это MiUi, т.к. их значительно больше
            return true;
        }
    }

    /** Раздел "Другие разрешения" настроек приложения MIUI 8 */
    public static Intent createMiUi8OtherSettingsIntent(Context context){
        Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        intent.setClassName("com.miui.securitycenter","com.miui.permcenter.permissions.PermissionsEditorActivity" );
        intent.putExtra("extra_pkgname", context.getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return intent;
    }


    /** Раздел "Другие разрешения" настроек приложения MIUI 5/6/7 */
    public static Intent createMiUi567OtherSettingsIntent(Context context){
        Intent intent = new Intent("miui.intent.action.APP_PERM_EDITOR");
        intent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
        intent.putExtra("extra_pkgname", context.getPackageName());
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        return intent;
    }

    public static String getSystemProperty(String propName) throws IOException {
        String line;
        BufferedReader input = null;
        try{
            Process p = Runtime.getRuntime().exec("getprop "+propName);
            input = new BufferedReader(new InputStreamReader(p.getInputStream()), BUFFER_SIZE);
            line = input.readLine();
            input.close();
            input = null;
        }catch(Exception error){
            throw error;
        }finally{
            if (input != null){
                try{
                input.close();
                }
                catch(Exception e){
                };
            }
        }

        return line;
    }

    private static int BUFFER_SIZE = 1024;

}
