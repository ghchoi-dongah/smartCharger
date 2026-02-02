package com.dongah.smartcharger.handler;

import android.os.Build;
import android.os.Environment;

import androidx.annotation.RequiresApi;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.utils.FileManagement;
import com.dongah.smartcharger.utils.LogDataSave;
import com.dongah.smartcharger.websocket.ocpp.common.OccurenceConstraintException;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.vas.VasData;
import com.dongah.smartcharger.websocket.ocpp.core.datatransfer.vas.VasRequest;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.SocketState;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BatteryInfoThread extends Thread {


    private static final Logger logger = LoggerFactory.getLogger(BatteryInfoThread.class);
    private static final String FILE_PATH = Environment.getExternalStorageDirectory().toString() + File.separator + "Download";
    private static final String FILE_NAME = "batteryInfo.dongah";

    Gson gson = new Gson();

    FileManagement fileManagement;

    boolean stopped = false;
    int delayTime;


    Object payload, call;
    final String CALL_FORMAT = "[2, \"%s\", \"%s\", %s]";

    public boolean isStopped() {
        return stopped;
    }

    public void setStopped(boolean stopped) {
        this.stopped = stopped;
    }

    public int getDelayTime() {
        return delayTime;
    }

    public void setDelayTime(int delayTime) {
        this.delayTime = delayTime;
    }

    public BatteryInfoThread(int delayTime) {
        this.delayTime = delayTime;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void run() {
        super.run();

        int count = 0;
        while (!stopped) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            count++;

            if (count >= (delayTime)) {
                count = 0;


                String filePath = FILE_PATH + File.separator + FILE_NAME;
                File file = new File(filePath);
                if (file.exists()) {
                    SocketReceiveMessage socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                    ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();
                    ZonedDateTime timestamp = zonedDateTimeConvert.doZonedDateTimeToDatetime();
                    SocketState socketState = socketReceiveMessage.getSocket().getState();

                    int configCnt = ((MainActivity) MainActivity.mContext).getChargerConfiguration().getConfigCnt();
                    int connectorId =  ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData().getConnectorId();
                    List<String> lines = new ArrayList<>();
                    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            lines.add(line.trim());
                        }
                    } catch (IOException ee) {
                        logger.error("file read :  {}", ee.getMessage());
                        return;
                    }

                    List<String> toSend = lines.subList(0, Math.min(configCnt, lines.size()));
                    // remain data
                    List<String> toKeep = lines.subList(Math.min(configCnt, lines.size()), lines.size());

                    if (!toSend.isEmpty()) {
                        List<Map<String, String>> cleanList = new ArrayList<>();

                        for (String line : toSend) {
                            try {
                                JSONObject lineObj = new JSONObject(line);
                                String battery = lineObj.getString("battery");

                                Map<String, String> batteryMap = new HashMap<>();
                                batteryMap.put("battery", battery);
                                batteryMap.put("timeStamp", lineObj.getString("timeStamp"));

                                cleanList.add(batteryMap);

                            } catch (JSONException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        int sendCnt = Math.min(configCnt, cleanList.size());

                        try {
                            VasData vasData = new VasData();
                            vasData.setInfoCnt(String.valueOf(sendCnt));
                            vasData.setBatteryData(cleanList);

                            VasRequest vasRequest = new VasRequest();
                            vasRequest.setVendorId("kr.or.keco");
                            vasRequest.setMessageId("BatteryInfo");
                            vasRequest.setData(vasData);


                            if (socketState == SocketState.OPEN) {
                                try {
                                    socketReceiveMessage.onSend(connectorId, vasRequest.getActionName(), vasRequest);
                                } catch (OccurenceConstraintException e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                String uuid = UUID.randomUUID().toString();
                                payload = packPayload(vasRequest);
                                call = String.format(CALL_FORMAT, uuid, vasRequest.getActionName(), payload);
                                LogDataSave logDataSave = new LogDataSave();
                                logDataSave.makeDump(call.toString());
                            }

                        } catch (Exception  e) {
                            throw new RuntimeException(e);
                        }
                    }


                    // 남은 데이터를 다시 파일에 저장
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath, false))) {
                        for (String line : toKeep) {
                            writer.write(line);
                            writer.newLine();
                        }
                        System.out.println("남은 JSON 데이터 파일에 저장 완료");
                    } catch (IOException ez) {
                        logger.error(" remain data save : {}", ez.getMessage());
                    }

                }
            }
        }
    }

    @Override
    public void interrupt() {
        super.interrupt();
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Object packPayload(Object payload) {
        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeSerializer());
        Gson gson = builder.create();
        return gson.toJson(payload);
    }


    private static class ZonedDateTimeSerializer implements JsonSerializer<ZonedDateTime> {
        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public JsonElement serialize(ZonedDateTime zonedDateTime, Type type, JsonSerializationContext jsonSerializationContext) {
            return new JsonPrimitive(zonedDateTime.format(DateTimeFormatter.ISO_INSTANT));
        }
    }
}
