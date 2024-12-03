package tetris;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Random;

public class Tetris extends JFrame {
    public Tetris() {
        setTitle("테트리스 게임!!");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        Game game = new Game();
        add(game);
        pack();

        setLocationRelativeTo(null);
        setVisible(true);

        game.start();
    }
    public static void main(String[] args) {
        new Tetris();
    }

    class Game extends JPanel implements Runnable {
        public static final int WIDTH = 500;
        public static final int HEIGHT = 600;
        public static final int CELL_SIZE = 30;

        private Thread gameThread;
        private boolean running = true;
        private PlayManager playManager;

        public Game() {
            setPreferredSize(new Dimension(WIDTH, HEIGHT));
            setBackground(Color.BLACK);
            setFocusable(true);

            playManager = new PlayManager();

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    switch (e.getKeyCode()) {
                        case KeyEvent.VK_LEFT -> playManager.moveMino(-CELL_SIZE, 0);
                        case KeyEvent.VK_RIGHT -> playManager.moveMino(CELL_SIZE, 0);
                        case KeyEvent.VK_DOWN -> playManager.moveMino(0, CELL_SIZE);
                        case KeyEvent.VK_UP -> {
                            System.out.println("Rotate key pressed"); // 디버깅용 로그 추가
                            playManager.rotateMino();
                            System.out.println("Rotation attempted"); // 디버깅용 로그 추가
                        } 
            
                    }
                    repaint();
                }
            });
        }

        public void start() {
            gameThread = new Thread(this);
            gameThread.start();
        }

        @Override
        public void run() {
            while (running) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                playManager.moveMino(0, CELL_SIZE);
                repaint();
            }
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);

            Graphics2D g2 = (Graphics2D) g;
            playManager.draw(g2);

            g2.setColor(Color.WHITE);
            g2.setFont(new Font("맑은 고딕", Font.BOLD, 18));
            g2.drawString("다음 블록", 380, 50);
            g2.drawString("점수: " + playManager.getScore(), 380, 150);
        }
    }

    class PlayManager {
        private final int rows = 20;
        private final int cols = 10;
        private final int[][] board = new int[rows][cols];
        private Mino currentMino;
        private Mino nextMino;
        private int score = 0;
        private final Random random = new Random();
        
        private final int[][] wallKickOffsets = {
        	    {0, 0},      // 현재 위치
        	    {-1, 0},     // 왼쪽으로 한 칸
        	    {-2,0},
        	    {1, 0},      // 오른쪽으로 한 칸
        	    {0, -1},     // 아래로 한 칸
        	    {-1, -1},    // 왼쪽 아래 대각선
        	    {1, -1}      // 오른쪽 아래 대각선
        	};


        private Mino getRandomMino() {
            return switch (random.nextInt(7)) {
                case 0 -> new Mino_I();
                case 1 -> new Mino_J();
                case 2 -> new Mino_L1();
                case 3 -> new Mino_O();
                case 4 -> new Mino_S();
                case 5 -> new Mino_T();
                case 6 -> new Mino_Z();
                default -> new Mino_L1();
            };
        }

        public PlayManager() {
            currentMino = getRandomMino();
            currentMino.setXY(cols / 2 * Game.CELL_SIZE, 0);

            nextMino = getRandomMino();
        }

        public int getScore() {
            return score;
        }

        public void moveMino(int dx, int dy) {
            for (Block block : currentMino.tempB) {
                block.x = block.x + dx;
                block.y = block.y + dy;
            }

            if (checkValidMove()) {
                currentMino.update();
            } else if (dy > 0) {
                lockMino();
                clearLines();
                spawnNewMino();
            } else if (dx != 0 || dy != 0) {
                for (Block block : currentMino.tempB) {
                    block.x -= dx;
                    block.y -= dy;
                }
            }
        }

        public void rotateMino() {
            System.out.println("Current mino type: " + currentMino.getClass().getSimpleName()); // 디버깅용 로그

            // 현재 블록이 회전 가능한 블록인지 확인 (O 블록 제외)
            if (!(currentMino instanceof Mino_O)) {
                // 방향 업데이트
                int newDirection = (currentMino.direction % 4) + 1;

                // 원래 위치 백업
                for (int i = 0; i < currentMino.b.length; i++) {
                    currentMino.tempB[i].x = currentMino.b[i].x;
                    currentMino.tempB[i].y = currentMino.b[i].y;
                }

                // 회전 시도
                currentMino.updateXY(newDirection);

                // 회전 가능 여부 확인
                if (checkValidMove()) {
                    System.out.println("Rotation successful");
                    currentMino.direction = newDirection;
                    currentMino.update();
                } else {
                    // Wall Kick 시도
                    boolean wallKickSuccessful = false;

                    for (int[] offset : wallKickOffsets) {
                        for (int i = 0; i < currentMino.tempB.length; i++) {
                            currentMino.tempB[i].x += offset[0] * Game.CELL_SIZE;
                            currentMino.tempB[i].y += offset[1] * Game.CELL_SIZE;
                        }

                        if (checkValidMove()) {
                            System.out.println("Wall Kick successful with offset: " + offset[0] + ", " + offset[1]);
                            currentMino.direction = newDirection;
                            currentMino.update();
                            wallKickSuccessful = true;
                            break;
                        } else {
                            // 복원
                            for (int i = 0; i < currentMino.tempB.length; i++) {
                                currentMino.tempB[i].x -= offset[0] * Game.CELL_SIZE;
                                currentMino.tempB[i].y -= offset[1] * Game.CELL_SIZE;
                            }
                        }
                    }

                    if (!wallKickSuccessful) {
                        System.out.println("Rotation and Wall Kick failed");
                        // 원래 상태로 복원
                        for (int i = 0; i < currentMino.b.length; i++) {
                            currentMino.b[i].x = currentMino.tempB[i].x;
                            currentMino.b[i].y = currentMino.tempB[i].y;
                        }
                    }
                }
            }
        }

        private void spawnNewMino() {
            currentMino = nextMino;
            nextMino = getRandomMino();
            currentMino.setXY(cols / 2 * Game.CELL_SIZE, 0);

            if (!checkValidMove()) {
                JOptionPane.showMessageDialog(null, "게임 오버! 점수: " + score);
                System.exit(0);
            }
        }

        private void lockMino() {
            for (Block block : currentMino.b) {
                int x = block.x / Game.CELL_SIZE;
                int y = block.y / Game.CELL_SIZE;
                if (y >= 0) {
                    board[y][x] = 1;
                }
            }
        }

        private void clearLines() {
            for (int y = 0; y < rows; y++) {
                boolean full = true;
                for (int x = 0; x < cols; x++) {
                    if (board[y][x] == 0) {
                        full = false;
                        break;
                    }
                }
                if (full) {
                    clearLine(y);
                    score += 100;
                }
            }
        }

        private void clearLine(int line) {
            for (int y = line; y > 0; y--) {
                System.arraycopy(board[y - 1], 0, board[y], 0, cols);
            }
            board[0] = new int[cols];
        }

        private boolean checkValidMove() {
            for (Block block : currentMino.tempB) {
                int gridX = block.x / Game.CELL_SIZE;
                int gridY = block.y / Game.CELL_SIZE;

                if (gridX < 0 || gridX >= cols || gridY >= rows || (gridY >= 0 && board[gridY][gridX] == 1)) {
                    return false;
                }
            }
            return true;
        }

        public void draw(Graphics2D g2) {
            g2.setColor(Color.GRAY);
            for (int y = 0; y < rows; y++) {
                for (int x = 0; x < cols; x++) {
                    if (board[y][x] == 1) {
                        g2.setColor(Color.LIGHT_GRAY);
                        g2.fillRect(x * Game.CELL_SIZE, y * Game.CELL_SIZE, Game.CELL_SIZE, Game.CELL_SIZE);
                    }
                    g2.setColor(Color.GRAY);
                    g2.drawRect(x * Game.CELL_SIZE, y * Game.CELL_SIZE, Game.CELL_SIZE, Game.CELL_SIZE);
                }
            }

            currentMino.draw(g2);

            nextMino.setXY(400, 70);
            nextMino.draw(g2);
        }
    }

    class Block {
        public int x, y;
        public static final int SIZE = Game.CELL_SIZE;
        public Color color;

        public Block(Color color) {
            this.color = color;
        }

        public void draw(Graphics2D g2) {
            g2.setColor(color);
            g2.fillRect(x, y, SIZE, SIZE);
            g2.setColor(Color.BLACK);
            g2.drawRect(x, y, SIZE, SIZE);
        }
    }

    abstract class Mino {
        public Block[] b = new Block[4];
        public Block[] tempB = new Block[4];
        public int direction = 1;

        public void create(Color color) {
            for (int i = 0; i < 4; i++) {
                b[i] = new Block(color);
                tempB[i] = new Block(color);
            }
        }

        public abstract void setXY(int x, int y);
        public abstract void updateXY(int direction);

        public void update() {
            for (int i = 0; i < b.length; i++) {
                b[i].x = tempB[i].x;
                b[i].y = tempB[i].y;
            }
        }

        public void draw(Graphics2D g2) {
            for (Block block : b) {
                block.draw(g2);
            }
        }
    }

    class Mino_L1 extends Mino {
        public Mino_L1() {
            create(Color.ORANGE);
        }

        @Override
        public void setXY(int x, int y) {
            b[0].x = x;
            b[0].y = y;
            b[1].x = x;
            b[1].y = y - Block.SIZE;
            b[2].x = x;
            b[2].y = y + Block.SIZE;
            b[3].x = x + Block.SIZE;
            b[3].y = y + Block.SIZE;

            for (int i = 0; i < 4; i++) {
                tempB[i].x = b[i].x;
                tempB[i].y = b[i].y;
            }
        }

        @Override
        public void updateXY(int direction) {
            this.direction = direction;
            
            int x = b[0].x;
            int y = b[0].y;

            switch (direction) {
                case 1 -> {
                    tempB[1].x = x;
                    tempB[1].y = y - Block.SIZE;
                    tempB[2].x = x;
                    tempB[2].y = y + Block.SIZE;
                    tempB[3].x = x + Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
                case 2 -> {
                    tempB[1].x = x + Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x - Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x - Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
                case 3 -> {
                    tempB[1].x = x;
                    tempB[1].y = y + Block.SIZE;
                    tempB[2].x = x;
                    tempB[2].y = y - Block.SIZE;
                    tempB[3].x = x - Block.SIZE;
                    tempB[3].y = y - Block.SIZE;
                }
                case 4 -> {
                    tempB[1].x = x - Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x + Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x + Block.SIZE;
                    tempB[3].y = y - Block.SIZE;
                }
            }
        }
    }

    class Mino_I extends Mino {
        public Mino_I() {
            create(Color.CYAN);
        }

        @Override
        public void setXY(int x, int y) {
            b[0].x = x;
            b[0].y = y;
            b[1].x = x;
            b[1].y = y - Block.SIZE;
            b[2].x = x;
            b[2].y = y + Block.SIZE;
            b[3].x = x;
            b[3].y = y + 2 * Block.SIZE;

            for (int i = 0; i < 4; i++) {
                tempB[i].x = b[i].x;
                tempB[i].y = b[i].y;
            }
        }

        @Override
        public void updateXY(int direction) {
            this.direction = direction;
            
            int x = b[0].x;
            int y = b[0].y;

            switch (direction) {
                case 1, 3 -> {
                    tempB[1].x = x;
                    tempB[1].y = y - Block.SIZE;
                    tempB[2].x = x;
                    tempB[2].y = y + Block.SIZE;
                    tempB[3].x = x;
                    tempB[3].y = y + 2 * Block.SIZE;
                }
                case 2, 4 -> {
                    tempB[1].x = x - Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x + Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x + 2 * Block.SIZE;
                    tempB[3].y = y;
                }
            }
        }
    }

    class Mino_O extends Mino {
        public Mino_O() {
            create(Color.YELLOW);
        }

        @Override
        public void setXY(int x, int y) {
            b[0].x = x;
            b[0].y = y;
            b[1].x = x + Block.SIZE;
            b[1].y = y;
            b[2].x = x;
            b[2].y = y + Block.SIZE;
            b[3].x = x + Block.SIZE;
            b[3].y = y + Block.SIZE;

            for (int i = 0; i < 4; i++) {
                tempB[i].x = b[i].x;
                tempB[i].y = b[i].y;
            }
        }

        @Override
        public void updateXY(int direction) {
            // O 블록은 회전하지 않음
        }
    }

    class Mino_T extends Mino {
        public Mino_T() {
            create(Color.MAGENTA);
        }

        @Override
        public void setXY(int x, int y) {
            b[0].x = x;
            b[0].y = y;
            b[1].x = x - Block.SIZE;
            b[1].y = y;
            b[2].x = x + Block.SIZE;
            b[2].y = y;
            b[3].x = x;
            b[3].y = y + Block.SIZE;

            for (int i = 0; i < 4; i++) {
                tempB[i].x = b[i].x;
                tempB[i].y = b[i].y;
            }
        }

        @Override
        public void updateXY(int direction) {
            this.direction = direction;
            
            int x = b[0].x;
            int y = b[0].y;

            switch (direction) {
                case 1 -> {
                    tempB[1].x = x - Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x + Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x;
                    tempB[3].y = y + Block.SIZE;
                }
                case 2 -> {
                    tempB[1].x = x;
                    tempB[1].y = y - Block.SIZE;
                    tempB[2].x = x;
                    tempB[2].y = y + Block.SIZE;
                    tempB[3].x = x - Block.SIZE;
                    tempB[3].y = y;
                }
                case 3 -> {
                    tempB[1].x = x + Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x - Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x;
                    tempB[3].y = y - Block.SIZE;
                }
                case 4 -> {
                    tempB[1].x = x;
                    tempB[1].y = y + Block.SIZE;
                    tempB[2].x = x;
                    tempB[2].y = y - Block.SIZE;
                    tempB[3].x = x + Block.SIZE;
                    tempB[3].y = y;
                }
            }
        }
    }

    class Mino_S extends Mino {
        public Mino_S() {
            create(Color.GREEN);
        }

        @Override
        public void setXY(int x, int y) {
            b[0].x = x;
            b[0].y = y;
            b[1].x = x + Block.SIZE;
            b[1].y = y;
            b[2].x = x;
            b[2].y = y + Block.SIZE;
            b[3].x = x - Block.SIZE;
            b[3].y = y + Block.SIZE;

            for (int i = 0; i < 4; i++) {
                tempB[i].x = b[i].x;
                tempB[i].y = b[i].y;
            }
        }

        @Override
        public void updateXY(int direction) {
            this.direction = direction;
            
            int x = b[0].x;
            int y = b[0].y;

            switch (direction) {
                case 1, 3 -> {
                    tempB[1].x = x + Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x;
                    tempB[2].y = y + Block.SIZE;
                    tempB[3].x = x - Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
                case 2, 4 -> {
                    tempB[1].x = x;
                    tempB[1].y = y - Block.SIZE;
                    tempB[2].x = x + Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x + Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
            }
        }
    }

    class Mino_Z extends Mino {
        public Mino_Z() {
            create(Color.RED);
        }

        @Override
        public void setXY(int x, int y) {
            b[0].x = x;
            b[0].y = y;
            b[1].x = x - Block.SIZE;
            b[1].y = y;
            b[2].x = x;
            b[2].y = y + Block.SIZE;
            b[3].x = x + Block.SIZE;
            b[3].y = y + Block.SIZE;

            for (int i = 0; i < 4; i++) {
                tempB[i].x = b[i].x;
                tempB[i].y = b[i].y;
            }
        }

        @Override
        public void updateXY(int direction) {
            this.direction = direction;
            
            int x = b[0].x;
            int y = b[0].y;

            switch (direction) {
                case 1, 3 -> {
                    tempB[1].x = x - Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x;
                    tempB[2].y = y + Block.SIZE;
                    tempB[3].x = x + Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
                case 2, 4 -> {
                    tempB[1].x = x;
                    tempB[1].y = y - Block.SIZE;
                    tempB[2].x = x - Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x - Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
            }
        }
    }

    class Mino_J extends Mino {
        public Mino_J() {
            create(Color.BLUE);
        }

        @Override
        public void setXY(int x, int y) {
            b[0].x = x;
            b[0].y = y;
            b[1].x = x;
            b[1].y = y - Block.SIZE;
            b[2].x = x;
            b[2].y = y + Block.SIZE;
            b[3].x = x - Block.SIZE;
            b[3].y = y + Block.SIZE;

            for (int i = 0; i < 4; i++) {
                tempB[i].x = b[i].x;
                tempB[i].y = b[i].y;
            }
        }

        @Override
        public void updateXY(int direction) {
            this.direction = direction;
            
            int x = b[0].x;
            int y = b[0].y;

            switch (direction) {
                case 1 -> {
                    tempB[1].x = x;
                    tempB[1].y = y - Block.SIZE;
                    tempB[2].x = x;
                    tempB[2].y = y + Block.SIZE;
                    tempB[3].x = x - Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
                case 2 -> {
                    tempB[1].x = x + Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x - Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x - Block.SIZE;
                    tempB[3].y = y - Block.SIZE;
                }
                case 3 -> {
                    tempB[1].x = x;
                    tempB[1].y = y + Block.SIZE;
                    tempB[2].x = x;
                    tempB[2].y = y - Block.SIZE;
                    tempB[3].x = x + Block.SIZE;
                    tempB[3].y = y - Block.SIZE;
                }
                case 4 -> {
                    tempB[1].x = x - Block.SIZE;
                    tempB[1].y = y;
                    tempB[2].x = x + Block.SIZE;
                    tempB[2].y = y;
                    tempB[3].x = x + Block.SIZE;
                    tempB[3].y = y + Block.SIZE;
                }
            }
        }
        
    }
 }