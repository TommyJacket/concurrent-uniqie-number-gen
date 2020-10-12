import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class main {
    private static final int NUMBER_OF_THREADS = 2;
    private static final int UPPER_VALUE = Integer.MAX_VALUE;

    public static void main(String[] args) throws IOException {
        Vault vault = new Vault();

        HttpServer server = HttpServer.create(new InetSocketAddress(8000), 0);
        server.createContext("/getNumber", new GetRequestHandler(vault));
        Executor executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);
        server.setExecutor(executor);
        server.start();
    }

    private static class Vault {
        private List<Integer> generatedNumbers = new ArrayList<>();
        private ReentrantReadWriteLock reentrantReadWriteLock = new ReentrantReadWriteLock();
        private Lock readLock = reentrantReadWriteLock.readLock();
        private Lock writeLock = reentrantReadWriteLock.writeLock();

        public void addNumber(Integer generatedNumber) {
            writeLock.lock();
            try {
                generatedNumbers.add(generatedNumber);
            } finally {
                writeLock.unlock();
            }
        }

        public boolean alreadyGenerated(Integer generatedNumber) {
            readLock.lock();
            try {
                return generatedNumbers.contains(generatedNumber);
            } finally {
                readLock.unlock();
            }
        }

        public boolean allPossibleNumbersGenerated() {
            readLock.lock();
            try {
                return generatedNumbers.size() == UPPER_VALUE;
            } finally {
                readLock.unlock();
            }
        }
    }

    private static class GetRequestHandler implements HttpHandler {

        private final Vault vault;

        public GetRequestHandler(Vault vault) {
            this.vault = vault;
        }

        @Override
        public void handle(HttpExchange exchange) {
            final Integer[] result = {0};
            Thread requestThread = new Thread(() -> {
                boolean success = false;
                if (vault.allPossibleNumbersGenerated()) {
                    result[0] = -1;
                    return;
                }
                while (!success) {
                    Integer generatedNumber = ThreadLocalRandom.current().nextInt(UPPER_VALUE);
                    if (!vault.alreadyGenerated(generatedNumber)) {
                        vault.addNumber(generatedNumber);
                        result[0] = generatedNumber;
                        success = true;
                    }
                }
            });
            requestThread.start();
            try {
                requestThread.join();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            String respJson = String.format("{\"id\": %s}", result[0]);
            byte[] response = respJson.getBytes();
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, response.length);
                OutputStream outputStream = exchange.getResponseBody();
                outputStream.write(response);
                outputStream.close();
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }
}
