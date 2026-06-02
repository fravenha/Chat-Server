# Chat-Server
A multithreaded Java chat application implementing a client-server architecture with chat rooms, private messaging, and real-time communication using sockets and non-blocking I/O.

# Java Chat Application

A multithreaded Java chat application implementing a client-server architecture with chat rooms, private messaging, and real-time communication using sockets and non-blocking I/O.

---

# Features

- Real-time chat communication
- Multiple chat rooms
- Unique nicknames for users
- Private messaging between users
- Join and leave room notifications
- Nickname change notifications
- Graphical client interface using Java Swing
- Non-blocking server implementation using Java NIO
- UTF-8 message encoding support

---

# Technologies Used

- Java
- Java NIO (Non-blocking I/O)
- Java Sockets
- Java Swing
- Multithreading

---

# Project Structure

```text
.
├── ChatServer.java
├── ChatClient.java
└── grupo.txt
How It Works

The project follows a client-server architecture:

The ChatServer handles:
Client connections
Room management
Message broadcasting
Private messaging
Nickname validation
The ChatClient provides:
A graphical interface
Command input
Real-time message reception
User-friendly message formatting
Compilation

Compile both files:

javac ChatServer.java
javac ChatClient.java

Or:

javac *.java
Running the Server

Start the server by providing a port number:

java ChatServer 12345

Example:

java ChatServer 8080
Running the Client

Connect to the server using:

java ChatClient <server-ip> <port>

Example:

java ChatClient localhost 8080
Available Commands
Set Nickname
/nick <nickname>

Example:

/nick Flavia
Join a Chat Room
/join <room>

Example:

/join general
Leave Current Room
/leave
Send Private Message
/priv <nickname> <message>

Example:

/priv John Hello!
Disconnect from Server
/bye
Protocol Messages

The server uses a simple text-based protocol:

Message Type	Description
OK	Command executed successfully
ERROR	Invalid command or operation
MESSAGE	Public room message
PRIVATE	Private message
JOINED	User joined the room
LEFT	User left the room
NEWNICK	User changed nickname
BYE	Disconnection confirmation
Example Usage
/nick Alice
/join general
Hello everyone!
/priv Bob Hi Bob!
/leave
/bye
Technical Details
Server
Uses Selector for multiplexing connections
Handles multiple clients concurrently
Stores:
Connected clients
Chat rooms
User states
Client
Uses a dedicated receiving thread
Swing-based graphical interface
Supports formatted system messages
Possible Improvements
Message history persistence
Authentication system
File sharing
End-to-end encryption
User list visualization
Multiple room tabs
Emoji support
Authors
Flávia Queiroz
Sara Soares
