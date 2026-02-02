package com.dongah.smartcharger.pages;

import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.dongah.smartcharger.MainActivity;
import com.dongah.smartcharger.R;
import com.dongah.smartcharger.basefunction.ClassUiProcess;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.DecimalFormat;
import java.util.Objects;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link CreditCardWaitFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class CreditCardWaitFragment extends Fragment {

    private static final Logger logger = LoggerFactory.getLogger(CreditCardWaitFragment.class);

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    LinearLayout loadingContainer;
    final String[] colors = { "#b4c7e7", "#8fa3c6", "#6a8ea6", "#455c85", "#203864" };
    final int[] dotIds = { R.id.dot1, R.id.dot2, R.id.dot3, R.id.dot4, R.id.dot5 };
    Handler handler;
    int currentStep = 0;

    int cnt = 0;
    ImageView creditInsert;
    AnimationDrawable animationDrawable;
    TextView txtInputAmt;
    DecimalFormat payFormatter = new DecimalFormat("#,###,##0");
    ClassUiProcess classUiProcess;
    Handler countHandler, paymentHandler;
    Runnable countRunnable;

    public CreditCardWaitFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment CreditCardWaitFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static CreditCardWaitFragment newInstance(String param1, String param2) {
        CreditCardWaitFragment fragment = new CreditCardWaitFragment();
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
        View view = inflater.inflate(R.layout.fragment_credit_card_wait, container, false);
        classUiProcess = ((MainActivity) MainActivity.mContext).getClassUiProcess();

        loadingContainer = view.findViewById(R.id.loadingContainer);
        handler = new Handler(Looper.getMainLooper());
        startDotLoop(view);

//        tls3800 = ((MainActivity) MainActivity.mContext).getTls3800();

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        try {
            animationDrawable.start();
            //image check
            //결제
            ((MainActivity) MainActivity.mContext).runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    countHandler = new Handler();
                    countRunnable = new Runnable() {
                        @Override
                        public void run() {
                            cnt++;
                            if (Objects.equals(cnt, 45)) {
                                countHandler.removeCallbacks(countRunnable);
                                countHandler.removeCallbacksAndMessages(null);
                                countHandler.removeMessages(0);
//                                if (classUiProcess.getChargingCurrentData().isPrePaymentResult()) {
//                                    //선 결제에 의한 무카드 취소 (4:무카드 취소)(5:부분 취소)
//                                    tls3800.onTLS3800Request(mChannel, TLS3800.CMD_TX_PAYCANCEL, 4);
//                                }
                                classUiProcess.onHome();
                            } else {
                                countHandler.postDelayed(countRunnable, 1000);
                            }
                        }
                    };
                    countHandler.postDelayed(countRunnable, 1000);
                }
            });
            // 신용 카드 결제
//            onTls3800Payment();
        } catch (Exception e) {
            logger.error(" CreditCardWaitFragment onViewCreated : {}", e.getMessage());
        }
    }


    private void startDotLoop(View root) {
        currentStep = 0;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (currentStep < dotIds.length) {
                    View dot = root.findViewById(dotIds[currentStep]);
                    GradientDrawable drawable = new GradientDrawable();
                    drawable.setShape(GradientDrawable.OVAL);
                    drawable.setColor(Color.parseColor(colors[currentStep]));
                    dot.setBackground(drawable);
                    dot.setVisibility(View.VISIBLE);
                    currentStep++;
                    handler.postDelayed(this, 200);
                } else {
                    handler.postDelayed(() -> {
                        for (int id : dotIds) {
                            View dot = root.findViewById(id);
                            dot.setVisibility(View.INVISIBLE);
                        }
                        // 다음 사이클 시작
                        startDotLoop(root);
                    }, 200); // 다 보여진 후 0.8초 기다림
                }
            }
        }, 100);
    }


    @Override
    public void onDetach() {
        super.onDetach();
        if (handler != null) handler.removeCallbacksAndMessages(null);
    }


}