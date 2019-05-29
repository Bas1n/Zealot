package com.novo.zealot.UI.Activity;


import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.PopupMenu;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.AMapUtils;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.UiSettings;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.maps.model.Polyline;
import com.amap.api.maps.model.PolylineOptions;
import com.novo.zealot.Bean.RunRecord;
import com.novo.zealot.R;
import com.novo.zealot.Utils.GlobalUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class mapActivity extends Activity implements AMapLocationListener,
        LocationSource {

    private static final String TAG = "mapActivity";

    private static final int WRITE_COARSE_LOCATION_REQUEST_CODE = 0;
    private MapView mapView = null;
    private AMap aMap;
    PopupMenu popup = null;
    //定位需要的声明
    private AMapLocationClient mLocationClient = null;//定位发起端
    private AMapLocationClientOption mLocationOption = null;//定位参数
    private LocationSource.OnLocationChangedListener mListener = null;//定位监听器
    //标识，用于判断是否只显示一次定位信息和用户重新定位
    private boolean isFirstLoc = true;
    //绘制路线
    List<LatLng> path = new ArrayList<LatLng>();
    //上一次定位位置和当前定位位置，用于计算距离
    LatLng lastLatLng, nowLatLng;
    //此次运动总距离
    int distanceThisTime = 0;
    //平均速度
    float avgSpeed = 0;
    //开始时间
    Date startTime;


    //停止按钮
    FloatingActionButton btn_stop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_map);
        //获取地图控件引用
        mapView = (MapView) findViewById(R.id.map);
        //在activity执行onCreate时执行mapView.onCreate(savedInstanceState)，创建地图
        mapView.onCreate(savedInstanceState);

        //初始化开始时间
        startTime = new Date();

        btn_stop = findViewById(R.id.btn_stop);
        btn_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //若距离为0，则不存入数据库
                if (distanceThisTime == 0){
                    finish();
                    return;
                }

                //将此次数据存入数据库
                RunRecord runRecord = new RunRecord();
                runRecord.setEndTime(new Date());
                runRecord.setDistance(distanceThisTime);
                runRecord.setDuration((int) ((new Date().getTime() - startTime.getTime()) / 1000));
                runRecord.setAvgSpeed(avgSpeed);
                if (GlobalUtil.getInstance().databaseHelper.addRecord(runRecord)) {
                    Toast.makeText(getApplicationContext(), "数据存储成功", Toast.LENGTH_SHORT).show();
                } else {
                    Log.d(TAG, "数据存储失败");
                    Toast.makeText(getApplicationContext(), "数据记录失败", Toast.LENGTH_SHORT).show();
                }

                finish();
            }
        });

        //初始化地图控制器对象
        init();
        //设置显示定位按钮 并且可以点击
        UiSettings settings = aMap.getUiSettings();
        //设置定位监听
        aMap.setLocationSource(this);
        // 是否显示定位按钮
        settings.setMyLocationButtonEnabled(true);
        // 是否可触发定位并显示定位层
        aMap.setMyLocationEnabled(true);
        //定位的小图标
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.showMyLocation(true);
        myLocationStyle.myLocationType(myLocationStyle.LOCATION_TYPE_MAP_ROTATE);
        aMap.setMyLocationStyle(myLocationStyle);
        // 开启定位
        initLoc();
        RadioButton rb = (RadioButton) findViewById(R.id.gps);
        // 为GPS单选按钮设置监听器
        rb.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //初始化地图控制器对象
                init();
                //设置显示定位按钮 并且可以点击
                UiSettings settings = aMap.getUiSettings();
                //设置定位监听
                aMap.setLocationSource(mapActivity.this);
                // 是否显示定位按钮
                settings.setMyLocationButtonEnabled(true);
                // 是否可触发定位并显示定位层
                aMap.setMyLocationEnabled(true);
                // 开启定位
                initLoc();
            }
        });
        ToggleButton tb = (ToggleButton) findViewById(R.id.tb);
        tb.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    // 设置使用卫星地图
                    aMap.setMapType(AMap.MAP_TYPE_SATELLITE);
                } else {
                    // 设置使用普通地图
                    aMap.setMapType(AMap.MAP_TYPE_NORMAL);
                }
            }
        });
    }

    // 初始化AMap对象
    void init() {
        if (aMap == null) {
            aMap = mapView.getMap();
        }
    }

    //定位
    private void initLoc() {
        //这里用到ACCESS_FINE_LOCATION与ACCESS_COARSE_LOCATION权限
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            //申请WRITE_EXTERNAL_STORAGE权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    WRITE_COARSE_LOCATION_REQUEST_CODE);//自定义的code
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            //申请WRITE_EXTERNAL_STORAGE权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    WRITE_COARSE_LOCATION_REQUEST_CODE);//自定义的code
        }
        //初始化定位
        mLocationClient = new AMapLocationClient(getApplicationContext());
        //设置定位回调监听
        mLocationClient.setLocationListener(this);
        //初始化定位参数
        mLocationOption = new AMapLocationClientOption();
        //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        //设置是否返回地址信息（默认返回地址信息）
        mLocationOption.setNeedAddress(true);
        //设置是否只定位一次,默认为false
        mLocationOption.setOnceLocation(false);
        //设置是否强制刷新WIFI，默认为强制刷新
        mLocationOption.setWifiActiveScan(true);
        //设置是否允许模拟位置,默认为false，不允许模拟位置
        mLocationOption.setMockEnable(false);
        //设置定位间隔,单位毫秒,默认为2000ms
        mLocationOption.setInterval(3000);
        //给定位客户端对象设置定位参数
        mLocationClient.setLocationOption(mLocationOption);
        //启动定位
        mLocationClient.startLocation();
    }

    //定位回调函数
    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (amapLocation != null) {
            if (amapLocation.getErrorCode() == 0) {
                Log.d(TAG, "location changed");
                //定位成功回调信息，设置相关消息
                amapLocation.getLocationType();//获取当前定位结果来源，如网络定位结果，详见官方定位类型表
                amapLocation.getAccuracy();//获取精度信息
                amapLocation.getAddress();  // 地址，如果option中设置isNeedAddress为false，则没有此结果，网络定位结果中会有地址信息，GPS定位不返回地址信息。
                amapLocation.getCountry();  // 国家信息
                amapLocation.getProvince();  // 省信息
                amapLocation.getCity();  // 城市信息
                amapLocation.getDistrict();  // 城区信息
                amapLocation.getStreet();  // 街道信息
                amapLocation.getStreetNum();  // 街道门牌号信息
                amapLocation.getCityCode();  // 城市编码
                amapLocation.getAdCode();//地区编码
                amapLocation.getGpsAccuracyStatus();//GPS状态

                float nowSpeed = amapLocation.getSpeed();//获取速度

                //计算平均速度，即 (当前速度+当前平均速度)/2
                //若为第一次定位，则当前速度为平均速度
                if (isFirstLoc) {
                    avgSpeed = nowSpeed;
                } else {
                    avgSpeed = (avgSpeed + nowSpeed) / 2;
                }

                //如果不是第一次定位，则把上次定位信息传给lastLatLng
                if (!isFirstLoc) {
                    if ((int) AMapUtils.calculateLineDistance(nowLatLng, lastLatLng) < 100) {
                        lastLatLng = nowLatLng;
                    } else {
                        //定位出现问题，如突然瞬移，则取消此次定位修改
                        Toast.makeText(getApplicationContext()
                                , "此次计算距离差异过大，取消此次修改"
                                , Toast.LENGTH_SHORT).show();
                    }
                }
                double latitude = amapLocation.getLatitude();//获取纬度
                double longitude = amapLocation.getLongitude();//获取经度
                //新位置
                nowLatLng = new LatLng(latitude, longitude);

                //当前时间
                SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                Date date = new Date(amapLocation.getTime());
                String locTime = df.format(date);//定位时间

                //路径添加当前位置
                path.add(nowLatLng);


                //绘制路径
                Polyline polyline = aMap.addPolyline(
                        new PolylineOptions()
                                .addAll(path)
                                .width(10)
                                .color(Color.argb(255, 255, 0, 0)));

//                Toast.makeText(getApplicationContext(), locTime, Toast.LENGTH_SHORT).show();


                //如果不是第一次定位，就计算距离
                if (!isFirstLoc) {
                    int tempDistance = (int) AMapUtils.calculateLineDistance(nowLatLng, lastLatLng);

                    //
                    int testDuration = (int)(new Date().getTime() - startTime.getTime()) / 1000;
                    //

                    distanceThisTime += tempDistance;
                    Toast.makeText(getApplicationContext()
                            , "此次计算距离：" + tempDistance
                                    + " 此次运动总距离: " + distanceThisTime
                                    + " 速度：" + nowSpeed
                                    + "持续时间：" + testDuration
                            , Toast.LENGTH_SHORT).show();
                }

                //将地图移动到定位点
                aMap.moveCamera(CameraUpdateFactory.changeLatLng(nowLatLng));
                //点击定位按钮 能够将地图的中心移动到定位点
                mListener.onLocationChanged(amapLocation);
                if (isFirstLoc) {
                    //设置缩放级别
                    aMap.moveCamera(CameraUpdateFactory.zoomTo(18));
                    aMap.moveCamera(CameraUpdateFactory.changeTilt(0));
                    //获取定位信息
                    StringBuilder buffer = new StringBuilder();
                    buffer.append(amapLocation.getCountry() + "" + amapLocation.getProvince()
                            + "" + amapLocation.getCity() + "" + amapLocation.getProvince()
                            + "" + amapLocation.getDistrict() + "" + amapLocation.getStreet()
                            + "" + amapLocation.getStreetNum());
                    Toast.makeText(getApplicationContext(), buffer.toString(), Toast.LENGTH_LONG).show();
                    isFirstLoc = false;
                }
            } else {
                //显示错误信息ErrCode是错误码，errInfo是错误信息，详见错误码表。
                Log.e("AmapError", "location Error, ErrCode:"
                        + amapLocation.getErrorCode() + ", errInfo:"
                        + amapLocation.getErrorInfo());
                Toast.makeText(getApplicationContext(), "定位失败", Toast.LENGTH_LONG).show();
            }
        }
    }

    public void onPopupMenuClick(View v) {
        // 创建PopupMenu对象
        popup = new PopupMenu(this, v);
        // 将R.menu.menu_main菜单资源加载到popup菜单中
        getMenuInflater().inflate(R.menu.menu_main, popup.getMenu());
        // 为popup菜单的菜单项单击事件绑定事件监听器
        popup.setOnMenuItemClickListener(
                new PopupMenu.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        if (item.getItemId() == R.id.loc_map) {  // 开启定位地图模式
                            Intent intent1 = new Intent(mapActivity.this, mapActivity.class);
                            startActivity(intent1);
                        }
                        return true;
                    }
                });
        popup.show();
    }

    // 激活定位
    @Override
    public void activate(OnLocationChangedListener onLocationChangedListener) {
        mListener = onLocationChangedListener;
    }

    // 停止定位
    @Override
    public void deactivate() {
        mListener = null;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        //在activity执行onDestroy时执行mapView.onDestroy()，销毁地图
        mapView.onDestroy();
        mLocationClient.onDestroy();
    }

    @Override
    protected void onResume() {
        super.onResume();
        //在activity执行onResume时执行mapView.onResume ()，重新绘制加载地图
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        //在activity执行onPause时执行mapView.onPause ()，暂停地图的绘制
        mapView.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();

        //结束时停止更新位置
        mLocationClient.stopLocation();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        //在activity执行onSaveInstanceState时执行mMapView.onSaveInstanceState (outState)，保存地图当前的状态
        mapView.onSaveInstanceState(outState);
    }


    //用于检测是否连按两下
    private long time = 0;

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if ((System.currentTimeMillis() - time > 1000)) {
                Toast.makeText(this, "再按一次返回主界面", Toast.LENGTH_SHORT).show();
                time = System.currentTimeMillis();
            } else {
                finish();
            }
            return true;
        } else {
            return super.onKeyDown(keyCode, event);
        }
    }
}