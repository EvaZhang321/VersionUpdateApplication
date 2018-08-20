package com.jingbanyun.versionupdateapplication

import android.Manifest
import android.app.ProgressDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Message
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.util.Log
import kotlinx.android.synthetic.main.activity_main.*
import org.jetbrains.anko.alert
import org.jetbrains.anko.toast

class MainActivity : AppCompatActivity() {

    private var isBindService: Boolean = false //service为绑定解绑报错，自己存储状态
    var iService: Iservice? = null
    private var downloadingDialog :ProgressDialog ? = null
    private var mustUpdate = false

    companion object {
        const val WRITE_EXTERNAL_STORAGE_REQUEST_CODE = 105
        const val MSG_PROGRESS = 106
    }

    private val handler = object : Handler() {
        override fun handleMessage(msg: Message?) {
            when (msg?.what) {
                MSG_PROGRESS -> startUpdateProgress()
            }
        }
    }

    private val conn = object : ServiceConnection {

        /**
         * servcie连接的时
         */
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            isBindService = true
            iService = service as Iservice
            //更新进度
            startUpdateProgress()
        }

        /**
         * 意外断开连接时，
         */
        override fun onServiceDisconnected(name: ComponentName) {
            isBindService = false
        }
    }

    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tv_update.setOnClickListener {
            mustUpdate = false
            checkPermission()
        }

        tv_mustUpdate.setOnClickListener {
            mustUpdate = true
            checkPermission()
        }
    }

  private fun  checkPermission(){
      //判断下有没有读写权限
      if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
          //没权限，则申请权限
          if (ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
              //需要弹窗解释 解释为什么需要权限
              alert("下载最新安装包需要访问文件路径权限", "提示") {
                  positiveButton("确定") { myRequestPermission() }
                  negativeButton("取消") { }
              }.show()
          } else {
              //不需要弹窗解释 第一次系统提示了或用户勾选了不再提示
              myRequestPermission()
          }
      } else {
          //有权限 执行操作
          startDownloadService()
      }
  }

    //申请权限
    private fun myRequestPermission() {
        ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                WRITE_EXTERNAL_STORAGE_REQUEST_CODE)
    }

    //申请权限结果
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //权限已申请
                startDownloadService()
            } else {
                toast("访问文件路径被拒绝，无法安装")
            }
        }
    }

    // 通过绑定服务的方式下载新版本app
    private fun startDownloadService() {
        val downloadUrl = "http://www.jingbanyun.com/Public/apk/JingBanYun2_9_0.apk"
        val intent = Intent(this, APKDownLoadService::class.java)
        intent.putExtra(APKDownLoadService.BUNDLE_KEY_DOWNLOAD_URL, downloadUrl)
        //该项目必须先绑定
        bindService(intent, conn, Context.BIND_AUTO_CREATE)
        //再开启
        //这样子，才能调用service中的方法，且不受开启者生命周期控制（就是activity关闭，service仍然运行）
        startService(intent)

        //如果不是强制更新，就进入主页面
        if (mustUpdate) {
            //显示下载dialog
            showDownloadingDialog()
        } else {
           //前台不作限制，在后台下载apk
        }
    }

    private fun showDownloadingDialog() {
        downloadingDialog = ProgressDialog(this)
        // 设置对话框参数
        downloadingDialog!!.setTitle("版本升级")
        downloadingDialog!!.setMessage("正在下载安装包，请稍后")
        downloadingDialog!!.setCancelable(false)
        // 设置进度条参数
        downloadingDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        downloadingDialog!!.max = 100
        downloadingDialog!!.incrementProgressBy(10)
        downloadingDialog!!.isIndeterminate = false // 填false表示是明确显示进度的 填true表示不是明确显示进度的
        downloadingDialog!!.show()
    }

    /**
     * 开始更新进度
     */
    private fun startUpdateProgress() {
        //获取当前进度
        val process: Int = iService?.getProgress() ?: 0
        Log.e("更新进度:" , process.toString())
        //更新进度更新
        updateProgress(process)
        //定时更新进度
        when (process) {
            -1 -> {
                //更新失败
                this@MainActivity.finish()
            }
            100 -> {
                //更新完成
                this@MainActivity.finish()
            }
            else -> //更新进度
                handler.sendEmptyMessage(MSG_PROGRESS)
        }
    }

    /**
     * 根据当前进度数据更新界面
     */
    private fun updateProgress(process: Int) {
        //更新进度条
        downloadingDialog?.progress = process
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isBindService)
        //手动解绑
        unbindService(conn)

        //清空handler发送的所有消息 避免handler泄露
        handler.removeCallbacksAndMessages(null)

        downloadingDialog?.dismiss()
    }
}
