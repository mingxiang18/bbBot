package com.bb.bot.handler.russianRoulette;

import java.util.Random;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * 俄罗斯转盘游戏类
 * @author ren
 */
public class RussianRoulette {
    /**
     * 随机数生成器
     */
    private final Random random;
    /**
     * 弹夹容量
     */
    private final int chamberSize;
    /**
     * 子弹位置
     */
    private final int bulletPosition;
    /**
     * 当前旋转到的位置
     */
    private int currentPosition;
    /**
     * 参与的用户集合
     */
    private final Set<String> userSet = new CopyOnWriteArraySet<>();

    /**
     * 构造函数，初始化游戏
     */
    public RussianRoulette(int chamberSize) {
        this.random = new Random();
        this.chamberSize = chamberSize;
        this.bulletPosition = random.nextInt(chamberSize);
        this.currentPosition = 0;
    }

    /**
     * 参与游戏
     */
    public void joinRussianRoulette(String userId) {
        userSet.add(userId);
    }

    /**
     * 旋转弹夹
     */
    public void spinChamber(String userId) {
        if (!userSet.contains(userId)) {
            throw new IllegalArgumentException("你还没有参与进游戏呢！请发送“参与俄罗斯转盘”进行参与噢");
        }
        currentPosition = random.nextInt(chamberSize);
    }

    /**
     * 扣动扳机
     */
    public boolean pullTrigger(String userId) {
        if (!userSet.contains(userId)) {
            throw new IllegalArgumentException("你还没有参与进游戏呢！请发送“参与俄罗斯转盘”进行参与噢");
        }
        boolean result = currentPosition == bulletPosition;
        currentPosition = (currentPosition + 1) % chamberSize;
        return result;
    }

    /**
     * 控制台测试游戏的方法
     */
    public void startGame() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("欢迎参加俄罗斯转盘游戏！");

        while (true) {
            System.out.println("输入 'spin' 旋转弹夹，输入 'shoot' 扣动扳机，输入 'exit' 退出游戏：");
            String command = scanner.nextLine();

            if (command.equalsIgnoreCase("spin")) {
                spinChamber("1");
                System.out.println("你旋转了弹夹。");
            } else if (command.equalsIgnoreCase("shoot")) {
                if (pullTrigger("1")) {
                    System.out.println("砰！你输了！");
                    break;
                } else {
                    System.out.println("咔哒！你还活着。");
                }
            } else if (command.equalsIgnoreCase("exit")) {
                System.out.println("游戏结束。");
                break;
            } else {
                System.out.println("无效命令，请重新输入。");
            }
        }

        scanner.close();
    }

    // 主方法，程序入口
    public static void main(String[] args) {
        RussianRoulette game = new RussianRoulette(6); // 6表示弹夹容量为6
        game.startGame();
    }
}
