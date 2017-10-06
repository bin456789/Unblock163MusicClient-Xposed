package bin.xposed.Unblock163MusicClient;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.conn.params.ConnPerRoute;
import org.apache.http.conn.params.ConnPerRouteBean;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.json.JSONException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.GZIPInputStream;

@SuppressWarnings("ALL")
class Http {
    private static HttpClient httpClient = null;
    private int responseCode;
    private String responseText;
    private long contentLength;
    private String finalLocation;


    private Http(String method, String urlString, String postData, Boolean sendDefaultHead, Map<String, String> additionalHeaders) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        int retryCount = 3;
        while (true) {
            try {
                doRequest(method, urlString, postData, sendDefaultHead, additionalHeaders);
                break;
            } catch (Throwable t) {
                t.printStackTrace();
                retryCount--;
                if (retryCount <= 0)
                    throw t;
            }
        }
    }

    public static Http get(String urlString, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        return new Http("GET", urlString, null, sendDefaultHead, null);
    }

    static Http post(String urlString, Map postData, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        return new Http("POST", urlString, Utility.serialData(postData), sendDefaultHead, null);
    }


    public static Http head(String urlString, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        return new Http("HEAD", urlString, null, sendDefaultHead, null);
    }

    static Http headByGet(String urlString, Boolean sendDefaultHead) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        Map<String, String> map = new LinkedHashMap<String, String>() {{
            // 虾米0-0有时会416
            put("Range", "bytes=0-1");
        }};
        return new Http("GET", urlString, null, sendDefaultHead, map);
    }

    public static synchronized HttpClient getHttpClient() {
        if (httpClient == null) {
            HttpParams params = new BasicHttpParams();
            HttpClientParams.setRedirecting(params, false);

            HttpConnectionParams.setConnectionTimeout(params, 5000);
            HttpConnectionParams.setSoTimeout(params, 5000);

            SchemeRegistry schReg = new SchemeRegistry();
            schReg.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
            schReg.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));


            ConnPerRoute perRoute = new ConnPerRouteBean(1000);
            ConnManagerParams.setMaxConnectionsPerRoute(params, perRoute);
            ConnManagerParams.setMaxTotalConnections(params, 1000);

            ClientConnectionManager conMgr = new ThreadSafeClientConnManager(params, schReg);
            httpClient = new DefaultHttpClient(conMgr, params);
        }

        return httpClient;
    }

    private void doRequest(String method, String urlString, String postData, Boolean sendDefaultHead, Map<String, String> additionHeaders) throws IOException, JSONException, InvocationTargetException, IllegalAccessException {
        if (urlString == null)
            return;

        if (method == null)
            method = "GET";


        urlString = urlString.replace("/thirdyires.imusicapp.cn/res/thirdparty/", "/wapst.ctmus.cn/res/V/");

        HttpUriRequest request = null;

        if ("HEAD".equals(method)) {
            request = new HttpHead(urlString);
        } else if ("GET".equals(method)) {
            request = new HttpGet(urlString);
        } else if ("POST".equals(method)) {
            request = new HttpPost(urlString);
            HttpEntity entity = new ByteArrayEntity(postData.getBytes("UTF-8"));
            ((HttpPost) request).setEntity(entity);
            request.setHeader("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        }

        finalLocation = request.getURI().toString();

        // 开始设置 Header
        request.setHeader("User-Agent", "android");

        if (sendDefaultHead) {
            request.setHeader("Cookie", String.format("modver=%s; %s", BuildConfig.VERSION_NAME, CloudMusicPackage.HttpEapi.getDefaultCookie()));
        }

        if (additionHeaders != null) {
            for (Map.Entry<String, String> entry : additionHeaders.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }

        // 开始请求
        HttpResponse response = getHttpClient().execute(request);


        // receive
        responseCode = response.getStatusLine().getStatusCode();
        if (responseCode == 301 || responseCode == 302) {
            doRequest(method, response.getFirstHeader("Location").getValue(), postData, sendDefaultHead, additionHeaders);
            return;
        }


        if (responseCode >= 200 && responseCode < 400) {
            InputStream inputStream = response.getEntity().getContent();
            Header contentEncoding = response.getFirstHeader("Content-Encoding");
            if (contentEncoding != null && contentEncoding.getValue().equalsIgnoreCase("gzip")) {
                inputStream = new GZIPInputStream(inputStream);
            }

            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            StringBuilder sb = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null)
                sb.append(line).append("\n");

            bufferedReader.close();
            inputStream.close();
            responseText = sb.toString();

            // whole file size
            if (response.containsHeader("Content-Range"))
                contentLength = Integer.parseInt(Utility.getLastPartOfString(response.getFirstHeader("Content-Range").getValue(), "/"));
            else
                contentLength = response.getEntity().getContentLength();


        }
    }

    public int getResponseCode() {
        return responseCode;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getResponseText() {
        return responseText;
    }

    public String getFinalLocation() {
        return finalLocation;
    }

}

