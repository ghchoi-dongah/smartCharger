package com.dongah.smartcharger.pages;

import static android.content.Context.INPUT_METHOD_SERVICE;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ChargerConfiguration;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.utils.SharedModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link ConfigSettingFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ConfigSettingFragment extends Fragment implements View.OnClickListener {

    private static final Logger logger = LoggerFactory.getLogger(ConfigSettingFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    ChargerConfiguration chargerConfiguration;
    InputMethodManager imm;
    Spinner spChargerType, spChargerModel;
    int spPosition = 0, spChargerModelCode = 0;
    EditText editChargerId;
    EditText editServerUrl, editServerPort, editHttpURL;
    EditText editControlPort, editRfPort, editPlcPort, editDuty;
    EditText editTestPrice,  editVendorCode, editMid;
    EditText editSerial, editFirmware;
    EditText editImsi, editM2mTel, editSoc;
    EditText editTargetChargingTime;
    Button btnKeyBoard, btnSave, btnExit, btnPowerOff;
    RadioGroup rgPayment, rgMode;
    RadioButton rbServer, rbAutoMode, rbTest, rbPowerMeter;
    RadioButton rbMemberCredit, rbMember, rbCredit;
    TextView txtTestPrice;
    CheckBox chkStopConfirm, chkSigned;


    public ConfigSettingFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment ConfigSettingFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static ConfigSettingFragment newInstance(String param1, String param2) {
        ConfigSettingFragment fragment = new ConfigSettingFragment();
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
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_config_setting, container, false);
        try {
            chargerConfiguration = ((MainActivity) MainActivity.mContext).getChargerConfiguration();
            // keyboard hidden*/
            imm = (InputMethodManager) (MainActivity.mContext).getSystemService(INPUT_METHOD_SERVICE);
            btnKeyBoard = view.findViewById(R.id.btnKeyBoard);
            btnKeyBoard.setOnClickListener(this);
            btnSave = view.findViewById(R.id.btnConfigSave);
            btnSave.setOnClickListener(this);
            btnExit = view.findViewById(R.id.btnConfigExit);
            btnExit.setOnClickListener(this);
            btnPowerOff = view.findViewById(R.id.btnPowerOff);
            btnPowerOff.setOnClickListener(this);

            rbServer = view.findViewById(R.id.rbServerMode);
            rbServer.setOnClickListener(this);
            rbAutoMode = view.findViewById(R.id.rbAutoMode);
            rbAutoMode.setOnClickListener(this);
            rbTest = view.findViewById(R.id.rbTestMode);
            rbTest.setOnClickListener(this);
            rbPowerMeter = view.findViewById(R.id.rbPowerMeterMode);
            rbPowerMeter.setOnClickListener(this);
            rbMemberCredit = view.findViewById(R.id.rbMemberCredit);
            rbMemberCredit.setOnClickListener(this);
            rbMember = view.findViewById(R.id.rbMember);
            rbMember.setOnClickListener(this);
            rbCredit = view.findViewById(R.id.rbCredit);
            rbCredit.setOnClickListener(this);

            spChargerType = view.findViewById(R.id.spinnerChargerType);
            ArrayAdapter<CharSequence> chargerTypeAdapter = ArrayAdapter.createFromResource(MainActivity.mContext, R.array.chargerType, R.layout.spinner_item);
            chargerTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spChargerType.setAdapter(chargerTypeAdapter);
            spChargerType.setSelection(chargerConfiguration.getChargerType() - 1);
            spChargerType.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    spPosition = position + 1;
                    chargerConfiguration.setChargerType(position + 1);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
            //** spChargerModel  ==> 0: DEVS100D12 */
            spChargerModel = view.findViewById(R.id.spinnerChargerModel);
            ArrayAdapter<CharSequence> mcuTypeAdapter = ArrayAdapter.createFromResource(MainActivity.mContext, R.array.chargerModel, R.layout.spinner_item);
            mcuTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spChargerModel.setAdapter(mcuTypeAdapter);
            spChargerModel.setSelection(chargerConfiguration.getChargerPointModelCode());
            spChargerModel.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    chargerConfiguration.setChargerPointModelCode(position);
                    Resources resources = getResources();
                    String[] chargerModel = resources.getStringArray(R.array.chargerModel);
                    chargerConfiguration.setChargerPointModel(chargerModel[position]);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {

                }
            });

            InitializationComponents(view);

            // focus : EditText focus 있어 초기화면에 키보드가 먼저 보임 */
            btnExit.setFocusableInTouchMode(true);
            btnExit.requestFocus();

        } catch (Exception e) {
            logger.error(" ConfigSettingFragment onCreateView {}", e.getMessage());
        }
        return view;
    }

    private void InitializationComponents(View v) {
        try {
            spPosition = chargerConfiguration.getChargerType();
            spChargerModelCode = chargerConfiguration.getChargerPointModelCode();
            editChargerId = v.findViewById(R.id.editChargerId);
            editChargerId.setText(chargerConfiguration.getChargerId());
            editHttpURL = v.findViewById(R.id.editHttpURL);
            editHttpURL.setText(chargerConfiguration.getServerHttpString());
            editServerUrl = v.findViewById(R.id.editServerUrl);
            editServerUrl.setText(chargerConfiguration.getServerConnectingString());
            editServerPort = v.findViewById(R.id.editServerPort);
            editServerPort.setText(String.valueOf(chargerConfiguration.getServerPort()));

            editControlPort = v.findViewById(R.id.editControlPort);
            editControlPort.setText(chargerConfiguration.getControlCom());
            editRfPort = v.findViewById(R.id.editRfPort);
            editRfPort.setText(chargerConfiguration.getRfCom());
            editPlcPort = v.findViewById(R.id.editPlcPort);
            editPlcPort.setText(chargerConfiguration.getPlcCom());
            editDuty = v.findViewById(R.id.editDuty);
            editDuty.setText(String.valueOf(chargerConfiguration.getDuty()));

            txtTestPrice = v.findViewById(R.id.txtTestPrice);
            editTestPrice = v.findViewById(R.id.editTestPrice);
            editTestPrice.setText(chargerConfiguration.getTestPrice());

            editVendorCode = v.findViewById(R.id.editVendorCode);
            editVendorCode.setText(chargerConfiguration.getChargerPointVendor());
            editMid = v.findViewById(R.id.editMid);
            editMid.setText(chargerConfiguration.getMID());
            editSerial = v.findViewById(R.id.editSerial);
            editSerial.setText(chargerConfiguration.getChargerPointSerialNumber());
            editFirmware = v.findViewById(R.id.editFirmware);
            editFirmware.setText(chargerConfiguration.getFirmwareVersion());
            editImsi = v.findViewById(R.id.editImsi);
            editImsi.setText(chargerConfiguration.getImsi());

            editM2mTel = v.findViewById(R.id.editM2mTel);
            editM2mTel.setText(chargerConfiguration.getM2mTel());

            try {
                editSoc = v.findViewById(R.id.editSoc);
                editSoc.setText(chargerConfiguration.getTargetSoc());
            } catch (Exception e) {
                editSoc.setText("80");
                logger.error(e.getMessage());
            }


            editTargetChargingTime = v.findViewById(R.id.editTargetChargingTime);
            editTargetChargingTime.setText(String.valueOf(chargerConfiguration.getTargetChargingTime()));

            rgPayment = v.findViewById(R.id.radioGrpPayment);
            rgMode = v.findViewById(R.id.radioGrpModeSelect);
            try {
                // 0:credit + member   1:member*/
                switch (Integer.parseInt(chargerConfiguration.getSelectPayment())) {
                    case 0:
                        rbMemberCredit.setChecked(true);
                        break;
                    case 1:
                        rbMember.setChecked(true);
                        break;
                    case 2:
                        rbCredit.setChecked(true);
                        break;
                }
                // 0:서버 1:로컬 2:자동 3:테스트 4: 계량기 테스트 */
                switch (Integer.parseInt(chargerConfiguration.getAuthMode())) {
                    case 0:
                        rbServer.setChecked(true);
                        break;
                    case 2:
                        rbAutoMode.setChecked(true);
                        break;
                    case 3:
                        rbTest.setChecked(true);
                        break;
                    case 4:
                        rbPowerMeter.setChecked(true);
                        break;
                }
                //stop confirm
                chkStopConfirm = v.findViewById(R.id.chkStopConfirm);
                chkStopConfirm.setChecked(chargerConfiguration.isStopConfirm());
                chkSigned = v.findViewById(R.id.chkSigned);
                chkSigned.setChecked(chargerConfiguration.isSigned());


            } catch (Exception e) {
                logger.error("InitializationComponents fail : {}", e.getMessage());
            }
        } catch (Exception e) {
            logger.error("InitializationComponents error : {}", e.getMessage());
        }
    }

    @Override
    public void onClick(View v) {
        if (Objects.equals(v.getId(), R.id.btnKeyBoard)) {
            try {
                View view = ((MainActivity) MainActivity.mContext).getCurrentFocus();
                if (view instanceof EditText) {
                    EditText editText = (EditText) ((MainActivity) MainActivity.mContext).getCurrentFocus();
                    imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                }
            } catch (Exception e) {
                logger.error(" config - onClick {}", e.getMessage());
            }
        } else if (Objects.equals(v.getId(), R.id.rbMemberCredit)) {
            chargerConfiguration.setSelectPayment("0");
        } else if (Objects.equals(v.getId(), R.id.rbMember)) {
            chargerConfiguration.setSelectPayment("1");
        } else if (Objects.equals(v.getId(), R.id.rbCredit)) {
            chargerConfiguration.setSelectPayment("2");
        } else if (Objects.equals(v.getId(), R.id.rbTestMode)) {
            chargerConfiguration.setAuthMode("3");
            txtTestPrice.setVisibility(View.VISIBLE);
            editTestPrice.setVisibility(View.VISIBLE);
        } else if (Objects.equals(v.getId(), R.id.rbAutoMode)) {
            chargerConfiguration.setAuthMode("2");
            txtTestPrice.setVisibility(View.VISIBLE);
            editTestPrice.setVisibility(View.VISIBLE);
        } else if (Objects.equals(v.getId(), R.id.rbServerMode)) {
            chargerConfiguration.setAuthMode("0");
            txtTestPrice.setVisibility(View.INVISIBLE);
            editTestPrice.setVisibility(View.INVISIBLE);
        } else if (Objects.equals(v.getId(), R.id.rbPowerMeterMode)) {
            chargerConfiguration.setAuthMode("4");
            txtTestPrice.setVisibility(View.INVISIBLE);
            editTestPrice.setVisibility(View.INVISIBLE);
        } else if (Objects.equals(v.getId(), R.id.btnConfigSave)) {
//            if (TextUtils.isEmpty(editChargerId.getText().toString())) {
//                editChargerId.setFocusableInTouchMode(true);
//                editChargerId.requestFocus();
//                Toast.makeText(((MainActivity) MainActivity.mContext), R.string.chargerIdSaveFail, Toast.LENGTH_LONG).show();
//                return;
//            }


            new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.configSaveTitle)
                    .setMessage(R.string.configSaveYesNo)
                    .setPositiveButton("YES", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            onSaveConfiguration();
                            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    Toast.makeText(getActivity(), R.string.configSaveSuccess, Toast.LENGTH_SHORT).show();
                                }
                            }, 50);
                        }
                    })
                    .setNegativeButton("NO", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            InitializationComponents(getView());
                            Toast.makeText(((MainActivity) getActivity()), R.string.configSaveCancel, Toast.LENGTH_SHORT).show();
                        }
                    }).show();
        } else if (Objects.equals(v.getId(), R.id.btnConfigExit)) {
            try {
                //** fragment change */
                ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.ENVIRONMENT, "ENVIRONMENT", null);
            } catch (Exception e) {
                logger.error("fragment change fail : {}", e.getMessage());
            }
        } else if (Objects.equals(v.getId(), R.id.btnPowerOff)) {
            ((MainActivity) MainActivity.mContext).onRebooting("Hard");
        }
    }


    private void onSaveConfiguration() {
        try {
            onConfigurationUpdate();
            chargerConfiguration.onSaveConfiguration();
            chargerConfiguration.onLoadConfiguration();
        } catch (Exception e) {
            Log.e("ConfigSettingFragment", e.toString());
        }
    }

    private void onConfigurationUpdate() {
        try {
            chargerConfiguration.setChargerType(spPosition);
            chargerConfiguration.setChargerPointModelCode(spChargerModelCode);
            chargerConfiguration.setChargerId(editChargerId.getText().toString());
            chargerConfiguration.setServerHttpString(editHttpURL.getText().toString());
            chargerConfiguration.setServerConnectingString(editServerUrl.getText().toString());
            chargerConfiguration.setServerPort(Integer.parseInt(editServerPort.getText().toString()));

            chargerConfiguration.setControlCom(editControlPort.getText().toString());
            chargerConfiguration.setRfCom(editRfPort.getText().toString());
            chargerConfiguration.setPlcCom(editPlcPort.getText().toString());
            chargerConfiguration.setDuty(Integer.parseInt(editDuty.getText().toString()));
            chargerConfiguration.setChargerPointVendor(editVendorCode.getText().toString());

            chargerConfiguration.setMID(editMid.getText().toString());
            chargerConfiguration.setChargerPointSerialNumber(editSerial.getText().toString());
            chargerConfiguration.setFirmwareVersion(editFirmware.getText().toString());
            chargerConfiguration.setImsi(editImsi.getText().toString());
            chargerConfiguration.setTestPrice(editTestPrice.getText().toString());

            chargerConfiguration.setTargetSoc(Integer.parseInt(editSoc.getText().toString()));

            chargerConfiguration.setM2mTel(editM2mTel.getText().toString());
            chargerConfiguration.setTargetChargingTime(Integer.parseInt(editTargetChargingTime.getText().toString()));
            //0:credit + member   1:member  2:credit
            if (rbMemberCredit.isChecked())
                chargerConfiguration.setSelectPayment("0");
            else if (rbMember.isChecked())
                chargerConfiguration.setSelectPayment("1");
            else if (rbCredit.isChecked())
                chargerConfiguration.setSelectPayment("2");
            //** 0:서버 1:로컬 2:자동 3:테스트 4:PowerMeter 테스트*/
            if (rbTest.isChecked()) {
                chargerConfiguration.setAuthMode("3");
//                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData(0).setTestMode((short) 3);
//                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData(1).setTestMode((short) 3);
            } else {
//                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData(0).setTestMode((short) 0);
//                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData(1).setTestMode((short) 0);
                if (rbServer.isChecked()) chargerConfiguration.setAuthMode("0");
                else if (rbAutoMode.isChecked()) chargerConfiguration.setAuthMode("2");
                else if (rbPowerMeter.isChecked()) chargerConfiguration.setAuthMode("4");
            }
            chargerConfiguration.setStopConfirm(chkStopConfirm.isChecked());
            chargerConfiguration.setSigned(chkSigned.isChecked());

        } catch (Exception e) {
            logger.error("onConfigurationUpdate error : {}", e.getMessage());
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        // header title message change
        SharedModel sharedModel = new ViewModelProvider(requireActivity()).get(SharedModel.class);
        String[] requestStrings = new String[1];
        requestStrings[0] = "0";
        sharedModel.setMutableLiveData(requestStrings);
    }
}