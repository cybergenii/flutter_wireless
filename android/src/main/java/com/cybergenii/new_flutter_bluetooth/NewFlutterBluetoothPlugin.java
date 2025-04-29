// package com.cybergenii.flutter_wireless;

// import androidx.annotation.NonNull;
// import androidx.annotation.RequiresApi;

// import io.flutter.embedding.engine.plugins.FlutterPlugin;
// import io.flutter.plugin.common.MethodCall;
// import io.flutter.plugin.common.MethodChannel;
// import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
// import io.flutter.plugin.common.MethodChannel.Result;

// import android.Manifest;
// import android.annotation.SuppressLint;
// import android.app.Activity;
// import android.bluetooth.BluetoothAdapter;
// import android.bluetooth.BluetoothDevice;
// import android.bluetooth.BluetoothManager;
// import android.content.BroadcastReceiver;
// import android.content.Context;
// import android.content.Intent;
// import android.content.IntentFilter;
// import android.content.pm.PackageManager;
// import android.os.Build;
// import android.os.Handler;
// import android.os.Looper;

// import androidx.core.app.ActivityCompat;
// import androidx.core.content.ContextCompat;

// import android.util.Log;
// import android.util.SparseArray;

// import java.io.PrintWriter;
// import java.io.StringWriter;
// import java.util.ArrayList;
// import java.util.HashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.concurrent.ExecutorService;
// import java.util.concurrent.Executors;

// import io.flutter.embedding.engine.plugins.activity.ActivityAware;
// import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
// import io.flutter.plugin.common.BinaryMessenger;
// import io.flutter.plugin.common.EventChannel;
// import io.flutter.plugin.common.EventChannel.StreamHandler;
// import io.flutter.plugin.common.EventChannel.EventSink;

// /** NewFlutterBluetoothPlugin */
// public class NewFlutterBluetoothPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
//     // Plugin
//     private static final String TAG = "FlutterBluePlugin";
//     private static final String PLUGIN_NAMESPACE = "flutter_bluetooth_serial";
//     private MethodChannel methodChannel;
//     private Result pendingResultForActivityResult = null;

//     // Permissions and request constants
//     private static final int REQUEST_BLUETOOTH_PERMISSIONS = 1451;
//     private static final int REQUEST_ENABLE_BLUETOOTH = 1337;
//     private static final int REQUEST_DISCOVERABLE_BLUETOOTH = 2137;

//     // General Bluetooth
//     private BluetoothAdapter bluetoothAdapter;
//     private final ExecutorService executorService = Executors.newCachedThreadPool();
//     private final Handler mainHandler = new Handler(Looper.getMainLooper());

//     // State
//     private final BroadcastReceiver stateReceiver;
//     private EventSink stateSink;

//     // Pairing requests
//     private final BroadcastReceiver pairingRequestReceiver;
//     private boolean isPairingRequestHandlerSet = false;
//     private BroadcastReceiver bondStateBroadcastReceiver = null;

//     private EventSink discoverySink;
//     private final BroadcastReceiver discoveryReceiver;

//     // Connections
//     private final SparseArray<BluetoothConnectionWrapper> connections = new SparseArray<>(2);
//     private int lastConnectionId = 0;
//     private Activity activity;
//     private BinaryMessenger messenger;
//     private Context activeContext;

//     public NewFlutterBluetoothPlugin() {
//         // State receiver
//         stateReceiver = new BroadcastReceiver() {
//             @Override
//             public void onReceive(Context context, Intent intent) {
//                 if (stateSink == null) return;

//                 final String action = intent.getAction();
//                 if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
//                     // Disconnect all connections
//                     synchronized (connections) {
//                         for (int i = 0; i < connections.size(); i++) {
//                             BluetoothConnection connection = connections.valueAt(i);
//                             connection.disconnect();
//                         }
//                         connections.clear();
//                     }
//                     stateSink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothDevice.ERROR));
//                 }
//             }
//         };

//         // Pairing requests
//         pairingRequestReceiver = new BroadcastReceiver() {
//             @Override
//             public void onReceive(Context context, Intent intent) {
//                 if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(intent.getAction())) {
//                     handlePairingRequest(intent);
//                 }
//             }
//         };

//         // Discovery receiver
//         discoveryReceiver = new BroadcastReceiver() {
//             @Override
//             public void onReceive(Context context, Intent intent) {
//                 final String action = intent.getAction();
//                 if (BluetoothDevice.ACTION_FOUND.equals(action)) {
//                     handleDeviceFound(intent);
//                 } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
//                     handleDiscoveryFinished(context);
//                 }
//             }
//         };
//     }

//     private void handlePairingRequest(Intent intent) {
//         final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//         final int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
        
//         Log.d(TAG, "Pairing request (variant " + pairingVariant + ") incoming from " + device.getAddress());
        
//         final BroadcastReceiver.PendingResult broadcastResult = this.goAsync();
//         Map<String, Object> arguments = new HashMap<>();
//         arguments.put("address", device.getAddress());
//         arguments.put("variant", pairingVariant);

//         if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_PIN) {
//             handlePinPairing(device, arguments, broadcastResult);
//         } else if (pairingVariant == BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION || 
//                   pairingVariant == 3 /* PAIRING_VARIANT_CONSENT */) {
//             handlePasskeyConfirmation(device, intent, arguments, broadcastResult);
//         } else if (pairingVariant == 4 /* PAIRING_VARIANT_DISPLAY_PASSKEY */ || 
//                   pairingVariant == 5 /* PAIRING_VARIANT_DISPLAY_PIN */) {
//             handleDisplayPasskey(device, intent, arguments);
//             broadcastResult.finish();
//         } else {
//             Log.w(TAG, "Unknown pairing variant: " + pairingVariant);
//             broadcastResult.finish();
//         }
//     }

//     private void handlePinPairing(BluetoothDevice device, Map<String, Object> arguments, 
//                                 BroadcastReceiver.PendingResult broadcastResult) {
//         methodChannel.invokeMethod("handlePairingRequest", arguments, new MethodChannel.Result() {
//             @Override
//             public void success(Object handlerResult) {
//                 Log.d(TAG, handlerResult.toString());
//                 try {
//                     if (handlerResult instanceof String) {
//                         final String passkeyString = (String) handlerResult;
//                         final byte[] passkey = passkeyString.getBytes();
//                         Log.d(TAG, "Trying to set passkey for pairing to " + passkeyString);
//                         device.setPin(passkey);
//                         broadcastResult.abortBroadcast();
//                     } else {
//                         Log.d(TAG, "Manual pin pairing in progress");
//                         ActivityCompat.startActivity(activity, intent, null);
//                     }
//                 } catch (Exception ex) {
//                     Log.e(TAG, "Error handling PIN pairing", ex);
//                 } finally {
//                     broadcastResult.finish();
//                 }
//             }

//             @Override
//             public void notImplemented() {
//                 broadcastResult.finish();
//             }

//             @Override
//             public void error(String code, String message, Object details) {
//                 broadcastResult.finish();
//             }
//         });
//     }

//     private void handlePasskeyConfirmation(BluetoothDevice device, Intent intent, 
//                                          Map<String, Object> arguments, 
//                                          BroadcastReceiver.PendingResult broadcastResult) {
//         final int pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);
//         arguments.put("pairingKey", pairingKey);

//         methodChannel.invokeMethod("handlePairingRequest", arguments, new MethodChannel.Result() {
//             @SuppressLint("MissingPermission")
//             @Override
//             public void success(Object handlerResult) {
//                 try {
//                     if (handlerResult instanceof Boolean) {
//                         final boolean confirm = (Boolean) handlerResult;
//                         Log.d(TAG, "Trying to set pairing confirmation to " + confirm + " (key: " + pairingKey + ")");
//                         device.setPairingConfirmation(confirm);
//                         broadcastResult.abortBroadcast();
//                     } else {
//                         Log.d(TAG, "Manual passkey confirmation pairing in progress (key: " + pairingKey + ")");
//                         ActivityCompat.startActivity(activity, intent, null);
//                     }
//                 } catch (Exception ex) {
//                     Log.e(TAG, "Error handling passkey confirmation", ex);
//                 } finally {
//                     broadcastResult.finish();
//                 }
//             }

//             @Override
//             public void notImplemented() {
//                 broadcastResult.finish();
//             }

//             @Override
//             public void error(String code, String message, Object details) {
//                 broadcastResult.finish();
//             }
//         });
//     }

//     private void handleDisplayPasskey(BluetoothDevice device, Intent intent, Map<String, Object> arguments) {
//         final int pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);
//         arguments.put("pairingKey", pairingKey);
//         methodChannel.invokeMethod("handlePairingRequest", arguments);
//     }

//     private void handleDeviceFound(Intent intent) {
//         final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//         final int deviceRSSI = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

//         Map<String, Object> discoveryResult = new HashMap<>();
//         discoveryResult.put("address", device.getAddress());
//         discoveryResult.put("name", getNameCompat(device));
        
//         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//             discoveryResult.put("type", device.getType());
//         } else {
//             discoveryResult.put("type", 0); // Default to TYPE_UNKNOWN for older devices
//         }
        
//         discoveryResult.put("isConnected", isDeviceConnectedCompat(device));
//         discoveryResult.put("bondState", device.getBondState());
//         discoveryResult.put("rssi", deviceRSSI);

//         Log.d(TAG, "Discovered " + device.getAddress());
//         if (discoverySink != null) {
//             discoverySink.success(discoveryResult);
//         }
//     }

//     private void handleDiscoveryFinished(Context context) {
//         Log.d(TAG, "Discovery finished");
//         try {
//             context.unregisterReceiver(discoveryReceiver);
//         } catch (IllegalArgumentException ex) {
//             Log.d(TAG, "Receiver not registered", ex);
//         }

//         bluetoothAdapter.cancelDiscovery();

//         if (discoverySink != null) {
//             discoverySink.endOfStream();
//             discoverySink = null;
//         }
//     }

//     @Override
//     public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
//         Log.v(TAG, "Attached to engine");
//         messenger = binding.getBinaryMessenger();

//         methodChannel = new MethodChannel(messenger, PLUGIN_NAMESPACE + "/methods");
//         methodChannel.setMethodCallHandler(this);

//         setupStateChannel();
//         setupDiscoveryChannel();
//     }

//     private void setupStateChannel() {
//         EventChannel stateChannel = new EventChannel(messenger, PLUGIN_NAMESPACE + "/state");
//         stateChannel.setStreamHandler(new StreamHandler() {
//             @Override
//             public void onListen(Object o, EventSink eventSink) {
//                 stateSink = eventSink;
//                 IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED);
//                 activeContext.registerReceiver(stateReceiver, filter);
//             }

//             @Override
//             public void onCancel(Object o) {
//                 stateSink = null;
//                 try {
//                     activeContext.unregisterReceiver(stateReceiver);
//                 } catch (IllegalArgumentException ex) {
//                     Log.d(TAG, "Receiver not registered", ex);
//                 }
//             }
//         });
//     }

//     private void setupDiscoveryChannel() {
//         EventChannel discoveryChannel = new EventChannel(messenger, PLUGIN_NAMESPACE + "/discovery");
//         discoveryChannel.setStreamHandler(new StreamHandler() {
//             @Override
//             public void onListen(Object o, EventSink eventSink) {
//                 discoverySink = eventSink;
//             }

//             @Override
//             public void onCancel(Object o) {
//                 Log.d(TAG, "Canceling discovery (stream closed)");
//                 try {
//                     activeContext.unregisterReceiver(discoveryReceiver);
//                 } catch (IllegalArgumentException ex) {
//                     Log.d(TAG, "Receiver not registered", ex);
//                 }

//                 bluetoothAdapter.cancelDiscovery();

//                 if (discoverySink != null) {
//                     discoverySink.endOfStream();
//                     discoverySink = null;
//                 }
//             }
//         });
//     }

//     @Override
//     public void onDetachedFromEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
//         if (methodChannel != null) {
//             methodChannel.setMethodCallHandler(null);
//         }
//         executorService.shutdown();
//     }

//     @Override
//     public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
//         this.activity = binding.getActivity();
//         BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
//         if (bluetoothManager != null) {
//             this.bluetoothAdapter = bluetoothManager.getAdapter();
//         }

//         binding.addActivityResultListener((requestCode, resultCode, data) -> {
//             switch (requestCode) {
//                 case REQUEST_ENABLE_BLUETOOTH:
//                     if (pendingResultForActivityResult != null) {
//                         pendingResultForActivityResult.success(resultCode != Activity.RESULT_CANCELED);
//                     }
//                     return true;

//                 case REQUEST_DISCOVERABLE_BLUETOOTH:
//                     if (pendingResultForActivityResult != null) {
//                         pendingResultForActivityResult.success(resultCode == Activity.RESULT_CANCELED ? -1 : resultCode);
//                     }
//                     return true;

//                 default:
//                     return false;
//             }
//         });

//         binding.addRequestPermissionsResultListener((requestCode, permissions, grantResults) -> {
//             if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS && pendingPermissionsEnsureCallbacks != null) {
//                 pendingPermissionsEnsureCallbacks.onResult(grantResults.length > 0 && 
//                     grantResults[0] == PackageManager.PERMISSION_GRANTED);
//                 pendingPermissionsEnsureCallbacks = null;
//                 return true;
//             }
//             return false;
//         });

//         this.activeContext = binding.getActivity().getApplicationContext();
//     }

//     @Override
//     public void onDetachedFromActivityForConfigChanges() {}

//     @Override
//     public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
//         onAttachedToActivity(binding);
//     }

//     @Override
//     public void onDetachedFromActivity() {}

//     private interface EnsurePermissionsCallback {
//         void onResult(boolean granted);
//     }

//     private EnsurePermissionsCallback pendingPermissionsEnsureCallbacks = null;

//     private void ensurePermissions(EnsurePermissionsCallback callbacks) {
//         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//             checkAndRequestBluetoothPermissions(callbacks);
//         } else {
//             checkAndRequestLocationPermissions(callbacks);
//         }
//     }

//     @RequiresApi(api = Build.VERSION_CODES.S)
//     private void checkAndRequestBluetoothPermissions(EnsurePermissionsCallback callbacks) {
//         if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
//             ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
//             callbacks.onResult(true);
//         } else {
//             pendingPermissionsEnsureCallbacks = callbacks;
//             ActivityCompat.requestPermissions(activity,
//                 new String[]{
//                     Manifest.permission.BLUETOOTH_CONNECT,
//                     Manifest.permission.BLUETOOTH_SCAN
//                 },
//                 REQUEST_BLUETOOTH_PERMISSIONS);
//         }
//     }

//     private void checkAndRequestLocationPermissions(EnsurePermissionsCallback callbacks) {
//         if (ContextCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
//             callbacks.onResult(true);
//         } else {
//             pendingPermissionsEnsureCallbacks = callbacks;
//             ActivityCompat.requestPermissions(activity,
//                 new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                 REQUEST_BLUETOOTH_PERMISSIONS);
//         }
//     }

//     private static String exceptionToString(Exception ex) {
//         StringWriter sw = new StringWriter();
//         PrintWriter pw = new PrintWriter(sw);
//         ex.printStackTrace(pw);
//         return sw.toString();
//     }

//     private static boolean isDeviceConnectedCompat(BluetoothDevice device) {
//         try {
//             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                 return device.isConnected();
//             } else {
//                 // Fallback for older devices
//                 java.lang.reflect.Method method = device.getClass().getMethod("isConnected");
//                 return (boolean) method.invoke(device);
//             }
//         } catch (Exception ex) {
//             return false;
//         }
//     }

//     private static String getNameCompat(BluetoothDevice device) {
//         try {
//             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                 return device.getAlias() != null ? device.getAlias() : device.getName();
//             } else {
//                 return device.getName();
//             }
//         } catch (Exception ex) {
//             return null;
//         }
//     }

//     private class BluetoothConnectionWrapper extends BluetoothConnection {
//         private final int id;
//         protected EventSink readSink;
//         protected EventChannel readChannel;

//         public BluetoothConnectionWrapper(int id, BluetoothAdapter adapter) {
//             super(adapter);
//             this.id = id;

//             readChannel = new EventChannel(messenger, PLUGIN_NAMESPACE + "/read/" + id);
//             readChannel.setStreamHandler(new StreamHandler() {
//                 @Override
//                 public void onListen(Object o, EventSink eventSink) {
//                     readSink = eventSink;
//                 }

//                 @Override
//                 public void onCancel(Object o) {
//                     disconnect();
//                     executorService.execute(() -> {
//                         readChannel.setStreamHandler(null);
//                         synchronized (connections) {
//                             connections.remove(id);
//                         }
//                         Log.d(TAG, "Disconnected (id: " + id + ")");
//                     });
//                 }
//             });
//         }

//         @Override
//         protected void onRead(byte[] buffer) {
//             mainHandler.post(() -> {
//                 if (readSink != null) {
//                     readSink.success(buffer);
//                 }
//             });
//         }

//         @Override
//         protected void onDisconnected(boolean byRemote) {
//             mainHandler.post(() -> {
//                 if (byRemote) {
//                     Log.d(TAG, "onDisconnected by remote (id: " + id + ")");
//                     if (readSink != null) {
//                         readSink.endOfStream();
//                         readSink = null;
//                     }
//                 } else {
//                     Log.d(TAG, "onDisconnected by local (id: " + id + ")");
//                 }
//             });
//         }
//     }

//     @Override
//     public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
//         if (bluetoothAdapter == null) {
//             if ("isAvailable".equals(call.method)) {
//                 result.success(false);
//             } else {
//                 result.error("bluetooth_unavailable", "bluetooth is not available", null);
//             }
//             return;
//         }

//         try {
//             switch (call.method) {
//                 case "isAvailable":
//                     result.success(true);
//                     break;

//                 case "isOn":
//                 case "isEnabled":
//                     result.success(bluetoothAdapter.isEnabled());
//                     break;

//                 case "openSettings":
//                     ContextCompat.startActivity(activity, 
//                         new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), null);
//                     result.success(null);
//                     break;

//                 case "requestEnable":
//                     handleRequestEnable(result);
//                     break;

//                 case "requestDisable":
//                     if (bluetoothAdapter.isEnabled()) {
//                         bluetoothAdapter.disable();
//                         result.success(true);
//                     } else {
//                         result.success(false);
//                     }
//                     break;

//                 case "ensurePermissions":
//                     ensurePermissions(result::success);
//                     break;

//                 case "getState":
//                     result.success(bluetoothAdapter.getState());
//                     break;

//                 case "getAddress":
//                     handleGetAddress(result);
//                     break;

//                 case "getName":
//                     result.success(getNameCompat(bluetoothAdapter));
//                     break;

//                 case "setName":
//                     handleSetName(call, result);
//                     break;

//                 case "getDeviceBondState":
//                     handleGetDeviceBondState(call, result);
//                     break;

//                 case "removeDeviceBond":
//                     handleRemoveDeviceBond(call, result);
//                     break;

//                 case "bondDevice":
//                     handleBondDevice(call, result);
//                     break;

//                 case "pairingRequestHandlingEnable":
//                     handlePairingRequestEnable(result);
//                     break;

//                 case "pairingRequestHandlingDisable":
//                     handlePairingRequestDisable();
//                     result.success(null);
//                     break;

//                 case "getBondedDevices":
//                     handleGetBondedDevices(result);
//                     break;

//                 case "isDiscovering":
//                     result.success(bluetoothAdapter.isDiscovering());
//                     break;

//                 case "startDiscovery":
//                     handleStartDiscovery(result);
//                     break;

//                 case "cancelDiscovery":
//                     handleCancelDiscovery();
//                     result.success(null);
//                     break;

//                 case "isDiscoverable":
//                     result.success(bluetoothAdapter.getScanMode() == 
//                         BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
//                     break;

//                 case "requestDiscoverable":
//                     handleRequestDiscoverable(call, result);
//                     break;

//                 case "connect":
//                     handleConnect(call, result);
//                     break;

//                 case "write":
//                     handleWrite(call, result);
//                     break;

//                 default:
//                     result.notImplemented();
//                     break;
//             }
//         } catch (Exception ex) {
//             result.error("unexpected_error", ex.getMessage(), exceptionToString(ex));
//         }
//     }

//     private void handleRequestEnable(Result result) {
//         if (!bluetoothAdapter.isEnabled()) {
//             pendingResultForActivityResult = result;
//             Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
//             ActivityCompat.startActivityForResult(activity, intent, REQUEST_ENABLE_BLUETOOTH, null);
//         } else {
//             result.success(true);
//         }
//     }

//     private void handleGetAddress(Result result) {
//         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//             result.success(bluetoothAdapter.getAddress());
//         } else {
//             // For older devices, we'll return a placeholder
//             result.success("02:00:00:00:00:00");
//         }
//     }

//     private void handleSetName(MethodCall call, Result result) {
//         if (!call.hasArgument("name")) {
//             result.error("invalid_argument", "argument 'name' not found", null);
//             return;
//         }

//         String name;
//         try {
//             name = call.argument("name");
//         } catch (ClassCastException ex) {
//             result.error("invalid_argument", "'name' argument is required to be string", null);
//             return;
//         }

//         if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//             result.success(bluetoothAdapter.setName(name));
//         } else {
//             // For older devices, we'll just return false as we can't change the name
//             result.success(false);
//         }
//     }

//     private void handleGetDeviceBondState(MethodCall call, Result result) {
//         String address = validateAddressArgument(call, result);
//         if (address == null) return;

//         BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
//         result.success(device.getBondState());
//     }

//     private void handleRemoveDeviceBond(MethodCall call, Result result) {
//         String address = validateAddressArgument(call, result);
//         if (address == null) return;

//         BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
//         switch (device.getBondState()) {
//             case BluetoothDevice.BOND_BONDING:
//                 result.error("bond_error", "device already bonding", null);
//                 break;
//             case BluetoothDevice.BOND_NONE:
//                 result.error("bond_error", "device already unbonded", null);
//                 break;
//             default:
//                 try {
//                     if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                         result.success(device.removeBond());
//                     } else {
//                         // Fallback for older devices
//                         java.lang.reflect.Method method = device.getClass().getMethod("removeBond");
//                         boolean value = (Boolean) method.invoke(device);
//                         result.success(value);
//                     }
//                 } catch (Exception ex) {
//                     result.error("bond_error", "error while unbonding", exceptionToString(ex));
//                 }
//                 break;
//         }
//     }

//     private void handleBondDevice(MethodCall call, Result result) {
//         String address = validateAddressArgument(call, result);
//         if (address == null) return;

//         if (bondStateBroadcastReceiver != null) {
//             result.error("bond_error", "another bonding process is ongoing from local device", null);
//             return;
//         }

//         BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
//         switch (device.getBondState()) {
//             case BluetoothDevice.BOND_BONDING:
//                 result.error("bond_error", "device already bonding", null);
//                 break;
//             case BluetoothDevice.BOND_BONDED:
//                 result.error("bond_error", "device already bonded", null);
//                 break;
//             default:
//                 bondStateBroadcastReceiver = new BroadcastReceiver() {
//                     @Override
//                     public void onReceive(Context context, Intent intent) {
//                         if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
//                             final BluetoothDevice someDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
//                             if (!someDevice.equals(device)) return;

//                             final int newBondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
//                             switch (newBondState) {
//                                 case BluetoothDevice.BOND_BONDING:
//                                     return;
//                                 case BluetoothDevice.BOND_BONDED:
//                                     result.success(true);
//                                     break;
//                                 case BluetoothDevice.BOND_NONE:
//                                     result.success(false);
//                                     break;
//                                 default:
//                                     result.error("bond_error", "invalid bond state while bonding", null);
//                                     break;
//                             }
//                             activeContext.unregisterReceiver(this);
//                             bondStateBroadcastReceiver = null;
//                         }
//                     }
//                 };

//                 final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
//                 activeContext.registerReceiver(bondStateBroadcastReceiver, filter);

//                 if (!device.createBond()) {
//                     result.error("bond_error", "error starting bonding process", null);
//                 }
//                 break;
//         }
//     }

//     private void handlePairingRequestEnable(Result result) {
//         if (isPairingRequestHandlerSet) {
//             result.error("logic_error", "pairing request handling is already enabled", null);
//             return;
//         }
//         Log.d(TAG, "Starting listening for pairing requests to handle");

//         isPairingRequestHandlerSet = true;
//         final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
//         activeContext.registerReceiver(pairingRequestReceiver, filter);
//         result.success(null);
//     }

//     private void handlePairingRequestDisable() {
//         isPairingRequestHandlerSet = false;
//         try {
//             activeContext.unregisterReceiver(pairingRequestReceiver);
//             Log.d(TAG, "Stopped listening for pairing requests to handle");
//         } catch (IllegalArgumentException ex) {
//             Log.d(TAG, "Receiver not registered", ex);
//         }
//     }

//     private void handleGetBondedDevices(Result result) {
//         ensurePermissions(granted -> {
//             if (!granted) {
//                 result.error("no_permissions", "discovering other devices requires permissions", null);
//                 return;
//             }

//             List<Map<String, Object>> list = new ArrayList<>();
//             for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
//                 Map<String, Object> entry = new HashMap<>();
//                 entry.put("address", device.getAddress());
//                 entry.put("name", getNameCompat(device));
                
//                 if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                     entry.put("type", device.getType());
//                 } else {
//                     entry.put("type", 0); // Default to TYPE_UNKNOWN for older devices
//                 }
                
//                 entry.put("isConnected", isDeviceConnectedCompat(device));
//                 entry.put("bondState", BluetoothDevice.BOND_BONDED);
//                 list.add(entry);
//             }

//             result.success(list);
//         });
//     }

//     private void handleStartDiscovery(Result result) {
//         ensurePermissions(granted -> {
//             if (!granted) {
//                 result.error("no_permissions", "discovering other devices requires permissions", null);
//                 return;
//             }

//             Log.d(TAG, "Starting discovery");
//             IntentFilter intent = new IntentFilter();
//             intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
//             intent.addAction(BluetoothDevice.ACTION_FOUND);
//             activeContext.registerReceiver(discoveryReceiver, intent);

//             bluetoothAdapter.startDiscovery();

//             result.success(null);
//         });
//     }

//     private void handleCancelDiscovery() {
//         Log.d(TAG, "Canceling discovery");
//         try {
//             activeContext.unregisterReceiver(discoveryReceiver);
//         } catch (IllegalArgumentException ex) {
//             Log.d(TAG, "Receiver not registered", ex);
//         }

//         bluetoothAdapter.cancelDiscovery();

//         if (discoverySink != null) {
//             discoverySink.endOfStream();
//             discoverySink = null;
//         }
//     }

//     private void handleRequestDiscoverable(MethodCall call, Result result) {
//         Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);

//         if (call.hasArgument("duration")) {
//             try {
//                 int duration = call.argument("duration");
//                 intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
//             } catch (ClassCastException ex) {
//                 result.error("invalid_argument", "'duration' argument is required to be integer", null);
//                 return;
//             }
//         }

//         pendingResultForActivityResult = result;
//         ActivityCompat.startActivityForResult(activity, intent, REQUEST_DISCOVERABLE_BLUETOOTH, null);
//     }

//     private void handleConnect(MethodCall call, Result result) {
//         String address = validateAddressArgument(call, result);
//         if (address == null) return;

//         int id = ++lastConnectionId;
//         BluetoothConnectionWrapper connection = new BluetoothConnectionWrapper(id, bluetoothAdapter);
        
//         synchronized (connections) {
//             connections.put(id, connection);
//         }

//         Log.d(TAG, "Connecting to " + address + " (id: " + id + ")");

//         executorService.execute(() -> {
//             try {
//                 connection.connect(address);
//                 result.success(id);
//             } catch (Exception ex) {
//                 synchronized (connections) {
//                     connections.remove(id);
//                 }
//                 result.error("connect_error", ex.getMessage(), exceptionToString(ex));
//             }
//         });
//     }

//     private void handleWrite(MethodCall call, Result result) {
//         if (!call.hasArgument("id")) {
//             result.error("invalid_argument", "argument 'id' not found", null);
//             return;
//         }

//         int id;
//         try {
//             id = call.argument("id");
//         } catch (ClassCastException ex) {
//             result.error("invalid_argument", "'id' argument is required to be integer id of connection", null);
//             return;
//         }

//         BluetoothConnection connection;
//         synchronized (connections) {
//             connection = connections.get(id);
//         }
        
//         if (connection == null) {
//             result.error("invalid_argument", "there is no connection with provided id", null);
//             return;
//         }

//         if (call.hasArgument("string")) {
//             String string = call.argument("string");
//             executorService.execute(() -> {
//                 try {
//                     connection.write(string.getBytes());
//                     result.success(null);
//                 } catch (Exception ex) {
//                     result.error("write_error", ex.getMessage(), exceptionToString(ex));
//                 }
//             });
//         } else if (call.hasArgument("bytes")) {
//             byte[] bytes = call.argument("bytes");
//             executorService.execute(() -> {
//                 try {
//                     connection.write(bytes);
//                     result.success(null);
//                 } catch (Exception ex) {
//                     result.error("write_error", ex.getMessage(), exceptionToString(ex));
//                 }
//             });
//         } else {
//             result.error("invalid_argument", "there must be 'string' or 'bytes' argument", null);
//         }
//     }

//     private String validateAddressArgument(MethodCall call, Result result) {
//         if (!call.hasArgument("address")) {
//             result.error("invalid_argument", "argument 'address' not found", null);
//             return null;
//         }

//         String address;
//         try {
//             address = call.argument("address");
//             if (!BluetoothAdapter.checkBluetoothAddress(address)) {
//                 throw new ClassCastException();
//             }
//         } catch (ClassCastException ex) {
//             result.error("invalid_argument", "'address' argument is required to be string containing remote MAC address", null);
//             return null;
//         }
//         return address;
//     }
// }




package com.cybergenii.flutter_wireless;

import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.util.Log;
import android.util.SparseArray;
import android.os.AsyncTask;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Enumeration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.NetworkInterface;

import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.EventChannel;
import io.flutter.plugin.common.EventChannel.StreamHandler;
import io.flutter.plugin.common.EventChannel.EventSink;

/** NewFlutterBluetoothPlugin */
public class NewFlutterBluetoothPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
    // Plugin
    private static final String TAG = "FlutterBluePlugin";
    private static final String PLUGIN_NAMESPACE = "flutter_bluetooth_serial";
    private MethodChannel methodChannel;
    private Result pendingResultForActivityResult = null;

    // Permissions and request constants
    private static final int REQUEST_COARSE_LOCATION_PERMISSIONS = 1451;
    private static final int REQUEST_ENABLE_BLUETOOTH = 1337;
    private static final int REQUEST_DISCOVERABLE_BLUETOOTH = 2137;

    // General Bluetooth
    private BluetoothAdapter bluetoothAdapter;

    // State
    private final BroadcastReceiver stateReceiver;
    private EventSink stateSink;

    // Pairing requests
    private final BroadcastReceiver pairingRequestReceiver;
    private boolean isPairingRequestHandlerSet = false;
    private BroadcastReceiver bondStateBroadcastReceiver = null;

    private EventSink discoverySink;
    private final BroadcastReceiver discoveryReceiver;

    // Connections
    /// Contains all active connections. Maps ID of the connection with plugin data channels. 
    private final SparseArray<BluetoothConnectionWrapper> connections = new SparseArray<>(2);

    /// Last ID given to any connection, used to avoid duplicate IDs 
    private int lastConnectionId = 0;
    private Activity activity;
    private BinaryMessenger messenger;
    private Context activeContext;

    /// Constructs the plugin instance
    public NewFlutterBluetoothPlugin() {

        // State
        stateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (stateSink == null) {
                    return;
                }

                final String action = intent.getAction();
                switch (action) {
                    case BluetoothAdapter.ACTION_STATE_CHANGED:
                        // Disconnect all connections
                        int size = connections.size();
                        for (int i = 0; i < size; i++) {
                            BluetoothConnection connection = connections.valueAt(i);
                            connection.disconnect();
                        }
                        connections.clear();

                        stateSink.success(intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothDevice.ERROR));
                        break;
                }
            }
        };

        // Pairing requests
        pairingRequestReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case BluetoothDevice.ACTION_PAIRING_REQUEST:
                        final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        final int pairingVariant = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, BluetoothDevice.ERROR);
                        Log.d(TAG, "Pairing request (variant " + pairingVariant + ") incoming from " + device.getAddress());
                        switch (pairingVariant) {
                            case BluetoothDevice.PAIRING_VARIANT_PIN:
                                // Simplest method - 4 digit number
                            {
                                final BroadcastReceiver.PendingResult broadcastResult = this.goAsync();

                                Map<String, Object> arguments = new HashMap<String, Object>();
                                arguments.put("address", device.getAddress());
                                arguments.put("variant", pairingVariant);

                                methodChannel.invokeMethod("handlePairingRequest", arguments, new MethodChannel.Result() {
                                    @Override
                                    public void success(Object handlerResult) {
                                        Log.d(TAG, handlerResult.toString());
                                        if (handlerResult instanceof String) {
                                            try {
                                                final String passkeyString = (String) handlerResult;
                                                final byte[] passkey = passkeyString.getBytes();
                                                Log.d(TAG, "Trying to set passkey for pairing to " + passkeyString);
                                                device.setPin(passkey);
                                                broadcastResult.abortBroadcast();
                                            } catch (Exception ex) {
                                                Log.e(TAG, ex.getMessage());
                                                ex.printStackTrace();
                                            }
                                        } else {
                                            Log.d(TAG, "Manual pin pairing in progress");
                                            ActivityCompat.startActivity(activity, intent, null);
                                        }
                                        broadcastResult.finish();
                                    }

                                    @Override
                                    public void notImplemented() {
                                        throw new UnsupportedOperationException();
                                    }

                                    @Override
                                    public void error(String code, String message, Object details) {
                                        throw new UnsupportedOperationException();
                                    }
                                });
                                break;
                            }

                            case BluetoothDevice.PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                            case 3: // BluetoothDevice.PAIRING_VARIANT_CONSENT
                            {
                                final int pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);

                                Map<String, Object> arguments = new HashMap<String, Object>();
                                arguments.put("address", device.getAddress());
                                arguments.put("variant", pairingVariant);
                                arguments.put("pairingKey", pairingKey);

                                final BroadcastReceiver.PendingResult broadcastResult = this.goAsync();
                                methodChannel.invokeMethod("handlePairingRequest", arguments, new MethodChannel.Result() {
                                    @SuppressLint("MissingPermission")
                                    @Override
                                    public void success(Object handlerResult) {
                                        if (handlerResult instanceof Boolean) {
                                            try {
                                                final boolean confirm = (Boolean) handlerResult;
                                                Log.d(TAG, "Trying to set pairing confirmation to " + confirm + " (key: " + pairingKey + ")");
                                                device.setPairingConfirmation(confirm);
                                                broadcastResult.abortBroadcast();
                                            } catch (Exception ex) {
                                                Log.e(TAG, ex.getMessage());
                                                ex.printStackTrace();
                                            }
                                        } else {
                                            Log.d(TAG, "Manual passkey confirmation pairing in progress (key: " + pairingKey + ")");
                                            ActivityCompat.startActivity(activity, intent, null);
                                        }
                                        broadcastResult.finish();
                                    }

                                    @Override
                                    public void notImplemented() {
                                        throw new UnsupportedOperationException();
                                    }

                                    @Override
                                    public void error(String code, String message, Object details) {
                                        Log.e(TAG, code + " " + message);
                                        throw new UnsupportedOperationException();
                                    }
                                });
                                break;
                            }

                            case 4: // BluetoothDevice.PAIRING_VARIANT_DISPLAY_PASSKEY
                            case 5: // BluetoothDevice.PAIRING_VARIANT_DISPLAY_PIN
                            {
                                final int pairingKey = intent.getIntExtra(BluetoothDevice.EXTRA_PAIRING_KEY, BluetoothDevice.ERROR);

                                Map<String, Object> arguments = new HashMap<String, Object>();
                                arguments.put("address", device.getAddress());
                                arguments.put("variant", pairingVariant);
                                arguments.put("pairingKey", pairingKey);

                                methodChannel.invokeMethod("handlePairingRequest", arguments);
                                break;
                            }

                            default:
                                Log.w(TAG, "Unknown pairing variant: " + pairingVariant);
                                break;
                        }
                        break;

                    default:
                        break;
                }
            }
        };

        // Discovery
        discoveryReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                switch (action) {
                    case BluetoothDevice.ACTION_FOUND:
                        final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                        final int deviceRSSI = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE);

                        Map<String, Object> discoveryResult = new HashMap<>();
                        discoveryResult.put("address", device.getAddress());
                        discoveryResult.put("name", device.getName());
                        discoveryResult.put("type", device.getType());
                        discoveryResult.put("isConnected", checkIsDeviceConnected(device));
                        discoveryResult.put("bondState", device.getBondState());
                        discoveryResult.put("rssi", deviceRSSI);

                        Log.d(TAG, "Discovered " + device.getAddress());
                        if (discoverySink != null) {
                            discoverySink.success(discoveryResult);
                        }
                        break;

                    case BluetoothAdapter.ACTION_DISCOVERY_FINISHED:
                        Log.d(TAG, "Discovery finished");
                        try {
                            context.unregisterReceiver(discoveryReceiver);
                        } catch (IllegalArgumentException ex) {
                        }

                        bluetoothAdapter.cancelDiscovery();

                        if (discoverySink != null) {
                            discoverySink.endOfStream();
                            discoverySink = null;
                        }
                        break;

                    default:
                        break;
                }
            }
        };
    }

    @Override
    public void onAttachedToEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        Log.v("FlutterBluetoothSerial", "Attached to engine");
        messenger = binding.getBinaryMessenger();

        methodChannel = new MethodChannel(messenger, PLUGIN_NAMESPACE + "/methods");
        methodChannel.setMethodCallHandler(this);

        EventChannel stateChannel = new EventChannel(messenger, PLUGIN_NAMESPACE + "/state");

        stateChannel.setStreamHandler(new StreamHandler() {
            @Override
            public void onListen(Object o, EventSink eventSink) {
                stateSink = eventSink;
                activeContext.registerReceiver(stateReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
            }

            @Override
            public void onCancel(Object o) {
                stateSink = null;
                try {
                    activeContext.unregisterReceiver(stateReceiver);
                } catch (IllegalArgumentException ex) {
                }
            }
        });

        EventChannel discoveryChannel = new EventChannel(messenger, PLUGIN_NAMESPACE + "/discovery");

        StreamHandler discoveryStreamHandler = new StreamHandler() {
            @Override
            public void onListen(Object o, EventSink eventSink) {
                discoverySink = eventSink;
            }

            @Override
            public void onCancel(Object o) {
                Log.d(TAG, "Canceling discovery (stream closed)");
                try {
                    activeContext.unregisterReceiver(discoveryReceiver);
                } catch (IllegalArgumentException ex) {
                }

                bluetoothAdapter.cancelDiscovery();

                if (discoverySink != null) {
                    discoverySink.endOfStream();
                    discoverySink = null;
                }
            }
        };
        discoveryChannel.setStreamHandler(discoveryStreamHandler);
    }

    @Override
    public void onDetachedFromEngine(@NonNull FlutterPlugin.FlutterPluginBinding binding) {
        if (methodChannel != null) methodChannel.setMethodCallHandler(null);
    }

    @Override
    public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
        this.activity = binding.getActivity();
        BluetoothManager bluetoothManager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        assert bluetoothManager != null;

        this.bluetoothAdapter = bluetoothManager.getAdapter();

        binding.addActivityResultListener(
                (requestCode, resultCode, data) -> {
                    switch (requestCode) {
                        case REQUEST_ENABLE_BLUETOOTH:
                            if (pendingResultForActivityResult != null) {
                                pendingResultForActivityResult.success(resultCode != 0);
                            }
                            return true;

                        case REQUEST_DISCOVERABLE_BLUETOOTH:
                            pendingResultForActivityResult.success(resultCode == 0 ? -1 : resultCode);
                            return true;

                        default:
                            return false;
                    }
                }
        );
        binding.addRequestPermissionsResultListener(
                (requestCode, permissions, grantResults) -> {
                    switch (requestCode) {
                        case REQUEST_COARSE_LOCATION_PERMISSIONS:
                            pendingPermissionsEnsureCallbacks.onResult(grantResults[0] == PackageManager.PERMISSION_GRANTED);
                            pendingPermissionsEnsureCallbacks = null;
                            return true;
                    }
                    return false;
                }
        );
        activity = binding.getActivity();
        activeContext = binding.getActivity().getApplicationContext();
    }

    @Override
    public void onDetachedFromActivityForConfigChanges() {
    }

    @Override
    public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    }

    @Override
    public void onDetachedFromActivity() {
    }

    private interface EnsurePermissionsCallback {
        void onResult(boolean granted);
    }

    EnsurePermissionsCallback pendingPermissionsEnsureCallbacks = null;

    private void ensurePermissions(EnsurePermissionsCallback callbacks) {
        if (
                ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.ACCESS_COARSE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED
                        || ContextCompat.checkSelfPermission(activity,
                        Manifest.permission.ACCESS_FINE_LOCATION)
                        != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity,
                    new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION},
                    REQUEST_COARSE_LOCATION_PERMISSIONS);

            pendingPermissionsEnsureCallbacks = callbacks;
        } else {
            callbacks.onResult(true);
        }
    }

    static private String exceptionToString(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    static private boolean checkIsDeviceConnected(BluetoothDevice device) {
        try {
            java.lang.reflect.Method method;
            method = device.getClass().getMethod("isConnected");
            return (boolean) (Boolean) method.invoke(device);
        } catch (Exception ex) {
            return false;
        }
    }

    private class BluetoothConnectionWrapper extends BluetoothConnection {
        private final int id;
        protected EventSink readSink;
        protected EventChannel readChannel;
        private final BluetoothConnectionWrapper self = this;

        public BluetoothConnectionWrapper(int id, BluetoothAdapter adapter) {
            super(adapter);
            this.id = id;

            readChannel = new EventChannel(messenger, PLUGIN_NAMESPACE + "/read/" + id);
            StreamHandler readStreamHandler = new StreamHandler() {
                @Override
                public void onListen(Object o, EventSink eventSink) {
                    readSink = eventSink;
                }

                @Override
                public void onCancel(Object o) {
                    self.disconnect();
                    AsyncTask.execute(() -> {
                        readChannel.setStreamHandler(null);
                        connections.remove(id);
                        Log.d(TAG, "Disconnected (id: " + id + ")");
                    });
                }
            };
            readChannel.setStreamHandler(readStreamHandler);
        }

        @Override
        protected void onRead(byte[] buffer) {
            activity.runOnUiThread(() -> {
                if (readSink != null) {
                    readSink.success(buffer);
                }
            });
        }

        @Override
        protected void onDisconnected(boolean byRemote) {
            activity.runOnUiThread(() -> {
                if (byRemote) {
                    Log.d(TAG, "onDisconnected by remote (id: " + id + ")");
                    if (readSink != null) {
                        readSink.endOfStream();
                        readSink = null;
                    }
                } else {
                    Log.d(TAG, "onDisconnected by local (id: " + id + ")");
                }
            });
        }
    }

    @Override
    public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
        if (bluetoothAdapter == null) {
            if ("isAvailable".equals(call.method)) {
                result.success(false);
            } else {
                result.error("bluetooth_unavailable", "bluetooth is not available", null);
            }
            return;
        }

        switch (call.method) {
            case "isAvailable":
                result.success(true);
                break;

            case "isOn":
            case "isEnabled":
                result.success(bluetoothAdapter.isEnabled());
                break;

            case "openSettings":
                ContextCompat.startActivity(activity, new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS), null);
                result.success(null);
                break;

            case "requestEnable":
                if (!bluetoothAdapter.isEnabled()) {
                    pendingResultForActivityResult = result;
                    Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    ActivityCompat.startActivityForResult(activity, intent, REQUEST_ENABLE_BLUETOOTH, null);
                } else {
                    result.success(true);
                }
                break;

            case "requestDisable":
                if (bluetoothAdapter.isEnabled()) {
                    bluetoothAdapter.disable();
                    result.success(true);
                } else {
                    result.success(false);
                }
                break;

            case "ensurePermissions":
                ensurePermissions(result::success);
                break;

            case "getState":
                result.success(bluetoothAdapter.getState());
                break;

            case "getAddress": {
                String address = bluetoothAdapter.getAddress();
                if (address.equals("02:00:00:00:00:00")) {
                    Log.w(TAG, "Local Bluetooth MAC address is hidden by system, trying other options...");
                    do {
                        try {
                            String value = android.provider.Settings.Secure.getString(activeContext.getContentResolver(), "bluetooth_address");
                            if (value == null) {
                                throw new NullPointerException("null returned, might be no permissions problem");
                            }
                            address = value;
                            break;
                        } catch (Exception ex) {
                            Log.d(TAG, "Obtaining address using Settings Secure bank failed");
                        }

                        try {
                            java.lang.reflect.Field mServiceField;
                            mServiceField = bluetoothAdapter.getClass().getDeclaredField("mService");
                            mServiceField.setAccessible(true);

                            Object bluetoothManagerService = mServiceField.get(bluetoothAdapter);
                            if (bluetoothManagerService == null) {
                                if (!bluetoothAdapter.isEnabled()) {
                                    Log.d(TAG, "Probably failed just because adapter is disabled!");
                                }
                                throw new NullPointerException();
                            }
                            java.lang.reflect.Method getAddressMethod;
                            getAddressMethod = bluetoothManagerService.getClass().getMethod("getAddress");
                            String value = (String) getAddressMethod.invoke(bluetoothManagerService);
                            if (value == null) {
                                throw new NullPointerException();
                            }
                            address = value;
                            Log.d(TAG, "Probably succed: " + address + " ✨ :F");
                            break;
                        } catch (Exception ex) {
                            Log.d(TAG, "Obtaining address using reflection against internal Android code failed");
                        }

                        try {
                            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
                            String value = null;
                            while (interfaces.hasMoreElements()) {
                                NetworkInterface networkInterface = interfaces.nextElement();
                                String name = networkInterface.getName();

                                if (!name.equalsIgnoreCase("wlan0")) {
                                    continue;
                                }

                                byte[] addressBytes = networkInterface.getHardwareAddress();
                                if (addressBytes != null) {
                                    StringBuilder addressBuilder = new StringBuilder(18);
                                    for (byte b : addressBytes) {
                                        addressBuilder.append(String.format("%02X:", b));
                                    }
                                    addressBuilder.setLength(17);
                                    value = addressBuilder.toString();
                                }
                            }
                            if (value == null) {
                                throw new NullPointerException();
                            }
                            address = value;
                        } catch (Exception ex) {
                            Log.w(TAG, "Looking for address by network interfaces failed");
                        }
                    } while (false);
                }
                result.success(address);
                break;
            }

            case "getName":
                result.success(bluetoothAdapter.getName());
                break;

            case "setName": {
                if (!call.hasArgument("name")) {
                    result.error("invalid_argument", "argument 'name' not found", null);
                    break;
                }

                String name;
                try {
                    name = call.argument("name");
                } catch (ClassCastException ex) {
                    result.error("invalid_argument", "'name' argument is required to be string", null);
                    break;
                }

                result.success(bluetoothAdapter.setName(name));
                break;
            }

            case "getDeviceBondState": {
                if (!call.hasArgument("address")) {
                    result.error("invalid_argument", "argument 'address' not found", null);
                    break;
                }

                String address;
                try {
                    address = call.argument("address");
                    if (!BluetoothAdapter.checkBluetoothAddress(address)) {
                        throw new ClassCastException();
                    }
                } catch (ClassCastException ex) {
                    result.error("invalid_argument", "'address' argument is required to be string containing remote MAC address", null);
                    break;
                }

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                result.success(device.getBondState());
                break;
            }

            case "removeDeviceBond": {
                if (!call.hasArgument("address")) {
                    result.error("invalid_argument", "argument 'address' not found", null);
                    break;
                }

                String address;
                try {
                    address = call.argument("address");
                    if (!BluetoothAdapter.checkBluetoothAddress(address)) {
                        throw new ClassCastException();
                    }
                } catch (ClassCastException ex) {
                    result.error("invalid_argument", "'address' argument is required to be string containing remote MAC address", null);
                    break;
                }

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING:
                        result.error("bond_error", "device already bonding", null);
                        break;
                    case BluetoothDevice.BOND_NONE:
                        result.error("bond_error", "device already unbonded", null);
                        break;
                    default:
                        try {
                            java.lang.reflect.Method method;
                            method = device.getClass().getMethod("removeBond");
                            boolean value = (Boolean) method.invoke(device);
                            result.success(value);
                        } catch (Exception ex) {
                            result.error("bond_error", "error while unbonding", exceptionToString(ex));
                        }
                        break;
                }
                break;
            }

            case "bondDevice": {
                if (!call.hasArgument("address")) {
                    result.error("invalid_argument", "argument 'address' not found", null);
                    break;
                }

                String address;
                try {
                    address = call.argument("address");
                    if (!BluetoothAdapter.checkBluetoothAddress(address)) {
                        throw new ClassCastException();
                    }
                } catch (ClassCastException ex) {
                    result.error("invalid_argument", "'address' argument is required to be string containing remote MAC address", null);
                    break;
                }

                if (bondStateBroadcastReceiver != null) {
                    result.error("bond_error", "another bonding process is ongoing from local device", null);
                    break;
                }

                BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
                switch (device.getBondState()) {
                    case BluetoothDevice.BOND_BONDING:
                        result.error("bond_error", "device already bonding", null);
                        break;
                    case BluetoothDevice.BOND_BONDED:
                        result.error("bond_error", "device already bonded", null);
                        break;
                    default:
                        bondStateBroadcastReceiver = new BroadcastReceiver() {
                            @Override
                            public void onReceive(Context context, Intent intent) {
                                switch (intent.getAction()) {
                                    case BluetoothDevice.ACTION_BOND_STATE_CHANGED:
                                        final BluetoothDevice someDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                                        if (!someDevice.equals(device)) {
                                            break;
                                        }

                                        final int newBondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR);
                                        switch (newBondState) {
                                            case BluetoothDevice.BOND_BONDING:
                                                return;
                                            case BluetoothDevice.BOND_BONDED:
                                                result.success(true);
                                                break;
                                            case BluetoothDevice.BOND_NONE:
                                                result.success(false);
                                                break;
                                            default:
                                                result.error("bond_error", "invalid bond state while bonding", null);
                                                break;
                                        }
                                        activeContext.unregisterReceiver(this);
                                        bondStateBroadcastReceiver = null;
                                        break;
                                }
                            }
                        };

                        final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
                        activeContext.registerReceiver(bondStateBroadcastReceiver, filter);

                        if (!device.createBond()) {
                            result.error("bond_error", "error starting bonding process", null);
                        }
                        break;
                }
                break;
            }

            case "pairingRequestHandlingEnable":
                if (isPairingRequestHandlerSet) {
                    result.error("logic_error", "pairing request handling is already enabled", null);
                    break;
                }
                Log.d(TAG, "Starting listening for pairing requests to handle");

                isPairingRequestHandlerSet = true;
                final IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
                activeContext.registerReceiver(pairingRequestReceiver, filter);
                break;

            case "pairingRequestHandlingDisable":
                isPairingRequestHandlerSet = false;
                try {
                    activeContext.unregisterReceiver(pairingRequestReceiver);
                    Log.d(TAG, "Stopped listening for pairing requests to handle");
                } catch (IllegalArgumentException ex) {
                }
                break;

            case "getBondedDevices":
                ensurePermissions(granted -> {
                    if (!granted) {
                        result.error("no_permissions", "discovering other devices requires location access permission", null);
                        return;
                    }

                    List<Map<String, Object>> list = new ArrayList<>();
                    for (BluetoothDevice device : bluetoothAdapter.getBondedDevices()) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("address", device.getAddress());
                        entry.put("name", device.getName());
                        entry.put("type", device.getType());
                        entry.put("isConnected", checkIsDeviceConnected(device));
                        entry.put("bondState", BluetoothDevice.BOND_BONDED);
                        list.add(entry);
                    }

                    result.success(list);
                });
                break;

            case "isDiscovering":
                result.success(bluetoothAdapter.isDiscovering());
                break;

            case "startDiscovery":
                ensurePermissions(granted -> {
                    if (!granted) {
                        result.error("no_permissions", "discovering other devices requires location access permission", null);
                        return;
                    }

                    Log.d(TAG, "Starting discovery");
                    IntentFilter intent = new IntentFilter();
                    intent.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
                    intent.addAction(BluetoothDevice.ACTION_FOUND);
                    activeContext.registerReceiver(discoveryReceiver, intent);

                    bluetoothAdapter.startDiscovery();

                    result.success(null);
                });
                break;

            case "cancelDiscovery":
                Log.d(TAG, "Canceling discovery");
                try {
                    activeContext.unregisterReceiver(discoveryReceiver);
                } catch (IllegalArgumentException ex) {
                }

                bluetoothAdapter.cancelDiscovery();

                if (discoverySink != null) {
                    discoverySink.endOfStream();
                    discoverySink = null;
                }

                result.success(null);
                break;

            case "isDiscoverable":
                result.success(bluetoothAdapter.getScanMode() == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE);
                break;

            case "requestDiscoverable": {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);

                if (call.hasArgument("duration")) {
                    try {
                        int duration = (int) call.argument("duration");
                        intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, duration);
                    } catch (ClassCastException ex) {
                        result.error("invalid_argument", "'duration' argument is required to be integer", null);
                        break;
                    }
                }

                pendingResultForActivityResult = result;
                ActivityCompat.startActivityForResult(activity, intent, REQUEST_DISCOVERABLE_BLUETOOTH, null);
                break;
            }

            case "connect": {
                if (!call.hasArgument("address")) {
                    result.error("invalid_argument", "argument 'address' not found", null);
                    break;
                }

                String address;
                try {
                    address = call.argument("address");
                    if (!BluetoothAdapter.checkBluetoothAddress(address)) {
                        throw new ClassCastException();
                    }
                } catch (ClassCastException ex) {
                    result.error("invalid_argument", "'address' argument is required to be string containing remote MAC address", null);
                    break;
                }

                int id = ++lastConnectionId;
                BluetoothConnectionWrapper connection = new BluetoothConnectionWrapper(id, bluetoothAdapter);
                connections.put(id, connection);

                Log.d(TAG, "Connecting to " + address + " (id: " + id + ")");

                AsyncTask.execute(() -> {
                    try {
                        connection.connect(address);
                        activity.runOnUiThread(() -> result.success(id));
                    } catch (Exception ex) {
                        activity.runOnUiThread(() -> result.error("connect_error", ex.getMessage(), exceptionToString(ex)));
                        connections.remove(id);
                    }
                });
                break;
            }

            case "write": {
                if (!call.hasArgument("id")) {
                    result.error("invalid_argument", "argument 'id' not found", null);
                    break;
                }

                int id;
                try {
                    id = call.argument("id");
                } catch (ClassCastException ex) {
                    result.error("invalid_argument", "'id' argument is required to be integer id of connection", null);
                    break;
                }

                BluetoothConnection connection = connections.get(id);
                if (connection == null) {
                    result.error("invalid_argument", "there is no connection with provided id", null);
                    break;
                }

                if (call.hasArgument("string")) {
                    String string = call.argument("string");
                    AsyncTask.execute(() -> {
                        try {
                            connection.write(string.getBytes());
                            activity.runOnUiThread(() -> result.success(null));
                        } catch (Exception ex) {
                            activity.runOnUiThread(() -> result.error("write_error", ex.getMessage(), exceptionToString(ex)));
                        }
                    });
                } else if (call.hasArgument("bytes")) {
                    byte[] bytes = call.argument("bytes");
                    AsyncTask.execute(() -> {
                        try {
                            connection.write(bytes);
                            activity.runOnUiThread(() -> result.success(null));
                        } catch (Exception ex) {
                            activity.runOnUiThread(() -> result.error("write_error", ex.getMessage(), exceptionToString(ex)));
                        }
                    });
                } else {
                    result.error("invalid_argument", "there must be 'string' or 'bytes' argument", null);
                }
                break;
            }

            default:
                result.notImplemented();
                break;
        }
    }
}