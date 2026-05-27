package com.dongah.smartcharger.pages;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.ChargingCurrentData;
import com.dongah.smartcharger.basefunction.ClassUiProcess;
import com.dongah.smartcharger.basefunction.GlobalVariables;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.handler.ProcessHandler;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.SocketState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ChargingFinishFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ChargingFinishFragment extends Fragment implements View.OnClickListener {

    private static final Logger logger = LoggerFactory.getLogger(ChargingFinishFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;


    Button btnStopConfirm;
    TextView  txtSoc, txtAmountOfCharge, txtChargePay, txtChargeTime, txtPowerUnitPrice, textViewTargetSoc;
    double powerUnitPrice = 0f;

    MainActivity activity;
    ClassUiProcess classUiProcess;
    ChargingCurrentData chargingCurrentData;
    ChargerConfiguration chargerConfiguration;

    DecimalFormat payFormatter = new DecimalFormat("#,###,##0");
    DecimalFormat unitPriceFormatter = new DecimalFormat("#,###,##0.0");
    DecimalFormat powerFormatter = new DecimalFormat("#,###,##0.00");
    Handler uiCheckHandler, paymentHandler;


    public ChargingFinishFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ChargingFinishFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ChargingFinishFragment newInstance(String param1, String param2) {
        ChargingFinishFragment fragment = new ChargingFinishFragment();
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
        View view = inflater.inflate(R.layout.fragment_charging_finish, container, false);
        txtSoc = view.findViewById(R.id.txtSoc);
        txtAmountOfCharge = view.findViewById(R.id.txtAmountOfCharge);
        txtChargePay = view.findViewById(R.id.txtChargePay);
        txtChargeTime = view.findViewById(R.id.txtChargeTime);
        btnStopConfirm = view.findViewById(R.id.btnStopConfirm);
        btnStopConfirm.setOnClickListener(this);
        txtPowerUnitPrice = view.findViewById(R.id.txtPowerUnitPrice);
        textViewTargetSoc = view.findViewById(R.id.textViewTargetSoc);

        activity = (MainActivity) MainActivity.mContext;
        chargerConfiguration = activity.getChargerConfiguration();
        classUiProcess = activity.getClassUiProcess();
        chargingCurrentData = classUiProcess.getChargingCurrentData();
        classUiProcess.onStopData();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
//            MediaPlayer mediaPlayer = MediaPlayer.create(MainActivity.mContext, R.raw.chargingfinsih);
//            mediaPlayer.start();
            //unplug check 후 초기 화면
            uiCheckHandler = new Handler();
            uiCheckHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (!((MainActivity) MainActivity.mContext).getControlBoard().getRxData().isCsPilot()) {
                        btnStopConfirm.performClick();
                    }
                    uiCheckHandler.postDelayed(this, 60000);
                }
            }, 60000);

            //charging finish info
            ((MainActivity) MainActivity.mContext).runOnUiThread(new Runnable() {
                @SuppressLint("SetTextI18n")
                @Override
                public void run() {
                    try {
                        if (chargingCurrentData.getSoc() == 0) {
                            txtSoc.setVisibility(View.INVISIBLE);
                        } else {
                            txtSoc.setVisibility(View.INVISIBLE);
                            txtSoc.setText(chargingCurrentData.getSoc() + "%");
                        }
//                    txtSoc.setText(chargingCurrentData.getSoc() == 0 ? "미지원" : chargingCurrentData.getSoc() + "%");
                        txtAmountOfCharge.setText(powerFormatter.format(chargingCurrentData.getPowerMeterUse() * 0.001) + " kWh");
                        txtChargePay.setText(payFormatter.format(chargingCurrentData.getPowerMeterUsePay()) + " 원") ;
                        txtChargeTime.setText(chargingCurrentData.getChargingUseTime());

                        textViewTargetSoc.setText("목표 충전율: " + chargerConfiguration.getTargetSoc() + "%");
                        powerUnitPrice = classUiProcess.getChargingCurrentData().getPowerUnitPrice();
                        txtPowerUnitPrice.setText(powerUnitPrice + " 원");
                    } catch (Exception e) {
                        logger.error("onViewCreated charging result error: {}", e.getMessage(), e);
                    }

                    try {
                        //result price
                        SocketReceiveMessage socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                        ProcessHandler processHandler = ((MainActivity) MainActivity.mContext).getProcessHandler();
                        SocketState state = socketReceiveMessage.getSocket().getState();
                        //// dataTransfer - Result price
//                        if (Objects.equals(state.getValue(), 7) && Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
//                            processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
//                                    GlobalVariables.MESSAGE_HANDLER_RESULT_PRICE,
//                                    chargingCurrentData.getConnectorId(),
//                                    0,
//                                    chargingCurrentData.getIdTag(),
//                                    null,
//                                    null,
//                                    false));
//                        }
                        // unPlug 종료시 statusNotification available change
                        RxData rxData = ((MainActivity) MainActivity.mContext).getControlBoard().getRxData();
                        if (!rxData.isCsPilot()) {
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                            if (Objects.equals(state.getValue(), 7) && Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                                processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                        GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                                        chargingCurrentData.getConnectorId(),
                                        0,
                                        null,
                                        null,
                                        null,
                                        false));
                            }
                        }
                    } catch (Exception e) {
                        logger.error("result price send fail : {}", e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            logger.error("onViewCreated error : {}", e.getMessage(), e);
        }
    }

    @Override
    public void onClick(View v) {
        int getId = v.getId();
        if (Objects.deepEquals(getId, R.id.btnStopConfirm)) {
            ((MainActivity) MainActivity.mContext).getClassUiProcess().onHome();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        uiCheckHandler.removeCallbacksAndMessages(null);
        uiCheckHandler.removeMessages(0);
    }
}