# Android SDK

## Add Dependencies

Import AAR package: duix_cloud_sdk_release_${version}.aar
Create a new ‘libs’ directory in the app directory and place the AAR package inside. Add the following configuration in build.gradle:

```gradle
dependencies {
    implementation fileTree(include: ['*.jar', '*.aar'], dir: 'libs')

    implementation 'org.eclipse.paho:org.eclipse.paho.client.mqttv3:1.2.5'
    implementation 'androidx.localbroadcastmanager:localbroadcastmanager:1.0.0'
    implementation "org.webrtc:google-webrtc:1.0.32006"
    implementation 'com.auth0:java-jwt:3.18.1'

}
```

## Permission Configuration

```AndroidManifest.xml```:

```xml
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.CAMERA" />
```

## Proguard Configuration

Add the following configuration to your project’s proguard-rules.pro file::

```
-keep class org.webrtc.**{ *; }
-keep class org.eclipse.paho.client.** { *; }
-keep class org.eclipse.paho.uri.** { *; }
```


## Quick Start

When integrating the digital human module code in an Activity, please ensure that you have dynamically obtained microphone permission before starting if you need to use the microphone.

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

        VirtualFactory.init("your appId", "your appSecret")

        player = VirtualFactory.getPlayer(mContext, eglBaseContext)
        player?.addCallback(object : Player.Callback {
            override fun onShow() {
                // can show digital human
            }

            override fun onReady() {
                // digital human ready
            }

            @SuppressLint("SetTextI18n")
            override fun onError(msgType: Int, msgSubType: Int, msg: String?) {
                // Log.e(TAG, "connect error")
            }

            override fun onAsrResult(text: String?, sentenceEnd: Boolean) {

            }

            override fun onVideoTrack(
                track: VideoTrack,
            ) {
                runOnUiThread {
                    // Add source for digital human view
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
    }
}
```

***Now!***
You can now have a conversation with the digital human using the microphone.


## Key Interfaces

### 1. Initialization

Initialize the module with the appId and appSecret provided in your platform account and get the Play object
```
VirtualFactory.init("your appId", "your appSecret")
player = VirtualFactory.getPlayer(mContext, eglBaseContext)
```


### 2. Connect to Digital Human

Connect to the digital human using the conversationId provided by your platform account

```
    player?.addCallback(callback)
    player?.connect("your conversation id")
```

The callback contains the following callbacks

#### ```onShow```  Digital human is ready to display
```
void onShow();
```

#### ```onReady``` Digital human is ready
```
void onReady();
```

#### ```onError``` Exception occurred when connecting to digital human

```
void onError(int msgType, int msgSubType, String msg);
```

Parameter description:

| Parameter Name   | Type     | Description            |
|------------------|----------|------------------------|
| msgType          | int      | Error type             |
| msgSubType       | int      | Sub-error type         |
| msg              | String   | Exception message      |

Values of msgType:

| Value    | Description                           |     | 
|----------|---------------------------------------|-----|
| 1000     | Authorization exception	              |     |   
| 1001     | Session creation exception	           |     |   
| 1002     | Resource retrieval exception	         |     | 
| 1010     | IM connection creation failed	        |     |
| 1011     | Rendering service returned exception	 |     |
| 1020     | RTC status exception	                 |     |
| 1030     | Rendering service actively closed	    |     | 
| 1040     | IM connection lost                    |     | 
| 1050     | 	RTC connection lost	                 |     | 


#### ```onVideoTrack``` RTC media channel created successfully

Bind the digital human widget to the track in this callback

```
void onVideoTrack(VideoTrack track);
```

| Parameter Name   | Type              | Description               |
|------------------|-------------------|---------------------------|
| track            | VideoTrack        | Video media channel       |


#### ```onCameraTrack``` Camera media channel created successfully

Bind the camera widget to the track in this callback

```
void onCameraTrack(VideoTrack track);
```

| Parameter Name   | Type              | Description          |
|------------------|-------------------|----------------------|
| track            | VideoTrack        | Camera media channel |


#### ```onAudioSamples``` Local audio sampling data callback

You can implement audio visualization and other operations in this callback

```
default void onAudioSamples(int audioFormat, int channelCount, int sampleRat, byte[] data){}
```

#### ```onTtsSpeakStart``` Digital human starts playing TTS synthesized audio

```
default void onTtsSpeakStart(){}
```

#### ```onTtsSpeakText``` Text content of TTS played by digital human

```
default void onTtsSpeakText(String text){}
```

#### ```onTtsSpeakStop``` TTS text playback completed

```
default void onTtsSpeakStop(){}
```

#### ```onSpeakStart``` Digital human starts speaking

```
default void onSpeakStart(){}
```

#### ```onSpeakText``` Text content spoken by digital human

```
default void onSpeakText(String text){}
```

#### ```onSpeakStop``` Digital human finished speaking

```
default void onSpeakStop(){}
```

#### ```onAsrResult``` ASR recognition content callback

```
default void onAsrResult(String text, boolean sentenceEnd){}
```


### 3. Drive Digital Human to Speak with Audio URL

Use a WAV audio URL with 16kHz sampling rate, 16bit, mono channel to drive the digital human to speak

```kotlin
player?.speakWithWav(wavUrl, true)
```

| Parameter Name          | Type      | Description                                         |
|-------------------------|-----------|-----------------------------------------------------|
| wavUrl                  | String    | Network address of WAV audio                        |
| interrupt               | boolean   | Whether to interrupt the current speaking state     |


### 4. Drive Digital Human to Speak with Text

Input the text that you want the digital human to say, and the digital human will say the corresponding content according to the voice configured in the session.

```kotlin
player?.speakWithTxt(text, true)
```

| Parameter Name          | Type      | Description   |
|-------------------------|-----------|---------------|
| text                    | String    | 数字人的要说的文本内容   |
| interrupt               | boolean   | 是否打断当前说话状态    |


### 5. Digital Human Q&A

Let the digital human answer your questions.

```kotlin
player?.speakWithQuestion(text, true)
```

| Parameter Name          | Type      | Description                                       |
|-------------------------|-----------|---------------------------------------------------|
| text                    | String    | Express your question to the digital human        |
| interrupt               | boolean   | Whether to interrupt the current speaking state   |

### 6. Interrupt Digital Human Speaking

Make the digital human stop talking

```kotlin
player?.stopAudio()
```

### 7. Switch camera

```kotlin
player?.backFacing(isBackFacing)
```

### 8. Close camera


```kotlin
player?.closeCamera()
```


### 9. Open camera

```kotlin
player?.openCamera()
```


## 注意事项

1. The RTC module requires microphone access, so you need to request permission dynamically.
2. The WAV file format required for driving the digital human with audio must be: 16000Hz, mono, 16 bit.