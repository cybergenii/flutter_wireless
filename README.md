# flutter_wireless 

A comprehensive Flutter package for Bluetooth Serial communication that simplifies device discovery, connection management, and data transfer between Flutter applications and Bluetooth devices.

## Features

- Device discovery and scanning
- Connect to paired and unpaired Bluetooth devices
- Send and receive data over Bluetooth
- Bond/unbond with devices
- Background connection management
- Support for multiple simultaneous connections
- Bluetooth state monitoring and management

## Getting Started

### Installation

Add the following to your `pubspec.yaml` file:

```yaml
dependencies:
  flutter_wireless : ^latest_version
```

### Permissions

#### Android

Add these permissions to your `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

For Android 12 (API level 31) and above, you'll also need to add:

```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

#### iOS

Add these descriptions to your `Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>Need Bluetooth permission for communicating with devices</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>Need Bluetooth permission for communicating with devices</string>
```

## Usage

### Basic Usage

```dart
import 'package:flutter/material.dart';
import 'package:flutter_wireless /flutter_wireless .dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatefulWidget {
  const MyApp({super.key});

  @override
  State<MyApp> createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  final _newFlutterBluetoothPlugin = NewFlutterBluetooth();
  BluetoothState _bluetoothState = BluetoothState.UNKNOWN;
  
  @override
  void initState() {
    super.initState();
    
    // Get current state
    NewFlutterBluetooth.instance.state.then((state) {
      setState(() {
        _bluetoothState = state;
      });
    });
    
    // Listen for state changes
    NewFlutterBluetooth.instance.onStateChanged().listen((state) {
      setState(() {
        _bluetoothState = state;
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Bluetooth Serial Example'),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text('Bluetooth State: ${_bluetoothState.toString()}'),
              ElevatedButton(
                child: Text('Enable Bluetooth'),
                onPressed: () async {
                  await NewFlutterBluetooth.instance.requestEnable();
                },
              ),
              ElevatedButton(
                child: Text('Discover Devices'),
                onPressed: () {
                  Navigator.push(
                    context,
                    MaterialPageRoute(builder: (context) => DiscoveryPage()),
                  );
                },
              ),
            ],
          ),
        ),
      ),
    );
  }
}
```

### Device Discovery

```dart
class DiscoveryPage extends StatefulWidget {
  final bool start;
  const DiscoveryPage({this.start = true});

  @override
  _DiscoveryPageState createState() => _DiscoveryPageState();
}

class _DiscoveryPageState extends State<DiscoveryPage> {
  StreamSubscription<BluetoothDiscoveryResult>? _streamSubscription;
  List<BluetoothDiscoveryResult> results = [];
  bool isDiscovering = false;

  @override
  void initState() {
    super.initState();

    isDiscovering = widget.start;
    if (isDiscovering) {
      _startDiscovery();
    }
  }

  void _startDiscovery() {
    setState(() {
      results.clear();
      isDiscovering = true;
    });

    _streamSubscription =
        NewFlutterBluetooth.instance.startDiscovery().listen((r) {
      setState(() {
        final existingIndex = results.indexWhere(
            (element) => element.device.address == r.device.address);
        if (existingIndex >= 0)
          results[existingIndex] = r;
        else
          results.add(r);
      });
    });

    _streamSubscription!.onDone(() {
      setState(() {
        isDiscovering = false;
      });
    });
  }

  @override
  void dispose() {
    _streamSubscription?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(isDiscovering ? 'Discovering devices' : 'Discovered devices'),
        actions: [
          isDiscovering
              ? FittedBox(
                  child: Container(
                    margin: EdgeInsets.all(16.0),
                    child: CircularProgressIndicator(
                      valueColor: AlwaysStoppedAnimation<Color>(Colors.white),
                    ),
                  ),
                )
              : IconButton(
                  icon: Icon(Icons.replay),
                  onPressed: _startDiscovery,
                )
        ],
      ),
      body: ListView.builder(
        itemCount: results.length,
        itemBuilder: (context, index) {
          BluetoothDiscoveryResult result = results[index];
          return ListTile(
            title: Text(result.device.name ?? "Unknown Device"),
            subtitle: Text(result.device.address),
            trailing: Text(result.rssi.toString()),
            onTap: () {
              Navigator.pop(context, result.device);
            },
          );
        },
      ),
    );
  }
}
```

### Connecting and Chatting with a Device

```dart
class ChatPage extends StatefulWidget {
  final BluetoothDevice server;
  const ChatPage({required this.server});

  @override
  _ChatPageState createState() => _ChatPageState();
}

class _ChatPageState extends State<ChatPage> {
  BluetoothConnection? connection;
  bool isConnecting = true;
  bool get isConnected => connection?.isConnected ?? false;
  bool isDisconnecting = false;
  
  List<Message> messages = [];
  final TextEditingController textEditingController = TextEditingController();
  final ScrollController scrollController = ScrollController();
  
  @override
  void initState() {
    super.initState();
    
    BluetoothConnection.toAddress(widget.server.address).then((conn) {
      connection = conn;
      setState(() {
        isConnecting = false;
        isDisconnecting = false;
      });
      
      connection!.input!.listen(_onDataReceived).onDone(() {
        if (isDisconnecting) {
          print('Disconnecting locally');
        } else {
          print('Disconnected remotely');
        }
        setState(() {});
      });
    }).catchError((error) {
      print('Cannot connect, exception occurred: $error');
    });
  }
  
  @override
  void dispose() {
    if (isConnected) {
      isDisconnecting = true;
      connection?.dispose();
      connection = null;
    }
    super.dispose();
  }
  
  void _onDataReceived(Uint8List data) {
    // Process received data
    String message = String.fromCharCodes(data);
    setState(() {
      messages.add(Message(1, message));
    });
    
    scrollController.animateTo(
      scrollController.position.maxScrollExtent,
      duration: Duration(milliseconds: 333),
      curve: Curves.easeOut,
    );
  }
  
  void _sendMessage(String text) async {
    text = text.trim();
    textEditingController.clear();
    
    if (text.isNotEmpty) {
      try {
        connection!.output.add(Uint8List.fromList(utf8.encode(text + "\r\n")));
        await connection!.output.allSent;
        
        setState(() {
          messages.add(Message(0, text));
        });
        
        Future.delayed(Duration(milliseconds: 333)).then((_) {
          scrollController.animateTo(
            scrollController.position.maxScrollExtent,
            duration: Duration(milliseconds: 333),
            curve: Curves.easeOut,
          );
        });
      } catch (e) {
        setState(() {});
      }
    }
  }
  
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: isConnecting
            ? Text('Connecting to ${widget.server.name ?? "Unknown"}...')
            : isConnected
                ? Text('Connected to ${widget.server.name ?? "Unknown"}')
                : Text('Disconnected from ${widget.server.name ?? "Unknown"}'),
      ),
      body: SafeArea(
        child: Column(
          children: <Widget>[
            Expanded(
              child: ListView.builder(
                controller: scrollController,
                itemCount: messages.length,
                itemBuilder: (context, index) {
                  final message = messages[index];
                  return Row(
                    mainAxisAlignment: message.whom == 0
                        ? MainAxisAlignment.end
                        : MainAxisAlignment.start,
                    children: [
                      Container(
                        padding: EdgeInsets.all(12),
                        margin: EdgeInsets.symmetric(
                          horizontal: 8,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: message.whom == 0 ? Colors.blue : Colors.grey,
                          borderRadius: BorderRadius.circular(8),
                        ),
                        child: Text(
                          message.text,
                          style: TextStyle(color: Colors.white),
                        ),
                      ),
                    ],
                  );
                },
              ),
            ),
            Container(
              padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.grey.shade200,
              ),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: textEditingController,
                      decoration: InputDecoration(
                        hintText: isConnected ? 'Type a message...' : 'Disconnected',
                        border: InputBorder.none,
                      ),
                      enabled: isConnected,
                    ),
                  ),
                  IconButton(
                    icon: Icon(Icons.send),
                    onPressed: isConnected 
                        ? () => _sendMessage(textEditingController.text)
                        : null,
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class Message {
  int whom; // 0 for sent, 1 for received
  String text;
  Message(this.whom, this.text);
}
```

## Advanced Features

### Managing Bluetooth State

```dart
// Check if Bluetooth is enabled
bool? isEnabled = await NewFlutterBluetooth.instance.isEnabled;

// Request to enable Bluetooth
await NewFlutterBluetooth.instance.requestEnable();

// Request to disable Bluetooth
await NewFlutterBluetooth.instance.requestDisable();

// Open system Bluetooth settings
NewFlutterBluetooth.instance.openSettings();
```

### Working with Bonded Devices

```dart
// Get list of bonded devices
List<BluetoothDevice> bondedDevices = await NewFlutterBluetooth.instance.getBondedDevices();

// Bond with a device
bool? bonded = await NewFlutterBluetooth.instance.bondDeviceAtAddress(address);

// Remove bond
await NewFlutterBluetooth.instance.removeDeviceBondWithAddress(address);
```

### Making Device Discoverable

```dart
// Make device discoverable for 120 seconds
int? discoverableTimeoutSeconds = await NewFlutterBluetooth.instance.requestDiscoverable(120);
```

### Setting Pin for Pairing

```dart
// Auto-accept pairing with PIN code
NewFlutterBluetooth.instance.setPairingRequestHandler((BluetoothPairingRequest request) {
  print("Trying to auto-pair with PIN 1234");
  if (request.pairingVariant == PairingVariant.Pin) {
    return Future.value("1234");
  }
  return Future.value(null);
});

// Clear pairing handler
NewFlutterBluetooth.instance.setPairingRequestHandler(null);
```

## Example Projects

Check out our examples folder for complete implementation of:

1. Basic device discovery
2. Chat application
3. Background data collection
4. Multiple connections management

## Troubleshooting

### Common Issues:

1. **Connection fails immediately**:
   - Ensure the device is paired in system settings
   - Check that you have the correct MAC address
   - Verify permissions are granted

2. **Data reception issues**:
   - Check the data format and encoding
   - Ensure buffer handling is correct

3. **Device not showing in discovery**:
   - Make sure the device is discoverable
   - Check location permissions are granted
   - Verify Bluetooth is enabled

## Notes

- The package is based on the classic Bluetooth protocol (not BLE)
- iOS functionality is more limited due to platform restrictions
- Background operation may require additional configuration

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

This project is licensed under the MIT License - see the LICENSE file for details.