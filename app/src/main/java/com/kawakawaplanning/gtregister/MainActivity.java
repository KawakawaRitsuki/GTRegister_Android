package com.kawakawaplanning.gtregister;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
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

public class MainActivity extends ActionBarActivity {

    /*
     * このアプリケーションの命名規則
     *
     * クラス名
     *  - 最初の文字は大文字
     *  - 大文字で区切る
     *
     * メソッド名
     *  - 最初の文字は小文字
     *  - 大文字で区切る
     *
     *  - booleanを返すものは、isを最初につける
     *
     * クラス変数
     *  - 最初にmを付ける
     *  - 大文字で区切る
     *
     * Button = Btn
     * Textview = Tv
     *
     * 命名するときは、理解できるようにする。
     *
     * O mDiscountBtn
     * X mButton1
     *
     */

    private void assignViews() {
        mCameraPreview = (FrameLayout) findViewById(R.id.cameraPreview);
        mDiscountBtn   = (Button)      findViewById(R.id.discountBtn);
        mInputBtn      = (Button)      findViewById(R.id.inputBtn);
        mBillBtn       = (Button)      findViewById(R.id.billBtn);
        mGoodsLv       = (ListView)    findViewById(R.id.goodsLv);
    }

    //カメラ系
    private Camera mCamera;
    private ImageScanner mScanner;
    private CameraPreview mPreview;

    //フラグ系
    private boolean mBarcodeScanned = false;

    //レイアウト系
    private FrameLayout mCameraPreview;
    private Button mDiscountBtn;
    private Button mInputBtn;
    private Button mBillBtn;
    private ListView mGoodsLv;

    //変数
    private ArrayList<Integer> mAmountsList;
    private ArrayAdapter<String> mGoodsNameAdapter;
    private int sound_id;

    //その他
    private AlertDialog alertDialog;
    private SharedPreferences mSharedPreferences;
    private Vibrator mVibrator;
    private SoundPool mSoundPool;

    static {
        System.loadLibrary("iconv");
    }


    //onCreateでは定義＆関連付けのみをする
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);//多分画面回転的な何か
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);//画面常時点灯のアレ

        assignViews();

        //カメラ系の定義
        mScanner           = new ImageScanner();
        mCamera            = Camera.open();
        mPreview           = new CameraPreview(this, mCamera, previewCb, null);

        //変数系の定義
        mGoodsNameAdapter  = new ArrayAdapter<>(this,R.layout.list_item);
        mAmountsList       = new ArrayList<>();
        mSoundPool         = new SoundPool( 1, AudioManager.STREAM_MUSIC, 0 );
        sound_id           = mSoundPool.load(this, R.raw.success, 1);

        //その他の定義
        mVibrator          = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

    }

    @Override
    protected void onStart() {
        super.onStart();

        //画面の操作
        setVisibilitys(0);

        mScanner.setConfig(0, Config.X_DENSITY, 3);
        mScanner.setConfig(0, Config.Y_DENSITY, 3);
        mCameraPreview.addView(mPreview);

        mGoodsLv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            @Override
            public boolean onItemLongClick(AdapterView<?> parent, View view, final int position, long id) {
                AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
                adb.setTitle("Are you sure?");
                adb.setMessage(mGoodsLv.getItemAtPosition(position).toString() + "を削除しますか？");
                adb.setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                new SendThread(MainActivity.this, "delete," + position).start();
                                mAmountsList.remove(position);
                                ArrayAdapter<String> tempAdapter = new ArrayAdapter<String>(MainActivity.this, R.layout.list_item);
                                for (int i = 0; i != mGoodsNameAdapter.getCount(); i++) {
                                    if (i != position)
                                        tempAdapter.add(mGoodsNameAdapter.getItem(i));
                                }
                                mGoodsNameAdapter.clear();
                                mGoodsNameAdapter = tempAdapter;
                                mGoodsLv.setAdapter(mGoodsNameAdapter);

                            }
                        });
                adb.setNegativeButton("Cancel", null);
                adb.setCancelable(true);
                adb.show();
                return false;
            }
        });

        if(mSharedPreferences.getBoolean("booted",false) == false ){
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString("ip_preference","192.168.XXX.XXX");
            editor.putString("port_preference", "10000");
            editor.putBoolean("booted",true);
            editor.apply();
        }

        findViewById(R.id.root).setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (mBarcodeScanned) {
                    mBarcodeScanned = false;
                    mCamera.setPreviewCallback(previewCb);
                    mCamera.startPreview();
                    mCamera.cancelAutoFocus();
                    mCamera.autoFocus(null);
                    setVisibilitys(2);
                } else {
                    if (!mBarcodeScanned) {
                        mCamera.cancelAutoFocus();
                        try {
                            mCamera.autoFocus(null);
                        } catch (RuntimeException e) {
                        }
                    }
                }
                return false;
            }
        });
    }

    private void setVisibilitys(int i) {
        if(i == 0){
            mDiscountBtn.setVisibility(View.INVISIBLE);
            mBillBtn.setVisibility(View.INVISIBLE);
            mInputBtn.setVisibility(View.VISIBLE);
        }else if(i == 1){
            mDiscountBtn.setVisibility(View.VISIBLE);
            mBillBtn.setVisibility(View.VISIBLE);
            mInputBtn.setVisibility(View.INVISIBLE);
        }else if(i == 2){
            mDiscountBtn.setVisibility(View.INVISIBLE);
            mBillBtn.setVisibility(View.VISIBLE);
            mInputBtn.setVisibility(View.VISIBLE);
        }else if(i == 3){
            mDiscountBtn.setVisibility(View.INVISIBLE);
            mBillBtn.setVisibility(View.VISIBLE);
            mInputBtn.setVisibility(View.INVISIBLE);
        }

    }

    public static boolean isNumber(String str){
        Pattern p = Pattern.compile("^[1-9][0-9]*");
        Matcher m = p.matcher(str);
        return m.find();
    }

    public void bill(View v){

        int temp = 0;
        for(int i: mAmountsList){
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
                                            mAmountsList.removeAll(mAmountsList);
                                            mGoodsNameAdapter.clear();
                                            setVisibilitys(0);
                                        }
                                    });
                            adb.setCancelable(false);
                            adb.show();

                        }

                    }
                });
        adb.setCancelable(true);
        adb.show();
    }

    public void discount(View v){
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater)this.getSystemService(
                LAYOUT_INFLATER_SERVICE);
        View view =  inflater.inflate(R.layout.dialog_waribiki,
                (ViewGroup)findViewById(R.id.dialog_waribiki_layout));

        adb.setTitle("割引選択");
        adb.setView(view);
        adb.setCancelable(true);
        alertDialog = adb.create();
        alertDialog.show();
    }

    public void onDiscountClick(View v) {

        Button button = (Button)v.getRootView().findViewById(v.getId());
        String btnTxt = button.getText().toString().substring(0, 1);

        int temp = mAmountsList.get(0);
        mAmountsList.remove(0);

        int wari = 10 - Integer.parseInt(btnTxt);
        double a = temp/10;
        double b = a * wari;
        double c = b / 10;

        String round = mSharedPreferences.getString("round","normal");

        if (round.equals("round")) {
            long l = Math.round(c);
            int i = (int) (l * 10);
            mAmountsList.add(0, i);
        }else if (round.equals("ceil")) {
            double d = Math.ceil(c);
            int i = (int) (d * 10);
            mAmountsList.add(0, i);
        }else if (round.equals("floor")) {
            double d = Math.floor(c);
            int i = (int) (d * 10);
            mAmountsList.add(0, i);
        }else if (round.equals("normal")) {
            double d = c * 10;
            mAmountsList.add(0, (int) d);
        }

        ArrayAdapter<String> tempAdapter = new ArrayAdapter<String>(MainActivity.this, R.layout.list_item);

        for(int i = 0; i != mGoodsNameAdapter.getCount();i++){
            if(i != 0)
                tempAdapter.add(mGoodsNameAdapter.getItem(i));
        }

        mGoodsNameAdapter.clear();
        mGoodsNameAdapter = tempAdapter;
        mGoodsNameAdapter.insert(temp + "円商品 ¥" + mAmountsList.get(0) + "- (" + Integer.parseInt(btnTxt) + "割引)", 0);
        mGoodsLv.setAdapter(mGoodsNameAdapter);

        new SendThread(MainActivity.this,"waribiki," + temp + "," + mAmountsList.get(0) + "," + Integer.parseInt(btnTxt)).start();

        alertDialog.dismiss();
        setVisibilitys(3);
    }
    
    public void input(View v){
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
                            mCamera.setPreviewCallback(null);
                            mCamera.stopPreview();

                            setVisibilitys(1);
                            mBarcodeScanned = true;
                            mAmountsList.add(0, Integer.parseInt(input));
                            mGoodsNameAdapter.insert(input + "円商品 ¥" + input + "-", 0);
                            mGoodsLv.setAdapter(mGoodsNameAdapter);
                        }//else文でエラー書く
                    }
                });
        adb.setCancelable(true);

        final AlertDialog ad = adb.create();
        et.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus) {
                    ad.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                }
            }
        });

        ad.show();

    }

    public void onPause() {
        super.onPause();
        releaseCamera();
        finish();
    }

    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.setPreviewCallback(null);
            mCamera.release();
            mCamera = null;
        }
    }

    Camera.PreviewCallback previewCb = new Camera.PreviewCallback() {
        public void onPreviewFrame(byte[] data, Camera camera) {
            Camera.Parameters parameters = camera.getParameters();
            Camera.Size size = parameters.getPreviewSize();

            Image barcode = new Image(size.width, size.height, "Y800");
            barcode.setData(data);


            int result = mScanner.scanImage(barcode);

            if (result != 0) {
                SymbolSet syms = mScanner.getResults();
                for (Symbol sym : syms) {
                    if(isNumber(sym.getData())) {

                        mCamera.setPreviewCallback(null);
                        mCamera.stopPreview();
                        setVisibilitys(1);
                        mVibrator.vibrate(100);
                        mSoundPool.play(sound_id, 1.0F, 1.0F, 0, 0, 1.0F);
                        Toast.makeText(MainActivity.this, sym.getData(), Toast.LENGTH_SHORT).show();
                        new SendThread(MainActivity.this, sym.getData()).start();
                        mBarcodeScanned = true;
                        mAmountsList.add(0, Integer.parseInt(sym.getData()));
                        mGoodsNameAdapter.insert(sym.getData() + "円商品 ¥" + sym.getData() + "-", 0);
                        mGoodsLv.setAdapter(mGoodsNameAdapter);
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