package com.dongah.smartcharger.basefunction;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.controlboard.RxData;
import com.dongah.smartcharger.handler.ProcessHandler;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointErrorCode;
import com.dongah.smartcharger.websocket.ocpp.core.ChargePointStatus;
import com.dongah.smartcharger.websocket.socket.SocketReceiveMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public class NotifyFaultCheck {

    private static final Logger logger = LoggerFactory.getLogger(NotifyFaultCheck.class);

    ProcessHandler processHandler;
    SocketReceiveMessage socketReceiveMessage;
    ChargingCurrentData chargingCurrentData;


    NotifyPropertyChange UiDSP = new NotifyPropertyChange("1021");
    NotifyPropertyChange emergency = new NotifyPropertyChange("1000");
    NotifyPropertyChange csOVR = new NotifyPropertyChange("1005");
    NotifyPropertyChange csOCR = new NotifyPropertyChange("1006");
    NotifyPropertyChange csUnPlug = new NotifyPropertyChange("1009");
    NotifyPropertyChange csUVR = new NotifyPropertyChange("1010");


    public NotifyFaultCheck() {
        processHandler = ((MainActivity) MainActivity.mContext).getProcessHandler();

    }

    public void onErrorMessageMake(RxData rxData) {
        try {
            chargingCurrentData = ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData();
            chargingCurrentData.faultMessage = new StringBuilder();
            onFaultDetect(rxData);
            if (rxData.csFault || !((MainActivity) MainActivity.mContext).getControlBoard().isConnected()) {
                if (!((MainActivity) MainActivity.mContext).getControlBoard().isConnected()) chargingCurrentData.faultMessage.append("UI-Control Board 통신오류\n");
                if (rxData.isCsEmergency()) chargingCurrentData.faultMessage.append("비상 정지\n");
                if (rxData.isCsOVR()) chargingCurrentData.faultMessage.append("OVR 에러\n");
                if (rxData.isCsOCR()) chargingCurrentData.faultMessage.append("OCR 에러\n");
                if (rxData.isCsUVR()) chargingCurrentData.faultMessage.append("UVR 에러\n");
            }

        } catch (Exception e) {
            logger.error("onErrorMessageMake error : {} ", e.getMessage());
        }
    }

    private void onFaultDetect( RxData rxData) {
        try {
            socketReceiveMessage = ((MainActivity) MainActivity.mContext).getSocketReceiveMessage();
            boolean connected = ((MainActivity) MainActivity.mContext).getControlBoard().isConnected();    //false ==> fault 발생
            boolean disconnected = !connected;
            //connect
            if (!Objects.equals(UiDSP.ResultCompare, disconnected)) {
                UiDSP.setResultCompare(disconnected);
                if (disconnected) {
                    //발생
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.EVCommunicationError);
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                            GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                            chargingCurrentData.getConnectorId(),
                            0,
                            null,
                            null,
                            UiDSP.alarmCode,
                            false));
                } else {
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
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
            }

            //plug check
            //unPlug check ( rxData.csPilot == true : plug)
            if (!Objects.equals(csUnPlug.ResultCompare, rxData.csPilot)) {
                csUnPlug.setResultCompare(rxData.csPilot);
                if (!rxData.csPilot) {
                    //unPlug
                    boolean isPlugStatus = Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Finishing) ||
                            Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Preparing);
                    if (isPlugStatus) {
                        chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
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
                } else {
                    if (Objects.equals(chargingCurrentData.getChargePointStatus(), ChargePointStatus.Available)) {
                        chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
                        chargingCurrentData.setChargePointStatus(ChargePointStatus.Preparing);
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
            }

            // csEmergency
            if (!Objects.equals(emergency.ResultCompare, rxData.csEmergency)) {
                emergency.setResultCompare(rxData.csEmergency);
                if (rxData.csEmergency) {
                    //발생
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.OtherError);
                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
                            GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
                            chargingCurrentData.getConnectorId(),
                            0,
                            null,
                            null,
                            emergency.alarmCode,
                            false));
                } else {
                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
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
            }

            //csOVR
//            if (!Objects.equals(csOVR.ResultCompare, rxData.csOVR)) {
//                csOVR.setResultCompare(rxData.csOVR);
//                if (rxData.csOVR) {
//                    //발생
//                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.OverVoltage);
//                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
//                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
//                            GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
//                            chargingCurrentData.getConnectorId(),
//                            0,
//                            null,
//                            null,
//                            csOVR.alarmCode,
//                            false));
//                } else {
//                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
//                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
//                }
//            }

            //csOCR
//            if (!Objects.equals(csOCR.ResultCompare, rxData.csOCR)) {
//                csOCR.setResultCompare(rxData.csOCR);
//                if (rxData.csOCR) {
//                    //발생
//                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.OverCurrentFailure);
//                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
//                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
//                            GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
//                            chargingCurrentData.getConnectorId(),
//                            0,
//                            null,
//                            null,
//                            csOCR.alarmCode,
//                            false));
//                } else {
//                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
//                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
//                }
//            }

            //csUVR
//            if (!Objects.equals(csUVR.ResultCompare, rxData.csUVR)) {
//                csUVR.setResultCompare(rxData.csUVR);
//                if (rxData.csUVR) {
//                    //발생
//                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.OverCurrentFailure);
//                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Faulted);
//                    processHandler.sendMessage(socketReceiveMessage.onMakeHandlerMessage(
//                            GlobalVariables.MESSAGE_HANDLER_STATUS_NOTIFICATION,
//                            chargingCurrentData.getConnectorId(),
//                            0,
//                            null,
//                            null,
//                            csUVR.alarmCode,
//                            false));
//                } else {
//                    chargingCurrentData.setChargePointErrorCode(ChargePointErrorCode.NoError);
//                    chargingCurrentData.setChargePointStatus(ChargePointStatus.Available);
//                }
//            }


        } catch (Exception e) {
            logger.error("onFaultDetect error : {} ", e.getMessage());
        }
    }

}
