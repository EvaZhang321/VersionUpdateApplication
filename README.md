# VersionUpdateApplication
App new version update Demo 
APP 版本更新升级 (强制更新和非强制更新)

一个小功能，包含了Android四大组件 :)

1.MainActivity:  通过bindService和startService进行更新，并获取通过binder接口获取下载进度
  Service和Activity通信--->Binder；
  先bindService再startService,既可以调用binder方法，又可以不受开启者生命周期控制，独立在后台运行

2.APKDownLoadService: 通过DownloadManager安卓系统下载类进行下载
  DownloadManager下载完成后会发出广播，注册广播接受者，监听到下载完成，开始安装apk;
  通过内容观察者ContentObserver来监听DownloadManager下载进度，监听的下载文件路径的URI为"content://downloads/my_downloads"
  
![image](https://github.com/EvaZhang321/VersionUpdateApplication/blob/master/img/20180820105827.png)
