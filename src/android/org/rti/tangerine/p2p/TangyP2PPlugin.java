/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at

         http://www.apache.org/licenses/LICENSE-2.0

       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */

package org.rti.tangerine.p2p;

import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import android.support.v4.content.LocalBroadcastManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.pm.PackageManager;
import android.Manifest;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.os.Bundle;
import android.view.View;

import org.apache.cordova.*;
import org.drulabs.localdash.db.DBAdapter;
import org.drulabs.localdash.db.DBHelper;
import org.drulabs.localdash.model.DeviceDTO;
import org.drulabs.localdash.transfer.ConnectionListener;
import org.drulabs.localdash.transfer.DataHandler;
import org.drulabs.localdash.transfer.DataSender;
import org.drulabs.localdash.transfer.TransferConstants;
import org.drulabs.localdash.utils.ConnectionUtils;
import org.drulabs.localdash.utils.Utility;
import org.drulabs.localdash.wifidirect.WiFiDirectBroadcastReceiver;

import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

import android.util.Log;

public class TangyP2PPlugin extends CordovaPlugin implements WifiP2pManager.PeerListListener, WifiP2pManager.ConnectionInfoListener
{
    public static final String FIRST_DEVICE_CONNECTED = "first_device_connected";
    public static final String KEY_FIRST_DEVICE_IP = "first_device_ip";

    public static final String ACTION_CHAT_RECEIVED = "org.drulabs.localdash.chatreceived";
    public static final String KEY_CHAT_DATA = "chat_data_key";

    private static final String WRITE_PERMISSION = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    private static final int WRITE_PERM_REQ_CODE = 19;

    private static final String TAG = "TangyP2PPlugin";

    private ConnectionListener connListener;
    private int myPort;

    private boolean isConnectionListenerRunning = false;

    WifiP2pManager wifiP2pManager;
    WifiP2pManager.Channel wifip2pChannel;
    WiFiDirectBroadcastReceiver wiFiDirectBroadcastReceiver;
    private boolean isWDConnected = false;
    private boolean isWifiP2pEnabled = false;
    private String pluginMessage;

    public static final int SEARCH_REQ_CODE = 0;

    private CallbackContext cbContext;

    public static final String PERMISSION_TO_WIFI = Manifest.permission.CHANGE_WIFI_STATE;
//    public static final int PERMISSION_DENIED_ERROR = 20;
    private static final String PERMISSION_DENIED_ERROR = "Permission denied";
    String [] permissions = { PERMISSION_TO_WIFI };

    private PluginResult pluginResult;


    /**
     * Sets the context of the Command.
     *
     * @param cordova The context of the main Activity.
     * @param webView The CordovaWebView Cordova is running in.
     */
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        this.cbContext = null;
        Context context = cordova.getActivity().getApplicationContext();
        setupPeerDiscovery(context);
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        LOG.d(TAG, "We are entering execute of TangyP2PPlugin");
        cbContext = callbackContext;
        if(action.equals("getPermission"))
        {
            LOG.d(TAG, "Checking permissions.");
            if(hasPermisssion())
            {
                PluginResult r = new PluginResult(PluginResult.Status.OK);
                cbContext.sendPluginResult(r);
                return true;
            }
            else {
                Log.i(TAG, "Requesting permissions.");
                PermissionHelper.requestPermissions(this, 0, permissions);
            }
            return true;
        }
        else if ("init".equals(action)) {
            //callbackContext.success();
            //return true;
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
//                    TangyP2PPlugin.init();
                        Log.i(TAG, "init");
                        Context context = cordova.getActivity().getApplicationContext();
                        String myIP = Utility.getWiFiIPAddress(context);
                        Log.i(TAG, "Saving myIP: " + myIP);
                        Utility.saveString(context, TransferConstants.KEY_MY_IP, myIP);

                        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
                        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

                        startRegistration(ConnectionUtils.getPort(context));

                        wifiP2pManager.discoverPeers(wifip2pChannel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "Peer discovery started");
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                // reasonCode 2: BUSY - Indicates that the operation failed because the framework is busy and unable to service the request
                                // reasonCode 0: ERROR - Indicates that the operation failed due to an internal error.
                                // reasonCode 1: P2P_UNSUPPORTED -  Indicates that the operation failed because p2p is unsupported on the device.
                                Log.e(TAG, "Peer discovery failure: " + reasonCode);
                            }
                        });

                        startConnectionListener(TransferConstants.INITIAL_DEFAULT_PORT);
                        pluginMessage = "Connection Listener Running: " + isConnectionListenerRunning;
                        Log.i(TAG, pluginMessage);
                        Log.i(TAG, "setupPeerDiscovery");
                        setupPeerDiscovery(context);
                        pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
                        pluginResult.setKeepCallback(true);
                        cbContext.sendPluginResult(pluginResult);
//                        callbackContext.success(pluginMessage); // Thread-safe.
                    }
                });
                return true;
            } else {
                String message = "Requesting permissions";
                Log.i(TAG, message);
                PermissionHelper.requestPermissions(this, 0, permissions);
                pluginResult = new PluginResult(PluginResult.Status.OK, message);
                pluginResult.setKeepCallback(true);
                cbContext.sendPluginResult(pluginResult);
//                cordova.requestPermissions(this, 0, permissions);
//                cordova.requestPermission(this, requestCode, PERMISSION_TO_WIFI);
            }
            return true;
        } else if ("startRegistration".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Log.i(TAG, "startRegistration");
                        Context context = cordova.getActivity().getApplicationContext();
                        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
                        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

                        startRegistration(ConnectionUtils.getPort(context));
                        pluginMessage = "startRegistration initiated. isConnectionListenerRunning: " + isConnectionListenerRunning;
                        Log.i(TAG, pluginMessage);
                        pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
                        pluginResult.setKeepCallback(true);
                        cbContext.sendPluginResult(pluginResult);
                    }
                });
                return true;
            } else {
                Log.i(TAG, "permission helper pleeeeeze");
                PermissionHelper.requestPermissions(this, 0, permissions);
//                cordova.requestPermissions(this, 0, permissions);
//                cordova.requestPermission(this, requestCode, PERMISSION_TO_WIFI);
            }
            return true;
        } else if ("discoverPeers".equals(action)) {
            if(hasPermisssion()) {
                Log.i(TAG, "We hasPermisssion");
                cordova.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
//                    TangyP2PPlugin.init();
                        Log.i(TAG, "discoverPeers");
                        Context context = cordova.getActivity().getApplicationContext();
                        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
                        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

                        wifiP2pManager.discoverPeers(wifip2pChannel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.i(TAG, "Peer discovery started");
                            }

                            @Override
                            public void onFailure(int reasonCode) {
                                // reasonCode 2: BUSY - Indicates that the operation failed because the framework is busy and unable to service the request
                                // reasonCode 0: ERROR - Indicates that the operation failed due to an internal error.
                                // reasonCode 1: P2P_UNSUPPORTED -  Indicates that the operation failed because p2p is unsupported on the device.
                                Log.e(TAG, "Peer discovery failure: " + reasonCode);
                            }
                        });

                        startConnectionListener(TransferConstants.INITIAL_DEFAULT_PORT);
                        pluginMessage = "discoverPeers; Connection Listener Running: " + isConnectionListenerRunning;
                        Log.i(TAG, pluginMessage);
//                        Log.i(TAG, "setupPeerDiscovery");
//                        setupPeerDiscovery(context);
                        pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
                        pluginResult.setKeepCallback(true);
                        cbContext.sendPluginResult(pluginResult);
//                        callbackContext.success(pluginMessage); // Thread-safe.
                    }
                });
                return true;
            } else {
                Log.i(TAG, "permission helper pleeeeeze");
                PermissionHelper.requestPermissions(this, 0, permissions);
//                cordova.requestPermissions(this, 0, permissions);
//                cordova.requestPermission(this, requestCode, PERMISSION_TO_WIFI);
            }
            return true;
        }
        return false;  // Returning false results in a "MethodNotFound" error.
    }

    @Override
    public void onResume(boolean multitasking) {
        super.onResume(multitasking);

        Context context = cordova.getActivity().getApplicationContext();
        setupPeerDiscovery(context);
    }

    private void setupPeerDiscovery(Context context) {
        IntentFilter localFilter = new IntentFilter();
        localFilter.addAction(DataHandler.DEVICE_LIST_CHANGED);
        localFilter.addAction(FIRST_DEVICE_CONNECTED);
        localFilter.addAction(DataHandler.CHAT_REQUEST_RECEIVED);
        localFilter.addAction(DataHandler.CHAT_RESPONSE_RECEIVED);
        LocalBroadcastManager.getInstance(context).registerReceiver(localDashReceiver,
                localFilter);

        IntentFilter wifip2pFilter = new IntentFilter();
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        wifip2pFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        // When an application needs to fetch the current list of peers, it can request the list of
        // peers with requestPeers from wiFiDirectBroadcastReceiver
        wiFiDirectBroadcastReceiver = new WiFiDirectBroadcastReceiver(wifiP2pManager,
                wifip2pChannel, this);
//        registerReceiver(wiFiDirectBroadcastReceiver, wifip2pFilter);
        context.registerReceiver(wiFiDirectBroadcastReceiver, wifip2pFilter);

        LocalBroadcastManager.getInstance(context).sendBroadcast(new Intent(DataHandler.DEVICE_LIST_CHANGED));
    }

    private BroadcastReceiver localDashReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case FIRST_DEVICE_CONNECTED:
//                    connListener.tearDown();
//                    int newPort = ConnectionUtils.getPort(LocalDashWiFiDirect.this);
//                    connListener = new ConnectionListener(LocalDashWiFiDirect.this,
//                            newPort);
//                    connListener.start();
//                    appController.stopConnectionListener();
//                    appController.startConnectionListener(ConnectionUtils.getPort(LocalDashWiFiDirect.this));
                    TangyP2PPlugin.this.restartConnectionListenerWith(ConnectionUtils.getPort(context));

                    String senderIP = intent.getStringExtra(KEY_FIRST_DEVICE_IP);
                    int port = DBAdapter.getInstance(context).getDevice
                            (senderIP).getPort();
                    DataSender.sendCurrentDeviceData(context, senderIP, port, true);
                    isWDConnected = true;
                    break;
                case DataHandler.DEVICE_LIST_CHANGED:
                    ArrayList<DeviceDTO> devices = DBAdapter.getInstance(context)
                            .getDeviceList();
                    int peerCount = (devices == null) ? 0 : devices.size();
                    if (peerCount > 0) {
//                        progressBarLocalDash.setVisibility(View.GONE);
//                        deviceListFragment = new PeerListFragment();
//                        Bundle args = new Bundle();
//                        args.putSerializable(PeerListFragment.ARG_DEVICE_LIST, devices);
//                        deviceListFragment.setArguments(args);
//
//                        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//                        ft.replace(R.id.deviceListHolder, deviceListFragment);
//                        ft.setTransition(FragmentTransaction.TRANSIT_NONE);
//                        ft.commit();
                        StringBuilder sb = new StringBuilder();
                        for (DeviceDTO d : devices)
                        {
                            String name = d.getDeviceName();
                            String ip = d.getIp();
                            int devicePort = d.getPort();
                            String player = d.getPlayerName();
                            String identifier = "name: " + name + " ip: " + ip + " port: " + devicePort + " player: " + player;

                            sb.append(identifier);
                            sb.append(System.lineSeparator());
                        }
                        String devicesString = sb.toString();
                        Log.i(TAG,"Devices: " + devicesString);
                    }
//                    setToolBarTitle(peerCount);
                    Log.i(TAG,"peerCount: " + peerCount);
                    break;
//                case DataHandler.CHAT_REQUEST_RECEIVED:
//                    DeviceDTO chatRequesterDevice = (DeviceDTO) intent.getSerializableExtra(DataHandler
//                            .KEY_CHAT_REQUEST);
//                    DialogUtils.getChatRequestDialog(TangyP2PPlugin.this,
//                            chatRequesterDevice).show();
//                    break;
//                case DataHandler.CHAT_RESPONSE_RECEIVED:
//                    boolean isChatRequestAccepted = intent.getBooleanExtra(DataHandler
//                            .KEY_IS_CHAT_REQUEST_ACCEPTED, false);
//                    if (!isChatRequestAccepted) {
//                        NotificationToast.showToast(TangyP2PPlugin.this, "Chat request " +
//                                "rejected");
//                    } else {
//                        DeviceDTO chatDevice = (DeviceDTO) intent.getSerializableExtra(DataHandler
//                                .KEY_CHAT_REQUEST);
//                        DialogUtils.openChatActivity(TangyP2PPlugin.this, chatDevice);
//                        NotificationToast.showToast(TangyP2PPlugin.this, chatDevice
//                                .getPlayerName() + "Accepted Chat request");
//                    }
//                    break;
                default:
                    break;
            }
        }
    };

    private void startRegistration(int port) {

        Context context = cordova.getActivity().getApplicationContext();

        //  Create a string map containing information about your service.
        String serviceName = "John Doe" + (int) (Math.random() * 1000);
        Map record = new HashMap();
        record.put("listenport", String.valueOf(port));
        record.put("buddyname", serviceName);
        record.put("available", "visible");

        Log.i(TAG,"startRegistration for : " + serviceName);

        // Service information.  Pass it an instance name, service type
        // _protocol._transportlayer , and the map containing
        // information other devices will want once they connect to this one.
        WifiP2pDnsSdServiceInfo serviceInfo =
                WifiP2pDnsSdServiceInfo.newInstance("_test", "_presence._tcp", record);

        // Add the local service, sending the service info, network channel,
        // and listener that will be used to indicate success or failure of
        // the request.
        wifiP2pManager = (WifiP2pManager) context.getSystemService(context.WIFI_P2P_SERVICE);
//        wifip2pChannel = wifiP2pManager.initialize(context, context.getMainLooper(), null);

        wifiP2pManager.addLocalService(wifip2pChannel, serviceInfo, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.i(TAG,"local service is now added as: " + serviceName);
            }

            @Override
            public void onFailure(int arg0) {
                // Command failed.  Check for P2P_UNSUPPORTED, ERROR, or BUSY
            }
        });
    }

    public void stopConnectionListener() {
        if (!isConnectionListenerRunning) {
            return;
        }
        if (connListener != null) {
            connListener.tearDown();
            connListener = null;
        }
        isConnectionListenerRunning = false;
    }

    public void startConnectionListener() {
        Context context = cordova.getActivity().getApplicationContext();
        if (isConnectionListenerRunning) {
            return;
        }
        if (connListener == null) {
            connListener = new ConnectionListener(context, myPort);
        }
        if (!connListener.isAlive()) {
            connListener.interrupt();
            connListener.tearDown();
            connListener = null;
        }
        connListener = new ConnectionListener(context, myPort);
        connListener.start();
        isConnectionListenerRunning = true;
    }

    public void startConnectionListener(int port) {
        myPort = port;
        startConnectionListener();
    }

    public void restartConnectionListenerWith(int port) {
        stopConnectionListener();
        startConnectionListener(port);
    }

    public boolean isConnListenerRunning() {
        return isConnectionListenerRunning;
    }

    public int getPort(){
        return myPort;
    }

    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {

        PluginResult result;

        ArrayList<DeviceDTO> deviceDTOs = new ArrayList<>();

        List<WifiP2pDevice> devices = (new ArrayList<>());
        devices.addAll(peerList.getDeviceList());
        String deviceNames = "";
        for (WifiP2pDevice device : devices) {
            DeviceDTO deviceDTO = new DeviceDTO();
            deviceDTO.setIp(device.deviceAddress);
            deviceDTO.setPlayerName(device.deviceName);
            deviceDTO.setDeviceName(new String());
            deviceDTO.setOsVersion(new String());
            deviceDTO.setPort(-1);
            deviceDTOs.add(deviceDTO);
            String identifier = " ip: " + device.deviceAddress + " device.deviceName: " + device.deviceName;
            pluginMessage = deviceNames + "<br>" + identifier;
            Log.i(TAG,"Peer found! : " + identifier);
        }

//        navigator.notification.alert(
//                deviceNames,  // message
//                alertDismissed,         // callback
//                'Peers Available',            // title
//                'Done'                  // buttonName
//        );

        if (devices.size() == 0) {
            Log.i(TAG,"No peers found...");
        } else {
//            result = new PluginResult(deviceNames);
//            Context context = cordova.getActivity().getApplicationContext();
//            context.sendPluginResult(result);
            Log.i(TAG,"Sending message to pluginResult");
            pluginResult = new PluginResult(PluginResult.Status.OK, pluginMessage);
            pluginResult.setKeepCallback(true);
            cbContext.sendPluginResult(pluginResult);
        }


//        progressBarLocalDash.setVisibility(View.GONE);
//        deviceListFragment = new PeerListFragment();
//        Bundle args = new Bundle();
//        args.putSerializable(PeerListFragment.ARG_DEVICE_LIST, deviceDTOs);
//        deviceListFragment.setArguments(args);
//
//        FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
//        ft.replace(R.id.deviceListHolder, deviceListFragment);
//        ft.setTransition(FragmentTransaction.TRANSIT_NONE);
//        ft.commit();
    }

    boolean isConnectionInfoSent = false;

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo wifiP2pInfo) {
        Context context = cordova.getActivity().getApplicationContext();
        if (wifiP2pInfo.groupFormed && !wifiP2pInfo.isGroupOwner && !isConnectionInfoSent) {

            isWDConnected = true;

//            connListener.tearDown();
//            connListener = new ConnectionListener(LocalDashWiFiDirect.this, ConnectionUtils.getPort
//                    (LocalDashWiFiDirect.this));
//            connListener.start();
//            appController.stopConnectionListener();
//            appController.startConnectionListener(ConnectionUtils.getPort(LocalDashWiFiDirect.this));
            this.restartConnectionListenerWith(ConnectionUtils.getPort(context));

            String groupOwnerAddress = wifiP2pInfo.groupOwnerAddress.getHostAddress();
            DataSender.sendCurrentDeviceDataWD(context, groupOwnerAddress, TransferConstants
                    .INITIAL_DEFAULT_PORT, true);
            isConnectionInfoSent = true;
        }
    }

    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        PluginResult result;
        LOG.d(TAG, "onRequestPermissionResult: requestCode: " + requestCode);
        //This is important if we're using Cordova without using Cordova, but we have the geolocation plugin installed
        if(cbContext != null) {
            for (int r : grantResults) {
                if (r == PackageManager.PERMISSION_DENIED) {
                    LOG.d(TAG, "onRequestPermissionResult: Permission Denied!");
                    result = new PluginResult(PluginResult.Status.ERROR, PERMISSION_DENIED_ERROR);
                    result.setKeepCallback(true);
                    cbContext.sendPluginResult(result);
                    return;
                }

            }
            LOG.d(TAG, "onRequestPermissionResult: ok");
            result = new PluginResult(PluginResult.Status.OK);
            result.setKeepCallback(true);
            cbContext.sendPluginResult(result);
        }
    }

    public boolean hasPermisssion() {
        for(String p : permissions)
        {
            if(!PermissionHelper.hasPermission(this, p))
            {
//                LOG.d(TAG, "hasPermisssion() is false for: " + p);
                LOG.d(TAG, "hasPermisssion() is false for: " + p + " but we will let this pass for now. TODO fisx.");
//                return false;
            }
        }
        LOG.d(TAG, "Plugin has the correct permissions.");
        return true;
    }

    /*
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     */

//    public void requestPermissions(int requestCode)
//    {
//        PermissionHelper.requestPermissions(this, requestCode, permissions);
//    }
}
