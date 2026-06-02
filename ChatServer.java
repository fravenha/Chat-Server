import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class ChatServer {
    
    // Buffer para leitura de dados das sockets
    private static final ByteBuffer buffer = ByteBuffer.allocate(16384);
    
    // Codificação UTF-8 para converter bytes em texto e vice-versa
    private static final Charset charset = Charset.forName("UTF8");
    private static final CharsetDecoder decoder = charset.newDecoder();
    private static final CharsetEncoder encoder = charset.newEncoder();
    
    // Mapas para gestão de clientes e salas
    private static Map<SocketChannel, ClientState> clients = new HashMap<>();
    private static Map<String, Set<SocketChannel>> rooms = new HashMap<>();
    
    // Estado de cada cliente: nickname, sala, estado (init/outside/inside), buffer de mensagens parciais
    static class ClientState {
        String nickname = null;
        String room = null;
        String state = "init"; // init, outside, inside
        StringBuilder buffer = new StringBuilder(); // Buffer para mensagens incompletas
        
        public ClientState() {}
    }
    
    public static void main(String args[]) throws Exception {
        int port = Integer.parseInt(args[0]);
        
        try {
            // Criar canal do servidor em modo não-bloqueante
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.configureBlocking(false);
            ServerSocket ss = ssc.socket();
            InetSocketAddress isa = new InetSocketAddress(port);
            ss.bind(isa);
            
            // Criar selector para multiplexing
            Selector selector = Selector.open();
            ssc.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("Listening on port " + port);
            
            // Loop principal do servidor
            while (true) {
                int num = selector.select();
                if (num == 0) continue;
                
                Set<SelectionKey> keys = selector.selectedKeys();
                Iterator<SelectionKey> it = keys.iterator();
                
                while (it.hasNext()) {
                    SelectionKey key = it.next();
                    
                    // Nova conexão
                    if (key.isAcceptable()) {
                        Socket s = ss.accept();
                        System.out.println("Got connection from " + s);
                        
                        SocketChannel sc = s.getChannel();
                        sc.configureBlocking(false);
                        sc.register(selector, SelectionKey.OP_READ);
                        
                        // Criar estado inicial para o novo cliente
                        clients.put(sc, new ClientState());
                        
                    // Dados disponíveis para leitura
                    } else if (key.isReadable()) {
                        SocketChannel sc = null;
                        
                        try {
                            sc = (SocketChannel) key.channel();
                            boolean ok = processInput(sc);
                            
                            // Se retornou false, fechar conexão
                            if (!ok) {
                                key.cancel();
                                closeClient(sc);
                            }
                            
                        } catch (IOException ie) {
                            key.cancel();
                            closeClient(sc);
                        }
                    }
                }
                keys.clear();
            }
        } catch (IOException ie) {
            System.err.println(ie);
        }
    }
    
    // Lê dados da socket e processa mensagens completas (terminadas em \n)
    private static boolean processInput(SocketChannel sc) throws IOException {
        buffer.clear();
        int bytesRead = sc.read(buffer);
        buffer.flip();
        
        // Se não leu nada, cliente desconectou
        if (bytesRead <= 0) {
            return false;
        }
        
        ClientState client = clients.get(sc);
        if (client == null) return false;
        
        // Adicionar dados ao buffer do cliente
        String data = decoder.decode(buffer).toString();
        client.buffer.append(data);
        
        // Processar todas as mensagens completas (terminadas em \n)
        String bufferContent = client.buffer.toString();
        int newlineIndex;
        
        while ((newlineIndex = bufferContent.indexOf('\n')) != -1) {
            String message = bufferContent.substring(0, newlineIndex).trim();
            bufferContent = bufferContent.substring(newlineIndex + 1);
            
            if (!message.isEmpty()) {
                processMessage(sc, client, message);
            }
        }
        
        // Guardar o que sobrou (mensagem incompleta)
        client.buffer = new StringBuilder(bufferContent);
        return true;
    }
    
    // Processa um comando ou mensagem do cliente
    private static void processMessage(SocketChannel sc, ClientState client, String message) throws IOException {
        
        // Comando /nick - escolher ou mudar nickname
        if (message.startsWith("/nick ")) {
            String newNick = message.substring(6).trim();
            
            // Verificar se o nick está disponível
            if (newNick.isEmpty() || isNickInUse(newNick, sc)) {
                sendMessage(sc, "ERROR\n");
            } else {
                String oldNick = client.nickname;
                client.nickname = newNick;
                
                // Transições de estado conforme especificação
                if (client.state.equals("init")) {
                    client.state = "outside";
                    sendMessage(sc, "OK\n");
                } else if (client.state.equals("outside")) {
                    sendMessage(sc, "OK\n");
                } else if (client.state.equals("inside")) {
                    sendMessage(sc, "OK\n");
                    // Notificar outros na sala sobre mudança de nome
                    broadcastToRoom(client.room, "NEWNICK " + oldNick + " " + newNick + "\n", sc);
                }
            }
        }
        // Comando /join - entrar numa sala
        else if (message.startsWith("/join ")) {
            String roomName = message.substring(6).trim();
            
            // Só pode entrar em sala se já tiver nickname
            if (client.state.equals("init")) {
                sendMessage(sc, "ERROR\n");
            } else {
                String oldRoom = client.room;
                
                // Se já estava numa sala, sair dela
                if (client.state.equals("inside")) {
                    broadcastToRoom(oldRoom, "LEFT " + client.nickname + "\n", sc);
                    rooms.get(oldRoom).remove(sc);
                    if (rooms.get(oldRoom).isEmpty()) {
                        rooms.remove(oldRoom);
                    }
                }
                
                // Entrar na nova sala
                client.room = roomName;
                client.state = "inside";
                
                // Criar sala se não existir
                if (!rooms.containsKey(roomName)) {
                    rooms.put(roomName, new HashSet<>());
                }
                
                sendMessage(sc, "OK\n");
                // Notificar outros na sala sobre nova entrada
                broadcastToRoom(roomName, "JOINED " + client.nickname + "\n", sc);
                rooms.get(roomName).add(sc);
            }
        }
        // Comando /leave - sair da sala atual
        else if (message.equals("/leave")) {
            if (client.state.equals("inside")) {
                sendMessage(sc, "OK\n");
                broadcastToRoom(client.room, "LEFT " + client.nickname + "\n", sc);
                
                // Remover da sala
                rooms.get(client.room).remove(sc);
                if (rooms.get(client.room).isEmpty()) {
                    rooms.remove(client.room);
                }
                
                client.room = null;
                client.state = "outside";
            } else {
                sendMessage(sc, "ERROR\n");
            }
        }
        // Comando /bye - desconectar do servidor
        else if (message.equals("/bye")) {
            // Se estava numa sala, notificar outros
            if (client.state.equals("inside")) {
                broadcastToRoom(client.room, "LEFT " + client.nickname + "\n", sc);
                rooms.get(client.room).remove(sc);
                if (rooms.get(client.room).isEmpty()) {
                    rooms.remove(client.room);
                }
                client.room = null;
                client.state = "outside";
            }
            sendMessage(sc, "BYE\n");
            closeClient(sc);
        }
        // Comando /priv - mensagem privada (10% extra)
        else if (message.startsWith("/priv ")) {
            String[] parts = message.substring(6).split(" ", 2);
            if (parts.length < 2) {
                sendMessage(sc, "ERROR\n");
                return;
            }
            
            String targetNick = parts[0];
            String privMessage = parts[1];
            
            // Procurar destinatário pelo nickname
            SocketChannel targetSc = findClientByNick(targetNick);
            if (targetSc == null) {
                sendMessage(sc, "ERROR\n"); // Utilizador não existe
            } else {
                sendMessage(sc, "OK\n");
                sendMessage(targetSc, "PRIVATE " + client.nickname + " " + privMessage + "\n");
            }
        }
        // Mensagem simples (não é comando)
        else {
            // Só pode enviar mensagens se estiver numa sala
            if (client.state.equals("inside")) {
                // Processar escape de / inicial
                String actualMessage = message;
                if (message.startsWith("/")) {
                    actualMessage = message.substring(1); // Remove o / extra
                }
                
                // Difundir mensagem para todos na sala (incluindo o próprio)
                String msgToSend = "MESSAGE " + client.nickname + " " + actualMessage + "\n";
                broadcastToRoom(client.room, msgToSend, null);
            } else {
                sendMessage(sc, "ERROR\n");
            }
        }
    }
    
    // Verifica se um nickname já está em uso por outro cliente
    private static boolean isNickInUse(String nick, SocketChannel exclude) {
        for (Map.Entry<SocketChannel, ClientState> entry : clients.entrySet()) {
            if (entry.getKey() != exclude && 
                entry.getValue().nickname != null && 
                entry.getValue().nickname.equals(nick)) {
                return true;
            }
        }
        return false;
    }
    
    // Encontra um cliente pelo nickname
    private static SocketChannel findClientByNick(String nick) {
        for (Map.Entry<SocketChannel, ClientState> entry : clients.entrySet()) {
            if (entry.getValue().nickname != null && 
                entry.getValue().nickname.equals(nick)) {
                return entry.getKey();
            }
        }
        return null;
    }
    
    // Envia mensagem para todos os clientes numa sala (exceto exclude)
    private static void broadcastToRoom(String roomName, String message, SocketChannel exclude) throws IOException {
        Set<SocketChannel> roomClients = rooms.get(roomName);
        if (roomClients == null) return;
        
        for (SocketChannel sc : roomClients) {
            if (sc != exclude) {
                sendMessage(sc, message);
            }
        }
    }
    
    // Envia uma mensagem para um cliente específico
    private static void sendMessage(SocketChannel sc, String message) throws IOException {
        ByteBuffer buf = encoder.encode(CharBuffer.wrap(message));
        while (buf.hasRemaining()) {
            sc.write(buf);
        }
    }
    
    // Fecha a conexão com um cliente e limpa seu estado
    private static void closeClient(SocketChannel sc) {
        ClientState client = clients.get(sc);
        
        // Se estava numa sala, notificar outros
        if (client != null && client.state.equals("inside")) {
            try {
                broadcastToRoom(client.room, "LEFT " + client.nickname + "\n", sc);
                rooms.get(client.room).remove(sc);
                if (rooms.get(client.room).isEmpty()) {
                    rooms.remove(client.room);
                }
            } catch (IOException e) {
                // Ignore
            }
        }
        
        // Remover cliente da lista
        clients.remove(sc);
        
        // Fechar socket
        try {
            Socket s = sc.socket();
            System.out.println("Closing connection to " + s);
            sc.close();
            s.close();
        } catch (IOException ie) {
            System.err.println("Error closing socket: " + ie);
        }
    }
}