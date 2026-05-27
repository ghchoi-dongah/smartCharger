package com.dongah.smartcharger.pages;

import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.UiSeq;
import com.dongah.smartcharger.plc.request.StopAllRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MessageYesNoFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MessageYesNoFragment extends Fragment implements View.OnClickListener {

    private static final Logger logger = LoggerFactory.getLogger(MessageYesNoFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    int cnt = 5;
    View view;
    TextView txtMessage;
    Button btnCancel, btnConfirm;
    ImageView imageViewLoading;
    AnimationDrawable animationDrawable;
    Handler uiCheckHandler;


    public MessageYesNoFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MessageYesNoFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MessageYesNoFragment newInstance(String param1, String param2) {
        MessageYesNoFragment fragment = new MessageYesNoFragment();
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
        view = inflater.inflate(R.layout.fragment_message_yes_no, container, false);
        txtMessage = view.findViewById(R.id.txtMessage);
        btnCancel = view.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(this);
        btnConfirm = view.findViewById(R.id.btnConfirm);
        btnConfirm.setOnClickListener(this);
        imageViewLoading = view.findViewById(R.id.imageViewLoading);
        imageViewLoading.setBackgroundResource(R.drawable.ani_loading);
        animationDrawable = (AnimationDrawable) imageViewLoading.getBackground();
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

//        MediaPlayer mediaPlayer = MediaPlayer.create(MainActivity.mContext, R.raw.messageyesno);
//        mediaPlayer.start();

    }

    @Override
    public void onClick(View v) {
        int getId = v.getId();
        try {
            if (Objects.equals(getId, R.id.btnCancel)) {
                ((MainActivity) MainActivity.mContext).getClassUiProcess().setUiSeq(UiSeq.CHARGING);
                ((MainActivity) MainActivity.mContext).getFragmentChange().onFragmentChange(UiSeq.CHARGING, "CHARGING", null);
            } else if (Objects.equals(getId, R.id.btnConfirm)) {
                ((MainActivity) MainActivity.mContext).getClassUiProcess().getChargingCurrentData().setUserStop(true);
                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData().setMainMC(false);
                ((MainActivity) MainActivity.mContext).getControlBoard().getTxData().setPwmDuty((short) 100);
                txtMessage.setText(R.string.stoppingMessage);
                btnConfirm.setVisibility(View.INVISIBLE);
                btnCancel.setVisibility(View.INVISIBLE);
                imageViewLoading.setVisibility(View.VISIBLE);
                animationDrawable.start();

                StopAllRequest stopAllRequest = new StopAllRequest((byte) 0x76, (short) 8, (byte) 0x00);

                byte[] report = stopAllRequest.makeStopAllRequest("STOP", (short) 534, (short) 123);
                ((MainActivity) MainActivity.mContext).getPlcModem().onSend(report);

                uiCheckHandler = new Handler();
                uiCheckHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (cnt == 0) {
                            ((MainActivity) MainActivity.mContext).getClassUiProcess().setUiSeq(UiSeq.FINISH_WAIT);
                        } else{
                            cnt--;
                            uiCheckHandler.postDelayed(this, 1000);
                        }
                    }
                }, 1000);
            }

        } catch (Exception e) {
            logger.error("onClick error : {} ", e.getMessage(),  e);
        }
    }

    @Override
    public void onDestroyView() {
        try {
            if (animationDrawable != null) {
                animationDrawable.stop();
            }

            if (imageViewLoading != null) {
                Drawable bg = imageViewLoading.getBackground();
                if (bg instanceof AnimationDrawable) {
                    ((AnimationDrawable) bg).stop();
                }
                imageViewLoading.setBackground(null);
            }
        } catch (Exception e) {
            logger.error("onDestroyView error : {}", e.getMessage(), e);
        }
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
    }
}