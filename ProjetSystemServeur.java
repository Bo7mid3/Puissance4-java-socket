import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.Random;

public class ProjetSystemServeur {

    public static void main(String[] args) throws Exception {
        try (ServerSocket listener = new ServerSocket(58901)) {
            System.out.println("Le serveur est en marche...");
            ExecutorService pool = Executors.newFixedThreadPool(200);
            while (true) {
                Game game = new Game();
                pool.execute(game.new Player(listener.accept(), 'B'));
                pool.execute(game.new Player(listener.accept(), 'R'));
            }
        }
    }
}

class Game {
    private Player[] board = new Player[42];

    Player currentPlayer;
	Player firstPlayer;

    public boolean hasWinner() {
        for (int i=0; i<board.length; i++)
		{
			if (board[i] == null)
				continue;
			if ((i+3<42) && ((i%7)<4) &&(board[i] == board [i+1]) && (board[i] == board [i+2]) && (board[i] == board [i+3]))
				return true;
			if ((i+21<42) &&(board[i] == board [i+7]) && (board[i] == board [i+14]) && (board[i] == board [i+21]))
				return true;
			if ((i+24<42) &&(board[i] == board [i+8]) && (board[i] == board [i+16]) && (board[i] == board [i+24]))
				return true;
			if ((i+18<42) && ((i%7)>2) &&(board[i] == board [i+6]) && (board[i] == board [i+12]) && (board[i] == board [i+18]))
				return true;
		}
		return false;
    }

    public boolean boardFilledUp() {
        return Arrays.stream(board).allMatch(p -> p != null);
    }

    public synchronized void move(int location, Player player) {
        if (player != currentPlayer) {
            throw new IllegalStateException("C'est pas ton tourne");
        } else if (player.opponent == null) {
            throw new IllegalStateException("Vous n'avez pas encore d'adversaire");
        } else if (board[location] != null) {
            throw new IllegalStateException("Cellule deja occupe");
        }
		else if ((location+7<42) && (board[location+7]==null))
			throw new IllegalStateException("Movement non valide");
        board[location] = currentPlayer;
        currentPlayer = currentPlayer.opponent;
    }
	
    class Player implements Runnable {
        char mark;
        Player opponent;
        Socket socket;
        Scanner input;
        PrintWriter output;

        public Player(Socket socket, char mark) {
            this.socket = socket;
            this.mark = mark;
        }

        @Override
        public void run() {
            try {
                setup();
                processCommands();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (opponent != null && opponent.output != null) {
                    opponent.output.println("OTHER_PLAYER_LEFT");
                }
                try {socket.close();} catch (IOException e) {}
            }
        }

        private void setup() throws IOException {
            input = new Scanner(socket.getInputStream());
            output = new PrintWriter(socket.getOutputStream(), true);
            output.println("WELCOME " + mark);
            if (mark == 'B') {
				firstPlayer = this;
                output.println("MESSAGE En attend l'adversaire");
            } else {
                opponent = firstPlayer;
                opponent.opponent = this;
				currentPlayer = (new Random().nextInt(2)==0)? this : opponent;
                currentPlayer.output.println("MESSAGE Votre tourne");
				currentPlayer.opponent.output.println("MESSAGE L'adversaire est le premier");
            }
        }

        private void processCommands() {
            while (input.hasNextLine()) {
                String command = input.nextLine();
                if (command.startsWith("QUIT")) {
                    return;
                } else if (command.startsWith("MOVE")) {
                    processMoveCommand(Integer.parseInt(command.substring(5)));
                }
            }
        }

        private void processMoveCommand(int location) {
            try {
                move(location, this);
                output.println("VALID_MOVE");
                opponent.output.println("OPPONENT_MOVED " + location);
                if (hasWinner()) {
                    output.println("VICTORY");
                    opponent.output.println("DEFEAT");
                } else if (boardFilledUp()) {
                    output.println("TIE");
                    opponent.output.println("TIE");
                }
            } catch (IllegalStateException e) {
                output.println("MESSAGE " + e.getMessage());
            }
        }
    }
}