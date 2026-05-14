package dev.httpserver.blocking;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class BlockingEchoServer {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    public static void main(String[] args) {
        /*
         * ServerSocket은 서버 쪽 TCP 소켓입니다.
         *
         * new ServerSocket(PORT, backlog, bindAddr)
         * - PORT: 서버가 열 포트입니다. 여기서는 localhost:8080으로 접속합니다.
         * - backlog: 동시에 연결 요청이 몰렸을 때 OS가 대기시킬 연결 큐의 크기 힌트입니다.
         * - bindAddr: 어떤 네트워크 인터페이스에 바인딩할지 정합니다.
         *
         * try-with-resources를 사용하면 서버가 종료될 때 ServerSocket.close()가 자동 호출됩니다.
         */
        try (ServerSocket serverSocket = new ServerSocket(
                PORT,
                50,
                InetAddress.getByName(HOST)
        )) {
            System.out.println("[server] Blocking Echo Server started");
            System.out.println("[server] Listening on " + HOST + ":" + PORT);
            System.out.println("[server] Test with: nc localhost 8080");

            /*
             * 서버는 계속 실행되어야 하므로 무한 루프로 클라이언트 연결을 받습니다.
             *
             * serverSocket.accept()는 blocking 메서드입니다.
             * 클라이언트가 접속하기 전까지 이 줄에서 현재 스레드가 멈춰 기다립니다.
             */
            while (true) {
                Socket clientSocket = serverSocket.accept();

                /*
                 * accept()가 반환한 Socket은 "방금 연결된 클라이언트와 통신하는 통로"입니다.
                 * 여기서는 코드를 단순하게 유지하기 위해 한 번에 한 클라이언트만 처리합니다.
                 *
                 * 즉, 첫 번째 클라이언트가 연결된 상태로 입력을 계속 보내지 않고 가만히 있으면,
                 * 서버는 handleClient() 안에서 그 클라이언트 입력을 기다립니다.
                 * 그동안 다음 클라이언트의 accept()로 돌아가지 못합니다.
                 *
                 * 이것이 Blocking I/O 서버의 가장 기본적인 특징입니다.
                 */
                handleClient(clientSocket);
            }
        } catch (IOException e) {
            System.err.println("[server] Server error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void handleClient(Socket clientSocket) {
        String clientAddress = clientSocket.getInetAddress().getHostAddress();
        int clientPort = clientSocket.getPort();

        System.out.println("[server] Client connected: " + clientAddress + ":" + clientPort);

        /*
         * Socket도 close가 필요한 자원입니다.
         * try-with-resources에 clientSocket을 넣어두면 클라이언트 처리가 끝난 뒤 자동으로 닫힙니다.
         */
        try (clientSocket;
             /*
              * InputStream은 클라이언트 -> 서버 방향으로 들어오는 바이트 흐름입니다.
              * TCP는 문자열이 아니라 바이트를 주고받습니다.
              */
             InputStream inputStream = clientSocket.getInputStream();
             /*
              * OutputStream은 서버 -> 클라이언트 방향으로 나가는 바이트 흐름입니다.
              */
             OutputStream outputStream = clientSocket.getOutputStream();
             /*
              * InputStreamReader는 바이트를 문자로 변환합니다.
              * 네트워크에서 받은 바이트를 UTF-8 문자열로 해석하기 위해 사용합니다.
              */
             InputStreamReader inputStreamReader = new InputStreamReader(inputStream, "UTF-8");
             /*
              * BufferedReader는 문자 입력을 버퍼링하고, readLine()으로 한 줄씩 읽게 해줍니다.
              */
             BufferedReader reader = new BufferedReader(inputStreamReader);
             /*
              * OutputStreamWriter는 문자를 UTF-8 바이트로 변환해 OutputStream에 씁니다.
              */
             OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream, "UTF-8");
             /*
              * BufferedWriter는 문자 출력을 버퍼링합니다.
              * write()만 호출하면 버퍼에만 쌓일 수 있으므로 flush()로 실제 전송을 밀어내야 합니다.
              */
             BufferedWriter writer = new BufferedWriter(outputStreamWriter)) {

            String line;

            /*
             * reader.readLine()도 blocking 메서드입니다.
             *
             * 클라이언트가 한 줄을 보낼 때까지 현재 스레드는 여기서 멈춥니다.
             * nc에서 글자를 입력한 뒤 Enter를 누르면 줄 끝 개행이 전송되고 readLine()이 반환됩니다.
             *
             * 클라이언트가 연결을 종료하면 readLine()은 null을 반환합니다.
             */
            while ((line = reader.readLine()) != null) {
                System.out.println("[server] Received from " + clientAddress + ":" + clientPort + " -> " + line);

                /*
                 * Echo Server이므로 받은 문자열을 그대로 다시 보냅니다.
                 * readLine()은 줄 끝 개행 문자를 제외하고 반환하므로,
                 * 클라이언트 화면에서 줄 단위로 보기 좋게 newLine()을 다시 붙입니다.
                 */
                writer.write(line);
                writer.newLine();

                /*
                 * BufferedWriter는 내부 버퍼에 데이터를 모아둘 수 있습니다.
                 * flush()를 호출해야 지금 쓴 echo 응답이 클라이언트에게 즉시 전송됩니다.
                 */
                writer.flush();
            }

            System.out.println("[server] Client closed connection: " + clientAddress + ":" + clientPort);
        } catch (IOException e) {
            System.err.println("[server] Client error "
                    + clientAddress + ":" + clientPort + " -> " + e.getMessage());
        }
    }
}
