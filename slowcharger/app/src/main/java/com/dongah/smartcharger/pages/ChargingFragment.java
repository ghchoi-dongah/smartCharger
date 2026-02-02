package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.ClassUiProcess;
import com.dongah.smartcharger.basefunction.FocusChangeEnabled;
import com.dongah.smartcharger.basefunction.PaymentType;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.utils.LogDataSave;
import com.dongah.smartcharger.utils.SharedModel;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.ocpp.core.Reason;
import com.dongah.smartcharger.websocket.ocpp.core.StatusNotificationRequest;
import com.dongah.smartcharger.websocket.ocpp.core.StopTransactionRequest;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.mikhaellopez.circularfillableloaders.CircularFillableLoaders;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Type;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ChargingFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChargingFragment extends Fragment implements View.OnClickListener, FocusChangeEnabled {

    private static final Logger logger = LoggerFactory.getLogger(ChargingFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    TextView txtChargePay, txtChargeTime, txtAmountOfCharge, txtPowerUnitPrice;
    Button  btnChargingStop;
    CircularFillableLoaders circularFillableLoaders;
    TextView txtSoc;

    Handler uiUpdateHandler;
    double powerUnitPrice = 0f;
    SharedModel sharedModel;
    String[] requestStrings = new String[1];

    ClassUiProcess classUiProcess;
    Date startTime = null, useTime = null;
    DecimalFormat payFormatter = new DecimalFormat("#,###,##0");
    DecimalFormat powerFormatter = new DecimalFormat("#,###,##0.00");
    DecimalFormat voltageFormatter = new DecimalFormat("#,###,##0.0");
    ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();

    private int progress = 0;

    public ChargingFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ChargingFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ChargingFragment newInstance(String param1, String param2) {
        ChargingFragment fragment = new ChargingFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_charging, container, false);
        txtSoc = view.findViewById(R.id.txtSoc);
        txtPowerUnitPrice = view.findViewById(R.id.txtPowerUnitPrice);
        txtChargePay = view.findViewById(R.id.txtChargePay);
        txtChargeTime = view.findViewById(R.id.txtChargeTime);
        txtAmountOfCharge = view.findViewById(R.id.txtAmountOfCharge);
        btnChargingStop = view.findViewById(R.id.btnChargingStop);
        btnChargingStop.setOnClickListener(this);
        return view;
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sharedModel = new ViewModelProvider(requireActivity()).get(SharedModel.class);
        try {
//            MediaPlayer mediaPlayer = MediaPlayer.create(MainActivity.mContext, R.raw.charging);
//            mediaPlayer.start();
//            onLimitPower(0);
            try {
                classUiProcess = ((MainActivity) MainActivity.mContext).getClassUiProcess();
                startTime = zonedDateTimeConvert.doStringDateToDate(classUiProcess.getChargingCurrentData().getChargingStartTime());

                powerUnitPrice = classUiProcess.getChargingCurrentData().getPowerUnitPrice();

                txtPowerUnitPrice.setText(powerUnitPrice + " 원");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            onCharging();
        } catch (Exception e) {
            logger.error("ChargingFragment onViewCreated : {}", e.getMessage());
        }

    }

    @Override
    public void onClick(View v) {
        int getId = v.getId();
        if  (Objects.equals(getId, R.id.btnChargingStop)) {
            try {
                ChargerConfiguration chargerConfiguration = ((MainActivity) MainActivity.mContext).getChargerConfiguration();
                if (!Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                    ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.CHARGING_STOP_MESSAGE, "CHARGING_STOP_MESSAGE", null);
                } else {
                    //서버 인증 모드인 경우
                    PaymentType paymentType = classUiProcess.getChargingCurrentData().getPaymentType();
                    if (Objects.equals(paymentType, PaymentType.MEMBER) && chargerConfiguration.isStopConfirm()) {
                        //StopTransactionOnInvalidId
                        ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
                    } else {
                        ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.CHARGING_STOP_MESSAGE, "CHARGING_STOP_MESSAGE", null);
                    }
                }
            } catch (Exception e) {
                logger.error("Charging onClick error : {}", e.getMessage());
            }
        }
    }


    private void onCharging() {
        uiUpdateHandler = new Handler();
        uiUpdateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                ((MainActivity) MainActivity.mContext).runOnUiThread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @SuppressLint({"DefaultLocale", "SetTextI18n"})
                    @Override
                    public void run() {
                        try {
                            long diffTime = 0;
                            classUiProcess = ((MainActivity) MainActivity.mContext).getClassUiProcess();
                            useTime = zonedDateTimeConvert.doStringDateToDate(zonedDateTimeConvert.getStringCurrentTimeZone());
                            if (useTime != null) {
                                diffTime = (useTime.getTime() - startTime.getTime()) / 1000;
                                int hour = (int) diffTime / 3600;
                                int minute = (int) (diffTime % 3600) / 60;
                                int second = (int) diffTime % 60;
                                classUiProcess.getChargingCurrentData().setChargingTime((int) diffTime);
                                txtChargeTime.setText(String.format("%02d", hour) + ":" + String.format("%02d", minute) + ":" + String.format("%02d", second));
                                classUiProcess.getChargingCurrentData().setChargingUseTime(txtChargeTime.getText().toString());
                                txtChargePay.setText(payFormatter.format((long) classUiProcess.getChargingCurrentData().getPowerMeterUsePay()) + " 원");
                                txtAmountOfCharge.setText(powerFormatter.format(classUiProcess.getChargingCurrentData().getPowerMeterUse() * 0.001) + " kWh");
                                if (classUiProcess.getChargingCurrentData().getSoc() == 0) {
                                    txtSoc.setVisibility(View.INVISIBLE);
                                } else {
                                    txtSoc.setVisibility(View.VISIBLE);
                                    txtSoc.setText(classUiProcess.getChargingCurrentData().getSoc() + "%");
                                }
//
//                                // 충전 남은 시간 : PLC 에서 지원 안함
//                                int rHour = classUiProcess.getChargingCurrentData().getRemaintime() / 3600;
//                                int rMinute = (classUiProcess.getChargingCurrentData().getRemaintime() % 3600) / 60;
//                                int rSecond = classUiProcess.getChargingCurrentData().getRemaintime() % 60;
//                                txtRemainTime.setText(String.format("%02d", rHour) + ":" + String.format("%02d", rMinute) + ":" + String.format("%02d", rSecond));
                            }
                            // used OCPP
//                            onLimitPower((int) diffTime);

                        } catch (Exception e) {
                            logger.error("ChargingFragment onCharging : {}", e.getMessage());
                        }
                    }
                });
                uiUpdateHandler.postDelayed(this, 1000);
            }
        }, 50);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        uiUpdateHandler.removeCallbacksAndMessages(null);
        uiUpdateHandler.removeMessages(0);
        if (uiUpdateHandler != null) uiUpdateHandler = null;
        requestStrings[0] = "0";
        sharedModel.setMutableLiveData(requestStrings);

    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        @SuppressLint("SimpleDateFormat")
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        final String CALL_FORMAT = "[2, \"%s\", \"%s\", %s]";
        UiSeq chk;
        String id;
        Object payload;
        Object call;
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            //미전송 데이터 저장 후, reboot
            chk = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            if (Objects.equals(chk, UiSeq.CHARGING)) {
                ZonedDateTimeConvert zonedDateTimeConvert;
                ZonedDateTime timestamp;
                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData().setMainMC(false);
                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData().setPwmDuty((short) 100);

                ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData().setUserStop(false);
                try {
                    //Stop Transaction
                    LogDataSave logDataSave = new LogDataSave("dump");
                    ChargingCurrentData chargingCurrentData = ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData();
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);
                    chargingCurrentData.setChargingEndTime(sdf.format(new Date()));
                    zonedDateTimeConvert = new ZonedDateTimeConvert();
                    timestamp = zonedDateTimeConvert.doZonedDateTimeToDatetime(chargingCurrentData.getChargingEndTime());
                    StopTransactionRequest stopTransactionRequest = new StopTransactionRequest(chargingCurrentData.getIntegratedPower(), timestamp, chargingCurrentData.getTransactionId(), Reason.PowerLoss);
                    stopTransactionRequest.setIdTag(chargingCurrentData.getIdTag());
                    stopTransactionRequest.setTransactionId(chargingCurrentData.getTransactionId());
                    stopTransactionRequest.setTimestamp(timestamp);
                    id = UUID.randomUUID().toString();
                    payload = packPayload(stopTransactionRequest);
                    call = String.format(CALL_FORMAT, id, stopTransactionRequest.getActionName(), payload);
                    logDataSave.makeDump(call.toString());
                    //Status Notification
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);
                    StatusNotificationRequest statusNotificationRequest = new StatusNotificationRequest(timestamp);
                    statusNotificationRequest.setConnectorId(chargingCurrentData.getConnectorId());
                    statusNotificationRequest.setErrorCode(chargingCurrentData.getChargePointErrorCode());
                    statusNotificationRequest.setStatus(chargingCurrentData.getChargePointStatus());
                    id = UUID.randomUUID().toString();
                    payload = packPayload(statusNotificationRequest);
                    call = String.format(CALL_FORMAT, id, statusNotificationRequest.getActionName(), payload);
                    logDataSave.makeDump(call.toString());
                    //Meter value Stop
                    ((MainActivity) MainActivity.mContext).getClassUiProcess().onMeterValueStop();
                    //PLC USed
//                    if (((MainActivity) MainActivity.mContext).getChargerConfiguration().isUsedPLC()) {
                        ((MainActivity) MainActivity.mContext).getClassUiProcess().onBatteryInfoStop();
 //                   }
                } catch (Exception e) {
                    logger.error(" {}", e.getMessage());
                }
            }
            // rebooting
            PowerManager powerManager = (PowerManager) (MainActivity.mContext).getSystemService(Context.POWER_SERVICE);
            powerManager.reboot("reboot");
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(10);
        } catch (Exception e) {
            logger.error(" charging onWindowFocusChanged : {}", e.getMessage());
        }
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

    private void onLimitPower(int second) {
        try {
            short powerLimit = 0;
            ChargingCurrentData chargingCurrentData = ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData();
            if (chargingCurrentData.isRemoteStartSmartCharging() && chargingCurrentData.remoteSmartChargingJsonArray != null) {
                for (int i = 0; i < chargingCurrentData.remoteSmartChargingJsonArray.length(); i++) {
                    JSONObject jsonObject = chargingCurrentData.remoteSmartChargingJsonArray.getJSONObject(i);
                    int startPeriod = jsonObject.getInt("startPeriod");
                    double limit = jsonObject.getDouble("limit");
                    if (startPeriod == second) {
                        powerLimit = (short) limit;
                        break;
                    } else if (startPeriod >= second && startPeriod != 0) {
                        powerLimit = (short) limit;
                        break;
                    }
                }
                if (powerLimit != 0) {
                    // pwm duty
                    ((MainActivity) MainActivity.mContext).getControlBoard().getTxData().setPwmDuty(powerLimit);
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
    }
}