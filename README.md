# AndroidUnpinning
  一个Xposed过抓包检测的类，可以过大部分java层ssl证书检测。
## 功能：
  initZygote函数解决安卓7以上校验系统根证书的问题。
  doing函数解决检测服务端证书的问题，需要将自己证书cert.cer推到"/sdcard/"目录下。
  
