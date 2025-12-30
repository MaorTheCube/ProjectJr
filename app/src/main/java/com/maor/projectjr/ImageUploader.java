package com.maor.projectjr;


import android.graphics.Bitmap;
import android.os.StrictMode;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;


public class ImageUploader {


    private final String supabaseUrl = "https://evwzyxuhlpzpsniayqqe.supabase.co";
    private final String supabaseKey = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImV2d3p5eHVobHB6cHNuaWF5cXFlIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NTkyMjg4NDQsImV4cCI6MjA3NDgwNDg0NH0.XXfvElSDIyuIZVrF_HXnV_g3L_Q4jG0lyXz24ax5pxI";  // anon key או service_role
    private final String bucketName = "Amir";
    private final String studentName = "Maor";
    public ImageUploader() {
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
    }


    public String uploadBitmap(String name, Bitmap bitmap) throws IOException {
        String uploadUrl = supabaseUrl + "/storage/v1/object/" + bucketName + "/" + studentName+ "/"+ name;


        HttpURLConnection conn = (HttpURLConnection) new URL(uploadUrl).openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("apikey", supabaseKey);
        conn.setRequestProperty("Authorization", "Bearer " + supabaseKey);        conn.setRequestProperty("Content-Type", "image/jpeg");
        conn.setRequestProperty("Content-Type", "image/jpeg");


        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, bos);
        byte[] imageBytes = bos.toByteArray();


        OutputStream os = conn.getOutputStream();
        os.write(imageBytes);
        os.flush();
        os.close();
        String response = readResponse(conn);
        //int response = conn.getResponseCode();


        conn.disconnect();


        return getPublicUrl(name);
    }


    private String readResponse(HttpURLConnection conn) throws IOException {
        InputStream inputStream = conn.getResponseCode() < HttpURLConnection.HTTP_BAD_REQUEST
                ? conn.getInputStream() : conn.getErrorStream();


        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder result = new StringBuilder();
        String line;


        while ((line = reader.readLine()) != null) {
            result.append(line);
        }
        reader.close();
        return result.toString();
    }


    public String getPublicUrl(String path) {
        return supabaseUrl + "/storage/v1/object/public/" + bucketName + "/"+ studentName+ "/" + path;
    }
}
