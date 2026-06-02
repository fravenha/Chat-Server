import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;

public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    // --- Fim das variáveis relacionadas com a interface gráfica

    // Variáveis adicionais para comunicação com o servidor
    private Socket socket;
    private BufferedReader in;  // Para ler mensagens do servidor
    private PrintWriter out;     // Para enviar mensagens ao servidor
    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) {
        chatArea.append(message);
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                    chatBox.setText("");
                }
            }
        });
        frame.addWindowListener(new WindowAdapter() {
            public void windowOpened(WindowEvent e) {
                chatBox.requestFocusInWindow();
            }
        });
        // --- Fim da inicialização da interface gráfica

        // Estabelecer conexão com o servidor
        socket = new Socket(server, port);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(), true);
    }

    // Método invocado sempre que o utilizador insere uma mensagem na caixa de entrada
    public void newMessage(String message) throws IOException {
        // Lista de comandos válidos do protocolo
        boolean isCommand = message.equals("/leave") || 
                           message.equals("/bye") ||
                           message.startsWith("/nick ") ||
                           message.startsWith("/join ") ||
                           message.startsWith("/priv ");
        
        // Escape de / inicial: apenas para mensagens que não são comandos válidos
        // Exemplo: "//comentario" vira "///comentario", "/comando_invalido" vira "//comando_invalido"
        if (message.startsWith("/") && !isCommand) {
            message = "/" + message;
        }
        
        // Enviar mensagem ao servidor (termina com \n automático do println)
        out.println(message);
    }

    // Método principal do objeto - cria thread para receber mensagens
    public void run() throws IOException {
        // Thread separada para receber mensagens do servidor
        // Permite receber enquanto o utilizador escreve (não bloqueia a interface)
        Thread receiveThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    String line;
                    // Loop contínuo: lê mensagens do servidor até conexão fechar
                    while ((line = in.readLine()) != null) {
                        processServerMessage(line);
                    }
                } catch (IOException e) {
                    // Conexão fechada pelo servidor
                }
            }
        });
        
        receiveThread.start();
    }
    
    // Processa e mostra mensagens recebidas do servidor
    
    private void processServerMessage(String message) {
        // VERSÃO BÁSICA (35%): Mostra mensagem exatamente como recebida
        //printMessage(message + "\n");
        
        // VERSÃO COM FORMATAÇÃO AMIGÁVEL (5% extra) 
        // OK - Sucesso do comando
        if (message.equals("OK")) {
            printMessage("✓ Comando executado com sucesso\n");
        } 
        // ERROR - Falha do comando
        else if (message.equals("ERROR")) {
            printMessage("✗ Erro ao executar comando\n");
        } 
        // BYE - Desconexão confirmada
        else if (message.equals("BYE")) {
            printMessage("--- Desconectado do servidor ---\n");
            try {
                socket.close();
            } catch (IOException e) {}
            System.exit(0);
        } 
        // MESSAGE nome mensagem - Mensagem de outro utilizador
        else if (message.startsWith("MESSAGE ")) {
            String[] parts = message.substring(8).split(" ", 2);
            if (parts.length == 2) {
                printMessage(parts[0] + ": " + parts[1] + "\n");
            }
        } 
        // NEWNICK nome_antigo nome_novo - Utilizador mudou de nome
        else if (message.startsWith("NEWNICK ")) {
            String[] parts = message.substring(8).split(" ");
            if (parts.length == 2) {
                printMessage("*** " + parts[0] + " mudou de nome para " + parts[1] + " ***\n");
            }
        } 
        // JOINED nome - Novo utilizador entrou na sala
        else if (message.startsWith("JOINED ")) {
            String nick = message.substring(7);
            printMessage("*** " + nick + " entrou na sala ***\n");
        } 
        // LEFT nome - Utilizador saiu da sala
        else if (message.startsWith("LEFT ")) {
            String nick = message.substring(5);
            printMessage("*** " + nick + " saiu da sala ***\n");
        } 
        // PRIVATE emissor mensagem - Mensagem privada
        else if (message.startsWith("PRIVATE ")) {
            String[] parts = message.substring(8).split(" ", 2);
            if (parts.length == 2) {
                printMessage("[PRIVADO] " + parts[0] + ": " + parts[1] + "\n");
            }
        } 
        // Outras mensagens (não deve acontecer se servidor estiver correto)
        else {
            printMessage(message + "\n");
        }
        
    }

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }
}
