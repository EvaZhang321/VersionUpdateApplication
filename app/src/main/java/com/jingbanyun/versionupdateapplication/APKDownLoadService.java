package com.jingbanyun.versionupdateapplication;

import android.app.DownloadManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

public class APKDownLoadService extends Service {

    private String downloadUrl;
    //下载apk网络地址
    public static final String BUNDLE_KEY_DOWNLOAD_URL = "download_url";
    //安卓系统下载类
    private DownloadManager downloadManager;
    //下载任务ID
    private long downloadId;
    //"content://downloads/my_downloads" 监听的下载文件路径，必须这样写不可更改
    private Uri CONTENT_URI = Uri.parse("content://downloads/my_downloads");
    private DownloadChangeObserver downloadObserver;
    private int progress;
    public Binder binder;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder=new DownloadBinder();
    }

    @Override
    public void onDestroy() {
        // 注销下载广播
        if (receiver != null)
            unregisterReceiver(receiver);
        if (downloadObserver != null)
        getContentResolver().unregisterContentObserver(downloadObserver);
        super.onDestroy();
    }

    /**
     * 开始服务
     *
     * @param intent
     * @param flags
     * @param startId
     * @return
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //获取下载地址
        downloadUrl = intent.getStringExtra(BUNDLE_KEY_DOWNLOAD_URL);

        //先判断本地是存在文件
        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + "jingbanyun.apk";
        File file = new File(path);
        if(file.exists()){
            deleteFileWithPath(path);
        }

        try{
            //初始化下载器
            initDownManager();
            //注册下载完成广播
            registerBroadcast();
            //采用内容观察者模式实现进度
            registerObserver();
        }catch (Exception e){
            //从应用市场更新app
            updateFromAppStore();
        }

        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 初始化下载器
     **/
    private void initDownManager() {
        //1.创建下载管理器
        downloadManager = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);

        //2.创建下载任务
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(downloadUrl));
        //设置漫游状态下是否可以下载
        request.setAllowedOverRoaming(true);
        //设置用于下载时的网络状态
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
        //在通知栏中显示，默认就是显示的
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        request.setTitle("京版云");
        request.setDescription("最新版本正在下载中");
        //如果我们希望下载的文件可以被系统的Downloads应用扫描到并管理，
        //我们需要调用Request对象的setVisibleInDownloadsUi方法，传递参数true.
        request.setVisibleInDownloadsUi(true);
        //设置文件保存路径
        request.setDestinationInExternalPublicDir(Environment.getExternalStorageDirectory()
                .getAbsolutePath(), "jingbanyun.apk");

        //3.将下载请求放入队列， return下载任务的ID
        downloadId = downloadManager.enqueue(request);
    }

    private void registerObserver() {
        downloadObserver = new DownloadChangeObserver(null);
        getContentResolver().registerContentObserver(CONTENT_URI, true, downloadObserver);
    }

    /**
     * 注册广播
     */
    private void registerBroadcast() {
        /**注册service 广播 1.任务完成时*/
        //注册广播接收者，监听下载状态
        registerReceiver(receiver, new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
    }

    //广播监听下载的各个状态
    private BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent!=null){
                switch (intent.getAction()) {
                    case DownloadManager.ACTION_DOWNLOAD_COMPLETE:
                        long downId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                        if (downloadId == downId && downId != -1 && downloadManager != null) {
                            //获取下载文件的Uri
                            Uri downloadFileUri = downloadManager.getUriForDownloadedFile(downloadId);
                            if (downloadFileUri != null) {
                                //自动安装apk
                                installAPK(downloadFileUri);
                            } else {
                                Toast.makeText(context, "下载失败,准备从应用市场更新", Toast.LENGTH_SHORT).show();
                                updateFromAppStore();
                            }
                            //停止服务并关闭广播
                            APKDownLoadService.this.stopSelf();
                        }
                        break;
                }
            }
        }
    };

    //下载到本地后执行安装
    private void installAPK(Uri downloadFileUri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.setDataAndType(downloadFileUri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    //从应用市场更新app
    private void updateFromAppStore() {
        try {
            Uri uri = Uri.parse("market://details?id=" + getPackageName());
            Intent intent0 = new Intent(Intent.ACTION_VIEW, uri);
            intent0.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent0);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean deleteFileWithPath(String filePath) {
        SecurityManager checker = new SecurityManager();
        File f = new File(filePath);
        checker.checkDelete(filePath);
        if (f.isFile()) {
            f.delete();
            return true;
        }
        return false;
    }

    //观察者，用于显示下载进度
    class DownloadChangeObserver extends ContentObserver {

        public DownloadChangeObserver(Handler handler) {
            super(handler);
        }

        /**
         * 当所监听的Uri发生改变时，就会回调此方法
         *
         * @param selfChange 此值意义不大, 一般情况下该回调值false
         */
        @Override
        public void onChange(boolean selfChange) {
            DownloadManager.Query query = new DownloadManager.Query();
            query.setFilterById(downloadId);
            final Cursor cursor = downloadManager.query(query);
            if (cursor != null && cursor.moveToFirst()) {
                //下载状态
                final int statusColumn = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS);
                int downloadStatus = cursor.getInt(statusColumn);
                switch (downloadStatus){
                    case DownloadManager.STATUS_RUNNING:
                        Log.e("下载进度：", progress +",下载状态："+downloadStatus);
                        //下载文件的总大小
                        final int totalColumn = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES);
                        //已经下载文件大小
                        final int currentColumn = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR);

                        int totalSize = cursor.getInt(totalColumn);
                        int currentSize = cursor.getInt(currentColumn);

                        float percent = (float) currentSize / (float) totalSize;
                        progress = Math.round(percent * 100);
                        break;
                    case DownloadManager.STATUS_SUCCESSFUL:
                        progress = 100;
                        break;
                    case DownloadManager.STATUS_FAILED:
                        updateFromAppStore();
                        progress = -1;
                        break;
                }
                cursor.close();
            }
        }
    }

    //service与activity通信的桥梁-->Binder
    public class DownloadBinder extends Binder implements Iservice{
        /**
         * 获取当前下载进度
         */
       public int getProgress(){
            return  progress;
        }
    }
}
