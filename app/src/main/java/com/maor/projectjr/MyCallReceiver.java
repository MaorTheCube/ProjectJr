package com.maor.projectjr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;

import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MyCallReceiver extends BroadcastReceiver {
    private static boolean isSaved = false;
    private FirebaseFirestore db;

    @Override
    public void onReceive(Context context, Intent intent) {
        TelephonyManager telephony = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        telephony.listen(new PhoneStateListener() {
            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                super.onCallStateChanged(state, incomingNumber);
                if (state == TelephonyManager.CALL_STATE_RINGING) {
                    if (!isSaved) {
                        isSaved = true;
                        if (incomingNumber == null || incomingNumber.equals("") || incomingNumber.equals("0")) {
                            incomingNumber = "private";
                        }
                        SavePhoneNumber(context, incomingNumber);
                    }
                } else {
                    isSaved = false;
                }
            }
        }, PhoneStateListener.LISTEN_CALL_STATE);
    }

    private void SavePhoneNumber(Context context, String phoneNumber) {
        db = FirebaseFirestore.getInstance();

        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                .format(new java.util.Date());

        Toast.makeText(context, phoneNumber + " " + timestamp, Toast.LENGTH_LONG).show();

        Map<String, Object> data = new HashMap<>();
        data.put("phone", phoneNumber);
        data.put("date", timestamp);

        db.collection("calls").add(data);
    }
}
