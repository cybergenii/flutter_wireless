# flutter_wireless 

[![pub package](https://img.shields.io/pub/v/flutter_wireless.svg)](https://pub.dev/packages/flutter_wireless)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Platform](https://img.shields.io/badge/platform-android%20|%20ios-lightgrey.svg)](https://pub.dev/packages/flutter_wireless)

A comprehensive Flutter package for Bluetooth Serial communication that simplifies device discovery, connection management, and data transfer between Flutter applications and Bluetooth devices.

## 🚀 Features

- ✅ Device discovery and scanning
- ✅ Connect to paired and unpaired Bluetooth devices
- ✅ Send and receive data over Bluetooth
- ✅ Bond/unbond with devices
- ✅ Background connection management
- ✅ Support for multiple simultaneous connections
- ✅ Bluetooth state monitoring and management
- ✅ Auto-pairing with PIN codes
- ✅ Cross-platform support (Android/iOS)

## 📋 Table of Contents

- [Installation](#installation)
- [Permissions](#permissions)
- [Quick Start](#quick-start)
- [Usage Examples](#usage-examples)
- [API Reference](#api-reference)
- [Advanced Features](#advanced-features)
- [Example Projects](#example-projects)
- [Troubleshooting](#troubleshooting)
- [Contributing](#contributing)

## 📦 Installation

Add the following to your `pubspec.yaml` file:

```yaml
dependencies:
  flutter_wireless: ^latest_version
```

Then run:
```bash
flutter pub get
```

## 🔐 Permissions

### Android

Add these permissions to your `android/app/src/main/AndroidManifest.xml`:

```xml
<!-- Required for all Android versions -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Required for Android 12+ (API level 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" 
    android:usesPermissionFlags="neverForLocation" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

### iOS

Add these descriptions to your `ios/Runner/Info.plist`:

```xml
<key>NSBluetoothAlwaysUsageDescription</key>
<string>This app needs Bluetooth access to communicate with external devices</string>
<key>NSBluetoothPeripheralUsageDescription</key>
<string>This app needs Bluetooth access to communicate with external devices</string>
```

## 🚀 Quick Start

Here's a minimal example to get you started:

```dart
import 'package:flutter/material.dart';
import 'package:flutter_wireless/flutter_wireless.dart';

class BluetoothExample extends StatefulWidget {
  @override
  _BluetoothExampleState createState() => _BluetoothExampleState();
}

class _BluetoothExampleState extends State<BluetoothExample> {
  BluetoothState _bluetoothState = BluetoothState.UNKNOWN;
  
  @override
  void initState() {
    super.initState();
    _initBluetooth();
  }

  void _initBluetooth() async {
    // Get current state
    final state = await NewFlutterBluetooth.instance.state;
    setState(() => _bluetoothState = state);
    
    // Listen for state changes
    NewFlutterBluetooth.instance.onStateChanged().listen((state) {
      setState(() => _bluetoothState = state);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Bluetooth Example')),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('Bluetooth State: ${_bluetoothState.toString()}'),
            SizedBox(height: 20),
            ElevatedButton(
              onPressed: () async {
                await NewFlutterBluetooth.instance.requestEnable();
              },
              child: Text('Enable Bluetooth'),
            ),
          ],
        ),
      ),
    );
  }
}
```

## 📚 Usage Examples

### Device Discovery

```dart
class DeviceDiscovery extends StatefulWidget {
  @override
  _DeviceDiscoveryState createState() => _DeviceDiscoveryState();
}

class _DeviceDiscoveryState extends State<DeviceDiscovery> {
  List<BluetoothDiscoveryResult> _devices = [];
  bool _isDiscovering = false;
  StreamSubscription<BluetoothDiscoveryResult>? _discoverySubscription;

  void _startDiscovery() {
    setState(() {
      _devices.clear();
      _isDiscovering = true;
    });

    _discoverySubscription = NewFlutterBluetooth.instance
        .startDiscovery()
        .listen(_onDeviceDiscovered);

    _discoverySubscription!.onDone(() {
      setState(() => _isDiscovering = false);
    });
  }

  void _onDeviceDiscovered(BluetoothDiscoveryResult result) {
    setState(() {
      final index = _devices.indexWhere(
        (device) => device.device.address == result.device.address,
      );
      
      if (index >= 0) {
        _devices[index] = result;
      } else {
        _devices.add(result);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Text(_isDiscovering ? 'Discovering...' : 'Devices Found'),
        actions: [
          if (_isDiscovering)
            Padding(
              padding: EdgeInsets.all(16),
              child: SizedBox(
                width: 20,
                height: 20,
                child: CircularProgressIndicator(strokeWidth: 2),
              ),
            )
          else
            IconButton(
              icon: Icon(Icons.refresh),
              onPressed: _startDiscovery,
            ),
        ],
      ),
      body: ListView.builder(
        itemCount: _devices.length,
        itemBuilder: (context, index) {
          final result = _devices[index];
          return Card(
            child: ListTile(
              leading: Icon(Icons.bluetooth),
              title: Text(result.device.name ?? 'Unknown Device'),
              subtitle: Text('${result.device.address} • RSSI: ${result.rssi}'),
              trailing: Icon(Icons.arrow_forward_ios),
              onTap: () => _connectToDevice(result.device),
            ),
          );
        },
      ),
    );
  }

  void _connectToDevice(BluetoothDevice device) {
    Navigator.push(
      context,
      MaterialPageRoute(
        builder: (context) => ChatScreen(device: device),
      ),
    );
  }

  @override
  void dispose() {
    _discoverySubscription?.cancel();
    super.dispose();
  }
}
```

### Connecting and Data Transfer

```dart
class ChatScreen extends StatefulWidget {
  final BluetoothDevice device;
  
  const ChatScreen({Key? key, required this.device}) : super(key: key);

  @override
  _ChatScreenState createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  BluetoothConnection? _connection;
  bool _isConnecting = true;
  bool get _isConnected => _connection?.isConnected ?? false;
  
  final List<ChatMessage> _messages = [];
  final TextEditingController _messageController = TextEditingController();
  final ScrollController _scrollController = ScrollController();
  
  @override
  void initState() {
    super.initState();
    _connectToDevice();
  }

  Future<void> _connectToDevice() async {
    try {
      final connection = await BluetoothConnection.toAddress(widget.device.address);
      
      setState(() {
        _connection = connection;
        _isConnecting = false;
      });
      
      _connection!.input!.listen(_onDataReceived).onDone(() {
        if (mounted) {
          setState(() {});
          _showSnackBar('Device disconnected');
        }
      });
      
      _showSnackBar('Connected to ${widget.device.name}');
    } catch (error) {
      setState(() => _isConnecting = false);
      _showSnackBar('Connection failed: $error');
    }
  }

  void _onDataReceived(Uint8List data) {
    final message = String.fromCharCodes(data);
    setState(() {
      _messages.add(ChatMessage(text: message, isOutgoing: false));
    });
    _scrollToBottom();
  }

  Future<void> _sendMessage() async {
    final text = _messageController.text.trim();
    if (text.isEmpty || !_isConnected) return;
    
    _messageController.clear();
    
    try {
      _connection!.output.add(Uint8List.fromList(utf8.encode('$text\r\n')));
      await _connection!.output.allSent;
      
      setState(() {
        _messages.add(ChatMessage(text: text, isOutgoing: true));
      });
      _scrollToBottom();
    } catch (e) {
      _showSnackBar('Failed to send message: $e');
    }
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scrollController.hasClients) {
        _scrollController.animateTo(
          _scrollController.position.maxScrollExtent,
          duration: Duration(milliseconds: 300),
          curve: Curves.easeOut,
        );
      }
    });
  }

  void _showSnackBar(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(widget.device.name ?? 'Unknown Device'),
            Text(
              _isConnecting ? 'Connecting...' : (_isConnected ? 'Connected' : 'Disconnected'),
              style: TextStyle(fontSize: 12, color: Colors.white70),
            ),
          ],
        ),
        backgroundColor: _isConnected ? Colors.green : Colors.red,
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              controller: _scrollController,
              itemCount: _messages.length,
              itemBuilder: (context, index) {
                final message = _messages[index];
                return ChatBubble(message: message);
              },
            ),
          ),
          _buildMessageInput(),
        ],
      ),
    );
  }

  Widget _buildMessageInput() {
    return Container(
      padding: EdgeInsets.all(8),
      decoration: BoxDecoration(
        color: Colors.grey[100],
        border: Border(top: BorderSide(color: Colors.grey[300]!)),
      ),
      child: Row(
        children: [
          Expanded(
            child: TextField(
              controller: _messageController,
              decoration: InputDecoration(
                hintText: _isConnected ? 'Type a message...' : 'Not connected',
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(25),
                ),
                contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              ),
              enabled: _isConnected,
              onSubmitted: (_) => _sendMessage(),
            ),
          ),
          SizedBox(width: 8),
          FloatingActionButton(
            mini: true,
            onPressed: _isConnected ? _sendMessage : null,
            child: Icon(Icons.send),
          ),
        ],
      ),
    );
  }

  @override
  void dispose() {
    _connection?.dispose();
    _messageController.dispose();
    _scrollController.dispose();
    super.dispose();
  }
}

class ChatMessage {
  final String text;
  final bool isOutgoing;
  final DateTime timestamp;
  
  ChatMessage({
    required this.text,
    required this.isOutgoing,
    DateTime? timestamp,
  }) : timestamp = timestamp ?? DateTime.now();
}

class ChatBubble extends StatelessWidget {
  final ChatMessage message;
  
  const ChatBubble({Key? key, required this.message}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Row(
        mainAxisAlignment: message.isOutgoing 
            ? MainAxisAlignment.end 
            : MainAxisAlignment.start,
        children: [
          Container(
            constraints: BoxConstraints(
              maxWidth: MediaQuery.of(context).size.width * 0.75,
            ),
            padding: EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: message.isOutgoing ? Colors.blue : Colors.grey[300],
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              message.text,
              style: TextStyle(
                color: message.isOutgoing ? Colors.white : Colors.black87,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
```

## 🔧 API Reference

### Core Methods

| Method | Description | Returns |
|--------|-------------|---------|
| `NewFlutterBluetooth.instance.state` | Get current Bluetooth state | `Future<BluetoothState>` |
| `NewFlutterBluetooth.instance.isEnabled` | Check if Bluetooth is enabled | `Future<bool?>` |
| `NewFlutterBluetooth.instance.requestEnable()` | Request to enable Bluetooth | `Future<bool?>` |
| `NewFlutterBluetooth.instance.requestDisable()` | Request to disable Bluetooth | `Future<bool?>` |
| `NewFlutterBluetooth.instance.startDiscovery()` | Start device discovery | `Stream<BluetoothDiscoveryResult>` |
| `NewFlutterBluetooth.instance.getBondedDevices()` | Get paired devices | `Future<List<BluetoothDevice>>` |

### Connection Management

```dart
// Connect to a device
BluetoothConnection connection = await BluetoothConnection.toAddress('XX:XX:XX:XX:XX:XX');

// Check connection status
bool isConnected = connection.isConnected;

// Send data
connection.output.add(Uint8List.fromList(utf8.encode('Hello')));
await connection.output.allSent;

// Listen for incoming data
connection.input!.listen((Uint8List data) {
  String receivedData = String.fromCharCodes(data);
  print('Received: $receivedData');
});

// Close connection
connection.dispose();
```

## ⚡ Advanced Features

### Auto-Pairing with PIN

```dart
NewFlutterBluetooth.instance.setPairingRequestHandler((BluetoothPairingRequest request) {
  if (request.pairingVariant == PairingVariant.Pin) {
    return Future.value("1234"); // Your PIN code
  }
  return Future.value(null);
});
```

### Making Device Discoverable

```dart
// Make device discoverable for 2 minutes
int? timeout = await NewFlutterBluetooth.instance.requestDiscoverable(120);
```

### Working with Bonded Devices

```dart
// Get all paired devices
List<BluetoothDevice> pairedDevices = await NewFlutterBluetooth.instance.getBondedDevices();

// Bond with a new device
bool? success = await NewFlutterBluetooth.instance.bondDeviceAtAddress('XX:XX:XX:XX:XX:XX');

// Remove pairing
await NewFlutterBluetooth.instance.removeDeviceBondWithAddress('XX:XX:XX:XX:XX:XX');
```

## 📱 Platform Support

| Feature | Android | iOS |
|---------|---------|-----|
| Device Discovery | ✅ | ⚠️ Limited |
| Connection Management | ✅ | ✅ |
| Data Transfer | ✅ | ✅ |
| Auto-Pairing | ✅ | ❌ |
| Background Operation | ✅ | ⚠️ Limited |

## 🛠️ Troubleshooting

### Common Issues and Solutions

#### Connection Problems
```
Problem: Connection fails immediately
Solutions:
• Ensure device is paired in system Bluetooth settings
• Verify correct MAC address format (XX:XX:XX:XX:XX:XX)
• Check that target device is powered on and discoverable
• Grant all required permissions
```

#### Permission Issues
```
Problem: App crashes or cannot discover devices
Solutions:
• Add all required permissions to AndroidManifest.xml
• Request runtime permissions for location (Android 6+)
• For Android 12+, ensure BLUETOOTH_SCAN and BLUETOOTH_CONNECT are granted
```

#### Data Transfer Issues
```
Problem: Data not being received correctly
Solutions:
• Check data encoding (UTF-8 vs ASCII)
• Verify message termination characters (\r\n)
• Implement proper buffer handling for large messages
• Check for proper connection status before sending
```

### Debug Mode

Enable debug logging to troubleshoot issues:

```dart
// Add this to see detailed logs
import 'dart:developer' as developer;

void debugPrint(String message) {
  developer.log(message, name: 'flutter_wireless');
}
```

## 💡 Best Practices

1. **Always check connection status** before sending data
2. **Handle connection drops gracefully** with proper error handling
3. **Implement connection timeouts** to avoid hanging connections
4. **Use proper data encoding** (UTF-8 recommended)
5. **Request permissions at runtime** for better user experience
6. **Test on real devices** - Bluetooth doesn't work on simulators

## 📖 Example Projects

Complete example applications are available in the `/example` folder:

- **[Basic Discovery](example/basic_discovery/)** - Simple device scanning
- **[Chat App](example/chat_app/)** - Full-featured messaging app  
- **[Sensor Monitor](example/sensor_monitor/)** - Real-time data collection
- **[Multi-Connection](example/multi_connection/)** - Managing multiple devices

## 🤝 Contributing

We welcome contributions! Please see our [Contributing Guide](CONTRIBUTING.md) for details.

### Development Setup

```bash
git clone https://github.com/yourusername/flutter_wireless.git
cd flutter_wireless
flutter pub get
cd example
flutter run
```

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 📞 Support

- 📧 **Email**: your.email@example.com
- 🐛 **Issues**: [GitHub Issues](https://github.com/yourusername/flutter_wireless/issues)
- 💬 **Discord**: [Join our community](https://discord.gg/your-invite)
- 📖 **Documentation**: [Full API docs](https://pub.dev/documentation/flutter_wireless/)

---

<div align="center">
  <p>Made with ❤️ by <a href="https://github.com/yourusername">Your Name</a></p>
  <p>
    <a href="https://pub.dev/packages/flutter_wireless">
      <img src="https://img.shields.io/pub/v/flutter_wireless.svg" alt="pub package">
    </a>
    <a href="https://opensource.org/licenses/MIT">
      <img src="https://img.shields.io/badge/License-MIT-yellow.svg" alt="License: MIT">
    </a>
    <a href="https://github.com/yourusername/flutter_wireless/stargazers">
      <img src="https://img.shields.io/github/stars/yourusername/flutter_wireless.svg?style=social" alt="GitHub stars">
    </a>
  </p>
</div>
