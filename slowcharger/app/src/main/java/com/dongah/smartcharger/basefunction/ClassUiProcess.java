package com.dongah.smartcharger.basefunction;

import android.annotation.SuppressLint;
import android.os.Build;
import android.widget.Toast;

import androidx.annotation.RequiresApi;
import androidx.fragment.app.Fragment;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.controlboard.ControlBoard;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.controlboard.TxData;
import com.dongah.smartcharger.handler.BatteryInfoThread;
import com.dongah.smartcharger.handler.MeterValueThread;
import com.dongah.smartcharger.handler.MeterValuesAlignedDataThread;
import com.dongah.smartcharger.handler.ProcessHandler;
import com.dongah.smartcharger.pages.FaultFragment;
import com.dongah.smartcharger.plc.request.StopAllRequest;
import com.dongah.smartcharger.rfcard.RfCardReaderListener;
import com.dongah.smartcharger.rfcard.RfCardReaderReceive;
import com.dongah.smartcharger.utils.FileManagement;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointErrorCode;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.ocpp.core.Reason;
import com.dongah.smartcharger.websocket.ocpp.core.ResetType;
import com.dongah.smartcharger.websocket.ocpp.utilities.ZonedDateTimeConvert;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;
import com.dongah.smartcharger.websocket.socket.SocketState;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

public class ClassUiProcess implements RfCardReaderListener {

    private static final Logger logger = LoggerFactory.getLogger(ClassUiProcess.class);

    @SuppressLint("SimpleDateFormat")
    final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

    UiSeq uiSeq,oSeq;;
    ZonedDateTimeConvert zonedDateTimeConvert;

    ChargerConfiguration chargerConfiguration;
    ChargingCurrentData chargingCurrentData;
    FragmentChange fragmentChange;
    ControlBoard controlBoard;
    NotifyFaultCheck notifyFaultCheck;
    SocketReceiveMessage socketReceiveMessage;
    ProcessHandler processHandler;
    Timer eventTimer;

    RfCardReaderReceive rfCardReaderReceive;

    double powerUnitPrice = 0f;
    int powerMeterCheck = 0;

    /**
     * MeterValue Thread
     */
    MeterValueThread meterValueThread;
    BatteryInfoThread batteryInfoThread;
    MeterValuesAlignedDataThread meterValuesAlignedDataThread;

    public UiSeq getUiSeq() {
        return uiSeq;
    }

    public void setUiSeq(UiSeq uiSeq) {
        this.uiSeq = uiSeq;
    }

    public UiSeq getoSeq() {
        return oSeq;
    }

    public void setoSeq(UiSeq oSeq) {
        this.oSeq = oSeq;
    }

    public int getPowerMeterCheck() {
        return powerMeterCheck;
    }

    public void setPowerMeterCheck(int powerMeterCheck) {
        this.powerMeterCheck = powerMeterCheck;
    }

    public ChargingCurrentData getChargingCurrentData() {
        return chargingCurrentData;
    }

    public ClassUiProcess() {
        try {
            setUiSeq(UiSeq.INIT);
            zonedDateTimeConvert = new ZonedDateTimeConvert();
            //rf card
            rfCardReaderReceive = ((MainActivity) MainActivity.mContext).getRfCardReaderReceive();
            rfCardReaderReceive.setRfCardReaderListener(this);

            // configuration
             chargerConfiguration = ((MainActivity) MainActivity.mContext).getChargerConfiguration();
            //charging current data
            chargingCurrentData = new ChargingCurrentData();
            chargingCurrentData.onCurrentDataClear();
            //fragment change
            fragmentChange = ((MainActivity) MainActivity.mContext).getFragmentChange();
            //control board
            controlBoard = ((MainActivity) MainActivity.mContext).getControlBoard();
            // alarm check
            notifyFaultCheck = new NotifyFaultCheck();
            //process handler
            processHandler = ((MainActivity) MainActivity.mContext).getProcessHandler();
            //loop
            eventTimer = new Timer();
            eventTimer.schedule(new TimerTask() {
                @RequiresApi(api = Build.VERSION_CODES.O)
                @Override
                public void run() {
                    onEventAction();
                }
            }, 3000, 1000);

        } catch (Exception e) {
            logger.error( "ClassUiProcess create error : {}", e.getMessage());
        }
    }


    /**
     * charging sequence loop
     * server data send : 서버와 연결이 안된 경우 ProcessHandler dump data save
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void onEventAction() {
        try {
            RxData rxData = controlBoard.getRxData();
            TxData txData = controlBoard.getTxData();
            chargingCurrentData.setIntegratedPower(rxData.getActiveEnergy());
            if (getUiSeq().getValue() < 17) onFaultCheck(rxData);

            //sequence check
            switch (getUiSeq()) {
                case NONE:
                case INIT:
                    setoSeq(UiSeq.INIT);
                    setPowerMeterCheck(0);
                    txData.setPwmDuty((short) 100);
                    txData.setUiSequence((short) 1);
                    onMeterValueStop();
                    if (chargingCurrentData.isReBoot() && onRebootCheck()) {
                        setUiSeq(UiSeq.REBOOTING);
                    }
                    if (chargingCurrentData.getChargePointStatus() == ChargePointStatus.Reserved) {
                        String currentTime = zonedDateTimeConvert.doGetUtcDatetimeAsStringSimple();
                        if (currentTime.compareTo(getChargingCurrentData().getResExpiryDate()) > 0) {
                            // available
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                            chargingCurrentData.setResConnectorId(0);
                            chargingCurrentData.setResIdTag("");
                            chargingCurrentData.setResExpiryDate("");
                            chargingCurrentData.setResReservationId("");
                            chargingCurrentData.setResParentIdTag("");
                            if (Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                                socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                                processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                        GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                                        chargingCurrentData.getResConnectorId(),
                                        0,
                                        null,
                                        null,
                                        null,
                                        false));
                            }
                        }
                    }
                    break;
                case REBOOTING:
                    if (!(getCurrentFragment() instanceof FaultFragment)) {
                        fragmentChange.onFragmentChange(
                                UiSeq.REBOOTING,
                                "REBOOTING",
                                chargingCurrentData.getStopReason() == Reason.HardReset ? "Hard" : "Soft");
                    }
                    break;
                case MEMBER_CARD:
                case MEMBER_CARD_WAIT:
                case CREDIT_CARD:
                case CREDIT_CARD_WAIT:
                    break;
                case PLUG_CHECK:
                    if (rxData.isCsPilot()) {
                        //plug
                        txData.setPwmDuty((short) chargerConfiguration.getDuty());
                        setUiSeq(UiSeq.CONNECT_CHECK);
                    }
                    break;
                case CONNECT_CHECK:
                    // MC ON
                    if (Objects.equals(rxData.getCsCPStatus(), (short) 1)) {
                        txData.setPwmDuty((short) chargerConfiguration.getDuty());
                    }
                    if (Objects.equals(rxData.getCsCPStatus(), (short) 2)) {
                        txData.setMainMC(true);
                        chargingCurrentData.setChargePointStatus(ChargePointStatus.Charging);
                        powerUnitPrice = Objects.equals(chargerConfiguration.getAuthMode(), "0") ?
                                chargingCurrentData.getPowerUnitPrice() : Double.parseDouble(chargerConfiguration.getTestPrice());
                        chargingCurrentData.setPowerMeterStart(rxData.getActiveEnergy());                       // 전력량 와트(w) 기준
                        chargingCurrentData.setPowerMeterCalculate(chargingCurrentData.getPowerMeterStart());   // 전력량 와트(w) 기준
                        chargingCurrentData.setChargingStartTime(zonedDateTimeConvert.getStringCurrentTimeZone());
                        //Auto 및 Test
                        if (Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                            //socket receive message get instance
                            socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                            //meter values start
                            if (GlobalVariables.getMeterValueSampleInterval() > 0) {
                                onMeterValueStart(chargingCurrentData.getConnectorId(), GlobalVariables.getMeterValueSampleInterval());
                            }
                            //start transaction send to server
                            processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                    GlobalVariables.MESSAGE_HANDLER_START_TRANSACTION,
                                    getChargingCurrentData().getConnectorId(),
                                    0,
                                    chargingCurrentData.getIdTag(),
                                    null,
                                    null,
                                    false));

                            //status notification send to server
                            processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                    GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                                    getChargingCurrentData().getConnectorId(),
                                    0,
                                    null,
                                    null,
                                    null,
                                    false));
                        } else {
                            txData.setMainMC(true);
                            setUiSeq(UiSeq.CHARGING);
                            ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.CHARGING, "CHARGING", null);

//                            if (!chargerConfiguration.usedPLC) {
//                                if (Objects.equals(rxData.csCPStatus, (short) 2)) {
//                                    txData.setMainMC(true);
//                                    setUiSeq(UiSeq.CHARGING);
//                                    ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.CHARGING, "CHARGING", null);
//                                }
//                            }
                        }

                    }
                    break;

                case CHARGING:
                    try {
                        //충전 사용량 계산
                        onUsePowerMeter(rxData);
                        txData.setUiSequence((short) 2);
                        boolean isStopped = rxData.isCsStop();
                        boolean isPilotDisconnected = rxData.getCsCPStatus() == 1 || rxData.getCsCPStatus() == 0;
                        boolean isSocReached = (chargingCurrentData.getSoc() != 0
                                && chargingCurrentData.getSoc() >= chargerConfiguration.getTargetSoc());
                        boolean isPrePaymentEnabled = chargingCurrentData.isPrePaymentResult();
                        boolean isPaymentDepleted = chargingCurrentData.getPrePayment() <= chargingCurrentData.getPowerMeterUsePay();

                        //stop 조건
                        if (!GlobalVariables.isStopTransactionOnEVSideDisconnect() &&
                                !GlobalVariables.isUnlockConnectorOnEVSideDisconnect()) {
                            if (isStopped || isPilotDisconnected || isSocReached) {
                                if (chargingCurrentData.getStopReason() == Reason.Remote || chargingCurrentData.isUserStop()) {
                                    onStop();
                                    if (!rxData.isCsPilot()) {
                                        //status notification send to server : ChargePointStatus.SuspendedEV
                                        //2.4.5. EV Side Disconnected
                                        if (Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                                            chargingCurrentData.setStopReason(Reason.EVDisconnected);
                                        }
                                    }
                                    setUiSeq(UiSeq.FINISH_WAIT);
                                }
                            }
                        } else {
                            if (isStopped || isPilotDisconnected || isSocReached) {
                                onStop();
                                //status notification send to server : ChargePointStatus.SuspendedEV
                                //2.4.5. EV Side Disconnected
                                if (Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                                    chargingCurrentData.setStopReason(Reason.EVDisconnected);
                                }
                                setUiSeq(UiSeq.FINISH_WAIT);
                            } else if (isPrePaymentEnabled && isPaymentDepleted) {
                                onStop();
                                chargingCurrentData.setPowerMeterUsePay(chargingCurrentData.getPrePayment());
                                chargingCurrentData.setStopReason(Reason.Other);
                                setUiSeq(UiSeq.FINISH_WAIT);
                            }
                        }
                    } catch (Exception e) {
                        logger.error("classUiProcess - charging error : {}", e.getMessage());
                    }
                    break;
                case FINISH_WAIT:
                    try {
                        txData.setUiSequence((short) 3);
                        onMeterValueStop();
                        onBatteryInfoStop();
                        //사용자 user stop
                        chargingCurrentData.setStopReason(chargingCurrentData.isUserStop() ? Reason.Local : chargingCurrentData.getStopReason());
                        // 충전 사용량 정리
                        chargingCurrentData.setPowerMeterStop(rxData.getActiveEnergy());
                        chargingCurrentData.setChargingEndTime(zonedDateTimeConvert.getStringCurrentTimeZone());
                        //stop transaction send to server
                        chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);
                        //socket receive message get instance
                        socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                        if (Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                            processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                    GlobalVariables.MESSAGE_HANDLER_STOP_TRANSACTION,
                                    chargingCurrentData.getConnectorId(),
                                    0,
                                    chargingCurrentData.getIdTag(),
                                    null,
                                    null,
                                    false));
                            //status notification send to server
                            processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                    GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                                    chargingCurrentData.getConnectorId(),
                                    0,
                                    null,
                                    null,
                                    null,
                                    false));
                            //unit price
                            GlobalVariables.setCustomUnitPriceReq(true);
                            GlobalVariables.setHumaxUserType("A");
                            processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                    GlobalVariables.MESSAGE_CUSTOM_UNIT_PRICE,
                                    1,
                                    0,
                                    null,
                                    null,
                                    GlobalVariables.getHumaxUserType(),       /////////Customer unit price 에서만 userType(A:회원, B:비회원) 사용 한다.
                                    false));
                        }
                        setUiSeq(UiSeq.FINISH);
                        fragmentChange.onFragmentChange(UiSeq.FINISH, "FINISH", null);
                    } catch (Exception e) {
                        logger.error("classUiProcess - FINISH_WAIT error : {} ", e.getMessage());
                    }
                    break;
                case FINISH:
                    onFinish();
                    // 만약에 chargingCurrentData.setStopReason(Reason.EVDisconnected) && ChargePointStatus.Finishing
                    // 이면 ChargePointStatus.Available 인식을 못한 경우
                    if (!rxData.isCsPilot() && Objects.equals(ChargePointStatus.Finishing, chargingCurrentData.getChargePointStatus())) {
                        chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                        processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                                chargingCurrentData.getConnectorId(),
                                0,
                                null,
                                null,
                                null,
                                false));
                    }
                    break;
                case FAULT:
                    //* fault check */
                    if (getUiSeq().getValue() < 15) {
                        if (!(getCurrentFragment() instanceof FaultFragment)) {
                            // server mode 및 charging
                            if (Objects.equals(chargerConfiguration.getAuthMode(), "0") &&
                                    Objects.equals(getoSeq(), UiSeq.CHARGING)) {
                                // meter values stop
                                onMeterValueStop();
                                txData.setMainMC(false);
                                txData.setPwmDuty((short) 100);
                                //PLC USed
//                                if (chargerConfiguration.isUsedPLC()) {
                                    onBatteryInfoStop();
//                                }
                                chargingCurrentData.setUserStop(false);
                                chargingCurrentData.setStopReason(rxData.isCsEmergency()? Reason.EmergencyStop : Reason.Other);
                                chargingCurrentData.setPowerMeterStop(rxData.getActiveEnergy());
                                chargingCurrentData.setChargingEndTime(zonedDateTimeConvert.getStringCurrentTimeZone());
                                chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);
                                //socket receive message get instance
                                socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                                SocketState state = socketReceiveMessage.getSocket().getState();
                                if (Objects.equals(state.getValue(), 7) && Objects.equals(chargerConfiguration.getAuthMode(), "0")) {
                                    //server send
                                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                            GlobalVariables.MESSAGE_HANDLER_STOP_TRANSACTION,
                                            chargingCurrentData.getConnectorId(),
                                            0,
                                            chargingCurrentData.getIdTag(),
                                            null,
                                            null,
                                            false));
                                    //status notification send to server
                                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                            GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                                            chargingCurrentData.getConnectorId(),
                                            0,
                                            null,
                                            null,
                                            null,
                                            false));
                                    //unit price
                                    GlobalVariables.setCustomUnitPriceReq(true);
                                    GlobalVariables.setHumaxUserType("A");
                                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                                            GlobalVariables.MESSAGE_CUSTOM_UNIT_PRICE,
                                            1,
                                            0,
                                            null,
                                            null,
                                            GlobalVariables.getHumaxUserType(),       /////////Customer unit price 에서만 userType(A:회원, B:비회원) 사용 한다.
                                            false));
                                }
                            }
                            fragmentChange.onFragmentChange(UiSeq.FAULT, "FAULT", "1");
                        }
                    }
                    //fault 가 해제가 되면..........
                    if (controlBoard.isConnected() && !rxData.isCsFault()) {
                        if (Objects.equals(getoSeq(), UiSeq.CHARGING)) {
                            txData.setUiSequence((short) 3);
                            chargingCurrentData.setChargePointStatus(ChargePointStatus.Finishing);
                            chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
                            setUiSeq(UiSeq.FINISH);
                            fragmentChange.onFragmentChange(UiSeq.FINISH, "FINISH", null);
                        } else {
                            if (Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Preparing) &&
                                    !rxData.isCsPilot()) {
                                chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
                                //socket receive message get instance
                                socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
                                SocketState state = socketReceiveMessage.getSocket().getState();
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
                            onHome();
                        }
                    }
                    break;
            }
        } catch (Exception e) {
            logger.error(" onEventAction error : {}", e.getMessage());
        }
    }


    public void onHome() {
        setUiSeq(UiSeq.INIT);
        fragmentChange.onFragmentChange(UiSeq.INIT,"INIT",null);
        rfCardReaderReceive.rfCardReadRelease();
    }

    private void onFinish() {
        //충전 완료
        if (chargingCurrentData.isReBoot()) {
            setUiSeq(UiSeq.INIT);
        }
    }


    public void onStopData() {
        try {
            onMeterValueStop();
            //PLC USed
//            if (chargerConfiguration.isUsedPLC()) {
                onBatteryInfoStop();
//            }
        } catch (Exception e) {
            logger.error(" onStop Data {}", e.getMessage());
        }
    }


    /**
     * 충전 사용량 계산
     *
     * @param rxData power meter raw data pick
     */
    private void onUsePowerMeter(RxData rxData) {
        try {
            long gapPower;
            double gapPay;
            if (rxData.getActiveEnergy() > 0) {
                //current power meter --> chargingCurrentData .powerKwh
                //전력량 변화 여부 체크
                chargingCurrentData = ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData();
                gapPower = rxData.getActiveEnergy() - chargingCurrentData.getPowerMeterCalculate();
                gapPower = (gapPower <= 0) ? 0 : (gapPower > 300) ? 100 : gapPower;
                //전력량 변화 여부 체크 892 = 8.92kW
                powerMeterCheck = gapPower == 0 ? powerMeterCheck + 1 : 0;
                chargingCurrentData.setPowerMeterUse(chargingCurrentData.getPowerMeterUse() + gapPower);
                gapPay = gapPower * 0.001 * powerUnitPrice;

                chargingCurrentData.setPowerMeterUsePay(chargingCurrentData.getPowerMeterUsePay() + gapPay);
                chargingCurrentData.setPowerMeterCalculate(rxData.getActiveEnergy());
            }
            chargingCurrentData.setOutPutCurrent(rxData.getCurrent());  //출력전류
            chargingCurrentData.setOutPutVoltage(rxData.getVoltage());  //출력전압
            chargingCurrentData.setPowerMeter(rxData.getActiveEnergy());  //전력량
            chargingCurrentData.setFrequency(rxData.getFrequency() * 0.01);    //주파수
//            chargingCurrentData.setChargingRemainTime(rxData.getRemainTime());  //충전 남은 시간
//            chargingCurrentData.setSoc(rxData.getSoc());
        } catch (Exception e) {
            logger.error("power meter calculate error : {}", e.getMessage());
        }
    }

    /**
     * 현재 Fragment 찾기
     *
     * @return fragment;
     */
    private Fragment getCurrentFragment() {
        return ((MainActivity) MainActivity.mContext).getSupportFragmentManager().findFragmentById(R.id.frameBody);
    }


    /**
     * Remote Transaction stop
     */
    public void onRemoteTransactionStop(Reason reason) {
        try {
            UiSeq uiSeq = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            if (Objects.equals(uiSeq, UiSeq.CHARGING)) {
                controlBoard = ((MainActivity) MainActivity.mContext).getControlBoard();
                chargingCurrentData = ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData();

                controlBoard.getTxData().setMainMC(false);
                controlBoard.getTxData().setPwmDuty((short) 100);

                chargingCurrentData.setUserStop(false);
                chargingCurrentData.setStopReason(reason);
            }
        } catch (Exception e) {
            logger.error("remote stop error : {} ", e.getMessage());
        }
    }

    public void onResetStop(ResetType resetType) {
        try {
            UiSeq uiSeq = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            if (Objects.equals(uiSeq, UiSeq.CHARGING)) {
                controlBoard.getTxData().setMainMC(false);
                controlBoard.getTxData().setPwmDuty((short) 100);
                chargingCurrentData.setUserStop(false);
                chargingCurrentData.setStopReason(resetType == ResetType.Hard ? Reason.HardReset : Reason.SoftReset);
                setUiSeq(UiSeq.FINISH_WAIT);
            }
        } catch (Exception e) {
            logger.error("reset stop error : {} ", e.getMessage());
        }
    }

    private boolean onRebootCheck() {
        boolean result = false;
        try {
            UiSeq uiSeq = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            result = Objects.equals(UiSeq.REBOOTING, uiSeq) || Objects.equals(UiSeq.INIT, uiSeq);
        } catch (Exception e) {
            logger.error(e.getMessage());
        }
        return result;
    }

    private void onStop() {
        try {
            UiSeq uiSeq = ((MainActivity) MainActivity.mContext).getClassUiProcess().getUiSeq();
            if (Objects.equals(uiSeq, UiSeq.CHARGING)) {
                controlBoard = ((MainActivity) MainActivity.mContext).getControlBoard();
                //충전기 정지
                controlBoard.getTxData().setMainMC(false);
                controlBoard.getTxData().setPwmDuty((short) 100);
                chargerConfiguration =((MainActivity) MainActivity.mContext).getChargerConfiguration();
//                if (chargerConfiguration.isUsedPLC()) {
                    //PLC Modem send (0x76 StopAll)
                    short cpVoltage = controlBoard.getRxData().getCsCpVoltage();
                    StopAllRequest stopAllRequest = new StopAllRequest((byte) 0x76, (short) 8, (byte) 0x00);
                    byte[] report = stopAllRequest.makeStopAllRequest("STOP", (short) 100, cpVoltage);
                    ((MainActivity) MainActivity.mContext).getPlcModem().onSend(report);
//                }
            }
        } catch (Exception e) {
            logger.error(" onStop error : {}", e.getMessage());
        }
    }


    /**
     * Meter value
     *
     * @param connectorId connector id
     * @param delay       delay time
     */
    public void onMeterValueStart(int connectorId, int delay) {
        onMeterValueStop();
        meterValueThread = new MeterValueThread(connectorId, delay);
        meterValueThread.setStopped(false);
        meterValueThread.start();
    }

    public void onMeterValueStop() {
        if (meterValueThread != null) {
            meterValueThread.setStopped(true);
            meterValueThread.interrupt();
            meterValueThread = null;
        }
    }

    public void onMeterValuesAlignedDataStart(int connectorId, int delay) {
        onMeterValuesAlignedDataStop();
        meterValuesAlignedDataThread = new MeterValuesAlignedDataThread(connectorId, delay);
        meterValuesAlignedDataThread.setStopped(false);
        meterValuesAlignedDataThread.start();
    }

    //meterValuesAlignedData
    public void onMeterValuesAlignedDataStop() {
        if (meterValuesAlignedDataThread != null) {
            meterValuesAlignedDataThread.setStopped(true);
            meterValuesAlignedDataThread.interrupt();
            meterValuesAlignedDataThread = null;
        }
    }


    /**
     * Battery info start
     * @param delay duration time
     */
    public void onBatteryInfoStart(int delay) {
        onBatteryInfoStop();
        batteryInfoThread = new BatteryInfoThread(delay);
        batteryInfoThread.setStopped(false);
        batteryInfoThread.start();
    }

    public void onBatteryInfoStop() {
        if (batteryInfoThread != null) {
            batteryInfoThread.interrupt();
            batteryInfoThread.setStopped(true);
            batteryInfoThread = null;
        }
    }

    // charger point error check
    private void onFaultCheck(RxData rxData) {
        try {
            //충전중 일 때 fault 가 발생한 경우
            if (!controlBoard.isConnected() || rxData.csFault) {
                if (Objects.equals(getUiSeq(), UiSeq.CHARGING)) {
                    controlBoard.getTxData().setMainMC(false);
                    controlBoard.getTxData().setPwmDuty((short) 100);

                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.OtherError);
                    //비회원 충전 요금 단가 조정을 한다.
                    if (Objects.equals(chargingCurrentData.getPaymentType().value(), 2) &&
                            chargingCurrentData.getPrePayment() <= chargingCurrentData.getPowerMeterUsePay()) {
                        chargingCurrentData.setPowerMeterUsePay(chargingCurrentData.getPrePayment());
                    }
                }
                // fault 발생하기 전에 충전 스퀀스 저장
                if (getUiSeq() != UiSeq.FAULT) setoSeq(getUiSeq());
                setUiSeq(UiSeq.FAULT);
            }
            notifyFaultCheck.onErrorMessageMake(rxData);
        } catch (Exception e) {
            logger.error("onFaultCheck error.... : {}", e.toString());
        }
    }

    /**
     * Rf CARD reader
     * @param cardNum card number
     * @param value boolean
     */
    @Override
    public void onRfCardDataReceive(String cardNum, boolean value) {
        try {
            if (cardNum.isEmpty() || Objects.equals(cardNum,"0000000000000000")) {
                setUiSeq(UiSeq.INIT);
                fragmentChange.onFragmentChange(UiSeq.INIT,null,null);
                Toast.makeText(((MainActivity) MainActivity.mContext), "카드 리더기에서 응답이 없습니다.",Toast.LENGTH_SHORT).show();
            } else {
                onRfCardDataReceiveEvent(cardNum, true);
            }
        } catch (Exception e) {
            logger.error("onRfCardDataReceive error : {} ", e.getMessage());
        }
    }

    private void onRfCardDataReceiveEvent(String cardNum, boolean b) {
        if (b) {
            try {
                if (Objects.equals(cardNum,"0000000000000000")) {
                    rfCardReaderReceive.rfCardReadRequest();
                } else if (!cardNum.isEmpty()) {

                    String authMode = ((MainActivity) MainActivity.mContext).getChargerConfiguration().getAuthMode();
                    if (Objects.equals(authMode, "4") && !Objects.equals(getUiSeq(), UiSeq.MEMBER_CARD)) {
                        // member save
                        ((MainActivity) MainActivity.mContext).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                FileManagement fileManagement = new FileManagement();
                                boolean chk = fileManagement.stringToFileSave(GlobalVariables.ROOT_PATH, "memberList.dongah", cardNum, true);
                                Toast.makeText((MainActivity.mContext), chk ? "저장 성공" : " 저장 실패 ",Toast.LENGTH_SHORT).show();
                            }
                        });
                    } else {
                        //서버에 회원카드 정보를 보내여 인증을 취득하면 전역변수에 저장한다.
                        chargingCurrentData.setIdTag(cardNum);
                        setUiSeq(UiSeq.MEMBER_CARD_WAIT);
                        fragmentChange.onFragmentChange(UiSeq.MEMBER_CARD_WAIT,null,null);
                    }
                    rfCardReaderReceive.rfCardReadRelease();
                }
            } catch (Exception e) {
                logger.error("onRfCardDataReceiveEvent error : {} ", e.getMessage());
            }
        }
    }

}
