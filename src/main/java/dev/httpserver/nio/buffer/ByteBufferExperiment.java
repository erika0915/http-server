package dev.httpserver.nio.buffer;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class ByteBufferExperiment {

    public static void main(String[] args) {
        runBasicFlipClearExperiment();
        runCompactExperiment();
        runRewindExperiment();
    }

    private static void runBasicFlipClearExperiment() {
        printTitle("1. put -> flip -> get -> clear");

        /*
         * capacity가 10인 ByteBuffer를 만듭니다.
         *
         * 처음 상태:
         * - position: 0
         * - limit: 10
         * - capacity: 10
         *
         * position은 다음에 읽거나 쓸 위치입니다.
         * limit은 현재 모드에서 읽거나 쓸 수 있는 끝 위치입니다.
         * capacity는 버퍼의 전체 크기이며 한 번 만들어지면 바뀌지 않습니다.
         */
        ByteBuffer buffer = ByteBuffer.allocate(10);
        printState("allocate(10) 직후", buffer);

        /*
         * put()은 buffer에 바이트를 씁니다.
         * "abc"는 UTF-8 기준으로 3바이트입니다.
         *
         * put 전에는 position이 0입니다.
         * put 후에는 a, b, c 세 바이트를 썼기 때문에 position이 3이 됩니다.
         */
        printState("\"abc\" put 전", buffer);
        buffer.put("abc".getBytes(StandardCharsets.UTF_8));
        printState("\"abc\" put 후", buffer);

        /*
         * put 직후 buffer는 쓰기 모드 상태입니다.
         *
         * position은 3, limit은 10입니다.
         * 그런데 이제 get()으로 읽고 싶다면 0번 위치부터 3번 위치 전까지 읽어야 합니다.
         *
         * flip()은 다음처럼 읽기 모드로 바꿉니다.
         * - limit = 현재 position
         * - position = 0
         */
        printState("flip() 호출 전", buffer);
        buffer.flip();
        printState("flip() 호출 후", buffer);

        /*
         * get()은 현재 position의 바이트를 읽고 position을 1 증가시킵니다.
         */
        printState("첫 번째 get() 전", buffer);
        byte first = buffer.get();
        System.out.println("읽은 문자: " + (char) first);
        printState("첫 번째 get() 후", buffer);

        printState("두 번째 get() 전", buffer);
        byte second = buffer.get();
        System.out.println("읽은 문자: " + (char) second);
        printState("두 번째 get() 후", buffer);

        /*
         * clear()는 데이터를 0으로 지우는 메서드가 아닙니다.
         *
         * clear()는 다음 쓰기를 준비하도록 position과 limit을 초기화합니다.
         * - position = 0
         * - limit = capacity
         *
         * 기존 데이터는 메모리에 남아 있을 수 있지만,
         * 이제 새 데이터를 0번 위치부터 덮어쓸 준비가 된 상태입니다.
         */
        printState("clear() 호출 전", buffer);
        buffer.clear();
        printState("clear() 호출 후", buffer);
    }

    private static void runCompactExperiment() {
        printTitle("2. compact()가 필요한 상황");

        /*
         * compact()는 일부 데이터만 읽고, 아직 읽지 않은 데이터를 보존한 채
         * 뒤에 새 데이터를 이어 쓰고 싶을 때 사용합니다.
         */
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abcde".getBytes(StandardCharsets.UTF_8));
        printState("\"abcde\" put 후", buffer);

        /*
         * 읽기 위해 flip()을 호출합니다.
         * 이제 position은 0, limit은 5입니다.
         */
        buffer.flip();
        printState("flip() 후", buffer);

        /*
         * a, b 두 글자만 읽었다고 가정합니다.
         * 아직 c, d, e는 읽지 않은 데이터입니다.
         */
        System.out.println("읽은 문자: " + (char) buffer.get());
        System.out.println("읽은 문자: " + (char) buffer.get());
        printState("두 글자만 읽은 후", buffer);

        /*
         * 여기서 clear()를 호출하면 읽지 않은 c, d, e를 덮어쓸 수 있는 상태가 됩니다.
         * 하지만 compact()를 호출하면 읽지 않은 c, d, e를 버퍼 앞쪽으로 옮겨 보존합니다.
         *
         * compact() 후:
         * - 남은 데이터 c, d, e가 0번 위치부터 복사됩니다.
         * - position은 남은 데이터 바로 뒤인 3이 됩니다.
         * - limit은 capacity인 10이 됩니다.
         *
         * 즉, cde 뒤에 새 데이터를 이어 쓸 수 있습니다.
         */
        printState("compact() 호출 전", buffer);
        buffer.compact();
        printState("compact() 호출 후", buffer);

        /*
         * 남겨둔 cde 뒤에 XYZ를 이어 씁니다.
         * 버퍼의 논리적 내용은 cdeXYZ가 됩니다.
         */
        buffer.put("XYZ".getBytes(StandardCharsets.UTF_8));
        printState("\"XYZ\" 추가 put 후", buffer);

        /*
         * 다시 읽기 위해 flip()을 호출합니다.
         * cdeXYZ가 순서대로 읽힙니다.
         */
        buffer.flip();
        printState("다시 flip() 후", buffer);
        System.out.println("남은 데이터 + 새 데이터: " + readRemainingAsString(buffer));
        printState("모두 읽은 후", buffer);
    }

    private static void runRewindExperiment() {
        printTitle("3. rewind()가 필요한 상황");

        /*
         * rewind()는 같은 데이터를 처음부터 다시 읽고 싶을 때 사용합니다.
         *
         * flip()은 쓰기 모드에서 읽기 모드로 바꿀 때 사용합니다.
         * rewind()는 이미 읽기 모드인 buffer의 position만 0으로 되돌립니다.
         * limit은 그대로 유지됩니다.
         */
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.put("abc".getBytes(StandardCharsets.UTF_8));
        buffer.flip();
        printState("\"abc\" put 후 flip()", buffer);

        System.out.println("첫 번째 읽기: " + readRemainingAsString(buffer));
        printState("첫 번째 읽기 후", buffer);

        /*
         * position이 limit까지 이동했기 때문에 이 상태로는 더 읽을 데이터가 없습니다.
         * 같은 데이터를 다시 읽으려면 rewind()를 호출합니다.
         */
        printState("rewind() 호출 전", buffer);
        buffer.rewind();
        printState("rewind() 호출 후", buffer);

        System.out.println("두 번째 읽기: " + readRemainingAsString(buffer));
        printState("두 번째 읽기 후", buffer);
    }

    private static String readRemainingAsString(ByteBuffer buffer) {
        StringBuilder result = new StringBuilder();

        while (buffer.hasRemaining()) {
            result.append((char) buffer.get());
        }

        return result.toString();
    }

    private static void printTitle(String title) {
        System.out.println();
        System.out.println("========================================");
        System.out.println(title);
        System.out.println("========================================");
    }

    private static void printState(String label, ByteBuffer buffer) {
        System.out.printf(
                "%-24s | position=%2d, limit=%2d, capacity=%2d%n",
                label,
                buffer.position(),
                buffer.limit(),
                buffer.capacity()
        );
    }
}
