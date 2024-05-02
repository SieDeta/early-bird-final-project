package id.my.deta.simplewebserver;

import javafx.application.Application;
import javafx.geometry.Insets; // jarak antara elemen-elemen
import javafx.scene.Scene; // kelas scene menampilkan konten tampilan JavaFX di jendela aplikasi
import javafx.scene.control.*; // semua kelas kontrol seperti Button, Label, TextField
import javafx.scene.layout.GridPane; // menata elemen-elemen tampilan ke dalam grid
import javafx.stage.Stage; // jendela utama aplikasi JavaFX.

import java.io.*; // operasi input dan output, seperti membaca dan menulis file
import java.net.ServerSocket; // membuat soket server yang mendengarkan koneksi dari klien
import java.net.Socket; // membuat koneksi soket antara klien dan server.
import java.nio.file.Files; // file dan direktori
import java.nio.file.Path; // jalur file 
import java.nio.file.Paths;
import java.text.SimpleDateFormat; // memformat dan menguraikan tanggal
import java.util.Date; // merepresentasikan waktu dan tanggal
import java.util.Properties;

public class App extends Application {

    private TextArea logTextArea;
    private int port;
    private String webDirectory;
    private String logDirectory;
    private ServerSocket serverSocket;
    private boolean serverRunning = false;

    private static final String CONFIG_FILE = "config.properties"; //menyimpan konfigurasi port server, direktori web, direktori log
    
    // memulai aplikasi JavaFX
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Simple Web Server");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(10, 10, 10, 10));
        grid.setVgap(5);
        grid.setHgap(5);

        Label portLabel = new Label("Port:");
        grid.add(portLabel, 0, 0);
        TextField portField = new TextField();
        grid.add(portField, 1, 0);

        Label webDirLabel = new Label("Web Directory:");
        grid.add(webDirLabel, 0, 1);
        TextField webDirField = new TextField();
        grid.add(webDirField, 1, 1);

        Label logDirLabel = new Label("Log Directory:");
        grid.add(logDirLabel, 0, 2);
        TextField logDirField = new TextField();
        grid.add(logDirField, 1, 2);

        Button startButton = new Button("Start Server");
        grid.add(startButton, 0, 3);
        Button stopButton = new Button("Stop Server");
        grid.add(stopButton, 1, 3);

        logTextArea = new TextArea();
        logTextArea.setEditable(false);
        grid.add(logTextArea, 0, 4, 2, 1);

        // Mengisi inputan dengan nilai terakhir yang diinput user
        portField.setText(String.valueOf(getPort()));
        webDirField.setText(getWebDirectory());
        logDirField.setText(getLogDirectory());

        startButton.setOnAction(e -> {
            if (!serverRunning) {
                port = Integer.parseInt(portField.getText());
                webDirectory = webDirField.getText();
                logDirectory = logDirField.getText();
                startServer();
                startButton.setDisable(true); //mulai mendengarkan koneksi dari klien
                stopButton.setDisable(false);
                // Simpan konfigurasi saat tombol start ditekan
                saveConfig(port, webDirectory, logDirectory);
            }
        });

        stopButton.setOnAction(e -> {
            if (serverRunning) {
                stopServer(); 
                startButton.setDisable(false);
                stopButton.setDisable(true);
            }
        });

        Scene scene = new Scene(grid, 600, 400);
        primaryStage.setScene(scene);
        primaryStage.show();
    }
    
//    END OF APLIKASI 

    private void startServer() {
        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port); // ServerSocket akan terikat ke port yang telah ditentukan
                serverRunning = true; // server telah berhasil dimulai
                log("Server started on port " + port);
                while (serverRunning) {
                    Socket clientSocket = serverSocket.accept();
                    handleClientRequest(clientSocket);
                }
            } catch (IOException e) {
                log("Error starting server: " + e.getMessage());
            }
        }).start();
    }

    private void stopServer() {
        try {
            serverRunning = false; //menutup ServerSocket dan mengubah status serverRunning menjadi false.
            serverSocket.close();
            log("Server stopped");
        } catch (IOException e) {
            log("Error stopping server: " + e.getMessage());
        }
    }
    
    // Menangani permintaan dari klien yang terhubung ke server 
    private void handleClientRequest(Socket clientSocket) {
        new Thread(() -> {
            try {
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // membaca data masuk dari klien
                String request = in.readLine();
                log("Request from " + clientSocket.getInetAddress() + ": " + request); // Mencatat permintaan yang diterima dari klien ke dalam area log, bersama dengan alamat IP klien
                
                // Parsing request untuk mendapatkan nama file yang diminta
                String fileName = parseRequest(request);

                // Mengecek apakah pengguna mengakses root path
                if (fileName.equals("")) {
                    // Jika pengguna mengakses root path, tampilkan daftar file dalam direktori web
                    String fileListHTML = generateFileListHTML(webDirectory);
                    byte[] fileContent = fileListHTML.getBytes();
                    sendResponse(clientSocket, fileContent, "text/html");
                } else {
                    // Memeriksa apakah sumber daya yang diminta adalah direktori
                    File requestedFile = new File(webDirectory, fileName);
                    if (requestedFile.isDirectory()) {
                        // Jika sumber daya yang diminta adalah direktori/folder, buat dan kirim daftar file HTML
                        String fileListHTML = generateFileListHTML(requestedFile.getAbsolutePath());
                        byte[] fileContent = fileListHTML.getBytes();
                        sendResponse(clientSocket, fileContent, "text/html");
                    } else {
                        // Jika sumber daya yang diminta adalah file, baca dan kirim file seperti biasa
                        byte[] fileContent = readFileFromWebDirectory(fileName);
                        String contentType = getContentType(fileName);
                        sendResponse(clientSocket, fileContent, contentType);
                    }
                }

                clientSocket.close(); // Menutup koneksi dengan klien setelah permintaan selesai ditangani.
            } catch (IOException e) {
                log("Error handling client request: " + e.getMessage());
            }
        }).start();
    }
    
    // Metode untuk mengirimkan response ke klien
    private void sendResponse(Socket clientSocket, byte[] content, String contentType) throws IOException {
        OutputStream out = clientSocket.getOutputStream();
        out.write("HTTP/1.1 200 OK\r\n".getBytes()); // Respon berhasil dengan kode status "200 OK
        out.write(("Content-Type: " + contentType + "\r\n").getBytes());
        out.write(("Content-Length: " + content.length + "\r\n").getBytes());
        out.write("\r\n".getBytes());
        out.write(content);
        out.flush();
    }

    // Metode untuk membaca file dari direktori web
    private byte[] readFileFromWebDirectory(String fileName) throws IOException {
        Path filePath = Paths.get(webDirectory, fileName);
        if (Files.exists(filePath) && Files.isReadable(filePath)) {
            return Files.readAllBytes(filePath);
        } else {
            // Jika file tidak ditemukan atau tidak dapat dibaca, kirimkan pesan 404 Not Found
            String errorContent = "404 Not Found | The requested resource is not available.";
            return errorContent.getBytes();
        }
    }
    
    // Memperoleh nama file yang diminta dari permintaan HTTP yang diterima dari klien
    private String parseRequest(String request) {
        String[] parts = request.split(" ");
        String fileName = parts[1].substring(1); // Mengabaikan karakter '/' di awal URL
        if (fileName.isEmpty() || fileName.equals("/")) {
            return ""; // Jika tidak ada nama file yang disebutkan, kembalikan string kosong
        }
        return fileName;
    }

    // Metode untuk mencatat log akses
    private void log(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String logMessage = "[" + timestamp + "] " + message + "\n";
        logTextArea.appendText(logMessage);
        saveLogToFile(logMessage);
    }

    // Metode untuk menyimpan log ke file
    private void saveLogToFile(String logMessage) {
        try {
            String date = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
            Path logPath = Paths.get(logDirectory, "log_" + date + ".txt");
            Files.write(logPath, logMessage.getBytes(), logPath.toFile().exists() ? java.nio.file.StandardOpenOption.APPEND : java.nio.file.StandardOpenOption.CREATE);
        } catch (IOException e) {
            log("Error saving log to file: " + e.getMessage());
        }
    }
    
    // Menyimpan konfigurasi server ke dalam sebuah file properti (D:\TUGAS UDINUS\SEMESTER 4\PBO\SimpleWebServer\config.properties)  
    private static void saveConfig(int port, String webDirectory, String logDirectory) {
        try {
            Properties prop = new Properties();
            prop.setProperty("port", String.valueOf(port));
            prop.setProperty("webDirectory", webDirectory);
            prop.setProperty("logDirectory", logDirectory);
            prop.store(new FileOutputStream(CONFIG_FILE), null);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    // Mendapatkan nilai port dari file konfigurasi config.properties
    private static int getPort() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(CONFIG_FILE));
            return Integer.parseInt(prop.getProperty("port"));
        } catch (IOException e) {
            e.printStackTrace();
            return 8080; // Default port 
        }
    }
    
    // Mendapatkan letak file2 web dari file konfigurasi config.properties
    private static String getWebDirectory() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(CONFIG_FILE));
            return prop.getProperty("webDirectory");
        } catch (IOException e) {
            e.printStackTrace();
            return ""; // Default web directory
        }
    }
    
    // Mendapatkan letak log server dari file konfigurasi config.properties
    private static String getLogDirectory() {
        try {
            Properties prop = new Properties();
            prop.load(new FileInputStream(CONFIG_FILE));
            return prop.getProperty("logDirectory");
        } catch (IOException e) {
            e.printStackTrace();
            return ""; // Default log directory
        }
    }

    private String generateFileListHTML(String directoryPath) {
        File directory = new File(directoryPath);
        // Mengambil daftar file yang ada dalam File directory menggunakan metode listFiles() dari objek File. Hasilnya disimpan dalam array files.
        File[] files = directory.listFiles();
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<html><body><h1>File list of ").append(directoryPath).append("</h1><ul>");

        // Jika bukan di halaman root, tambahkan tautan untuk kembali ke direktori induk
        if (!directoryPath.equals("/")) {
            htmlBuilder.append("<li><a href=\"../\">.. (Back)</a></li>");
        }

        for (File file : files) {
            String fileName = file.getName(); // Mendapatkan nama file dari objek File saat ini.
            String filePath = directoryPath.equals("/") ? fileName : directoryPath + "/" + fileName; // Membuat path lengkap untuk setiap file. Jika kita berada di halaman root
            if (file.isDirectory()) {
                // Jika ini adalah direktori, server akan mengirimkan daftar file dalam direktori tersebut sebagai respons
                htmlBuilder.append("<li><a href=\"").append(fileName).append("/\">").append(fileName).append("</a></li>");
            } else {
                // Jika ini adalah file,  membaca file tersebut dan mengirimkannya ke klien sebagai respons
                htmlBuilder.append("<li><a href=\"").append(fileName).append("\">").append(fileName).append("</a></li>");
            }
        }

        htmlBuilder.append("</ul></body></html>");
        return htmlBuilder.toString();
    }

    // Menentukan tipe konten (content type) berdasarkan ekstensi file
    private String getContentType(String fileName) {
        if (fileName.endsWith(".html") || fileName.endsWith(".htm")) {
            return "text/html";
        } else if (fileName.endsWith(".css")) {
            return "text/css";
        } else if (fileName.endsWith(".js")) {
            return "application/javascript";
        } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".pdf")) {
            return "application/pdf";
        } else {
            return "text/plain";
        }
    }
}


