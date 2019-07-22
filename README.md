# TangyP2PPlugin

Inspired by [drulabs/LocalDash](https://github.com/drulabs/LocalDash) and [Create P2P connections with Wi-Fi Direct]
(https://developer.android.com/training/connect-devices-wirelessly/wifi-direct#java)

Usage:

```
  <script>
    if (this.window.isCordovaApp) {
      document.addEventListener('deviceready', () => {
        console.log("deviceready! Gonna run TangyP2PPlugin init now.")
        TangyP2PPlugin.init();
      }, false);
    } else {
      console.log("not a cordova app.")
    }
  </script>
```

