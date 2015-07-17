package com.kawakawaplanning.gtregister;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Vibrator;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import net.sourceforge.zbar.Config;
import net.sourceforge.zbar.Image;
import net.sourceforge.zbar.ImageScanner;
import net.sourceforge.zbar.Symbol;
import net.sourceforge.zbar.SymbolSet;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends ActionBarActivity implements View.OnClickListener{

    private Camera mCamera;
    private CameraPreview mPreview;
    private SoundPool sp;
    private int sound_id;
    private AlertDialog alertDialog;

    private Vibrator vib;
    private ArrayList<Integer> list;

    public static LinearLayout buttons;
    public static Button button;

    ImageScanner scanner;

    private boolean barcodeScanned = false;
    private boolean previewing = true;
    public static final int PREFERENCE_INIT = 0;
    public static final int PREFERENCE_BOOTED = 1;

    static {
        System.loadLibrary("iconv");
    }

    public void onCreate(Bundle savedInstanceState) {

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//画面常時点灯のアレ
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vib = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        list = new ArrayList<>();

        SharedPreferences spf = PreferenceManager.getDefaultSharedPreferences(this);
        if(PREFERENCE_INIT == getState() ){
            SharedPreferences.Editor editor = spf.edit();
            editor.putString("ip_preference","192.168.XXX.XXX");
            editor.putString("port_preference", "10000");
            editor.apply();
            setState(PREFERENCE_BOOTED);
        }

        sp = new SoundPool( 1, AudioManager.STREAM_MUSIC, 0 );
        sound_id = sp.load(this, R.raw.success, 1 );

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//多分画面回転的な何か

        mCamera = Camera.open();

        button = (Button)findViewById(R.id.button);
        buttons = (LinearLayout)findViewById(R.id.buttons);

        buttons.setVisibility(View.INVISIBLE);
        button.setVisibility(View.VISIBLE);

        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        mPreview = new CameraPreview(this, mCamera, previewCb, null);
        FrameLayout preview = (FrameLayout)findViewById(R.id.cameraPreview);

        preview.addView(mPreview);

        FrameLayout root = (FrameLayout)findViewById(R.id.root);
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (barcodeScanned) {
                    barcodeScanned = false;
                    mCamera.setPreviewCallback(previewCb);
                    mCamera.startPreview();
                    previewing = true;
                    mCamera.cancelAutoFocus();
                    mCamera.autoFocus(null);
                    buttons.setVisibility(View.INVISIBLE);
                    button.setVisibility(View.VISIBLE);
                }else{
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (previewing) {
                                mCamera.cancelAutoFocus();
                                mCamera.autoFocus(null);
                            }
                        }
                    }).start();
                }
                return false;
            }
        });

    }

    private void setState(int state) {
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        sp.edit().putInt("InitState", state).commit();
    }

    private int getState() {
        int state;
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(this);
        state = sp.getInt("InitState", PREFERENCE_INIT);
        return state;
    }

    public static boolean isNumber(String str){
        Pattern p = Pattern.compile("^[1-9][0-9]*");
        Matcher m = p.matcher(str);
        return m.find();
    }

    public void kaikei(View v){

        int temp = 0;
        for(int i:list){
            temp = temp + i;
        }

        final int sum = temp;

        new SendThread(MainActivity.this,"goukei," + temp).start();

        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_kaikei,(ViewGroup)findViewById(R.id.dialog_kaikei_layout));

        final EditText et = (EditText)view.findViewById(R.id.editText);
        adb.setTitle("合計金額は" + sum + "円です");
        adb.setView(view);
        adb.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String uketori = et.getEditableText().toString();
                        if (!uketori.isEmpty() && Integer.parseInt(uketori) >= sum) {
                            int change = Integer.parseInt(uketori) - sum;
                            new SendThread(MainActivity.this, "kaikei," + Integer.parseInt(uketori) + "," + sum + "," + change).start();

                            AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                            LayoutInflater inflater = (LayoutInflater) MainActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);
                            View view = inflater.inflate(R.layout.dialog_change, (ViewGroup) findViewById(R.id.dialog_change_layout));

                            TextView tv1 = (TextView)view.findViewById(R.id.azukari);
                            TextView tv2 = (TextView)view.findViewById(R.id.genkei);
                            TextView tv3 = (TextView)view.findViewById(R.id.change);

                            tv1.setText(uketori + "円");
                            tv2.setText(sum + "円");
                            tv3.setText(change + "円");

                            adb.setTitle("会計");
                            adb.setView(view);
                            adb.setPositiveButton("OK",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            list.removeAll(list);
                                        }
                                    });
                            adb.setCancelable(false);
                            alertDialog = adb.create();
                            alertDialog.show();

                        }

                    }
                });
        adb.setCancelable(true);
        adb.show();
    }

    public void waribiki(View v){
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater)this.getSystemService(
                LAYOUT_INFLATER_SERVICE);
        View view =  inflater.inflate(R.layout.dialog_waribiki,
                (ViewGroup)findViewById(R.id.dialog_waribiki_layout));

        view.findViewById(R.id.btn1).setOnClickListener(this);
        view.findViewById(R.id.btn2).setOnClickListener(this);
        view.findViewById(R.id.btn3).setOnClickListener(this);
        view.findViewById(R.id.btn4).setOnClickListener(this);
        view.findViewById(R.id.btn5).setOnClickListener(this);
        view.findViewById(R.id.btn6).setOnClickListener(this);
        view.findViewById(R.id.btn7).setOnClickListener(this);
        view.findViewById(R.id.btn8).setOnClickListener(this);
        view.findViewById(R.id.btn9).setOnClickListener(this);

        adb.setTitle("割引選択");
        adb.setView(view);
        adb.setCancelable(true);
        alertDialog = adb.create();
        alertDialog.show();
    }

    @Override
    public void onClick(View v) {

        Button button = (Button)v.getRootView().findViewById(v.getId());
        String btnTxt = button.getText().toString().substring(0,1);

        new SendThread(MainActivity.this,Integer.parseInt(btnTxt)+"割引").start();
        System.out.println(Integer.parseInt(btnTxt)+"割引");
        alertDialog.dismiss();
        barcodeScanned = false;
        mCamera.setPreviewCallback(previewCb);
        mCamera.startPreview();
        previewing = true;
        mCamera.cancelAutoFocus();
        mCamera.autoFocus(null);
        buttons.setVisibility(View.INVISIBLE);
        button.setVisibility(View.VISIBLE);
    }
    
    public void nyuryoku(View v){
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        LayoutInflater inflaterInput = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflaterInput.inflate(R.layout.dialog_input,(ViewGroup)findViewById(R.id.dialog_input_layout));

        final EditText et = (EditText)view.findViewById(R.id.editText);
        adb.setTitle("商品金額入力");
        adb.setView(view);
        adb.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String input = et.getEditableText().toString();
                        if (!input.isEmpty() && isNumber(input)) {
                            new SendThread(MainActivity.this, input).start();
                            previewing = false;
                            mCamera.setPreviewCallback(null);
                            mCamera.stopPreview();

                            buttons.setVisibility(View.VISIBLE);
                            button.setVisibility(View.INVISIBLE);
                            barcodeScanned = true;
                            list.add(Integer.parseInt(input));
                        }//else文でエラー書く
                    }
                });
        adb.setCancelable(true);
        adb.show();
    }

    public void cancel(View v){
        barcodeScanned = false;
        mCamera.setPreviewCallback(previewCb);
        mCamera.startPreview();
        previewing = true;
        mCamera.cancelAutoFocus();
        mCamera.autoFocus(null);
        buttons.setVisibility(View.INVISIBLE);
        button.setVisibility(View.VISIBLE);
        new SendThread(MainActivity.this,"cancel").start();
    }


    public void onPause() {
        super.onPause();
        releaseCamera();
        finish();
    }


    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    Camera.PreviewCallback previewCb = new PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);


            int result = scanner.scanImage(barcode);

            if (result != 0) {
                SymbolSet syms = scanner.getResults();
                for (Symbol sym : syms) {
                    if(isNumber(sym.getData())) {

                        previewing = false;
                        mCamera.setPreviewCallback(null);
                        mCamera.stopPreview();
                        buttons.setVisibility(View.VISIBLE);
                        button.setVisibility(View.INVISIBLE);
                        vib.vibrate(100);
                        sp.play(sound_id, 1.0F, 1.0F, 0, 0, 1.0F);
                        Toast.makeText(MainActivity.this, sym.getData(), Toast.LENGTH_SHORT).show();
                        new SendThread(MainActivity.this, sym.getData()).start();
                        barcodeScanned = true;
                        list.add(Integer.parseInt(sym.getData()));

                    }
                }
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClass(this , Preferences.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
class SendThread extends Thread{
    private String sendTxt;
    private Context context;

    public SendThread(Context con,String str){
        this.sendTxt = str;
        this.context = con;
    }

    public void run(){

        SharedPreferences spf = PreferenceManager.getDefaultSharedPreferences(context);
        String ip = spf.getString("ip_preference", "192.168.11.2");
        String port = spf.getString("port_preference", "10342");

        Socket socket = null;

        try {
            socket = new Socket(ip,Integer.parseInt(port));
            PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);
            pw.println(sendTxt);
        } catch (UnknownHostException e) {
            //送信先が見つからない時
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if( socket != null){
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}