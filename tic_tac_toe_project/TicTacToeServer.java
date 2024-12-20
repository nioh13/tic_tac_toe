import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TicTacToeServer {

    private static final int PORT = 12345;
    private static final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private static final Map<String, String> users = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        System.out.println("Starting Tic-Tac-Toe server...");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server is running on port: " + PORT);
            while (true) {
                new ClientHandler(serverSocket.accept()).start();
            }
        }
    }

    private static class ClientHandler extends Thread {
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;
        private Room currentRoom; 
        private boolean waitingForReplayAnswer = false; // Ждём ответа "yes/no" после конца игры?
        private boolean yourTurn = false; // Сейчас ход этого игрока?
        private boolean gameOver = false; // Игра закончилась?

        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public String getUsername() {
            return username;
        }

        public synchronized void setYourTurn(boolean turn) {
            this.yourTurn = turn;
        }

        public synchronized boolean isYourTurn() {
            return yourTurn;
        }

        public synchronized void setWaitingForReplayAnswer(boolean waiting) {
            this.waitingForReplayAnswer = waiting;
        }

        public synchronized boolean isWaitingForReplayAnswer() {
            return waitingForReplayAnswer;
        }

        public synchronized void setGameOver(boolean over) {
            this.gameOver = over;
        }

        public synchronized boolean isGameOver() {
            return gameOver;
        }

        public synchronized Room getCurrentRoom() {
            return currentRoom;
        }

        public synchronized void setCurrentRoom(Room room) {
            this.currentRoom = room;
        }

        public synchronized void leaveRoom() {
            if (currentRoom != null) {
                currentRoom.removePlayer(this);
                currentRoom = null;
                printMenuIfApplicable();
            }
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                out.println("Welcome to Tic-Tac-Toe!");
                authenticateUser();

                // Вывод меню
                printMenu();

                while (true) {
                    String input = in.readLine();
                    if (input == null) {
                        // Клиент отключился
                        break;
                    }

                    //Ждём ответа о переигровке
                    if (isWaitingForReplayAnswer()) {
                        handleReplayAnswer(input);
                        continue;
                    }

                    // Проверка на ход
                    if (currentRoom != null && currentRoom.isGameInProgress()) {
                        // Если сейчас ход этого игрока, пытаемся интерпретировать ввод как ход
                        if (isYourTurn()) {
                            if (tryMakeMove(input)) {
                                continue;
                            } 
                        }
                    }

                    // Обработк команды, если не ход
                    handleCommand(input);
                }

            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
            } finally {
                if (username != null) {
                    System.out.println(username + " disconnected.");
                    leaveRoom();
                }
                try {
                    socket.close();
                } catch (IOException e) {
                    System.out.println("Failed to close socket: " + e.getMessage());
                }
            }
        }

        private void authenticateUser() throws IOException {
            while (true) {
                out.println("Enter your username (letters and digits only):");
                String inputUsername = in.readLine();
                if (inputUsername == null || !inputUsername.matches("[a-zA-Z0-9]+")) {
                    out.println("Invalid username. Use letters and digits only.");
                    continue;
                }

                out.println("Enter your password:");
                String inputPassword = in.readLine();

                synchronized (users) {
                    if (users.containsKey(inputUsername)) {
                        if (users.get(inputUsername).equals(inputPassword)) {
                            username = inputUsername;
                            out.println("Welcome back, " + username + "!");
                            System.out.println("User logged in: " + username);
                            break;
                        } else {
                            out.println("Incorrect password.");
                        }
                    } else {
                        users.put(inputUsername, inputPassword);
                        username = inputUsername;
                        out.println("Registration successful. Welcome, " + username + "!");
                        System.out.println("New user registered: " + username);
                        break;
                    }
                }
            }
        }

        private void printMenu() {
            out.println("======================================");
            out.println("Available commands:");
            out.println("1. LIST - Show available rooms");
            out.println("2. CREATE <room_name> <password> - Create a new room");
            out.println("3. JOIN <room_name> <password> - Join an existing room");
            out.println("4. EXIT - Exit the game");
            out.println("======================================");
        }

        private void printMenuIfApplicable() {
            Room room = getCurrentRoom();
            if (room == null || (!room.isGameInProgress() && !isWaitingForReplayAnswer())) {
                printMenu();
            }
        }

        private void handleCommand(String command) {
            command = command.trim();
            if (command.isEmpty()) {
                printMenuIfApplicable();
                return;
            }

            String[] parts = command.split(" ", 3);
            String action = parts[0].toUpperCase();

            if (action.equals("LIST")) {
                listRooms();
            } else if (action.equals("CREATE")) {
                if (parts.length < 3) {
                    out.println("Usage: CREATE <room_name> <password>");
                } else {
                    createAndJoinRoom(parts[1], parts[2]);
                }
            } else if (action.equals("JOIN")) {
                if (parts.length < 3) {
                    out.println("Usage: JOIN <room_name> <password>");
                } else {
                    joinRoom(parts[1], parts[2]);
                }
            } else if (action.equals("EXIT")) {
                out.println("Goodbye!");
                leaveRoom();
                try {
                    socket.close();
                } catch (IOException e) {
                    
                }
                return; 
            } else {
                out.println("Unknown command.");
            }

            printMenuIfApplicable();
        }

        private void handleReplayAnswer(String input) {
            input = input.trim().toLowerCase();
            if (input.equals("yes")) {
                setWaitingForReplayAnswer(false);
                Room room = getCurrentRoom();
                if (room != null && room.getGameHandler() != null) {
                    room.getGameHandler().playerReplayAnswer(this, true);
                }
            } else if (input.equals("no")) {
                setWaitingForReplayAnswer(false);
                Room room = getCurrentRoom();
                if (room != null && room.getGameHandler() != null) {
                    room.getGameHandler().playerReplayAnswer(this, false);
                }
            } else {
                out.println("Please answer yes or no:");
                return;
            }

            printMenuIfApplicable();
        }

        private void listRooms() {
            if (rooms.isEmpty()) {
                out.println("No available rooms.");
            } else {
                StringBuilder roomList = new StringBuilder("Available rooms:\n");
                rooms.forEach((name, room) -> {
                    String roomStatus = room.isFull() ? "(Full)" : "(Waiting for players)";
                    roomList.append("- ").append(name).append(" ").append(roomStatus).append("\n");
                });
                out.println(roomList);
            }
        }

        private void createAndJoinRoom(String roomName, String password) {
            synchronized (rooms) {
                if (rooms.containsKey(roomName)) {
                    out.println("Room already exists.");
                } else {
                    Room newRoom = new Room(roomName, password);
                    rooms.put(roomName, newRoom);
                    out.println("Room created: " + roomName + ". Joining the room...");
                    System.out.println("Room created: " + roomName);
                    newRoom.addPlayer(this);
                    out.println("You joined room: " + roomName);
                }
            }
        }

        private void joinRoom(String roomName, String password) {
            Room room;
            synchronized (rooms) {
                room = rooms.get(roomName);
            }

            if (room == null) {
                out.println("Room not found.");
            } else if (!room.getPassword().equals(password)) {
                out.println("Incorrect password.");
            } else {
                out.println("Joining room: " + roomName);
                System.out.println("Player joining room: " + roomName);
                room.addPlayer(this);
                out.println("You joined room: " + roomName);
            }
        }

        private boolean tryMakeMove(String input) {
            String[] parts = input.trim().split(" ");
            if (parts.length == 2) {
                try {
                    int row = Integer.parseInt(parts[0]);
                    int col = Integer.parseInt(parts[1]);
                    Room room = getCurrentRoom();
                    if (room != null && room.isGameInProgress()) {
                        boolean moveMade = room.getGameHandler().tryMove(this, row, col);
                        if (!moveMade) {
                            out.println("Invalid move. Try again.");
                        }
                        return true; 
                    }
                } catch (NumberFormatException e) {
                    // Не число
                }
            }
            return false; 
        }
    }

    private static class Room {
        private final String name;
        private final String password;
        private final List<ClientHandler> players = new ArrayList<>();
        private GameHandler gameHandler;

        public Room(String name, String password) {
            this.name = name;
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public String getPassword() {
            return password;
        }

        public synchronized void addPlayer(ClientHandler player) {
            players.add(player);
            player.setCurrentRoom(this);
            if (players.size() == 1) {
                player.out.println("Waiting for another player to join...");
            } else if (players.size() == 2) {
                players.forEach(p -> p.out.println("Another player joined. Starting the game..."));
                System.out.println("Game starting in room: " + name);
                startGame();
            }
        }

        public synchronized void removePlayer(ClientHandler player) {
            players.remove(player);
            player.setCurrentRoom(null);
            // Если игроков не осталось, удаляем комнату
            if (players.isEmpty()) {
                rooms.remove(name);
                System.out.println("Room " + name + " is empty and removed.");
            }
        }

        public synchronized boolean isFull() {
            return players.size() >= 2;
        }

        public synchronized boolean isGameInProgress() {
            return gameHandler != null && gameHandler.isInProgress();
        }

        public synchronized GameHandler getGameHandler() {
            return gameHandler;
        }

        private synchronized void startGame() {
            gameHandler = new GameHandler(this, new ArrayList<>(players));
            gameHandler.start();
        }

        public synchronized void endGame() {
            
        }
    }

    private static class GameHandler extends Thread {
        private final Room room;
        private final List<ClientHandler> players;
        private char[][] board = new char[3][3];
        private int currentPlayerIndex = 0;
        private boolean inProgress = true;
        private boolean waitingReplay = false;
        private final Map<ClientHandler, Boolean> replayAnswers = new HashMap<>();

        public GameHandler(Room room, List<ClientHandler> players) {
            this.room = room;
            this.players = players;
            for (char[] row : board) {
                Arrays.fill(row, ' ');
            }
        }

        public synchronized boolean isInProgress() {
            return inProgress;
        }

        @Override
        public void run() {
            // Случайно выбираем, кто ходит первым при первом запуске игры
            currentPlayerIndex = new Random().nextInt(2);

            broadcast("Game is starting!");
            printBoardToAll();
            informTurns();

            gameLoop();

            inProgress = false;
            room.endGame();
        }

        private void gameLoop() {
            while (true) {
                ClientHandler currentPlayer = players.get(currentPlayerIndex);
                ClientHandler opponent = players.get(1 - currentPlayerIndex);

                setPlayerTurn(currentPlayer, true);
                waitForAction(); // Ожидание хода

                if (checkWin()) {
                    currentPlayer.out.println("You win!");
                    opponent.out.println("You lose.");
                    break;
                } else if (isBoardFull()) {
                    broadcast("It's a draw!");
                    break;
                }

                setPlayerTurn(currentPlayer, false);
                currentPlayerIndex = 1 - currentPlayerIndex;
                informTurns();
            }

            // Игра закончилась, предлагаем переиграть
            waitingReplay = true;
            replayAnswers.clear();
            for (ClientHandler p : players) {
                if (p.getCurrentRoom() == room) {
                    p.out.println("Do you want to play again with the same player? (yes/no):");
                    p.setWaitingForReplayAnswer(true);
                }
            }

            waitForReplayAnswers();

            boolean allYes = !replayAnswers.isEmpty() && replayAnswers.values().stream().allMatch(b -> b);
            if (allYes) {
                // Перезапуск игры
                // Случайно выбираем, кто ходит первым при новом раунде
                currentPlayerIndex = new Random().nextInt(2);
                resetBoard();
                waitingReplay = false;
                for (ClientHandler p : players) {
                    if (p.getCurrentRoom() == room) {
                        p.setWaitingForReplayAnswer(false);
                        p.setGameOver(false);
                    }
                }
                broadcast("Starting a new round...");
                printBoardToAll();
                informTurns();
                gameLoop();
            } else {
                // Кто-то сказал "no"
                List<ClientHandler> leavingPlayers = new ArrayList<>();
                for (Map.Entry<ClientHandler, Boolean> e : replayAnswers.entrySet()) {
                    if (!e.getValue()) {
                        e.getKey().out.println("Exiting the room.");
                        leavingPlayers.add(e.getKey());
                    }
                }

                for (ClientHandler lp : leavingPlayers) {
                    room.removePlayer(lp);
                }

                waitingReplay = false;
                for (ClientHandler p : players) {
                    if (p.getCurrentRoom() == room) {
                        p.setWaitingForReplayAnswer(false);
                        p.setGameOver(true);
                        p.setYourTurn(false);
                    }
                }
            }

            inProgress = false;
        }

        public synchronized void playerReplayAnswer(ClientHandler player, boolean answer) {
            if (!waitingReplay) return; 
            replayAnswers.put(player, answer);
            player.setWaitingForReplayAnswer(false);
            if (replayAnswers.size() == players.size()) {
                notifyAll();
            }
        }

        private synchronized void waitForReplayAnswers() {
            while (replayAnswers.size() < players.size() && inSameRoom()) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    
                }
            }
        }

        private boolean inSameRoom() {
            for (ClientHandler p : replayAnswers.keySet()) {
                if (p.getCurrentRoom() != room) {
                    return false;
                }
            }
            return true;
        }

        private synchronized void resetBoard() {
            for (char[] row : board) {
                Arrays.fill(row, ' ');
            }
        }

        public synchronized boolean tryMove(ClientHandler player, int row, int col) {
            if (players.get(currentPlayerIndex) != player) {
                player.out.println("Not your turn!");
                return false;
            }

            row = row - 1;
            col = col - 1;
            if (row < 0 || row >= 3 || col < 0 || col >= 3 || board[row][col] != ' ') {
                return false;
            }

            board[row][col] = (currentPlayerIndex == 0) ? 'X' : 'O';
            System.out.println("Move by " + player.getUsername() + " at " + (row+1) + ", " + (col+1));
            printBoardToAll();

            notifyAll(); // Ход сделан, продолжаем игру
            return true;
        }

        private void informTurns() {
            if (players.size() < 2) return;
            ClientHandler currentPlayer = players.get(currentPlayerIndex);
            ClientHandler opponent = players.get(1 - currentPlayerIndex);

            if (currentPlayer.getCurrentRoom() == room)
                currentPlayer.out.println("It is now your turn. You are " + (currentPlayerIndex == 0 ? 'X' : 'O') + ".");
            if (opponent.getCurrentRoom() == room)
                opponent.out.println("Please wait. Opponent (" + currentPlayer.getUsername() + ") is making a move. You are " + ((1 - currentPlayerIndex == 0) ? 'X' : 'O') + ".");
        }

        private void printBoardToAll() {
            StringBuilder sb = new StringBuilder();
            sb.append("Current board:\n");
            sb.append("   1   2   3\n");
            for (int i = 0; i < 3; i++) {
                sb.append(i+1).append(" ");
                for (int j = 0; j < 3; j++) {
                    sb.append(" ").append(board[i][j]).append(" ");
                    if (j < 2) sb.append("|");
                }
                sb.append("\n");
                if (i < 2) sb.append("  ---+---+---\n");
            }

            broadcast(sb.toString());
        }

        //проверка на выйгрышь
        private boolean checkWin() {
            for (int i = 0; i < 3; i++) {
                if (board[i][0] != ' ' && board[i][0] == board[i][1] && board[i][1] == board[i][2]) return true;
                if (board[0][i] != ' ' && board[0][i] == board[1][i] && board[1][i] == board[2][i]) return true;
            }
            return (board[0][0] != ' ' && board[0][0] == board[1][1] && board[1][1] == board[2][2]) ||
                   (board[0][2] != ' ' && board[0][2] == board[1][1] && board[1][1] == board[2][0]);
        }

        private boolean isBoardFull() {
            for (char[] row : board) {
                for (char cell : row) {
                    if (cell == ' ') return false;
                }
            }
            return true;
        }

        private synchronized void setPlayerTurn(ClientHandler player, boolean turn) {
            player.setYourTurn(turn);
        }

        private void broadcast(String message) {
            for (ClientHandler p : players) {
                if (p.getCurrentRoom() == room) {
                    p.out.println(message);
                }
            }
        }

        private synchronized void waitForAction() {
            try {
                wait();
            } catch (InterruptedException e) {
                
            }
        }
    }
}
