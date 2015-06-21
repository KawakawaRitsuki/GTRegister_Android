package com.kawakawaplanning.gtregister;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/* Import ZBar Class files */

public class MainActivity extends ActionBarActivity {
    private Camera mCamera;
    private CameraPreview mPreview;
    private Handler autoFocusHandler;
    private SoundPool sp;
    private int sound_id;
    private AlertDialog alertDialog;

    public static LinearLayout buttons;
    public static Button button;

    ImageScanner scanner;

    private boolean barcodeScanned = false;
    private boolean previewing = true;

    static {
        System.loadLibrary("iconv");
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sp = new SoundPool( 1, AudioManager.STREAM_MUSIC, 0 );
        sound_id = sp.load(this, R.raw.success, 1 );

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);

        autoFocusHandler = new Handler();
        mCamera = getCameraInstance();

        button = (Button)findViewById(R.id.button);
        buttons = (LinearLayout)findViewById(R.id.buttons);

        buttons.setVisibility(View.INVISIBLE);
        button.setVisibility(View.VISIBLE);

        /* Instance barcode scanner */
        scanner = new ImageScanner();
        scanner.setConfig(0, Config.X_DENSITY, 3);
        scanner.setConfig(0, Config.Y_DENSITY, 3);

        mPreview = new CameraPreview(this, mCamera, previewCb, autoFocusCB);
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
                    mCamera.autoFocus(autoFocusCB);
                    buttons.setVisibility(View.INVISIBLE);
                    button.setVisibility(View.VISIBLE);
                }else{
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            if (previewing) {
                                mCamera.cancelAutoFocus();
                                mCamera.autoFocus(autoFocusCB);
                            }
                        }
                    }).start();
                }
                return false;
            }
        });

    }

    public static boolean isNumber(String str){
        Pattern p = Pattern.compile("^[1-9][0-9]*");
        Matcher m = p.matcher(str);

        return m.find();
    }

    public void kaikei(View v){
        new SendThread(MainActivity.this,"kaikei").start();
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflatername = (LayoutInflater)this.getSystemService(
                LAYOUT_INFLATER_SERVICE);
        View view =  inflatername.inflate(R.layout.dialog_kaikei,
                (ViewGroup)findViewById(R.id.dialogname_layout));

        final EditText et = (EditText)view.findViewById(R.id.editText);
        alertDialogBuilder.setTitle("受取金額入力");
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String uketori = et.getEditableText().toString();

                        if (!uketori.isEmpty() && isNumber(uketori)) {
                            new SendThread(MainActivity.this, "uketori," + uketori).start();
                        }
                    }
                });
        alertDialogBuilder.setCancelable(true);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void waribiki(View v){
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflatername = (LayoutInflater)this.getSystemService(
                LAYOUT_INFLATER_SERVICE);
        View view =  inflatername.inflate(R.layout.dialog_waribiki,
                (ViewGroup)findViewById(R.id.dialog_waribiki_layout));

        view.findViewById(R.id.btn1).setOnClickListener(onClick);
        view.findViewById(R.id.btn2).setOnClickListener(onClick);
        view.findViewById(R.id.btn3).setOnClickListener(onClick);
        view.findViewById(R.id.btn4).setOnClickListener(onClick);
        view.findViewById(R.id.btn5).setOnClickListener(onClick);
        view.findViewById(R.id.btn6).setOnClickListener(onClick);
        view.findViewById(R.id.btn7).setOnClickListener(onClick);
        view.findViewById(R.id.btn8).setOnClickListener(onClick);
        view.findViewById(R.id.btn9).setOnClickListener(onClick);


        alertDialogBuilder.setTitle("割引選択");
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setCancelable(true);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public View.OnClickListener onClick = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int waribiki = 0;
            switch (v.getId()){
                case R.id.btn1:
                    waribiki = 1;
                    break;
                case R.id.btn2:
                    waribiki = 2;
                    break;
                case R.id.btn3:
                    waribiki = 3;
                    break;
                case R.id.btn4:
                    waribiki = 4;
                    break;
                case R.id.btn5:
                    waribiki = 5;
                    break;
                case R.id.btn6:
                    waribiki = 6;
                    break;
                case R.id.btn7:
                    waribiki = 7;
                    break;
                case R.id.btn8:
                    waribiki = 8;
                    break;
                case R.id.btn9:
                    waribiki = 9;
                    break;
            }
            new SendThread(MainActivity.this,waribiki+"割引").start();
            alertDialog.dismiss();
            barcodeScanned = false;
            mCamera.setPreviewCallback(previewCb);
            mCamera.startPreview();
            previewing = true;
            mCamera.cancelAutoFocus();
            mCamera.autoFocus(autoFocusCB);
            buttons.setVisibility(View.INVISIBLE);
            button.setVisibility(View.VISIBLE);
        }
    };

    public void nyuryoku(View v){
//        new SendThread(MainActivity.this,sym.getData()).start();
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflaterInput = (LayoutInflater)this.getSystemService(
                LAYOUT_INFLATER_SERVICE);
        View view =  inflaterInput.inflate(R.layout.dialog_input,
                (ViewGroup)findViewById(R.id.dialog_input_layout));

        final EditText et = (EditText)view.findViewById(R.id.editText);
        alertDialogBuilder.setTitle("商品金額入力");
        alertDialogBuilder.setView(view);
        alertDialogBuilder.setPositiveButton("OK",
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
                        }//else文でエラー書く
                    }
                });
        alertDialogBuilder.setCancelable(true);
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    public void cancel(View v){
        barcodeScanned = false;
        mCamera.setPreviewCallback(previewCb);
        mCamera.startPreview();
        previewing = true;
        mCamera.cancelAutoFocus();
        mCamera.autoFocus(autoFocusCB);
        buttons.setVisibility(View.INVISIBLE);
        button.setVisibility(View.VISIBLE);
        new SendThread(MainActivity.this,"cancel").start();
    }


    public void onPause() {
        super.onPause();
        releaseCamera();
        finish();
    }


    /** A safe way to get an instance of the Camera object. */
    public static Camera getCameraInstance(){
        Camera c = null;
        try {
            c = Camera.open();
        } catch (Exception e){
        }
        return c;
    }

    private void releaseCamera() {
        if (mCamera != null) {
            previewing = false;
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }


    private Runnable doAutoFocus = new Runnable() {
        public void run() {
//            if (previewing)
//                mCamera.autoFocus(autoFocusCB);
        }
    };

    Camera.PreviewCallback previewCb = new PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);


            int result = scanner.scanImage(barcode);

            if (result != 0) {
                previewing = false;
                mCamera.setPreviewCallback(null);
                mCamera.stopPreview();
                buttons.setVisibility(View.VISIBLE);
                button.setVisibility(View.INVISIBLE);

                SymbolSet syms = scanner.getResults();
                for (Symbol sym : syms) {
                    sp.play(sound_id, 1.0F, 1.0F, 0, 0, 1.0F);
                    if(isNumber(sym.getData())) {
                        Toast.makeText(MainActivity.this, sym.getData(), Toast.LENGTH_SHORT).show();
                        new SendThread(MainActivity.this, sym.getData()).start();
                    }
                    barcodeScanned = true;
                }
            }
        }
    };

    // Mimic continuous auto-focusing
    Camera.AutoFocusCallback autoFocusCB = new Camera.AutoFocusCallback() {
        public void onAutoFocus(boolean success, Camera camera) {
            autoFocusHandler.postDelayed(doAutoFocus, 1000);
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
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
class SendThread extends Thread{
    String sendTxt;
    Context context;

    public SendThread(Context con,String str){
        sendTxt = str;
        context = con;
    }

    public void run(){

        SharedPreferences spf = PreferenceManager.getDefaultSharedPreferences(context);
        String ip = spf.getString("ip_preference", "");
        String port = spf.getString("port_preference", "10342");

        ip = "192.168.10.2";

        Socket socket = null;

        try {
            socket = new Socket(ip,Integer.parseInt(port));
            PrintWriter pw = new PrintWriter(socket.getOutputStream(),true);


            pw.println(sendTxt);

        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        if( socket != null){
            try {
                socket.close();
                socket = null;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


}