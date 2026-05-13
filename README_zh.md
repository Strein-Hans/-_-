# Android SDK

本文介绍了如何使用硅基数字人服务提供的DUIX Android SDK，包括安装、关键接口及代码示例。

## 添加依赖

引入 aar 包: duix_cloud_sdk_release_${version}.aar  
app 目录新建 libs 目录,放入 aar 包.
在 build.gradle 中增加配置如下:

```gradle
dependencies {
    implementation fileTree(include: ['*.jar', '*.aar'], dir: 'libs')

    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0'
    implementation "org.webrtc:google-webrtc:1.0.32006"
    implementation 'com.auth0:java-jwt:3.18.1'

}
```

## 添加权限

在```AndroidManifest.xml```文件中添加如下权限:


```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />

<!--  可选： 当会话支持多模态时，需要该权限打开摄像头  -->
<uses-permission android:name="android.permission.CAMERA" />
```

## Proguard混淆配置

在项目的proguard-rules.pro文件中添加如下配置:

```
-keep class org.webrtc.**{ *; }
-keep class org.eclipse.paho.client.** { *; }
-keep class org.eclipse.paho.uri.** { *; }
```


## 快速启动

在Activity中集成数字人模块代码, 如需使用麦克风，请确保启动前已经动态获取麦克风权限。

```kotlin
class DisplayActivity : BaseActivity() {

    private var eglBaseContext = EglBase.create().eglBaseContext
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // ...

        binding.render.init(eglBaseContext, null)
        binding.render.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.render.setMirror(false)
        binding.render.setEnableHardwareScaler(false /* enabled */)

        binding.renderCamera.init(eglBaseContext, null)
        binding.renderCamera.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        binding.renderCamera.setMirror(false)
        binding.renderCamera.setEnableHardwareScaler(false /* enabled */)

        VirtualFactory.init("your appId", "your appSecret")

        player = VirtualFactory.getPlayer(mContext, eglBaseContext)
        player?.addCallback(object : Player.Callback {
            override fun onShow() {
                Log.d(TAG, "onShow")
            }

            override fun onReady() {
                Log.d(TAG, "onReady")
            }

            @SuppressLint("SetTextI18n")
            override fun onError(msgType: Int, msgSubType: Int, msg: String?) {
                runOnUiThread {
                    binding.tvMessage.visibility = View.VISIBLE
                    binding.tvMessage.text = "Session error: msgType: $msgType msgSubType: $msgSubType msg: $msg"
                    Log.e(
                        TAG,
                        "Session error: msgType: $msgType msgSubType: $msgSubType msg: $msg"
                    )
                    Toast.makeText(
                        mContext,
                        "Session error: msgType: $msgType msgSubType: $msgSubType msg: $msg",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onAsrResult(text: String?, sentenceEnd: Boolean) {
                Log.e(TAG, "onAsrResult: $text, sentenceEnd: $sentenceEnd")
                text?.let { showAsrResult(it, sentenceEnd) }

            }

            override fun onVideoTrack(
                track: VideoTrack,
            ) {
                runOnUiThread {
                    track.addSink(binding.render)
                }
            }

            override fun onCameraTrack(track: VideoTrack) {
                runOnUiThread {
                    track.addSink(localRenderer)
                }
            }

            override fun onSessionInfo(duixBean: DUIXBean?) {
                super.onSessionInfo(duixBean)
                runOnUiThread {
                    
                }
            }
        })
        player?.connect("your conversation id")
    }

    override fun onDestroy() {
        super.onDestroy()
        // Release resources 
        player?.release()
        binding.render.release()
        localRenderer?.release()
    }
}
```

***现在，您可以使用麦克风和数字人进行对话了。***


## 关键接口

### 1. 初始化

使用平台账户中提供的appId和appSecret初始化模块并获取Play对象
```
VirtualFactory.init("your appId", "your appSecret")
player = VirtualFactory.getPlayer(mContext, eglBaseContext)
```


### 2. 连接数字人

使用平台账号提供的conversationId连接数字人

```
    player?.addCallback(callback)
    player?.connect("your conversation id")
```

其中callback包含如下回调

#### ```onShow``` 数字人可以展示了
```
void onShow();
```

#### ```onReady``` 数字人准备完毕
```
void onShow();
```

#### ```onError``` 连接数字人发生异常

```
void onError(int msgType, int msgSubType, String msg);
```

返回参数说明:

| 参数名            | 类型     | 说明      |
|----------------|--------|---------|
| msgType        | int    | 错误类型    |
| msgSubType     | int    | 子错误类型   |
| msg            | String | 异常消息    |

其中msgType的取值:

| 取值     | 说明       |     | 
|--------|----------|-----|
| 1000   | 授权异常     |     |   
| 1001   | 创建会话异常   |     |   
| 1002   | 获取素材异常   |     | 
| 1010   | IM创建连接失败 |     |
| 1011   | 渲染服务返回异常 |     |
| 1020   | RTC 状态异常 |     |
| 1030   | 渲染服务主动关闭 |     | 
| 1040   | IM连接丢失   |     | 
| 1050   | RTC连接丢失  |     | 


#### ```onVideoTrack``` RTC媒体通道创建成功

在该回调中展示数字人控件绑定到track中

```
void onVideoTrack(VideoTrack track);
```

| 参数名            | 类型              | 说明         |
|----------------|-----------------|------------|
| track          | VideoTrack      | 视频媒体通道     |


#### ```onCameraTrack``` Camera视频采集通道创建成功

在该回调中展示摄像头媒体控件绑定到track中

```
void onCameraTrack(VideoTrack track);
```

| 参数名            | 类型              | 说明         |
|----------------|-----------------|------------|
| track          | VideoTrack      | 摄像头媒体通道     |


#### ```onAudioSamples``` 本地音频采集数据回调

可在该回调中实现音频图像化等操作

```
default void onAudioSamples(int audioFormat, int channelCount, int sampleRat, byte[] data){}
```

#### ```onTtsSpeakStart``` 数字人开始播放TTS合成音频

```
default void onTtsSpeakStart(){}
```

#### ```onTtsSpeakText``` 数字人播放TTS的文本内容

```
default void onTtsSpeakText(String text){}
```

#### ```onTtsSpeakStop``` TTS文本播放完成

```
default void onTtsSpeakStop(){}
```

#### ```onSpeakStart``` 数字人开始播报

```
default void onSpeakStart(){}
```

#### ```onSpeakText``` 数字人播报的文本内容

```
default void onSpeakText(String text){}
```

#### ```onSpeakStop``` 数字人播报完成

```
default void onSpeakStop(){}
```

#### ```onAsrResult``` ASR识别内容回调

```
default void onAsrResult(String text, boolean sentenceEnd){}
```


### 3. 音频URL驱动数字人说话

使用16k采样率16bit单通道的WAV音频地址驱动数字人说话

```kotlin
player?.speakWithWav(wavUrl, true)
```

| 参数名                   | 类型      | 说明         |
|-----------------------|---------|------------|
| wavUrl                | String  | WAV音频的网络地址 |
| interrupt             | boolean | 是否打断当前说话状态 |


### 4. 文本驱动数字人说话

输入想要数字人想说的话，数字人会根据会话配置的音色说出对应的内容。

```kotlin
player?.speakWithTxt(text, true)
```

| 参数名                   | 类型      | 说明          |
|-----------------------|---------|-------------|
| text                  | String  | 数字人的要说的文本内容 |
| interrupt             | boolean | 是否打断当前说话状态  |


### 5. 数字人问答

让数字人来解答您的疑问。

```kotlin
player?.speakWithQuestion(text, true)
```

| 参数名                   | 类型      | 说明         |
|-----------------------|---------|------------|
| text                  | String  | 向数字人表达您的问题 |
| interrupt             | boolean | 是否打断当前说话状态 |

### 6. 打断数字人说话

让数字人不要再说了

```kotlin
player?.stopAudio()
```

### 7. 摄像头翻转

会话中翻转前后置摄象头（当开启多模态会话时可用）

```kotlin
player?.backFacing(isBackFacing)
```

### 8. 关闭摄像头

会话中关闭摄象头（当开启多模态会话时可用）

```kotlin
player?.closeCamera()
```


### 9. 打开摄像头

会话中打开摄象头（当开启多模态会话时可用）

```kotlin
player?.openCamera()
```


## 注意事项

1. RTC模块中需要使用麦克风，需要动态申请权限。
2. 使用音频驱动数字人需要传递的wav文件格式要求为： 16000hz ，单声道， 16 bit。