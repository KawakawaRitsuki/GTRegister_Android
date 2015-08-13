package com.kawakawaplanning.gtregister;


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
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.KeyEvent;
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
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class MainActivity extends AppCompatActivity {

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
     *  Button = Btn
     *  TextView = Tv
     *
     * 命名するときは、理解できるようにする。
     *
     * O mDiscountBtn
     * X mButton1
     *
     *  複数訳があるものを統一
     *
     *  - 金額 -> amount
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
        // 下記、前カメラ用 質悪い故に反応速度悪し
        // mCamera            = Camera.open(1);
        mPreview           = new CameraPreview(this, mCamera, previewCb, null);

        //変数系の定義
        mGoodsNameAdapter  = new ArrayAdapter<>(this,R.layout.list_item);
        mAmountsList       = new ArrayList<>();
        mSoundPool         = new SoundPool( 1, AudioManager.STREAM_MUSIC, 0 );
        sound_id           = mSoundPool.load(this, R.raw.success, 1);

        //その他の定義
        mVibrator          = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

        if(mSharedPreferences.getBoolean("has_list",false)) {
            String amount[] = mSharedPreferences.getString("amountList", "").split(",");
            String name[]   = mSharedPreferences.getString("nameList", "").split(",");



            for (int i = 0; i != amount.length; i++){
                System.out.println(amount[i]);
                System.out.println(name[i]);
                mAmountsList.add(0, Integer.parseInt(amount[i]));
                mGoodsNameAdapter.insert(name[i],0);
            }
            mGoodsLv.setAdapter(mGoodsNameAdapter);
        }

    }

    @Override
    protected void onStart() {
        super.onStart();

        //画面の操作
        setVisibilities(0);

        mScanner.setConfig(0, Config.X_DENSITY, 3);
        mScanner.setConfig(0, Config.Y_DENSITY, 3);
        mCameraPreview.addView(mPreview);

        mGoodsLv.setOnItemLongClickListener((AdapterView<?> parent, View view, final int _position, long id) -> {

            alert("Are you sure?",
                    mGoodsLv.getItemAtPosition(_position).toString() + "を削除しますか？",
                    (DialogInterface dialog, int which) -> delete(_position)
                    , true);
            return false;
        });

        if(!mSharedPreferences.getBoolean("booted",false)){
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putString("ip_preference","192.168.XXX.XXX");
            editor.putString("port_preference", "10000");
            editor.putBoolean("booted", true);

            editor.putString("amountList", "");
            editor.putString("nameList","");
            editor.putBoolean("has_list", false);
            editor.apply();
        }

        findViewById(R.id.root).setOnTouchListener((View v, MotionEvent event) -> {
            if (mBarcodeScanned) {
                mBarcodeScanned = false;
                mCamera.setPreviewCallback(previewCb);
                mCamera.startPreview();
                mCamera.cancelAutoFocus();
                mCamera.autoFocus(null);
                setVisibilities(2);
            } else {
                mCamera.cancelAutoFocus();
                try {
                    mCamera.autoFocus(null);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                }
            }
            return false;
        });
    }

    private void alert(String title , String message , DialogInterface.OnClickListener onClick , boolean cancelable){
        AlertDialog.Builder adb = new AlertDialog.Builder(MainActivity.this);
        adb.setTitle(title);
        adb.setMessage(message);
        adb.setPositiveButton("OK", onClick);
        adb.setNegativeButton("Cancel", null);
        adb.setCancelable(cancelable);
        adb.show();
    }

    private void alertView(String title ,View view , DialogInterface.OnClickListener onClick , boolean cancelable ,boolean positiveBtn ,boolean negativeBtn){
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        adb.setTitle(title);
        adb.setView(view);
        if(positiveBtn)
            adb.setPositiveButton("OK", onClick);
        if(negativeBtn)
            adb.setNegativeButton("Cancel", onClick);
        adb.setCancelable(cancelable);
        adb.show();
    }

    private void setVisibilities(int i) {
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

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {

        if (e.getAction() == KeyEvent.ACTION_DOWN)
            switch (e.getKeyCode()) {

            case KeyEvent.KEYCODE_VOLUME_UP:
            case KeyEvent.KEYCODE_VOLUME_DOWN:

                switch (mSharedPreferences.getString("vol" + e.getKeyCode(), "none")) {
                    case "none":
                        return false;
                    case "next":
                        if (mBarcodeScanned) {
                            mBarcodeScanned = false;
                            mCamera.setPreviewCallback(previewCb);
                            mCamera.startPreview();
                            mCamera.cancelAutoFocus();
                            mCamera.autoFocus(null);
                            setVisibilities(2);
                        } else {
                            mCamera.cancelAutoFocus();
                            try {
                                mCamera.autoFocus(null);
                            } catch (RuntimeException e1) {
                                e1.printStackTrace();
                            }
                        }
                        return true;
                    case "bill":
                        bill(null);
                        return true;
                    case "discount":
                        discount(null);
                        return true;

                }
                return false;

            case KeyEvent.KEYCODE_BACK:
                alert("Are you sure?", "アプリを終了してもいいですか？", (DialogInterface dialog, int which) -> finish(), true);

        }

        return super.dispatchKeyEvent(e);
    }

    public void bill(View v){

        mCamera.setPreviewCallback(null);
        mCamera.stopPreview();
        mBarcodeScanned = true;

        int temp = 0;
        for(int i: mAmountsList){
            temp = temp + i;
        }
        final int sum = temp;

        new SendThread(MainActivity.this,"goukei," + sum).start();

        LayoutInflater inflater = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.dialog_bill, (ViewGroup) findViewById(R.id.dialog_bill_layout));

        alertView("合計金額は" + sum + "円です",
                view,
                (DialogInterface dialog, int which) -> {
                    if (which == DialogInterface.BUTTON_POSITIVE) {
                        EditText et = (EditText) view.findViewById(R.id.editText);
                        String acceptance = et.getEditableText().toString();

                        if (!acceptance.isEmpty()) {
                            if (Integer.parseInt(acceptance) >= sum) {

                                int change = Integer.parseInt(acceptance) - sum;
                                new SendThread(MainActivity.this, "kaikei," + Integer.parseInt(acceptance) + "," + sum + "," + change).start();

                                LayoutInflater inflater1 = (LayoutInflater) MainActivity.this.getSystemService(LAYOUT_INFLATER_SERVICE);
                                View view1 = inflater1.inflate(R.layout.dialog_change, (ViewGroup) findViewById(R.id.dialog_change_layout));

                                TextView tv1 = (TextView) view1.findViewById(R.id.azukari);
                                TextView tv2 = (TextView) view1.findViewById(R.id.genkei);
                                TextView tv3 = (TextView) view1.findViewById(R.id.change);

                                tv1.setText(acceptance + "円");
                                tv2.setText(sum + "円");
                                tv3.setText(change + "円");

                                alertView("合計金額は" + sum + "円です",
                                        view1,
                                        (DialogInterface dialog1, int which1) -> {
                                            mAmountsList.clear();
                                            mGoodsNameAdapter.clear();
                                            setVisibilities(0);
                                            SharedPreferences.Editor editor = mSharedPreferences.edit();

                                            editor.putString("amountList","");
                                            editor.putString("nameList", "");
                                            editor.putBoolean("has_list", false);
                                            editor.apply();
                                        },
                                        false,
                                        true,
                                        false);
                            } else {
                                Toast.makeText(this, "受取金額が合計金額を下回っています", Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            Toast.makeText(this, "金額を入力してください", Toast.LENGTH_SHORT).show();
                        }
                    }
                },
                true,
                true,
                true);

    }

    public void discount(View v){
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        LayoutInflater inflater = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View view =  inflater.inflate(R.layout.dialog_discount,(ViewGroup)findViewById(R.id.dialog_discount_layout));

        adb.setTitle("割引選択");
        adb.setView(view);
        adb.setCancelable(true);
        adb.setNegativeButton("Cancel",null);
        alertDialog = adb.show();
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
        switch (round) {
            case "round": {
                long l = Math.round(c);
                int i = (int) (l * 10);
                mAmountsList.add(0, i);
                break;
            }
            case "ceil": {
                double d = Math.ceil(c);
                int i = (int) (d * 10);
                mAmountsList.add(0, i);
                break;
            }
            case "floor": {
                double d = Math.floor(c);
                int i = (int) (d * 10);
                mAmountsList.add(0, i);
                break;
            }
            case "normal": {
                double d = c * 10;
                mAmountsList.add(0, (int) d);
                break;
            }
        }

        ArrayAdapter<String> tempAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.list_item);


        for(int i = 0; i != mGoodsNameAdapter.getCount();i++){
            if(i != 0)
                tempAdapter.add(mGoodsNameAdapter.getItem(i));
        }

        mGoodsNameAdapter.clear();
        mGoodsNameAdapter = tempAdapter;
        mGoodsNameAdapter.insert(temp + "円商品 ¥" + mAmountsList.get(0) + "- (" + Integer.parseInt(btnTxt) + "割引)", 0);
        mGoodsLv.setAdapter(mGoodsNameAdapter);

        SharedPreferences.Editor editor = mSharedPreferences.edit();
        String[] amountArray = mSharedPreferences.getString("amountList","").split(",");
        String[] nameArray = mSharedPreferences.getString("nameList","").split(",");

        String amount = "";
        String name = "";

        for (int i = 0; i != nameArray.length - 1; i++){
            if( i == 0 ){
                amount = amountArray[i];
                name   = nameArray[i];
            }else {
                amount = amount + "," + amountArray[i];
                name   = name + "," + nameArray[i];
            }
        }

        editor.putString("amountList", amount + "," + mAmountsList.get(0));
        editor.putString("nameList", name + "," +mGoodsNameAdapter.getItem(0));
        editor.putBoolean("has_list", true);

        editor.apply();

        new SendThread(MainActivity.this,"waribiki," + temp + "," + mAmountsList.get(0) + "," + Integer.parseInt(btnTxt)).start();

        alertDialog.dismiss();
        setVisibilities(3);
    }

    public void input(View v){
        AlertDialog.Builder adb = new AlertDialog.Builder(this);
        LayoutInflater inflaterInput = (LayoutInflater)this.getSystemService(LAYOUT_INFLATER_SERVICE);
        View view = inflaterInput.inflate(R.layout.dialog_input, (ViewGroup) findViewById(R.id.dialog_input_layout));

        final EditText et = (EditText)view.findViewById(R.id.editText);
        adb.setTitle("商品金額入力");
        adb.setView(view);
        adb.setPositiveButton("OK",
                (DialogInterface dialog, int which) -> {
                    String input = et.getEditableText().toString();
                    if (!input.isEmpty() && isNumber(input)) {
                        new SendThread(MainActivity.this, input).start();
                        mCamera.setPreviewCallback(null);
                        mCamera.stopPreview();

                        setVisibilities(1);
                        mBarcodeScanned = true;
                        mAmountsList.add(0, Integer.parseInt(input));
                        mGoodsNameAdapter.insert(input + "円商品 ¥" + input + "-", 0);
                        mGoodsLv.setAdapter(mGoodsNameAdapter);

                        if(!mSharedPreferences.getBoolean("has_list",false)){
                            SharedPreferences.Editor editor = mSharedPreferences.edit();
                            editor.putString("amountList", input);
                            editor.putString("nameList", input + "円商品 ¥" + input + "-");
                            editor.putBoolean("has_list", true);
                            editor.apply();
                        }else{
                            SharedPreferences.Editor editor = mSharedPreferences.edit();
                            editor.putString("amountList",mSharedPreferences.getString("amountList","") + "," + input);
                            editor.putString("nameList",mSharedPreferences.getString("nameList","") + "," + input + "円商品 ¥" + input + "-");
                            editor.apply();
                        }
                    }//else文でエラー書く
                });
        adb.setNegativeButton("Cancel",null);
        adb.setCancelable(true);

        final AlertDialog ad = adb.create();
        et.setOnFocusChangeListener((View vv, boolean hasFocus) -> {
            if (hasFocus) {
                ad.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });

        ad.show();

    }
    private void delete(int position){
        new SendThread(MainActivity.this, "delete," + position).start();
        mAmountsList.remove(position);
        ArrayAdapter<String> tempAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.list_item);
        for (int i = 0; i != mGoodsNameAdapter.getCount(); i++) {
            if (i != position)
                tempAdapter.add(mGoodsNameAdapter.getItem(i));
        }
        mGoodsNameAdapter.clear();
        mGoodsNameAdapter = tempAdapter;
        mGoodsLv.setAdapter(mGoodsNameAdapter);

        SharedPreferences.Editor editor = mSharedPreferences.edit();
        String[] amountArray = mSharedPreferences.getString("amountList","").split(",");
        String[] nameArray = mSharedPreferences.getString("nameList","").split(",");

        String amount = "";
        String name = "";

        for (int i = 0; i != nameArray.length; i++){

            if( i == 0 ){
                amount = amountArray[i];
                name   = nameArray[i];
            }else if(i != position){
                amount = amount + "," + amountArray[i];
                name   = name + "," + nameArray[i];
            }

        }
        editor.putString("amountList", amount);
        editor.putString("nameList", name);
        editor.putBoolean("has_list", true);
        editor.apply();
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
                SymbolSet symbolSet = mScanner.getResults();
                for (Symbol symbol : symbolSet)
                    if (isNumber(symbol.getData())) {
                        String money = symbol.getData();

                        mCamera.setPreviewCallback(null);
                        mCamera.stopPreview();
                        setVisibilities(1);
                        mVibrator.vibrate(100);
                        mBarcodeScanned = true;
                        mSoundPool.play(sound_id, 1.0F, 1.0F, 0, 0, 1.0F);
                        new SendThread(MainActivity.this, money).start();

                        mAmountsList.add(0, Integer.parseInt(money));
                        mGoodsNameAdapter.insert(money + "円商品 ¥" + money + "-", 0);
                        mGoodsLv.setAdapter(mGoodsNameAdapter);

                        if(!mSharedPreferences.getBoolean("has_list",false)){
                            SharedPreferences.Editor editor = mSharedPreferences.edit();
                            editor.putString("amountList", money);
                            editor.putString("nameList", money + "円商品 ¥" + money + "-");
                            editor.putBoolean("has_list", true);
                            editor.apply();
                        }else{
                            SharedPreferences.Editor editor = mSharedPreferences.edit();
                            editor.putString(mSharedPreferences.getString("amountList","") + "amountList", money);
                            editor.putString(mSharedPreferences.getString("nameList","") + "nameList", money + "円商品 ¥" + money + "-");
                            editor.apply();
                        }

                    }
            }
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0,0,0,"Settings");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == 0) {
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
        } catch (IOException e) {
            e.printStackTrace();
            //送信先が見つからない時
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