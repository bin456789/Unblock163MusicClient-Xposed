package bin.xposed.Unblock163MusicClient;

import org.json.JSONException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.GZIPInputStream;

public class Http {

    private int responseCode;
    private String responseText;

    private Http(String method, String urlString, String postData, Boolean sendDefaultHead) throws IOException, JSONException {
        int retryCount = 5;
        while (retryCount > 0) {
            try {
                doRequest(method, urlString, postData, sendDefaultHead);
                break;
            } catch (Exception e) {
                retryCount--;
            }
        }
    }

    public static String get(String urlString, Boolean sendDefaultHead) throws IOException, JSONException {
        return new Http("GET", urlString, null, sendDefaultHead).responseText;
    }

    public static String post(String urlString, String postData, Boolean sendDefaultHead) throws IOException, JSONException {
        return new Http("POST", urlString, postData, sendDefaultHead).responseText;
    }

    public static int head(String urlString, Boolean sendDefaultHead) throws IOException, JSONException {
        return new Http("HEAD", urlString, null, sendDefaultHead).responseCode;
    }

    private void doRequest(String method, String urlString, String postData, Boolean sendDefaultHead) throws IOException, JSONException {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        method = method.toUpperCase();
        conn.setRequestMethod(method);
        conn.setConnectTimeout(5000);
        CookieHandler.setDefault(new CookieManager()); // avoid two cookie header, __cfduid
        if (sendDefaultHead) {
            conn.setRequestProperty("User-Agent", "android");
            conn.setRequestProperty("Cookie", CloundMusicPackage.getDefaultCookie());
        }

        if ("HEAD".equals(method)) {
            // http://stackoverflow.com/a/17638671/5287900
            conn.setRequestProperty("Accept-Encoding", "");
        }


        // send post data
        if ("POST".equals(method)) {
            conn.setDoOutput(true);

            OutputStream outputStream = conn.getOutputStream();
            BufferedWriter bufferedWriter = new BufferedWriter(new OutputStreamWriter(outputStream));
            bufferedWriter.write(postData);
            bufferedWriter.close();
            outputStream.close();
        }

        // receive
        responseCode = conn.getResponseCode();

        InputStreamReader inputStreamReader;
        if ("gzip".equals(conn.getContentEncoding()))
            inputStreamReader = new InputStreamReader(new GZIPInputStream(conn.getInputStream()));
        else
            inputStreamReader = new InputStreamReader(conn.getInputStream());
        BufferedReader bufferedReader = new BufferedReader(inputStreamReader);


        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = bufferedReader.readLine()) != null)
            sb.append(line).append("\n");

        bufferedReader.close();
        inputStreamReader.close();
        conn.disconnect();
        responseText = sb.toString();
    }
}

