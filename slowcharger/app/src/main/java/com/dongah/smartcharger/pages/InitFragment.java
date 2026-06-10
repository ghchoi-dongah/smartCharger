package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.basefunction.PaymentType;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.plc.request.ConfigSetupReq;
import com.dongah.smartcharger.plc.request.PacketRequest;
import com.dongah.smartcharger.plc.request.StartRequest;
import com.dongah.smartcharger.utils.SharedModel;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.Connector;
import com.dongah.smartcharger.websocket.socket.SocketState;
import com.google.zxing.BarcodeFormat;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link InitFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class InitFragment extends Fragment implements View.OnClickListener {

    private static final Logger logger = LoggerFactory.getLogger(InitFragment.class);


    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    ImageView btnQr;
    Animation animBlink;
    View viewCircle;
    TextView textViewMemberUnitInput,textViewInitMessage ;
    ChargerConfiguration chargerConfiguration;
    Handler unitPriceHandler;
    SharedModel sharedModel;
    String[] requestStrings = new String[1];
    //plc modem
    PacketRequest packetRequest;
    byte[] report;
    MainActivity activity;

    public InitFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment InitFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static InitFragment newInstance(String param1, String param2) {
        InitFragment fragment = new InitFragment();
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
        View view = inflater.inflate(R.layout.fragment_init, container, false);
        activity = (MainActivity) MainActivity.mContext;
        activity.getClassUiProcess().setUiSeq(UiSeq.INIT);
        textViewMemberUnitInput = view.findViewById(R.id.textViewMemberUnitInput);
        animBlink = AnimationUtils.loadAnimation(getActivity(), R.anim.blink);
        viewCircle = view.findViewById(R.id.viewCircle);
        viewCircle.setOnClickListener(this);
        textViewInitMessage = view.findViewById(R.id.textViewInitMessage);
        textViewInitMessage.startAnimation(animBlink);

        btnQr = view.findViewById(R.id.btnQr);
        btnQr.setOnClickListener(this);
        try {
            chargerConfiguration = activity.getChargerConfiguration();
            if (!TextUtils.isEmpty(chargerConfiguration.getChargerId())) {
                BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
                Connector connector = activity.getConnectorList().get(0);
                String qrCodeURL = connector.getQrUrl();
                Bitmap bitmap = barcodeEncoder.encodeBitmap(qrCodeURL,BarcodeFormat.QR_CODE, 120, 120);
                btnQr.setImageBitmap(toGrayscale(bitmap));
            }
        } catch (Exception e) {
            logger.error("QrCode : {}", e.getMessage());
        }
        return view;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    @SuppressLint("SetTextI18n")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        activity.getClassUiProcess().getChargingCurrentData().setConnectorId(1);
        //사용 단가 display
        onUnitPriceDisplay();
        // home image
        sharedModel = new ViewModelProvider(requireActivity()).get(SharedModel.class);
        requestStrings[0] = String.valueOf(0);
        sharedModel.setMutableLiveData(requestStrings);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        try {
            if (unitPriceHandler != null) {
                unitPriceHandler.removeCallbacksAndMessages(null);
                unitPriceHandler.removeMessages(0);
                unitPriceHandler = null;
            }
            animBlink.cancel();
            animBlink = null;
            requestStrings[0] = String.valueOf(0);
            sharedModel.setMutableLiveData(requestStrings);
        } catch (Exception e) {
            logger.error("onDetach error : {}" , e.getMessage(), e);
        }
    }

    @Override
    public void onClick(View v) {
        try {
            int getId = v.getId();
            ChargingCurrentData chargingCurrentData = activity.getClassUiProcess().getChargingCurrentData();
            chargingCurrentData.onCurrentDataClear();

            if (!Objects.equals(v.getId(), R.id.viewCircle)) return;
            if (Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                if (!onUnitPrice()) {
                    Toast.makeText(getActivity(), "단가 정보가 없습니다. \n잠시 후, 충전하세요!", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    SocketState socketState = activity.getSocketReceiveMessage().getSocket().getState();
                    if (Objects.equals(socketState, SocketState.OPEN)) {
                        if (Objects.equals(chargerConfiguration.getSelectPayment(), "0")) {
                            // credit+member
                            activity.getClassUiProcess().setUiSeq(UiSeq.AUTH_SELECT);
                            activity.getFragmentChange().onFragmentChange(UiSeq.AUTH_SELECT, "AUTH_SELECT", null);
                        } else if (Objects.equals(chargerConfiguration.getSelectPayment(), "1")) {
                            // member
                            chargingCurrentData.setPaymentType(PaymentType.MEMBER);
                            activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                            activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
                        }
                    } else {
                        Toast.makeText(getActivity(), "서버 연결 DISCONNECT. \n충전을 할 수 없습니다.", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(getActivity(), "서버 연결 DISCONNECT. \n충전을 할 수 없습니다.", Toast.LENGTH_SHORT).show();
                    logger.error(e.getMessage());
                }
            } else if (Objects.equals(chargerConfiguration.getAuthMode(), "4")) {
                // local 회원 인증용
                double testPrice = Double.parseDouble(activity.getChargerConfiguration().getTestPrice());
                activity.getClassUiProcess().getChargingCurrentData().setPowerUnitPrice(testPrice);
                activity.getClassUiProcess().setUiSeq(UiSeq.MEMBER_CARD);
                activity.getFragmentChange().onFragmentChange(UiSeq.MEMBER_CARD, "MEMBER_CARD", null);
            } else {
                double testPrice = Double.parseDouble(activity.getChargerConfiguration().getTestPrice());
                activity.getClassUiProcess().getChargingCurrentData().setPowerUnitPrice(testPrice);
                activity.getClassUiProcess().setUiSeq(UiSeq.PLUG_CHECK);
                activity.getFragmentChange().onFragmentChange(UiSeq.PLUG_CHECK, "PLUG_CHECK", null);
            }

            // PLC modem used
            activity.getControlBoard().getTxData().setPwmDuty((short) 100);

            packetRequest = new PacketRequest((byte) 0x71, (short) 8, (byte) 0x00);
            short duty = activity.getControlBoard().getRxData().getCsPwmDuty();
            short cpVoltage = activity.getControlBoard().getRxData().getCsCpVoltage();
            report = packetRequest.onMakeRequestData(duty, cpVoltage);
            activity.getPlcModem().onSend(report);

            //ConfigSetupReq  0x70
            ConfigSetupReq configSetupReq = new ConfigSetupReq((byte) 0x70, (short) 36);
            report = configSetupReq.makeConfigSetupReq();
            activity.getPlcModem().onSend(report);


            //ConfigCheck_Request 0x79
            packetRequest = new PacketRequest((byte) 0x79, (short) 8, (byte) 0x00);
            report = packetRequest.onMakeRequestData(duty, cpVoltage);
            activity.getPlcModem().onSend(report);

//                StartRequest startRequest = new StartRequest((byte) 0x72, (short) 16, (byte) 0x00);
            StartRequest startRequest = new StartRequest((byte) 0x72, (short) 16, (byte) 0x02);
            report = startRequest.makeStartRequest((byte) 0x05);         //EV: 5%, test : 50% ==> 0x32
            activity.getPlcModem().onSend(report);
        } catch (Exception e) {
            logger.error(" init onClick error : {}", e.getMessage());
        }

    }


    public Bitmap toGrayscale(Bitmap bmpOriginal) {
        int width, height;
        height = bmpOriginal.getHeight();
        width = bmpOriginal.getWidth();

        Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmpGrayscale);
        Paint paint = new Paint();
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(0);
        ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
        paint.setColorFilter(f);
        c.drawBitmap(bmpOriginal, 0, 0, paint);
        return bmpGrayscale;
    }

    private boolean onUnitPrice() {
        boolean result = false;
        try {
            File file = new File(GlobalVariables.getRootPath() + File.separator + GlobalVariables.UNIT_FILE_NAME);
            result = file.exists() || !Objects.equals(chargerConfiguration.getAuthMode(), "0");
        } catch (Exception e){
            logger.error(e.getMessage());
        }
        return result;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public Map<String, Integer> onFindUnitPrices(Set<String> userTypes) {
        Map<String, Integer> resultMap = new HashMap<>();

        try {
            File file = new File(GlobalVariables.getRootPath() + File.separator + GlobalVariables.UNIT_FILE_NAME);
            if (file.exists()) {
                FileReader fileReader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                String line;

                ZonedDateTimeConvert zonedDateTimeConvert = new ZonedDateTimeConvert();
                ZonedDateTime now = zonedDateTimeConvert.doGetCurrentTime();

                while ((line = bufferedReader.readLine()) != null) {
                    JSONObject unitObject = new JSONObject(line);
                    String userType = unitObject.getString("userType");

                    if (userTypes.contains(userType)) {
                        JSONArray jsonArrayUnit = unitObject.getJSONArray("tariff");

                        for (int k = 0; k < jsonArrayUnit.length(); k++) {
                            JSONObject obj = jsonArrayUnit.getJSONObject(k);
                            ZonedDateTime startAt = ZonedDateTime.parse(obj.getString("startAt"), DateTimeFormatter.ISO_DATE_TIME);
                            ZonedDateTime endAt = ZonedDateTime.parse(obj.getString("endAt"), DateTimeFormatter.ISO_DATE_TIME);

                            if ((now.isEqual(startAt) || now.isAfter(startAt)) && (now.isBefore(endAt) || now.isEqual(endAt))) {
                                resultMap.put(userType, obj.getInt("price"));
                                break; // 그 userType에 대한 단가 정보를 찾으면 다음 라인으로 넘어감
                            }
                        }

                        // 모든 userType을 다 찾으면 종료
                        if (resultMap.keySet().containsAll(userTypes)) {
                            break;
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("onFindUnitPrices error : {}", e.getMessage());
        }

        return resultMap;
    }

    private void onUnitPriceDisplay() {
        unitPriceHandler = new Handler();
        unitPriceHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                activity.runOnUiThread(new Runnable() {
                    @RequiresApi(api = Build.VERSION_CODES.O)
                    @SuppressLint({"DefaultLocale", "SetTextI18n"})
                    @Override
                    public void run() {
                        try {
                            //사용 단가 갖고 오기
                            Set<String> userTypes = new HashSet<>(Arrays.asList("A", "B"));
                            Map<String, Integer> unitPrices = onFindUnitPrices(userTypes);
                            textViewMemberUnitInput.setText(getString(R.string.chargeUnitFormat, String.valueOf(unitPrices.getOrDefault("A", 0))));
                            activity.getClassUiProcess().getChargingCurrentData().setPowerUnitPrice(Double.parseDouble(String.valueOf(unitPrices.getOrDefault("A", 0))));
                        } catch (Exception e) {
                            logger.error("unitPriceHandler error : {}", e.getMessage());
                        }
                    }
                });
                unitPriceHandler.postDelayed(this, 120000);
            }
        }, 3000);
    }
}