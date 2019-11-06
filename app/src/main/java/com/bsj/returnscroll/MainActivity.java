package com.bsj.returnscroll;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;

import io.socket.client.IO;
import io.socket.client.Socket;
import io.socket.emitter.Emitter;

public class MainActivity extends AppCompatActivity {
    WebView webView;
    GpsInfo info;
    String unick;

    private String TAG = "WebSocket";
    private io.socket.client.Socket mSocket;

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        info = new GpsInfo(this);

        checkPermissions();

        LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        boolean isGps = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        boolean isNetwork = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
        boolean isPassive = lm.isProviderEnabled(LocationManager.PASSIVE_PROVIDER);

        if(isGps) {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 1, locationListener);
        }
        if(isNetwork) {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 100, 1, locationListener);
        }
//        GpsInfo gpsInfo = new GpsInfo(this);
//        final double lat = gpsInfo.getLatitude();
//        final double lng = gpsInfo.getLongitude();
//35.162807, 129.062828
//        Log.e("Main", lat + "");
//        Log.e("Main", lng + "");

        webView = findViewById(R.id.web_view);
       /* webView.setWebChromeClient(new WebChromeClient(){
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                super.onGeolocationPermissionsShowPrompt(origin, callback);
                callback.invoke(origin,true,false);
            }
        });*/


        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
//                view.loadUrl("javascript:alert(1)");
//                view.loadUrl("javascript:setLocation(" + lat + ", " + lng + ", 1)");
//                Log.e("Main", "end");
            }
        });

        webView.setWebChromeClient(new WebChromeClient(){
            @Override
            public boolean onJsAlert(WebView view, String url, String message, JsResult result){
                return super.onJsAlert(view, url, message, result);
            }
        });
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setAllowContentAccess(true);
        webView.getSettings().setAllowFileAccess(true);
        webView.getSettings().setAllowFileAccessFromFileURLs(true);
        webView.getSettings().setAllowUniversalAccessFromFileURLs(true);
        webView.loadUrl("http://192.168.0.28:8080/returnscroll");

        webView.addJavascriptInterface(new JavascriptInterface(), "loc");

        try{
            mSocket = IO.socket("http://192.168.0.28:82");
            mSocket.connect();
            mSocket.on(Socket.EVENT_CONNECT, onConnect);
            mSocket.on("serverMessage", onMessageReceived);
        }catch (URISyntaxException e){
            e.printStackTrace();

        }


    }


    private  Emitter.Listener onConnect = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            Log.e("onConnect", "11");
            // mSocket.emit("clientMessage","hi hi hi");
        }
    };

    // 서버로부터 전달받은 'chat-message' Event 처리.
    private Emitter.Listener onMessageReceived = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            // 전달받은 데이터는 아래와 같이 추출할 수 있습니다.
            try {
                JSONObject receivedData = (JSONObject) args[0];
                Log.d(TAG, receivedData.getString("msg"));
                Log.d(TAG, receivedData.getString("data"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    };

    Handler handler = new Handler();
    final class JavascriptInterface {
        @android.webkit.JavascriptInterface // 최근에는 이 어노테이션을 붙여줘야 동작하게 되어 있다..
        public void sendLocation(final String nick){// 반드시 final이어야 한다.
            // 네트워크를 통한 작업임으로 백그라운드 스레드를 써서 작업해야한다.
            // 또한, 백그라운드 스레드는 직접 메인 뷰에 접근해 제어할 수 없음으로
            // 핸들러를 통해서 작업해야하는데
            // 이 때문에 한번에 handler.post()를 통해서 내부에 Runnable을 구현해 작업한다.
            Log.i("init","init==============================");
            unick  = nick;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    Log.i("handler","handler===========================");

                    //유저아이디값 받기 (서버)
                    mSocket.on("send_userid_android", new Emitter.Listener() {
                        @Override
                        public void call(Object... args) {
                            double lat = info.getLatitude();
                            double lng = info.getLongitude();
                           /* mSocket.emit("send_userid",user_id);*/
                            String user_id = (String) args[0];
                            if(unick.equals(user_id)) {
                                //맨 처음 위치값 -> 서버로
                                Log.e("unick/user_id", unick + "/"+user_id); // 변경된 위도
                                mSocket.emit("send_a_latlng",user_id, lat,lng);
                            }

                        }
                    });

                   // mSocket.emit("send_logitude", lng);
                   // webView.loadUrl("javascript:setLocation(" + lat + ", " + lng + ")");

                }
            });
        }
    }

    LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) { // 위치 변경
            double lat = location.getLatitude();
            double lng = location.getLongitude();

            //위치 이동시 위치값 보냄 -> 서버로
            mSocket.emit("send_a_latlng2", unick,lat,lng);

            // mSocket.emit("send_logitude2", lng);
           //webView.loadUrl("javascript:setLocation2(" + lat + ", " + lng + ")");

//            Log.e("provider", location.getProvider()); // 위치 제공자
//            Log.e("lat", lat + ""); // 변경된 위도
//            Log.e("lng", lng + ""); // 변경된 경도
        }
        @Override
        public void onStatusChanged(String s, int i, Bundle bundle) {} // 위치 제공자 상태 변경
        @Override
        public void onProviderEnabled(String s) {} // 위치 제공자 활성화
        @Override
        public void onProviderDisabled(String s) {} // 위치 제공자 비활성화
    };
    public void checkPermissions() {
        String[] permissions = {
                Manifest.permission.ACCESS_FINE_LOCATION
        };

        int permissionCheck = PackageManager.PERMISSION_GRANTED;
        for (int i = 0; i < permissions.length; i++) {
            permissionCheck = ContextCompat.checkSelfPermission(this, permissions[i]);
            if (permissionCheck == PackageManager.PERMISSION_DENIED) {
                break;
            }
        }

        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
//            Toast.makeText(this, "권한 있음", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "권한 없음", Toast.LENGTH_LONG).show();

            if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[0])) {
                Toast.makeText(this, "권한 설명 필요함.", Toast.LENGTH_LONG).show();
            } else {
                ActivityCompat.requestPermissions(this, permissions, 1);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        if (requestCode == 1) {
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, permissions[i] + " 권한이 승인됨.", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, permissions[i] + " 권한이 승인되지 않음.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }


}
