# TangyP2PPlugin

This plugin implements the Android [Nearby Conections](https://developers.google.com/nearby/connections/overview) API.

The following instructions include details very specific to [Tangerine](https://github.com/Tangerine-Community/Tangerine),
 the project in which this project is being deployed.

## Usage:

```

  async getTangyP2PPermissions() {
    if (this.window.isCordovaApp) {
      window['TangyP2PPlugin'].getPermission(null, function(message) {
        console.log('Message from getTangyP2PPermissions: ' + message);
      }, function(err) {
        console.log('TangyP2P error:: ' + err);
      });
    }
  }

  async startAdvertising() {
    if (this.window.isCordovaApp) {
      window['TangyP2PPlugin'].startAdvertising(null, function(message) {
        console.log('Message: ' + message);
        document.querySelector('#p2p-results').innerHTML += message + '<br/>';
      }, function(err) {
        console.log('TangyP2P error:: ' + err);
      });
    }
  }

  async startDiscovery() {
    if (this.window.isCordovaApp) {
      window['TangyP2PPlugin'].startDiscovery(null, function(message) {
        console.log('Message: ' + message);
        document.querySelector('#p2p-results').innerHTML += message + '<br/>';
      }, function(err) {
        console.log('TangyP2P error:: ' + err);
      });
    }
  }

  async transferData() {
    if (this.window.isCordovaApp) {
      window['TangyP2PPlugin'].transferData(null, function(message) {
        console.log('Message: ' + message);
        document.querySelector('#p2p-results').innerHTML += message + '<br/>';
      }, function(err) {
        console.log('TangyP2P error:: ' + err);
      });
    }
  }

```

## Development

Clone [TangyP2PPlugin-Android](https://github.com/Tangerine-Community/TangyP2PPlugin-Android) and do your Android development in it - it is a proper Android project (Java syntax highlighting).
When you do a build it has a target that copies TangyP2PPlugin.java (only that one file) source into TangyP2PPlugin/arc/android

Updating the plugin in Tangerine:

```
cd /tangerine/client/builds/apk
mkdir /tangerine/client/builds/apk/platforms/android/app/src/main/assets/www
cordova plugins rm tangy-p2p-plugin
cordova plugin add /tangerine/client/TangyP2PPlugin
cordova clean
cordova build
release-apk group-7389960b-d951-480a-98b1-5fa1a8f66893 /tangerine/client/content/groups/group-7389960b-d951-480a-98b1-5fa1a8f66893 qa http localhost 2>&1 | tee -a /apk.log
```

Here is the code for releasing a PWA:
```
release-pwa group-7389960b-d951-480a-98b1-5fa1a8f66893 /tangerine/client/content/groups/group-7389960b-d951-480a-98b1-5fa1a8f66893 qa
```

If you make changes to the Tangerine code, run the following code to make the new code available without having to re-run develop.sh:

```
cd /tangerine/client && \
rm -rf builds/apk/www/shell && \
rm -rf builds/pwa/release-uuid/app && \
cp -r dev builds/apk/www/shell && \
cp -r pwa-tools/updater-app/build/default builds/pwa && \
cp -r dev builds/pwa/release-uuid/app
```

