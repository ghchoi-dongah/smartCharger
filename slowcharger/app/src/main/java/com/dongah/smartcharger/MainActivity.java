package com.dongah.smartcharger;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.ClassUiProcess;
import com.dongah.smartcharger.basefunction.ConfigurationKeyRead;
import com.dongah.smartcharger.basefunction.FocusChangeEnabled;
import com.dongah.smartcharger.basefunction.FragmentChange;
import com.dongah.smartcharger.basefunction.FragmentCurrent;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.controlboard.ControlBoard;
import com.dongah.smartcharger.handler.ProcessHandler;
import com.dongah.smartcharger.plc.PlcModem;
import com.dongah.smartcharger.rfcard.RfCardReaderReceive;
import com.dongah.smartcharger.websocket.ocpp.core.Reason;
import com.dongah.smartcharger.websocket.socket.ConnectionListJsonParse;
import com.dongah.smartcharger.websocket.socket.Connector;
import com.dongah.smartcharger.websocket.socket.HttpClientHelper;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.SocketState;
import com.dongah.smartcharger.websocket.socket.TripleDES;
import com.dongah.smartcharger.websocket.tcpsocket.ClientSocket;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {


    public static final Logger logger = LoggerFactory.getLogger(MainActivity.class);


    @SuppressLint("StaticFieldLeak")
    public static Context mContext;

    TextView textViewTime, textViewVersionValue;
    Handler handler = new Handler();
    Runnable runnable;
    ImageView imgNetwork;

    UiSeq fragmentSeq;
    FragmentChange fragmentChange;
    ChargerConfiguration chargerConfiguration;
    ClassUiProcess classUiProcess;
    ProcessHandler processHandler;
    SocketReceiveMessage socketReceiveMessage;
    ControlBoard controlBoard;
    RfCardReaderReceive rfCardReaderReceive;
    ConfigurationKeyRead configurationKeyRead;

    /**
     * current fragment Exception check
     */
    FragmentCurrent fragmentCurrent;

    // connection list
    List<Connector> connectorList = new ArrayList<>();
    Pattern pattern = Pattern.compile("\\+CNUM:\\s*\"([^\"]*)\",\"([^\"]*)\",(\\d+)");

    /** PLC Modem*/
    PlcModem plcModem;

    ClientSocket clientSocket;


    public FragmentChange getFragmentChange() {
        return fragmentChange;
    }
    public ChargerConfiguration getChargerConfiguration() {
        return chargerConfiguration;
    }
    public ClassUiProcess getClassUiProcess() {
        return classUiProcess;
    }
    public SocketReceiveMessage getSocketReceiveMessage() {
        return socketReceiveMessage;
    }

    public ControlBoard getControlBoard() {
        return controlBoard;
    }
    public RfCardReaderReceive getRfCardReaderReceive() {
        return rfCardReaderReceive;
    }

    public ProcessHandler getProcessHandler() {
        return processHandler;
    }
    public ConfigurationKeyRead getConfigurationKeyRead() {
        return configurationKeyRead;
    }

    public PlcModem getPlcModem() {
        return plcModem;
    }
    public List<Connector> getConnectorList() {
        return connectorList;
    }



    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);

        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE);

        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        hideNavigationBar();

        mContext = this;

        /* 슬립 모드 방지*/
        super.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        /* 세로 고정 */
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

        //Fragment change;
        fragmentChange = new FragmentChange();
        //Current Fragment
        fragmentCurrent = new FragmentCurrent();

        imgNetwork = findViewById(R.id.imgNetwork);
        textViewTime = findViewById(R.id.textViewTime);
        textViewVersionValue = findViewById(R.id.textViewVersionValue);
        textViewVersionValue.setText("VER-DEVW " + GlobalVariables.VERSION + " | ");


        //   ConfigurationKey read */
        configurationKeyRead = new ConfigurationKeyRead();
        configurationKeyRead.onRead();
        // 1. charger configuration, ConfigurationKey read */
        chargerConfiguration = new ChargerConfiguration();
        chargerConfiguration.onLoadConfiguration();
        // 2. fragment change management */
        for (int i = 0; i < GlobalVariables.maxChannel; i++) {
            fragmentChange.onFragmentChange(UiSeq.INIT, "INIT", "");
        }
        // 3. Control board
        controlBoard = new ControlBoard(GlobalVariables.maxChannel, chargerConfiguration.getControlCom());

        // 4. rf card reade : MID = terminal ID */
        rfCardReaderReceive = new RfCardReaderReceive(chargerConfiguration.getRfCom());
        // 5. web socket*/
        processHandler = new ProcessHandler(chargerConfiguration);


        //모뎀 정보 갖고 오기
        clientSocket = new ClientSocket("192.168.39.1", 9999, new ClientSocket.TcpClientListener() {
            @Override
            public void onConnected() {
                logger.debug("connected");
//                clientSocket.sendMessage("AT+CNUM");

                clientSocket.start();

                // 예: AT+CNUM 전송 후 +CNUM: 응답을 기다리고, 성공하면 AT$$DSCREEN? 전송
                clientSocket.sendCommandExpectPrefix("AT+CNUM", "+CNUM:", 10000)
                        .thenApply(line -> {
                            // line 예: +CNUM: "LGU","+821222492396",145
                            String[] parts = line.split(",");
                            String raw = parts.length >= 2 ? parts[1].replace("\"","") : null;
                            GlobalVariables.setIMSI(raw == null ? "" : parseToLocal(raw));
                            return parseToLocal(raw); // 01222492396
                        })
                        .thenCompose(localNumber -> {
                            Log.d("TCP","Parsed local number: " + localNumber);
                            // 이어서 DSCREEN 명령
                            return clientSocket.sendCommandExpectPrefix("AT$$DSCREEN?", "DSCREEN:", 5000);
                        })
                        .thenAccept(dscreenResp -> {
                            GlobalVariables.setRSRP(parseToRSRP(dscreenResp));
                            Log.d("TCP","DSCREEN response: " + dscreenResp);
                            clientSocket.postDisconnected();
                            clientSocket.closeSocket();
                        })
                        .exceptionally(ex -> {
                            Log.e("TCP","Command chain error", ex);
                            return null;
                        });
            }

            @Override
            public void onDisconnected() {

            }

            @Override
            public void onError(Exception e) {

            }

            @Override
            public void onMessageReceived(String message) {
//                Matcher matcher = pattern.matcher(message);
//                if (matcher.find()) {
//                    String name = matcher.group(1);
//                    String number = matcher.group(2);
//                    String type = matcher.group(3);
//                    GlobalVariables.setIMSI(number == null ? "" : parseToLocal(number));
//                    clientSocket.postDisconnected();
//                    clientSocket.closeSocket();
//                }
                // 모든 수신 메시지(일반)는 여기로 들어옵니다.
                Log.d("TCP","General recv: " + message);
            }
        });


//        String baseUrl = chargerConfiguration.getServerConnectingString() + ":" + chargerConfiguration.getServerPort() +
//                "/v2/" + chargerConfiguration.getChargerId();
//        socketReceiveMessage = new SocketReceiveMessage(baseUrl);
//
        //서버 모드
        if (Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
            sendOcppAuthInfoRequest();
        }

        // 6. classUiProcess */
        classUiProcess = new ClassUiProcess();

        //7. PLC Modem
        plcModem = new PlcModem(chargerConfiguration.getPlcCom());

        //8. ChargerOperate read
        File file = new File(GlobalVariables.getRootPath() + File.separator + "ChargerOperate");
        File firmwareFile = new File(GlobalVariables.getRootPath() + File.separator + "FirmwareStatusNotification");
        if (!firmwareFile.exists()) {
            if (file.exists()) {
                FileReader fileReader;
                try {
                    fileReader = new FileReader(file);
                    BufferedReader bufferedReader = new BufferedReader(fileReader);
                    String line;
                    int count = 0;
                    while ((line = bufferedReader.readLine()) != null) {
                        GlobalVariables.ChargerOperation[count] = Objects.equals(line, "true");
                        count++;
                    }
                } catch (IOException e) {
                    logger.error("ChargerOperate read error : {}", e.getMessage());
                }
            } else {
                for (int i = 0; i < GlobalVariables.maxPlugCount; i++) {
                    GlobalVariables.ChargerOperation[i] = true;
                }
            }
        }


        //GlobalVariables.setHmPreparingTranTerm
        processHandler.onCustomStatusNotificationStart(Integer.parseInt(GlobalVariables.getHmPreparingTranTerm()));
        // customer unit price
        processHandler.onCustomUnitPriceStart(3600);

        //Diagnostics thread start
//        processHandler.onDiagnosticsStart(20);
        /**
         * OTA Start
         * sample
         */
//        OtaOrderRequest otaOrderRequest = new OtaOrderRequest((byte) 0x18);
//        byte[] report = otaOrderRequest.makeOtaOrderRequest();
//        plcModem.onSend(report);
    }


    @Override
    protected void onStart() {
        super.onStart();

        runnable = new Runnable() {
            @Override
            public void run() {
                updateTime();
                // 1초마다 실행
                handler.postDelayed(this, 1000);
                try {
                    if (socketReceiveMessage.getSocket().getState() != null) {
                        imgNetwork.setBackgroundResource(socketReceiveMessage.getSocket().getState() == SocketState.OPEN ?
                                R.drawable.network : R.drawable.nonetwork);
                    }
                } catch (Exception e) {
                    logger.error(e.getMessage());
                }
            }
        };
        runnable.run();
    }

    private void updateTime() {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
            String currentTime = sdf.format(new Date());
            textViewTime.setText(currentTime);
        } catch (Exception e){
            logger.error(e.getMessage());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        try {
            // channel argument check
            if (fragmentCurrent.getCurrentFragment() instanceof FocusChangeEnabled) {
                ((FocusChangeEnabled) fragmentCurrent.getCurrentFragment()).onWindowFocusChanged(hasFocus);
            }
            if (hasFocus) {
                hideNavigationBar();
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }

    /**
     * ui version update
     */
    public void onRebooting() {
        try {
            boolean result = false;
            ChargingCurrentData chargingCurrentData;
            chargingCurrentData = getClassUiProcess().getChargingCurrentData();
            result = chargingCurrentData.isReBoot() && (getClassUiProcess().getUiSeq() == UiSeq.INIT);

            if (result) {
                getClassUiProcess().setUiSeq(UiSeq.REBOOTING);
                getClassUiProcess().getChargingCurrentData().setStopReason(Reason.Reboot);
            }

        } catch (Exception e) {
            logger.error(" version reboot : {}", e.getMessage());
        }
    }

    public void onRebooting(String type) {
        try {
            ((MainActivity) MainActivity.mContext).getSocketReceiveMessage().getSocket().disconnect();
            if (Objects.equals(type, "Soft")) {
//                Intent intent = ((MainActivity) MainActivity.mContext).getPackageManager().getLaunchIntentForPackage(PACKAGE_NAME);
//                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//                (MainActivity.mContext).startActivity(intent);
                ActivityCompat.finishAffinity(((MainActivity) MainActivity.mContext));
                System.exit(0);
            } else {
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                powerManager.reboot("reboot");
            }
        } catch (Exception e) {
            logger.error("onRebooting : {}", e.getMessage());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
////        versionHandler.removeMessages(0);
////        versionHandler.removeCallbacksAndMessages(null);
////        if (versionHandler != null) versionHandler = null;
        processHandler.onCustomStatusNotificationStop();
        processHandler.onDiagnosticsStop();
        handler.removeCallbacks(runnable); // 메모리 누수 방지
    }

    private void hideNavigationBar() {
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }


    /**
     * HTTPS 연결이 안되면 다시 접속
     */
    private static final int RETRY_DELAY_MS = 3000;  // 3초
    private static final int MAX_RETRY_COUNT = 5;    // 최대 재시도 횟수
    private int retryCount = 0;
    private Handler mainHandler = new Handler(Looper.getMainLooper());
    private void sendOcppAuthInfoRequest() {
        HttpClientHelper httpClient = new HttpClientHelper();
        String addUrl = TextUtils.isEmpty(chargerConfiguration.getM2mTel()) ? "SN" : "TN";
        String url = chargerConfiguration.getServerHttpString() + "/getOcppAuthInfo/" + addUrl;

        TripleDES tripleDES = new TripleDES();

        try {
            String encrypted = tripleDES.encrypt(TextUtils.isEmpty(chargerConfiguration.getM2mTel()) ?
                    chargerConfiguration.getChargerPointSerialNumber() : chargerConfiguration.getM2mTel());
            String jsonBody = httpClient.onJsonMake("reqVal", encrypted);

            httpClient.postWithRetry(url, jsonBody, new HttpClientHelper.HttpCallback() {
                @SuppressLint("SetTextI18n")
                @Override
                public void onSuccess(int statusCode, String response) {
                    try {
                        if (statusCode == 200) {
                            JSONObject jsonObject = new JSONObject(response);
                            String resultCode = jsonObject.optString("resultCode", "");

                            if ("OK".equals(resultCode)) {
                                // WebSocket 연결 설정
                                GlobalVariables.setHumaxClientId(tripleDES.decrypt(jsonObject.getString("clientId")));

                                if (!jsonObject.getString("passwd").isEmpty()) {
                                    GlobalVariables.setHumaxPassWd(tripleDES.decrypt(jsonObject.getString("passwd")));
                                }

                                ConnectionListJsonParse connectionListJsonParse = new ConnectionListJsonParse();
                                connectorList = connectionListJsonParse.parseConnectorList(response);

//                                runOnUiThread(() -> textViewChargerId.setText("ID: " + connectorList.get(0).getSearchKey()));
                                runOnUiThread(() -> chargerConfiguration.setChargerId(String.valueOf(connectorList.get(0).getSearchKey())));

                                String baseUrl = chargerConfiguration.getServerConnectingString() + "/" + GlobalVariables.getHumaxClientId();
                                socketReceiveMessage = new SocketReceiveMessage(baseUrl);
                                retryCount = 0; // 성공 시 재시도 횟수 초기화
                                //QR Code refres
                                fragmentChange.onFragmentChange(UiSeq.INIT, "INIT", "");

                            } else {
                                Log.w("HTTP", "resultCode != OK → 3초 후 재요청");
                                scheduleRetry();
                            }
                        } else {
                            Log.w("HTTP", "statusCode != 200 → 3초 후 재요청");
                            scheduleRetry();
                        }
                    } catch (Exception e) {
                        Log.e("PARSE_ERROR", "JSON 파싱 오류", e);
                        scheduleRetry();
                    }
                    Log.d("HTTP", "Response: " + response);
                }

                @Override
                public void onFailure(IOException e) {
                    Log.e("HTTP", "Failed to send request", e);
                    scheduleRetry();
                }
            });
        } catch (Exception e) {
            logger.error("REQUEST_ERROR {}", e.getMessage());
            scheduleRetry();
        }
    }


    private void scheduleRetry() {
        if (retryCount < MAX_RETRY_COUNT) {
            retryCount++;
            Log.w("HTTP", "재시도 " + retryCount + "회 / " + MAX_RETRY_COUNT + "회");
            mainHandler.postDelayed(this::sendOcppAuthInfoRequest, RETRY_DELAY_MS);
        } else {
            Log.e("HTTP", "최대 재시도 횟수 초과 → 요청 중단");
        }
    }


    private String parseToLocal(String number) {
        if (number.startsWith("+82")) {
            return "0" + number.substring(3);
        }
        return number;
    }


    private String parseToRSRP(String resp) {
        Pattern p = Pattern.compile("RSRP:([-]?\\d+)");
        Matcher m = p.matcher(resp);
        if (m.find()) {
            int rsrp = Integer.parseInt(m.group(1));  // -71
            return String.valueOf(rsrp);
        } else {
            System.out.println("RSRP not found");
        }
        return "";
    }


    public UiSeq getFragmentSeq() {
        return fragmentSeq;
    }

    public void setFragmentSeq(UiSeq fragmentSeq) {
        this.fragmentSeq = fragmentSeq;
    }
}