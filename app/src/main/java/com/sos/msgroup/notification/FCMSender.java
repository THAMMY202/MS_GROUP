package com.sos.msgroup.notification;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class FCMSender {
    /*
     * URL where we request to send notification and the key to send notification using admin sdk
     * */
    private static final String FCM_URL = "https://fcm.googleapis.com/fcm/send", KEY_STRING = "key=AAAAqMDbzEw:APA91bH6yVd7Mv6B8kBc0qACJVw3xE8OZdf21tEmscQdG-GPqwW9S4mbOrdimOyXgJkNxBBAshTDTi7tMiju6v0J0-x0GXH0zjx2Bd4Sotbtx0dE8mnwsT4T8ZxI51ODP-nOt_iLQisY";

    OkHttpClient client = new OkHttpClient();

    /*
     * Method to send notification to the application
     * */
    public void send(String message, Callback callback) {
        RequestBody reqBody = RequestBody.create(message
                , MediaType.get("application/json"));

        Request request = new Request.Builder()
                .url(FCM_URL)
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", KEY_STRING)
                .post(reqBody)
                .build();

        Call call = client.newCall(request);
        call.enqueue(callback);
    }
}
